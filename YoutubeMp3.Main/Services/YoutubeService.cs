using System.Diagnostics;
using System.IO;
using System.Linq;
using YoutubeDLSharp;
using YoutubeDLSharp.Options;

namespace YoutubeMp3.Main.Services;

public sealed class YoutubeService : IYoutubeService
{
    private static readonly TimeSpan YtDlpUpdateCheckInterval = TimeSpan.FromDays(7);
    private static readonly TimeSpan YtDlpUpdateTimeout = TimeSpan.FromMinutes(3);

    private readonly YoutubeDL _youtubeDl;
    private readonly string _binaryDirectory;
    private readonly SemaphoreSlim _binaryPreparationLock = new(1, 1);
    private bool _binariesReady;
    private string? _cookieFile;

    public YoutubeService()
    {
        // yt-dlp is a frozen Python executable; on a non-English Windows locale it can
        // mis-decode/print non-ASCII text (e.g. Korean search queries) unless forced into UTF-8 mode.
        Environment.SetEnvironmentVariable("PYTHONUTF8", "1");

        _binaryDirectory = ToolPaths.ToolsDirectory;
        _youtubeDl = new YoutubeDL
        {
            YoutubeDLPath = ToolPaths.YtDlp,
            FFmpegPath = ToolPaths.FFmpeg,
            OutputFolder = _binaryDirectory,
            // true면 yt-dlp가 파일명을 ASCII로만 만들어 한글 제목이 "_"로 뭉개진다.
            // Windows 금지 문자(\ / : * ? " < > |)는 yt-dlp가 알아서 치환하므로 끄기만 하면 된다.
            RestrictFilenames = false,
        };
    }

    public void SetCookieFile(string? path) =>
        _cookieFile = string.IsNullOrWhiteSpace(path) ? null : path;

    public Task PrepareBinariesAsync(
        IProgress<AudioDownloadProgress>? progress = null,
        CancellationToken ct = default)
        => EnsureBinariesAsync(progress, ct);

    public async Task<IReadOnlyList<VideoSearchResult>> SearchAsync(
        string query,
        int maxResults = 20,
        CancellationToken ct = default)
    {
        await EnsureBinariesAsync(null, ct);

        var result = await _youtubeDl.RunVideoDataFetch(
            $"ytsearch{maxResults}:{query}", ct, flat: true, overrideOptions: CreateJsRuntimeOptions());

        if (!result.Success || result.Data?.Entries is null)
            throw new InvalidOperationException(string.Join(Environment.NewLine, result.ErrorOutput));

        return result.Data.Entries
            .Where(entry => !string.IsNullOrEmpty(entry.ID))
            .Select(entry => new VideoSearchResult(
                Title: string.IsNullOrEmpty(entry.Title) ? "(제목 없음)" : entry.Title,
                Channel: entry.Channel ?? entry.Uploader ?? string.Empty,
                Duration: FormatDuration(entry.Duration),
                Url: $"https://www.youtube.com/watch?v={entry.ID}"))
            .ToList();
    }

    public async Task<string> DownloadAudioAsync(
        string url,
        string outputDirectory,
        string? audioQuality = null,
        IProgress<AudioDownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        await EnsureBinariesAsync(progress, ct);

        _youtubeDl.OutputFolder = outputDirectory;

        var ytProgress = new Progress<DownloadProgress>(p =>
            progress?.Report(new AudioDownloadProgress(p.Progress * 100, DescribeState(p.State, p.DownloadSpeed, p.ETA))));

        var options = CreateJsRuntimeOptions();
        // OptionSet.AudioQuality는 byte(0~10 VBR)만 받아 "320K" 같은 고정 비트레이트를 표현할 수 없어,
        // yt-dlp에 --audio-quality 인자를 그대로 전달하는 커스텀 옵션으로 우회한다.
        if (!string.IsNullOrEmpty(audioQuality))
            options.AddCustomOption("--audio-quality", audioQuality);

        var startedAt = DateTime.Now.AddSeconds(-5);
        var result = await _youtubeDl.RunAudioDownload(
            url, AudioConversionFormat.Mp3, ct, ytProgress, overrideOptions: options);

        if (!result.Success)
            throw new InvalidOperationException(string.Join(Environment.NewLine, result.ErrorOutput));

        return ResolveDownloadedFile(result.Data, outputDirectory, startedAt);
    }

    // yt-dlp가 알려 주는 경로는 한글 제목이면 인코딩이 깨져 실제 파일과 다를 수 있다.
    // 그러면 재생목록 추가·바로 재생이 조용히 실패하므로, 파일명에 붙는 [영상ID](ASCII라 깨지지 않음)로
    // 출력 폴더에서 진짜 파일을 찾고, 그것도 안 되면 이번에 만들어진 가장 최근 mp3를 쓴다.
    private static string ResolveDownloadedFile(string reportedPath, string outputDirectory, DateTime startedAt)
    {
        if (!string.IsNullOrEmpty(reportedPath) && File.Exists(reportedPath))
            return reportedPath;

        var mp3s = new DirectoryInfo(outputDirectory).GetFiles("*.mp3");

        var id = string.IsNullOrEmpty(reportedPath)
            ? null
            : System.Text.RegularExpressions.Regex.Match(reportedPath, @"\[([A-Za-z0-9_-]{11})\]") is { Success: true } m
                ? m.Groups[1].Value
                : null;
        if (id is not null)
        {
            var byId = mp3s
                .Where(f => f.Name.Contains($"[{id}]", StringComparison.Ordinal))
                .OrderByDescending(f => f.LastWriteTime)
                .FirstOrDefault();
            if (byId is not null)
                return byId.FullName;
        }

        var newest = mp3s
            .Where(f => f.LastWriteTime >= startedAt)
            .OrderByDescending(f => f.LastWriteTime)
            .FirstOrDefault();
        return newest?.FullName ?? reportedPath;
    }

    // yt-dlp requires a JS runtime to extract from YouTube; point it at the deno
    // binary downloaded alongside yt-dlp since it won't be on PATH.
    // Note: this must not end in a trailing backslash - YoutubeDLSharp wraps the value in
    // double quotes verbatim, and a trailing "\" escapes that closing quote on Windows,
    // corrupting every argument that follows it on the command line.
    private OptionSet CreateJsRuntimeOptions()
    {
        var options = new OptionSet
        {
            JsRuntimes = new MultiValue<string>(new[] { $"deno:{ToolPaths.Deno}" }),
        };

        // "Sign in to confirm you're not a bot" 오류 대응: 내보낸 쿠키 파일을 실어 보낸다.
        // --cookies-from-browser(브라우저 DB 직접 복사)는 최신 Windows 브라우저에서 DB 잠금·
        // DPAPI 복호화 문제로 거의 항상 실패해 지원하지 않는다.
        if (_cookieFile is not null)
            options.Cookies = _cookieFile;

        return options;
    }

    private async Task EnsureBinariesAsync(IProgress<AudioDownloadProgress>? progress, CancellationToken ct)
    {
        if (_binariesReady)
            return;

        await _binaryPreparationLock.WaitAsync(ct);
        try
        {
            if (_binariesReady)
                return;

            if (!BinariesExist())
            {
                progress?.Report(new AudioDownloadProgress(0, "필수 프로그램(yt-dlp, FFmpeg, Deno) 다운로드 중... (최초 1회)"));
                await Utils.DownloadBinaries(skipExisting: true, directoryPath: _binaryDirectory, downloadJSRuntime: true);
                MarkYtDlpUpdateChecked();
            }
            else if (IsYtDlpUpdateCheckDue())
            {
                progress?.Report(new AudioDownloadProgress(0, "yt-dlp 새 버전 확인 중..."));
                await UpdateYtDlpAsync();
            }

            _binariesReady = true;
        }
        finally
        {
            _binaryPreparationLock.Release();
        }
    }

    private bool BinariesExist() =>
        File.Exists(_youtubeDl.YoutubeDLPath) &&
        File.Exists(_youtubeDl.FFmpegPath) &&
        File.Exists(ToolPaths.Deno);

    private static bool IsYtDlpUpdateCheckDue()
    {
        var stamp = new FileInfo(ToolPaths.YtDlpUpdateStamp);
        return !stamp.Exists || DateTime.UtcNow - stamp.LastWriteTimeUtc >= YtDlpUpdateCheckInterval;
    }

    private static void MarkYtDlpUpdateChecked()
    {
        try
        {
            File.WriteAllBytes(ToolPaths.YtDlpUpdateStamp, []);
        }
        catch
        {
            // 기록하지 못하면 다음 실행 때 한 번 더 확인할 뿐이다.
        }
    }

    // YouTube가 자주 바뀌어 yt-dlp가 오래되면 403 등으로 다운로드가 막힌다. yt-dlp 자체 업데이트(-U)로
    // 새 버전이 있을 때만 exe를 바꿔 둔다. 실패(오프라인 등)해도 지금 있는 버전으로 계속 쓰고,
    // 확인 시각을 남기지 않아 다음 실행 때 다시 시도한다.
    private async Task UpdateYtDlpAsync()
    {
        try
        {
            var startInfo = new ProcessStartInfo(_youtubeDl.YoutubeDLPath, "-U")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
            };

            using var process = new Process { StartInfo = startInfo };
            process.Start();

            using var timeout = new CancellationTokenSource(YtDlpUpdateTimeout);
            try
            {
                await process.WaitForExitAsync(timeout.Token);
            }
            catch (OperationCanceledException)
            {
                process.Kill(entireProcessTree: true);
                return;
            }

            if (process.ExitCode == 0)
                MarkYtDlpUpdateChecked();
        }
        catch
        {
        }
    }

    private static string FormatDuration(float? seconds)
    {
        if (seconds is null)
            return string.Empty;

        var span = TimeSpan.FromSeconds(seconds.Value);
        return span.Hours > 0 ? span.ToString(@"h\:mm\:ss") : span.ToString(@"m\:ss");
    }

    private static string DescribeState(DownloadState state, string downloadSpeed, string eta) => state switch
    {
        DownloadState.PreProcessing => "준비 중...",
        DownloadState.Downloading => string.IsNullOrEmpty(downloadSpeed)
            ? "다운로드 중..."
            : $"다운로드 중... {downloadSpeed} (남은 시간 {eta})",
        DownloadState.PostProcessing => "MP3로 변환 중...",
        DownloadState.Success => "완료",
        DownloadState.Error => "오류 발생",
        _ => string.Empty,
    };
}

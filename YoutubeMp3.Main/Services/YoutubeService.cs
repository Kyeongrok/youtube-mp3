using System.IO;
using System.Linq;
using YoutubeDLSharp;
using YoutubeDLSharp.Options;

namespace YoutubeMp3.Main.Services;

public sealed class YoutubeService : IYoutubeService
{
    private readonly YoutubeDL _youtubeDl;
    private readonly string _binaryDirectory;
    private readonly SemaphoreSlim _binaryPreparationLock = new(1, 1);
    private bool _binariesReady;

    public YoutubeService()
    {
        // yt-dlp is a frozen Python executable; on a non-English Windows locale it can
        // mis-decode/print non-ASCII text (e.g. Korean search queries) unless forced into UTF-8 mode.
        Environment.SetEnvironmentVariable("PYTHONUTF8", "1");

        _binaryDirectory = AppContext.BaseDirectory;
        _youtubeDl = new YoutubeDL
        {
            YoutubeDLPath = Path.Combine(_binaryDirectory, "yt-dlp.exe"),
            FFmpegPath = Path.Combine(_binaryDirectory, "ffmpeg.exe"),
            OutputFolder = _binaryDirectory,
            RestrictFilenames = true,
        };
    }

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
        IProgress<AudioDownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        await EnsureBinariesAsync(progress, ct);

        _youtubeDl.OutputFolder = outputDirectory;

        var ytProgress = new Progress<DownloadProgress>(p =>
            progress?.Report(new AudioDownloadProgress(p.Progress * 100, DescribeState(p.State, p.DownloadSpeed, p.ETA))));

        var result = await _youtubeDl.RunAudioDownload(
            url, AudioConversionFormat.Mp3, ct, ytProgress, overrideOptions: CreateJsRuntimeOptions());

        if (!result.Success)
            throw new InvalidOperationException(string.Join(Environment.NewLine, result.ErrorOutput));

        return result.Data;
    }

    // yt-dlp requires a JS runtime to extract from YouTube; point it at the deno
    // binary downloaded alongside yt-dlp since it won't be on PATH.
    // Note: this must not end in a trailing backslash - YoutubeDLSharp wraps the value in
    // double quotes verbatim, and a trailing "\" escapes that closing quote on Windows,
    // corrupting every argument that follows it on the command line.
    private OptionSet CreateJsRuntimeOptions() => new()
    {
        JsRuntimes = new MultiValue<string>(new[] { $"deno:{Path.Combine(_binaryDirectory, "deno.exe")}" }),
    };

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
        File.Exists(Path.Combine(_binaryDirectory, "deno.exe"));

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

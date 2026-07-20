using System.Diagnostics;
using System.Globalization;
using System.IO;

namespace YoutubeMp3.Main.Services;

/// <summary>
/// 다운로드에 쓰는 번들 FFmpeg를 그대로 재사용해 mp3 볼륨을 dB 단위로 조절한다.
/// FFmpeg의 volume 필터는 "volume=3dB"처럼 데시벨을 직접 받으므로 게인 계산이 필요 없다.
/// </summary>
public sealed class AudioGainService : IAudioGainService
{
    private readonly string _ffmpegPath = Path.Combine(AppContext.BaseDirectory, "ffmpeg.exe");

    public async Task<string> AdjustGainAsync(string inputPath, double gainDb, CancellationToken ct = default)
    {
        if (!File.Exists(inputPath))
            throw new FileNotFoundException("파일을 찾을 수 없습니다.", inputPath);

        // FFmpeg는 최초 MP3 추출 시 yt-dlp가 함께 내려받는다. 그 전이면 아직 없을 수 있다.
        if (!File.Exists(_ffmpegPath))
            throw new FileNotFoundException(
                "FFmpeg가 아직 없습니다. MP3를 한 번 추출하면 FFmpeg가 자동으로 설치됩니다.", _ffmpegPath);

        var directory = Path.GetDirectoryName(inputPath) ?? string.Empty;
        var name = Path.GetFileNameWithoutExtension(inputPath);
        var suffix = gainDb >= 0 ? $"+{gainDb:0.#}dB" : $"-{Math.Abs(gainDb):0.#}dB";
        var outputPath = Path.Combine(directory, $"{name}_{suffix}.mp3");

        var db = gainDb.ToString("0.###", CultureInfo.InvariantCulture);
        var arguments =
            $"-y -i \"{inputPath}\" -af \"volume={db}dB\" -c:a libmp3lame -q:a 2 \"{outputPath}\"";

        var startInfo = new ProcessStartInfo(_ffmpegPath, arguments)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardError = true,
        };

        using var process = new Process { StartInfo = startInfo };
        process.Start();

        // FFmpeg는 진행/오류 로그를 모두 stderr로 내보낸다. 실패 시 원인을 담아 던지기 위해 읽어 둔다.
        var errorOutput = await process.StandardError.ReadToEndAsync(ct);
        await process.WaitForExitAsync(ct);

        if (process.ExitCode != 0)
            throw new InvalidOperationException($"FFmpeg 변환 실패: {errorOutput}");

        return outputPath;
    }
}

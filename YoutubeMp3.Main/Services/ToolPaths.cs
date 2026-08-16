using System.IO;

namespace YoutubeMp3.Main.Services;

/// <summary>
/// 앱이 내려받아 쓰는 외부 실행 파일(yt-dlp, FFmpeg/FFprobe, Deno)을 어디에 둘지 정한다.
/// 포터블 exe를 바탕화면에서 실행하면 exe 옆에 이 파일들이 쏟아져 폴더가 지저분해지므로,
/// 기본 위치를 %LocalAppData%\YoutubeMp3\tools로 잡아 한곳에 모은다.
/// </summary>
public static class ToolPaths
{
    private static readonly Lazy<string> LazyDirectory = new(ResolveDirectory);

    /// <summary>외부 실행 파일들이 있는(또는 내려받을) 폴더.</summary>
    public static string ToolsDirectory => LazyDirectory.Value;

    public static string YtDlp => Path.Combine(ToolsDirectory, "yt-dlp.exe");

    public static string FFmpeg => Path.Combine(ToolsDirectory, "ffmpeg.exe");

    public static string Deno => Path.Combine(ToolsDirectory, "deno.exe");

    private static string ResolveDirectory()
    {
        // 개발 빌드처럼 exe 옆에 이미 받아둔 파일이 있으면 다시 받지 않고 그대로 쓴다.
        var besideExe = AppContext.BaseDirectory;
        if (File.Exists(Path.Combine(besideExe, "yt-dlp.exe")))
            return besideExe;

        var toolsDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "YoutubeMp3", "tools");

        try
        {
            Directory.CreateDirectory(toolsDirectory);
        }
        catch
        {
            // 만들 수 없으면(권한 등) 예전처럼 exe 옆에 둔다.
            return besideExe;
        }

        return toolsDirectory;
    }
}

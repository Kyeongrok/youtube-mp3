using System.IO;

namespace YoutubeMp3.Main.Services;

/// <summary>
/// 재생목록의 "편집" 메뉴가 띄우는 YoutubeMp3.Editor(구간 잘라내기 도구)의 실행 파일 위치를 찾는다.
/// </summary>
public static class EditorPaths
{
    /// <summary>구간 잘라내기 도구의 exe 경로. 찾지 못하면 null.</summary>
    public static string? FindEditorExe()
    {
        // 배포판(publish)에서는 release.yml이 YoutubeMp3.Editor.exe를 YoutubeMp3.exe와 같은 폴더에 담아 낸다.
        var besideExe = Path.Combine(AppContext.BaseDirectory, "YoutubeMp3.Editor.exe");
        if (File.Exists(besideExe))
            return besideExe;

        // 개발 빌드(dotnet build)에서는 형제 프로젝트라 상대 경로로 계산할 수 있다.
        // YoutubeMp3.exe 는 YoutubeMp3\YoutubeMp3\bin\{Config}\net8.0-windows\ 에 있고,
        // YoutubeMp3.Editor.exe 는 그 옆의 YoutubeMp3.Editor\bin\{Config}\net8.0-windows\ 에 있다.
        var solutionDir = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", ".."));

        foreach (var config in new[] { "Release", "Debug" })
        {
            var path = Path.Combine(solutionDir, "YoutubeMp3.Editor", "bin", config, "net8.0-windows", "YoutubeMp3.Editor.exe");
            if (File.Exists(path))
                return path;
        }

        return null;
    }
}

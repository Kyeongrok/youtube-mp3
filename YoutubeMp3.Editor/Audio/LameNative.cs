using System.IO;
using System.Reflection;
using NAudio.Lame;

namespace YoutubeMp3.Editor.Audio;

/// <summary>
/// LAME 인코더의 네이티브 DLL(libmp3lame.32/64.dll)을 찾을 수 있게 해 준다.
/// NAudio.Lame은 이 DLL을 exe 옆에서 찾지만, 단일 exe로 배포하면 번들에 묶이지 않아 옆에 없다.
/// (NuGet이 이 DLL들을 그냥 복사 항목으로 넣기 때문. csproj에서 어셈블리에 함께 심어 둔다.)
/// 그래서 심어 둔 사본을 로컬 폴더에 풀고 그 위치를 NAudio.Lame에 알려 준다.
/// </summary>
internal static class LameNative
{
    private static readonly object Lock = new();
    private static bool _prepared;

    private static readonly string[] DllNames = { "libmp3lame.32.dll", "libmp3lame.64.dll" };

    /// <summary>MP3로 저장하기 직전에 호출한다. 두 번째부터는 아무 일도 하지 않는다.</summary>
    public static void Ensure()
    {
        if (_prepared) return;
        lock (Lock)
        {
            if (_prepared) return;
            _prepared = true;

            // 개발 빌드처럼 exe 옆에 이미 있으면 NAudio.Lame이 알아서 찾는다.
            if (File.Exists(Path.Combine(AppContext.BaseDirectory, "libmp3lame.64.dll")))
                return;

            var directory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "YoutubeMp3", "lame");
            Directory.CreateDirectory(directory);

            var assembly = Assembly.GetExecutingAssembly();
            foreach (var name in DllNames)
            {
                var target = Path.Combine(directory, name);
                using var source = assembly.GetManifestResourceStream(name);
                if (source is null)
                    continue;

                // 이미 풀어 둔 파일이 크기까지 같으면 다시 쓰지 않는다(다른 창이 쓰는 중일 수 있다).
                if (File.Exists(target) && new FileInfo(target).Length == source.Length)
                    continue;

                using var destination = File.Create(target);
                source.CopyTo(destination);
            }

            LameDLL.LoadNativeDLL(directory);
        }
    }
}

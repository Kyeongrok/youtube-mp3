using System.IO;
using System.Text.Json;

namespace YoutubeMp3.Forms;

/// <summary>
/// 타이틀바 맨 왼쪽 햄버거(☰) 버튼의 설정 창에서 바꾸는 사용자 설정.
/// %AppData%\YoutubeMp3\settings.json에 저장해 다음 실행에도 유지한다.
/// </summary>
public sealed class AppSettings
{
    /// <summary>플레이어 화면 최소 창 높이의 기본값.</summary>
    public const double DefaultPlayerMinimumHeight = 800;

    /// <summary>창이 너무 납작해지지 않도록 설정에서 받아들이는 최소값(MainWindow의 MinHeight와 맞춘다).</summary>
    public const double MinimumAllowedPlayerHeight = 300;

    private static readonly string SettingsPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "YoutubeMp3", "settings.json");

    /// <summary>플레이어 화면으로 갈 때 창을 이 높이까지 키운다. 이미 더 크면 그대로 둔다.</summary>
    public double PlayerMinimumHeight { get; set; } = DefaultPlayerMinimumHeight;

    public static AppSettings Load()
    {
        try
        {
            if (File.Exists(SettingsPath))
            {
                var loaded = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(SettingsPath));
                if (loaded is not null)
                {
                    // 손으로 고친 값이 말이 안 되면 기본값으로 되돌린다.
                    if (loaded.PlayerMinimumHeight < MinimumAllowedPlayerHeight)
                        loaded.PlayerMinimumHeight = DefaultPlayerMinimumHeight;
                    return loaded;
                }
            }
        }
        catch
        {
            // 설정 파일이 없거나 깨졌으면 기본값으로 시작한다.
        }

        return new AppSettings();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(SettingsPath)!);
            File.WriteAllText(
                SettingsPath,
                JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // 저장 실패는 무시(이번 실행에만 적용된다).
        }
    }
}

namespace YoutubeMp3.Main.Services;

public interface IAudioGainService
{
    /// <summary>
    /// mp3 파일의 볼륨을 <paramref name="gainDb"/> 데시벨만큼 조절(양수=증가, 음수=감소)해
    /// 원본 옆에 접미사를 붙인 새 mp3로 저장하고, 저장한 경로를 돌려준다.
    /// </summary>
    Task<string> AdjustGainAsync(string inputPath, double gainDb, CancellationToken ct = default);
}

namespace YoutubeMp3.Main.Services;

public interface IYoutubeService
{
    /// <summary>
    /// 필수 실행 파일(yt-dlp, FFmpeg, Deno)이 없으면 내려받아 둔다. 이미 있으면 즉시 반환한다.
    /// 앱 시작 시 백그라운드로 호출해 두면 검색·다운로드·볼륨 조절이 곧바로 동작한다.
    /// </summary>
    Task PrepareBinariesAsync(
        IProgress<AudioDownloadProgress>? progress = null,
        CancellationToken ct = default);

    Task<IReadOnlyList<VideoSearchResult>> SearchAsync(
        string query,
        int maxResults = 20,
        CancellationToken ct = default);

    /// <param name="audioQuality">ffmpeg 오디오 비트레이트(예: "128K", "192K", "320K"). null이면 yt-dlp 기본값을 쓴다.</param>
    Task<string> DownloadAudioAsync(
        string url,
        string outputDirectory,
        string? audioQuality = null,
        IProgress<AudioDownloadProgress>? progress = null,
        CancellationToken ct = default);
}

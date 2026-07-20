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

    Task<string> DownloadAudioAsync(
        string url,
        string outputDirectory,
        IProgress<AudioDownloadProgress>? progress = null,
        CancellationToken ct = default);
}

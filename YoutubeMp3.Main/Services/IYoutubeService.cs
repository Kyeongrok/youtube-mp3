namespace YoutubeMp3.Main.Services;

public interface IYoutubeService
{
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

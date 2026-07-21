namespace YoutubeMp3.Main.Services;

public interface IYoutubeService
{
    /// <summary>
    /// 이후의 검색·다운로드에 쓸 쿠키 파일(Netscape 형식 cookies.txt, 브라우저 확장 프로그램으로 내보낸 것)을 지정한다.
    /// YouTube가 "Sign in to confirm you're not a bot"로 막을 때 이 쿠키를 실어 우회한다.
    /// 브라우저 DB를 직접 복사하는 방식(--cookies-from-browser)은 최신 Windows 브라우저에서
    /// DB 잠금·DPAPI 복호화 문제로 거의 항상 실패해 지원하지 않는다. null/빈 문자열이면 해제한다.
    /// </summary>
    void SetCookieFile(string? path);

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

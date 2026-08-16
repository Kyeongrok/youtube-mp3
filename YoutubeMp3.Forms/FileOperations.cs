using System.IO;

namespace YoutubeMp3.Forms;

/// <summary>
/// 재생을 끊은 직후에도 MediaPlayer가 파일 핸들을 놓기까지 잠깐 걸려 삭제·이름 변경이 실패할 수 있다.
/// 잠금이 풀릴 때까지 짧게 다시 시도한다.
/// </summary>
internal static class FileOperations
{
    private const int MaxAttempts = 6;

    private static readonly TimeSpan RetryDelay = TimeSpan.FromMilliseconds(150);

    public static Task DeleteAsync(string path) => RetryAsync(() => File.Delete(path));

    public static Task MoveAsync(string sourcePath, string destinationPath) =>
        RetryAsync(() => File.Move(sourcePath, destinationPath));

    private static async Task RetryAsync(Action action)
    {
        for (var attempt = 1; ; attempt++)
        {
            try
            {
                action();
                return;
            }
            catch (Exception ex) when (attempt < MaxAttempts && ex is IOException or UnauthorizedAccessException)
            {
                await Task.Delay(RetryDelay);
            }
        }
    }
}

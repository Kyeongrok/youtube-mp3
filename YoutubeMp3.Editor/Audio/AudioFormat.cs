namespace YoutubeMp3.Editor.Audio;

public enum AudioFormat
{
    M4a,
    Mp3,
    Wav
}

public static class AudioFormatExtensions
{
    public static string ToExtension(this AudioFormat format) => format switch
    {
        AudioFormat.M4a => ".m4a",
        AudioFormat.Mp3 => ".mp3",
        AudioFormat.Wav => ".wav",
        _ => throw new ArgumentOutOfRangeException(nameof(format), format, null)
    };

    /// <summary>파일 확장자로부터 알맞은 내보내기 포맷을 추정한다. 모르는 확장자면 M4a로 기본 지정한다.</summary>
    public static AudioFormat FromExtension(string extension) => extension.ToLowerInvariant() switch
    {
        ".wav" => AudioFormat.Wav,
        ".mp3" => AudioFormat.Mp3,
        ".m4a" or ".mp4" or ".aac" => AudioFormat.M4a,
        _ => AudioFormat.M4a
    };
}

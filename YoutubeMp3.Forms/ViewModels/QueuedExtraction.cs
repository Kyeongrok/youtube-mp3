namespace YoutubeMp3.Forms.ViewModels;

/// <summary>MP3 추출 대기열의 항목. 대기열[0]이 현재 처리 중인 항목이다.</summary>
public sealed record QueuedExtraction(string Url, string Title);

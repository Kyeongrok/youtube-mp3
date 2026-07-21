namespace YoutubeMp3.Main.Services;

public interface IFileTransferService
{
    /// <summary>이 PC가 속한 Wi-Fi/LAN에서 접근 가능한 IPv4 주소 목록(Wi-Fi 우선).</summary>
    IReadOnlyList<string> GetLocalAddresses();

    /// <summary>지정한 파일을 로컬 웹서버로 열어 세션을 반환한다. 세션의 Url로 접속하면 파일이 내려받아진다.</summary>
    FileTransferSession Start(string filePath);
}

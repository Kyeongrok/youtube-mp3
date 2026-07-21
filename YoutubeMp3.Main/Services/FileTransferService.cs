using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace YoutubeMp3.Main.Services;

/// <summary>
/// HttpListener는 Windows에서 비-루프백 prefix로 열려면 관리자 권한이나 URL ACL 등록이 필요해서,
/// 대신 TcpListener로 최소한의 HTTP 응답만 직접 구현한다(단일 파일을 1회 서빙하는 용도라 충분하다).
/// </summary>
public sealed class FileTransferService : IFileTransferService
{
    public IReadOnlyList<string> GetLocalAddresses()
    {
        var wifi = new List<string>();
        var other = new List<string>();

        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up)
                continue;
            if (nic.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel)
                continue;

            foreach (var addr in nic.GetIPProperties().UnicastAddresses)
            {
                if (addr.Address.AddressFamily != AddressFamily.InterNetwork)
                    continue;
                if (IPAddress.IsLoopback(addr.Address))
                    continue;
                if (addr.Address.ToString().StartsWith("169.254.")) // APIPA(연결 안 됨)
                    continue;

                var text = addr.Address.ToString();
                if (nic.NetworkInterfaceType == NetworkInterfaceType.Wireless80211)
                    wifi.Add(text);
                else
                    other.Add(text);
            }
        }

        wifi.AddRange(other);
        return wifi;
    }

    public FileTransferSession Start(string filePath)
    {
        if (!File.Exists(filePath))
            throw new FileNotFoundException("전송할 파일을 찾을 수 없습니다.", filePath);

        var listener = new TcpListener(IPAddress.Any, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;

        var session = new FileTransferSession(listener, filePath, port);
        session.Start();
        return session;
    }
}

using System.IO;
using System.Net.Sockets;
using System.Text;

namespace YoutubeMp3.Main.Services;

/// <summary>
/// 파일 하나를 서빙하는 로컬 HTTP 서버 세션. 여러 기기가 같은 QR/링크로 반복해서
/// 내려받을 수 있도록 Dispose될 때까지 계속 연결을 받는다.
/// </summary>
public sealed class FileTransferSession : IDisposable
{
    private readonly TcpListener _listener;
    private readonly string _filePath;
    private readonly CancellationTokenSource _cts = new();
    private bool _disposed;

    public int Port { get; }

    public string FileName { get; }

    /// <summary>파일 하나가 다 전송될 때마다 발생(같은 링크로 다시 받을 수도 있으므로 서버는 계속 켜져 있다).</summary>
    public event Action? Completed;

    /// <summary>개별 연결 처리 중 오류(서버 자체는 계속 다음 연결을 받는다).</summary>
    public event Action<Exception>? Failed;

    internal FileTransferSession(TcpListener listener, string filePath, int port)
    {
        _listener = listener;
        _filePath = filePath;
        Port = port;
        FileName = Path.GetFileName(filePath);
    }

    internal void Start() => _ = AcceptLoopAsync(_cts.Token);

    private async Task AcceptLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            TcpClient client;
            try
            {
                client = await _listener.AcceptTcpClientAsync(ct);
            }
            catch (OperationCanceledException)
            {
                return; // 사용자가 중지했거나 화면을 떠난 정상 종료.
            }
            catch (ObjectDisposedException)
            {
                return; // 리스너가 이미 닫힌 뒤의 정상 종료.
            }

            try
            {
                using (client)
                    await SendFileAsync(client, ct);
                Completed?.Invoke();
            }
            catch (Exception ex)
            {
                // 이번 연결만 실패로 알리고 서버는 계속 다음 스캔을 받는다.
                Failed?.Invoke(ex);
            }
        }
    }

    private async Task SendFileAsync(TcpClient client, CancellationToken ct)
    {
        await using var networkStream = client.GetStream();

        // 요청 라인/헤더 내용은 필요 없다. 연결이 왔다는 사실만으로 파일을 흘려보낸다.
        using var reader = new StreamReader(networkStream, Encoding.ASCII, false, 1024, leaveOpen: true);
        await reader.ReadLineAsync(ct);

        await using var fileStream = File.OpenRead(_filePath);

        // 한글 등 비-ASCII 파일명은 RFC 5987(filename*=UTF-8''...)로 함께 실어 보낸다.
        var asciiFallbackName = "download" + Path.GetExtension(FileName);
        var encodedName = Uri.EscapeDataString(FileName);
        var header =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            $"Content-Length: {fileStream.Length}\r\n" +
            $"Content-Disposition: attachment; filename=\"{asciiFallbackName}\"; filename*=UTF-8''{encodedName}\r\n" +
            "Connection: close\r\n\r\n";
        var headerBytes = Encoding.ASCII.GetBytes(header);
        await networkStream.WriteAsync(headerBytes, ct);
        await fileStream.CopyToAsync(networkStream, ct);
    }

    public void Dispose()
    {
        if (_disposed)
            return;
        _disposed = true;
        _cts.Cancel();
        _listener.Stop();
        _cts.Dispose();
    }
}

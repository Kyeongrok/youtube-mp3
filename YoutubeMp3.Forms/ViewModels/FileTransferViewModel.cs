using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Windows;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using QRCoder;
using QRCoder.Xaml;
using YoutubeMp3.Main.Services;

namespace YoutubeMp3.Forms.ViewModels;

/// <summary>전송 대상 mp3 한 곡. 체크박스로 선택 여부를 나타낸다.</summary>
public partial class TransferableFile : ObservableObject
{
    public TransferableFile(string path)
    {
        Path = path;
        Name = System.IO.Path.GetFileName(path);
    }

    public string Path { get; }

    public string Name { get; }

    [ObservableProperty]
    private bool _isSelected;
}

/// <summary>
/// 다운로드 폴더의 mp3 중 체크박스로 고른 파일들을 같은 Wi-Fi의 휴대폰으로 QR 코드 하나로 보낸다.
/// 여러 곡을 고르면 zip으로 묶는다. 선택이 바뀔 때마다 QR/링크를 즉시 다시 만든다.
/// </summary>
public partial class FileTransferViewModel : ObservableObject
{
    private readonly IFileTransferService _fileTransferService;
    private FileTransferSession? _session;
    private string? _tempZipPath;

    public FileTransferViewModel(IFileTransferService fileTransferService)
    {
        _fileTransferService = fileTransferService;
    }

    public ObservableCollection<TransferableFile> AvailableFiles { get; } = new();

    [ObservableProperty]
    private string _status = "보낼 파일을 선택하세요";

    [ObservableProperty]
    private string? _transferUrl;

    [ObservableProperty]
    private DrawingImage? _qrImage;

    /// <summary>이 화면으로 들어올 때마다 다운로드 폴더를 다시 스캔한다(체크 상태는 파일별로 유지).</summary>
    public void RefreshFiles()
    {
        var previouslyChecked = AvailableFiles.Where(f => f.IsSelected).Select(f => f.Path).ToHashSet();

        foreach (var item in AvailableFiles)
            item.PropertyChanged -= OnFileSelectionChanged;
        AvailableFiles.Clear();

        var directory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.MyMusic), "YoutubeMp3");

        if (Directory.Exists(directory))
        {
            foreach (var file in Directory.EnumerateFiles(directory, "*.mp3")
                         .OrderByDescending(File.GetLastWriteTime))
            {
                var item = new TransferableFile(file) { IsSelected = previouslyChecked.Contains(file) };
                item.PropertyChanged += OnFileSelectionChanged;
                AvailableFiles.Add(item);
            }
        }

        // 처음 들어와서 아무것도 선택된 게 없으면 가장 최근 파일을 기본 선택해 QR이 바로 뜨게 한다.
        if (AvailableFiles.Count > 0 && !AvailableFiles.Any(f => f.IsSelected))
            AvailableFiles[0].IsSelected = true;
        else
            RegenerateSession();
    }

    private void OnFileSelectionChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(TransferableFile.IsSelected))
            RegenerateSession();
    }

    private void RegenerateSession()
    {
        StopSession();

        var selected = AvailableFiles.Where(f => f.IsSelected).Select(f => f.Path).ToList();
        if (selected.Count == 0)
        {
            Status = "보낼 파일을 선택하세요";
            return;
        }

        try
        {
            var address = _fileTransferService.GetLocalAddresses().FirstOrDefault();
            if (address is null)
            {
                Status = "Wi-Fi/LAN에 연결되어 있지 않습니다";
                return;
            }

            var filePath = selected.Count == 1 ? selected[0] : CreateZip(selected);
            if (selected.Count > 1)
                _tempZipPath = filePath;

            _session = _fileTransferService.Start(filePath);
            _session.Completed += OnCompleted;
            _session.Failed += OnFailed;

            TransferUrl = $"http://{address}:{_session.Port}/{Uri.EscapeDataString(_session.FileName)}";
            QrImage = BuildQrImage(TransferUrl);
            Status = selected.Count == 1
                ? "같은 Wi-Fi의 휴대폰 카메라로 QR을 스캔하세요"
                : $"{selected.Count}곡을 묶었습니다 · 같은 Wi-Fi의 휴대폰 카메라로 QR을 스캔하세요";
        }
        catch (Exception ex)
        {
            Status = $"전송 준비 실패: {ex.Message}";
        }
    }

    private static string CreateZip(IReadOnlyList<string> files)
    {
        var tempDir = Path.Combine(Path.GetTempPath(), "YoutubeMp3Transfer");
        Directory.CreateDirectory(tempDir);
        var zipPath = Path.Combine(tempDir, $"YoutubeMp3_{DateTime.Now:yyyyMMdd_HHmmss}.zip");

        using var archive = ZipFile.Open(zipPath, ZipArchiveMode.Create);
        var usedNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var file in files)
        {
            var name = Path.GetFileName(file);
            var candidate = name;
            var i = 1;
            while (!usedNames.Add(candidate))
                candidate = $"{Path.GetFileNameWithoutExtension(name)} ({i++}){Path.GetExtension(name)}";
            archive.CreateEntryFromFile(file, candidate);
        }

        return zipPath;
    }

    private static DrawingImage BuildQrImage(string url)
    {
        using var generator = new QRCodeGenerator();
        var data = generator.CreateQrCode(url, QRCodeGenerator.ECCLevel.M);
        return new XamlQRCode(data).GetGraphic(8);
    }

    // 세션 이벤트는 소켓 스레드에서 올라오므로 UI 스레드로 넘겨 바인딩 속성을 갱신한다.
    private void OnCompleted() => Application.Current.Dispatcher.Invoke(() =>
        Status = "전송 완료! 같은 QR로 계속 받을 수 있어요");

    private void OnFailed(Exception ex) => Application.Current.Dispatcher.Invoke(() =>
        Status = $"전송 중 오류가 발생했지만 QR은 계속 사용할 수 있어요: {ex.Message}");

    /// <summary>화면을 떠날 때 호출해 서버와 임시 zip을 정리한다.</summary>
    public void StopSession()
    {
        if (_session is not null)
        {
            _session.Completed -= OnCompleted;
            _session.Failed -= OnFailed;
            _session.Dispose();
            _session = null;
        }

        if (_tempZipPath is not null)
        {
            try { File.Delete(_tempZipPath); }
            catch { /* 정리 실패는 무시 */ }
            _tempZipPath = null;
        }

        TransferUrl = null;
        QrImage = null;
    }
}

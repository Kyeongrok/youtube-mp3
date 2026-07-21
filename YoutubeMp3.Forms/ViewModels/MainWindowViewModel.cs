using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using YoutubeMp3.Main.Services;

namespace YoutubeMp3.Forms.ViewModels;

public partial class MainWindowViewModel : ObservableObject
{
    private readonly IYoutubeService _youtubeService;
    private readonly IAudioGainService _audioGainService;
    private readonly PlayerViewModel _playerViewModel;
    private readonly FileTransferViewModel _fileTransferViewModel;

    public MainWindowViewModel(
        IYoutubeService youtubeService,
        IAudioGainService audioGainService,
        PlayerViewModel playerViewModel,
        FileTransferViewModel fileTransferViewModel)
    {
        _youtubeService = youtubeService;
        _audioGainService = audioGainService;
        _playerViewModel = playerViewModel;
        _fileTransferViewModel = fileTransferViewModel;
    }

    /// <summary>
    /// 창 로드 시 호출. FFmpeg 등 필수 실행 파일이 없으면 백그라운드에서 자동 설치한다.
    /// FFmpeg가 없으면 다운로드·볼륨 조절 모두 불가하므로 시작하자마자 준비해 둔다.
    /// </summary>
    public async Task InitializeAsync()
    {
        var downloadStarted = false;
        try
        {
            var progress = new Progress<AudioDownloadProgress>(p =>
            {
                downloadStarted = true;
                Status = p.Status;
            });
            await _youtubeService.PrepareBinariesAsync(progress);

            // 실제로 내려받은 경우에만 상태 문구를 마무리한다(이미 있으면 조용히 넘어간다).
            if (downloadStarted)
                Status = "준비 완료";
        }
        catch (Exception ex)
        {
            Status = $"필수 프로그램 준비 실패: {ex.Message}";
        }
    }

    public ObservableCollection<VideoSearchResult> SearchResults { get; } = new();

    // ── 페이지 전환 (LazyRegion) ──────────────────────────────────

    // 뷰 계층이 주입하는 페이지들. VM이 View 타입에 직접 의존하지 않도록 object로 보관한다.
    private object? _extractionPage;
    private object? _volumePage;
    private object? _playerPage;
    private object? _fileTransferPage;

    // LazyRegion에 표시할 현재 페이지. 값이 바뀌면 애니메이션으로 전환된다.
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsExtractionActive))]
    [NotifyPropertyChangedFor(nameof(IsVolumeActive))]
    [NotifyPropertyChangedFor(nameof(IsPlayerActive))]
    [NotifyPropertyChangedFor(nameof(IsFileTransferActive))]
    private object? _currentPage;

    // 타이틀바 버튼에서 지금 보고 있는 화면을 강조하는 데 쓴다.
    public bool IsExtractionActive => ReferenceEquals(CurrentPage, _extractionPage);
    public bool IsVolumeActive => ReferenceEquals(CurrentPage, _volumePage);
    public bool IsPlayerActive => ReferenceEquals(CurrentPage, _playerPage);
    public bool IsFileTransferActive => ReferenceEquals(CurrentPage, _fileTransferPage);

    /// <summary>뷰가 만든 페이지들을 받아 초기 화면(추출)을 세팅한다.</summary>
    public void InitializePages(object extractionPage, object volumePage, object playerPage, object fileTransferPage)
    {
        _extractionPage = extractionPage;
        _volumePage = volumePage;
        _playerPage = playerPage;
        _fileTransferPage = fileTransferPage;
        CurrentPage = _extractionPage;
    }

    /// <summary>Youtube Mp3 추출 화면으로 이동한다.</summary>
    [RelayCommand]
    private void ShowExtraction() => CurrentPage = _extractionPage;

    /// <summary>데시벨 조정 화면으로 이동한다.</summary>
    [RelayCommand]
    private void ShowVolume() => CurrentPage = _volumePage;

    /// <summary>플레이어 화면으로 이동한다.</summary>
    [RelayCommand]
    private void ShowPlayer() => CurrentPage = _playerPage;

    /// <summary>휴대폰 전송 화면으로 이동한다. 들어가자마자 최신 다운로드 목록으로 QR을 준비한다.</summary>
    [RelayCommand]
    private void ShowFileTransfer()
    {
        CurrentPage = _fileTransferPage;
        _fileTransferViewModel.RefreshFiles();
    }

    // 다른 화면으로 넘어가면 전송 서버(포트)를 계속 열어둘 이유가 없으므로 정리한다.
    partial void OnCurrentPageChanged(object? value)
    {
        if (!ReferenceEquals(value, _fileTransferPage))
            _fileTransferViewModel.StopSession();
    }

    [ObservableProperty]
    private string _searchQuery = string.Empty;

    [ObservableProperty]
    private bool _isSearching;

    [ObservableProperty]
    private VideoSearchResult? _selectedSearchResult;

    [ObservableProperty]
    private string _url = string.Empty;

    [ObservableProperty]
    private string _status = string.Empty;

    [ObservableProperty]
    private double _progressPercentage;

    [ObservableProperty]
    private bool _isDownloading;

    [ObservableProperty]
    private string? _lastDownloadedFilePath;

    private bool CanSearch() => !IsSearching && !string.IsNullOrWhiteSpace(SearchQuery);

    [RelayCommand(CanExecute = nameof(CanSearch))]
    private async Task SearchAsync()
    {
        IsSearching = true;
        Status = "검색 중...";
        SearchResults.Clear();

        try
        {
            var results = await _youtubeService.SearchAsync(SearchQuery);
            foreach (var result in results)
                SearchResults.Add(result);

            Status = SearchResults.Count > 0 ? $"검색 결과 {SearchResults.Count}건" : "검색 결과가 없습니다.";
        }
        catch (Exception ex)
        {
            Status = $"검색 오류: {ex.Message}";
        }
        finally
        {
            IsSearching = false;
        }
    }

    private bool CanDownload() => !IsDownloading && !string.IsNullOrWhiteSpace(Url);

    [RelayCommand(CanExecute = nameof(CanDownload))]
    private async Task DownloadAsync()
    {
        IsDownloading = true;
        ProgressPercentage = 0;
        Status = "준비 중...";

        var progress = new Progress<AudioDownloadProgress>(p =>
        {
            ProgressPercentage = p.Percentage;
            Status = p.Status;
        });

        try
        {
            var outputDirectory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.MyMusic), "YoutubeMp3");
            Directory.CreateDirectory(outputDirectory);

            var filePath = await _youtubeService.DownloadAudioAsync(Url, outputDirectory, progress);
            ProgressPercentage = 100;
            Status = $"완료: {Path.GetFileName(filePath)}";
            LastDownloadedFilePath = filePath;
        }
        catch (Exception ex)
        {
            Status = $"오류: {ex.Message}";
        }
        finally
        {
            IsDownloading = false;
        }
    }

    private bool CanOpenFolder() => !string.IsNullOrEmpty(LastDownloadedFilePath);

    [RelayCommand(CanExecute = nameof(CanOpenFolder))]
    private void OpenFolder()
    {
        if (string.IsNullOrEmpty(LastDownloadedFilePath))
            return;

        Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{LastDownloadedFilePath}\"")
        {
            UseShellExecute = true,
        });
    }

    private bool CanAddToPlaylist() => !string.IsNullOrEmpty(LastDownloadedFilePath);

    /// <summary>방금 추출한 mp3를 플레이어의 재생목록에 추가하고 플레이어 화면으로 이동한다.</summary>
    [RelayCommand(CanExecute = nameof(CanAddToPlaylist))]
    private void AddToPlaylist()
    {
        if (string.IsNullOrEmpty(LastDownloadedFilePath))
            return;

        _playerViewModel.AddFiles(new[] { LastDownloadedFilePath });
        Status = $"재생목록에 추가: {Path.GetFileName(LastDownloadedFilePath)}";
        ShowPlayer();
    }

    // ── MP3 볼륨(dB) 조절 ────────────────────────────────────────

    // 볼륨을 조절할 mp3 경로. 비어 있으면 증가/감소 버튼이 비활성.
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(VolumeUpCommand))]
    [NotifyCanExecuteChangedFor(nameof(VolumeDownCommand))]
    private string _volumeFilePath = string.Empty;

    // 표시용 파일명.
    [ObservableProperty]
    private string _volumeFileName = "(파일 없음)";

    // 증가/감소할 데시벨 양. 기본 3.
    [ObservableProperty]
    private double _volumeDb = 3;

    // 조절 중 여부(재진입 방지·버튼 비활성).
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(VolumeUpCommand))]
    [NotifyCanExecuteChangedFor(nameof(VolumeDownCommand))]
    private bool _isAdjustingVolume;

    /// <summary>볼륨을 조절할 mp3 파일을 고른다.</summary>
    [RelayCommand]
    private void OpenVolumeFile()
    {
        var dialog = new OpenFileDialog
        {
            Title = "MP3 파일 열기",
            Filter = "MP3 오디오 (*.mp3)|*.mp3",
        };

        // 다운로드 폴더가 있으면 거기서 시작한다.
        var musicDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.MyMusic), "YoutubeMp3");
        if (Directory.Exists(musicDirectory))
            dialog.InitialDirectory = musicDirectory;

        if (dialog.ShowDialog() != true)
            return;

        SetVolumeFile(dialog.FileName);
    }

    /// <summary>드롭된 경로들 중 첫 mp3 파일을 볼륨 조절 대상으로 지정한다.</summary>
    public void SetVolumeFiles(IEnumerable<string> paths)
    {
        var mp3 = paths.FirstOrDefault(p =>
            File.Exists(p) && Path.GetExtension(p).Equals(".mp3", StringComparison.OrdinalIgnoreCase));

        if (mp3 is null)
        {
            Status = "mp3 파일만 볼륨 조절이 가능합니다";
            return;
        }

        SetVolumeFile(mp3);
    }

    private void SetVolumeFile(string path)
    {
        VolumeFilePath = path;
        VolumeFileName = Path.GetFileName(path);
        Status = $"볼륨 조절 대상: {VolumeFileName}";
    }

    private bool CanAdjustVolume() => !IsAdjustingVolume && File.Exists(VolumeFilePath);

    /// <summary>지정한 dB만큼 볼륨을 키워 새 mp3로 저장한다.</summary>
    [RelayCommand(CanExecute = nameof(CanAdjustVolume))]
    private Task VolumeUpAsync() => AdjustVolumeAsync(VolumeDb);

    /// <summary>지정한 dB만큼 볼륨을 줄여 새 mp3로 저장한다.</summary>
    [RelayCommand(CanExecute = nameof(CanAdjustVolume))]
    private Task VolumeDownAsync() => AdjustVolumeAsync(-VolumeDb);

    private async Task AdjustVolumeAsync(double gainDb)
    {
        IsAdjustingVolume = true;
        Status = "볼륨 조절 중...";
        try
        {
            var outputPath = await _audioGainService.AdjustGainAsync(VolumeFilePath, gainDb);
            Status = $"완료: {Path.GetFileName(outputPath)} ({gainDb:+0.#;-0.#}dB)";
            LastDownloadedFilePath = outputPath; // '폴더 열기'로 결과 파일을 바로 확인
        }
        catch (Exception ex)
        {
            Status = $"오류: {ex.Message}";
        }
        finally
        {
            IsAdjustingVolume = false;
        }
    }

    partial void OnSearchQueryChanged(string value) => SearchCommand.NotifyCanExecuteChanged();

    partial void OnIsSearchingChanged(bool value) => SearchCommand.NotifyCanExecuteChanged();

    partial void OnSelectedSearchResultChanged(VideoSearchResult? value)
    {
        if (value is null)
            return;

        Url = value.Url;
    }

    partial void OnUrlChanged(string value) => DownloadCommand.NotifyCanExecuteChanged();

    partial void OnIsDownloadingChanged(bool value) => DownloadCommand.NotifyCanExecuteChanged();

    partial void OnLastDownloadedFilePathChanged(string? value)
    {
        OpenFolderCommand.NotifyCanExecuteChanged();
        AddToPlaylistCommand.NotifyCanExecuteChanged();
    }
}

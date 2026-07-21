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

        ExtractionQueue.CollectionChanged += (_, _) => OnPropertyChanged(nameof(PendingQueueDisplay));

        // 재생목록 컨텍스트 메뉴의 "볼륨 조정"에서 온 요청 - 대상 파일을 지정하고 화면을 전환한다.
        _playerViewModel.VolumeAdjustRequested += path =>
        {
            SetVolumeFiles(new[] { path });
            ShowVolume();
        };
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

    public ObservableCollection<QueuedExtraction> ExtractionQueue { get; } = new();

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
    private bool _isLoadingMoreResults;

    [ObservableProperty]
    private bool _hasMoreResults;

    [ObservableProperty]
    private VideoSearchResult? _selectedSearchResult;

    // 검색 API에 페이지 토큰이 없어 "더 보기"를 누를 때마다 이 개수만큼 늘려 재검색한다.
    private const int SearchPageSize = 20;
    private int _requestedResultCount = SearchPageSize;

    [ObservableProperty]
    private string _url = string.Empty;

    // MP3 추출 시 ffmpeg 오디오 비트레이트. 값을 바꿔도 이미 대기열에 들어간 항목엔 영향 없다.
    public string[] AudioQualityOptions { get; } = { "128K", "192K", "320K" };

    [ObservableProperty]
    private string _selectedAudioQuality = "192K";

    // 브라우저에서 직접 쿠키를 읽는 방식(--cookies-from-browser)은 최신 Windows 크로미움 브라우저에서
    // DB 잠금·DPAPI 복호화 문제로 거의 항상 실패해 빼고, 내보낸 쿠키 파일만 지원한다.
    [ObservableProperty]
    private string _cookieFileDisplay = string.Empty;

    /// <summary>"Sign in to confirm you're not a bot" 오류가 뜨면, 브라우저 확장 프로그램으로
    /// 내보낸 cookies.txt를 지정해 우회한다.</summary>
    [RelayCommand]
    private void SelectCookieFile()
    {
        var dialog = new OpenFileDialog
        {
            Title = "쿠키 파일 선택 (브라우저 확장 프로그램으로 내보낸 cookies.txt)",
            Filter = "쿠키 파일 (*.txt)|*.txt|모든 파일 (*.*)|*.*",
        };

        if (dialog.ShowDialog() != true)
            return;

        _youtubeService.SetCookieFile(dialog.FileName);
        CookieFileDisplay = $"파일: {Path.GetFileName(dialog.FileName)}";
    }

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
        _requestedResultCount = SearchPageSize;
        HasMoreResults = false;

        try
        {
            var results = await _youtubeService.SearchAsync(SearchQuery, _requestedResultCount);
            foreach (var result in results)
                SearchResults.Add(result);

            HasMoreResults = results.Count >= _requestedResultCount;
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

    private bool CanLoadMoreResults() => !IsSearching && !IsLoadingMoreResults && HasMoreResults;

    /// <summary>검색 결과 목록 맨 아래의 "더 보기"로 다음 페이지를 이어 붙인다.</summary>
    [RelayCommand(CanExecute = nameof(CanLoadMoreResults))]
    private async Task LoadMoreResultsAsync()
    {
        IsLoadingMoreResults = true;

        try
        {
            _requestedResultCount += SearchPageSize;
            var results = await _youtubeService.SearchAsync(SearchQuery, _requestedResultCount);

            var existingUrls = SearchResults.Select(r => r.Url).ToHashSet();
            var newResults = results.Where(r => !existingUrls.Contains(r.Url)).ToList();
            foreach (var result in newResults)
                SearchResults.Add(result);

            // 재검색해도 새 항목이 없으면 더 가져올 결과가 없다고 본다.
            HasMoreResults = newResults.Count > 0 && results.Count >= _requestedResultCount;
            Status = $"검색 결과 {SearchResults.Count}건";
        }
        catch (Exception ex)
        {
            Status = $"검색 오류: {ex.Message}";
        }
        finally
        {
            IsLoadingMoreResults = false;
        }
    }

    private bool _isProcessingExtractionQueue;

    // 대기열 맨 앞[0]이 지금 처리 중인 항목이므로, 2번째부터가 "대기 중"이다.
    public string PendingQueueDisplay => ExtractionQueue.Count > 1
        ? $"대기 중 ({ExtractionQueue.Count - 1}건): " + string.Join(", ", ExtractionQueue.Skip(1).Select(job => job.Title))
        : string.Empty;

    private bool CanDownload() => !string.IsNullOrWhiteSpace(Url);

    // 다운로드 중에도 버튼을 막지 않고 대기열에 쌓아, 처리 중인 항목이 끝나는 대로 이어서 추출한다.
    [RelayCommand(CanExecute = nameof(CanDownload))]
    private void Download()
    {
        var title = SelectedSearchResult is { } selected && selected.Url == Url ? selected.Title : Url;
        ExtractionQueue.Add(new QueuedExtraction(Url, title, SelectedAudioQuality));

        if (!_isProcessingExtractionQueue)
            _ = ProcessExtractionQueueAsync();
    }

    private async Task ProcessExtractionQueueAsync()
    {
        _isProcessingExtractionQueue = true;
        IsDownloading = true;

        try
        {
            while (ExtractionQueue.Count > 0)
            {
                var job = ExtractionQueue[0];
                ProgressPercentage = 0;
                Status = $"준비 중... ({job.Title})";

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

                    var filePath = await _youtubeService.DownloadAudioAsync(job.Url, outputDirectory, job.AudioQuality, progress);
                    ProgressPercentage = 100;
                    Status = $"완료: {Path.GetFileName(filePath)}";
                    LastDownloadedFilePath = filePath;
                    _playerViewModel.AddFiles(new[] { filePath });
                }
                catch (Exception ex)
                {
                    Status = $"오류 ({job.Title}): {ex.Message}";
                }
                finally
                {
                    ExtractionQueue.RemoveAt(0);
                }
            }
        }
        finally
        {
            IsDownloading = false;
            _isProcessingExtractionQueue = false;
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

    partial void OnIsSearchingChanged(bool value)
    {
        SearchCommand.NotifyCanExecuteChanged();
        LoadMoreResultsCommand.NotifyCanExecuteChanged();
    }

    partial void OnIsLoadingMoreResultsChanged(bool value) => LoadMoreResultsCommand.NotifyCanExecuteChanged();

    partial void OnHasMoreResultsChanged(bool value) => LoadMoreResultsCommand.NotifyCanExecuteChanged();

    partial void OnSelectedSearchResultChanged(VideoSearchResult? value)
    {
        if (value is null)
            return;

        Url = value.Url;
    }

    partial void OnUrlChanged(string value) => DownloadCommand.NotifyCanExecuteChanged();

    partial void OnLastDownloadedFilePathChanged(string? value)
    {
        OpenFolderCommand.NotifyCanExecuteChanged();
        AddToPlaylistCommand.NotifyCanExecuteChanged();
    }
}

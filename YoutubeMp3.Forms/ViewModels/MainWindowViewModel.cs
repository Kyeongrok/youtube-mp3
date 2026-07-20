using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using YoutubeMp3.Main.Services;

namespace YoutubeMp3.Forms.ViewModels;

public partial class MainWindowViewModel : ObservableObject
{
    private readonly IYoutubeService _youtubeService;

    public MainWindowViewModel(IYoutubeService youtubeService)
    {
        _youtubeService = youtubeService;
    }

    public ObservableCollection<VideoSearchResult> SearchResults { get; } = new();

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

    partial void OnLastDownloadedFilePathChanged(string? value) => OpenFolderCommand.NotifyCanExecuteChanged();
}

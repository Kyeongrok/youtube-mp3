using System.Collections.ObjectModel;
using System.IO;
using System.Linq;
using System.Windows.Media;
using System.Windows.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace YoutubeMp3.Forms.ViewModels;

/// <summary>재생목록 한 곡. 현재 재생 곡 표시를 위해 IsCurrent를 관찰 가능하게 둔다.</summary>
public partial class PlaylistItem : ObservableObject
{
    public PlaylistItem(string path)
    {
        Path = path;
        Name = System.IO.Path.GetFileName(path);
    }

    public string Path { get; }

    public string Name { get; }

    // 지금 재생 중인 곡이면 true. 리스트에서 ▶·강조 색으로 구분하는 데 바인딩된다.
    [ObservableProperty]
    private bool _isCurrent;
}

/// <summary>
/// 드래그앤드롭으로 채우는 재생목록 플레이어. 목록은 앱 데이터 폴더의 텍스트 파일에
/// 저장했다가 다음 실행 때 자동으로 불러온다. 재생은 WPF MediaPlayer로 처리한다.
/// </summary>
public partial class PlayerViewModel : ObservableObject
{
    // 지원 확장자(오디오). 드롭된 폴더는 하위까지 훑어 이 확장자만 담는다.
    private static readonly string[] SupportedExtensions =
        { ".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg", ".wma" };

    // 재생목록 저장 위치: %AppData%\YoutubeMp3\playlist.txt (한 줄에 경로 하나).
    private static readonly string PlaylistPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "YoutubeMp3", "playlist.txt");

    private readonly MediaPlayer _player = new();
    private readonly DispatcherTimer _timer;
    private PlaylistItem? _current;
    private bool _suppressSeek;

    // 사용자가 진행바를 잡고 있는 동안엔 타이머가 위치를 덮어쓰지 않게 한다.
    public bool IsSeeking { get; set; }

    public PlayerViewModel()
    {
        _player.MediaOpened += OnMediaOpened;
        _player.MediaEnded += (_, _) => PlayNext();

        // 재생 위치를 주기적으로 진행바에 반영한다.
        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        _timer.Tick += OnTick;
        _timer.Start();

        LoadPlaylist();
    }

    public ObservableCollection<PlaylistItem> Playlist { get; } = new();

    [ObservableProperty]
    private PlaylistItem? _selectedItem;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(PlayPauseLabel))]
    private bool _isPlaying;

    [ObservableProperty]
    private string _status = "오디오 파일을 여기로 드래그해 재생목록에 추가하세요";

    // 상단 '재생 중' 영역에 표시할 현재 곡 이름.
    [ObservableProperty]
    private string _currentName = "재생 중인 곡이 없습니다";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(PositionText))]
    private double _positionSeconds;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(DurationText))]
    private double _durationSeconds;

    public string PlayPauseLabel => IsPlaying ? "❚❚ 일시정지" : "▶ 재생";

    public string PositionText => FormatTime(PositionSeconds);

    public string DurationText => FormatTime(DurationSeconds);

    private static string FormatTime(double seconds)
    {
        if (double.IsNaN(seconds) || seconds < 0)
            seconds = 0;
        return TimeSpan.FromSeconds(seconds).ToString(@"m\:ss");
    }

    /// <summary>드롭된 경로(파일/폴더)를 재생목록에 추가한다. 중복·미지원 파일은 건너뛴다.</summary>
    public void AddFiles(IEnumerable<string> paths)
    {
        var added = 0;
        foreach (var path in paths)
        {
            if (Directory.Exists(path))
            {
                foreach (var file in SafeEnumerate(path))
                    if (TryAdd(file))
                        added++;
            }
            else if (TryAdd(path))
            {
                added++;
            }
        }

        if (added > 0)
        {
            SavePlaylist();
            Status = $"{added}곡 추가됨 · 총 {Playlist.Count}곡";
        }
    }

    /// <summary>드래그로 순서를 바꾼다. target이 null이면 맨 끝으로 이동한다.</summary>
    public void MoveItem(PlaylistItem source, PlaylistItem? target)
    {
        var oldIndex = Playlist.IndexOf(source);
        if (oldIndex < 0)
            return;

        var newIndex = target is null ? Playlist.Count - 1 : Playlist.IndexOf(target);
        if (newIndex < 0 || oldIndex == newIndex)
            return;

        Playlist.Move(oldIndex, newIndex);
        SavePlaylist();
    }

    private static IEnumerable<string> SafeEnumerate(string directory)
    {
        try
        {
            return Directory.EnumerateFiles(directory, "*", SearchOption.AllDirectories);
        }
        catch
        {
            return Enumerable.Empty<string>();
        }
    }

    private bool TryAdd(string path)
    {
        if (!File.Exists(path))
            return false;
        var ext = Path.GetExtension(path).ToLowerInvariant();
        if (!SupportedExtensions.Contains(ext))
            return false;
        if (Playlist.Any(i => string.Equals(i.Path, path, StringComparison.OrdinalIgnoreCase)))
            return false;

        Playlist.Add(new PlaylistItem(path));
        return true;
    }

    /// <summary>특정 곡을 재생한다(없으면 선택 곡 → 첫 곡 순으로).</summary>
    [RelayCommand]
    private void PlayItem(PlaylistItem? item)
    {
        item ??= SelectedItem ?? Playlist.FirstOrDefault();
        if (item is null)
            return;

        SetCurrent(item);
        try
        {
            _player.Open(new Uri(item.Path, UriKind.Absolute));
            _suppressSeek = true;
            PositionSeconds = 0;
            DurationSeconds = 0;
            _suppressSeek = false;
            _player.Play();
            IsPlaying = true;
            Status = $"재생 중 · {item.Name}";
        }
        catch (Exception ex)
        {
            IsPlaying = false;
            Status = $"재생 실패: {ex.Message}";
        }
    }

    /// <summary>재생/일시정지 토글. 재생 중인 곡이 없으면 선택/첫 곡을 재생한다.</summary>
    [RelayCommand]
    private void PlayPause()
    {
        if (_current is null)
        {
            PlayItem(null);
            return;
        }

        if (IsPlaying)
        {
            _player.Pause();
            IsPlaying = false;
            Status = $"일시정지 · {_current.Name}";
        }
        else
        {
            _player.Play();
            IsPlaying = true;
            Status = $"재생 중 · {_current.Name}";
        }
    }

    [RelayCommand]
    private void Stop()
    {
        _player.Stop();
        IsPlaying = false;
        _suppressSeek = true;
        PositionSeconds = 0;
        _suppressSeek = false;
        if (_current is not null)
            Status = $"정지 · {_current.Name}";
    }

    [RelayCommand]
    private void Next() => PlayNext();

    [RelayCommand]
    private void Previous()
    {
        if (Playlist.Count == 0)
            return;
        var idx = _current is null ? -1 : Playlist.IndexOf(_current);
        var prev = idx <= 0 ? Playlist.Count - 1 : idx - 1;
        PlayItem(Playlist[prev]);
    }

    private void PlayNext()
    {
        if (Playlist.Count == 0)
            return;
        var idx = _current is null ? -1 : Playlist.IndexOf(_current);
        if (idx + 1 >= Playlist.Count)
        {
            _player.Stop();
            IsPlaying = false;
            Status = "재생 완료";
            return;
        }

        PlayItem(Playlist[idx + 1]);
    }

    [RelayCommand]
    private void RemoveSelected()
    {
        if (SelectedItem is null)
            return;

        var removed = SelectedItem;
        if (ReferenceEquals(removed, _current))
        {
            _player.Stop();
            IsPlaying = false;
            _current = null;
            CurrentName = "재생 중인 곡이 없습니다";
        }

        Playlist.Remove(removed);
        SavePlaylist();
        Status = $"삭제됨 · 총 {Playlist.Count}곡";
    }

    [RelayCommand]
    private void ClearPlaylist()
    {
        _player.Stop();
        IsPlaying = false;
        _current = null;
        CurrentName = "재생 중인 곡이 없습니다";
        Playlist.Clear();
        SavePlaylist();
        Status = "재생목록을 비웠습니다";
    }

    // 현재 곡 표시를 갱신한다(이전 곡의 강조 해제 + 새 곡 강조).
    private void SetCurrent(PlaylistItem item)
    {
        if (_current is not null)
            _current.IsCurrent = false;
        _current = item;
        _current.IsCurrent = true;
        SelectedItem = item;
        CurrentName = item.Name;
    }

    private void OnMediaOpened(object? sender, EventArgs e)
    {
        if (_player.NaturalDuration.HasTimeSpan)
            DurationSeconds = _player.NaturalDuration.TimeSpan.TotalSeconds;
    }

    private void OnTick(object? sender, EventArgs e)
    {
        if (_player.NaturalDuration.HasTimeSpan)
            DurationSeconds = _player.NaturalDuration.TimeSpan.TotalSeconds;

        // 사용자가 진행바를 조작 중일 땐 위치를 덮어쓰지 않는다.
        if (IsSeeking)
            return;

        _suppressSeek = true;
        PositionSeconds = _player.Position.TotalSeconds;
        _suppressSeek = false;
    }

    // 진행바(슬라이더) 값이 사용자에 의해 바뀌면 그 위치로 탐색한다.
    partial void OnPositionSecondsChanged(double value)
    {
        if (_suppressSeek || _current is null)
            return;
        _player.Position = TimeSpan.FromSeconds(value);
    }

    private void LoadPlaylist()
    {
        try
        {
            if (!File.Exists(PlaylistPath))
                return;

            foreach (var line in File.ReadAllLines(PlaylistPath))
            {
                var path = line.Trim();
                if (path.Length == 0 || !File.Exists(path))
                    continue;
                if (Playlist.Any(i => string.Equals(i.Path, path, StringComparison.OrdinalIgnoreCase)))
                    continue;

                Playlist.Add(new PlaylistItem(path));
            }

            if (Playlist.Count > 0)
                Status = $"이전 재생목록 {Playlist.Count}곡을 불러왔습니다";
        }
        catch
        {
            // 재생목록 로드 실패는 무시(빈 목록으로 시작).
        }
    }

    private void SavePlaylist()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(PlaylistPath)!);
            File.WriteAllLines(PlaylistPath, Playlist.Select(i => i.Path));
        }
        catch
        {
            // 저장 실패는 무시.
        }
    }
}

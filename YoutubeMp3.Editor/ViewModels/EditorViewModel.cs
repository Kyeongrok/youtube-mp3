using System.IO;
using System.Windows;
using System.Windows.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using YoutubeMp3.Editor.Audio;
using YoutubeMp3.Editor.UI.Units;

namespace YoutubeMp3.Editor.ViewModels;

/// <summary>
/// YoutubeMp3 재생목록에서 넘겨받은 파일 하나를 열어, 파형에서 구간을 골라 잘라내고
/// 다시 저장하는 것만 하는 가벼운 편집기.
/// </summary>
public partial class EditorViewModel : ObservableObject, IDisposable
{
    private readonly IAudioEditor _editor = new NAudioEditor();
    private readonly AudioPlayer _player = new();
    private readonly DispatcherTimer _cursorTimer;

    private AudioDocument? _document;
    private string? _filePath;
    private bool _suppressCursorSeek;

    private const int WaveformResolution = 2000;

    public EditorViewModel()
    {
        _player.PlaybackStopped += (_, _) => OnPlaybackStopped();
        _cursorTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(40) };
        _cursorTimer.Tick += (_, _) => OnCursorTick();
    }

    /// <summary>창을 띄운 쪽에서 지정한 파일을 열어 준다(재생목록에서 고른 곡).</summary>
    public async Task LoadAsync(string? path)
    {
        if (!string.IsNullOrWhiteSpace(path) && File.Exists(path))
            await OpenFileAsync(path);
    }

    [ObservableProperty]
    private string _fileName = "(열린 파일 없음)";

    [ObservableProperty]
    private double _durationSeconds;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(CutCommand))]
    [NotifyCanExecuteChangedFor(nameof(ExportCommand))]
    [NotifyPropertyChangedFor(nameof(StartTimeLabel))]
    [NotifyPropertyChangedFor(nameof(EndTimeLabel))]
    [NotifyPropertyChangedFor(nameof(SelectionLabel))]
    private double _startSeconds;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(CutCommand))]
    [NotifyCanExecuteChangedFor(nameof(ExportCommand))]
    [NotifyPropertyChangedFor(nameof(StartTimeLabel))]
    [NotifyPropertyChangedFor(nameof(EndTimeLabel))]
    [NotifyPropertyChangedFor(nameof(SelectionLabel))]
    private double _endSeconds;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(OpenFileCommand))]
    [NotifyCanExecuteChangedFor(nameof(CutCommand))]
    [NotifyCanExecuteChangedFor(nameof(UndoCommand))]
    [NotifyCanExecuteChangedFor(nameof(ExportCommand))]
    [NotifyCanExecuteChangedFor(nameof(PlayPauseCommand))]
    [NotifyCanExecuteChangedFor(nameof(StopCommand))]
    private bool _isBusy;

    [ObservableProperty]
    private string _status = "파일을 열고 파형을 드래그해 구간 선택 → 잘라내기 (Ctrl+Z 실행취소)";

    [ObservableProperty]
    private float[] _peaks = Array.Empty<float>();

    [ObservableProperty]
    private WaveformInteractionMode _waveformMode = WaveformInteractionMode.Select;

    // 값을 바꾸면 파형이 전체 보기로 돌아간다(새 파일 열기 전용). 잘라내기/실행취소는 확대 유지.
    [ObservableProperty]
    private int _waveformResetView;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(PlayPauseLabel))]
    private bool _isPlaying;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(PlayPositionLabel))]
    private double _playPositionSeconds;

    public string PlayPauseLabel => IsPlaying ? "❚❚ 일시정지" : "▶ 재생";

    /// <summary>선택 구간 시작을 분:초.소수 형태로 표시한다(예: 1:24.47). 시간 단위처럼 보이지 않게 시:분:초는 쓰지 않는다.</summary>
    public string StartTimeLabel => FormatTime(StartSeconds);

    public string EndTimeLabel => FormatTime(EndSeconds);

    public string SelectionLabel => $"{StartTimeLabel} ~ {EndTimeLabel}";

    /// <summary>재생 커서(노란 선)가 지나가는 위치를 분:초.소수로 표시한다.</summary>
    public string PlayPositionLabel => FormatTime(PlayPositionSeconds);

    private static string FormatTime(double totalSeconds)
    {
        if (double.IsNaN(totalSeconds) || totalSeconds < 0)
            totalSeconds = 0;
        var minutes = (int)(totalSeconds / 60);
        var seconds = totalSeconds - minutes * 60;
        return $"{minutes}:{seconds:00.00}";
    }

    private bool HasDocument => _document is { FrameCount: > 0 };

    private bool CanInteract() => !IsBusy;

    private bool CanPlay() => !IsBusy && HasDocument;

    // ── 파일 열기 ────────────────────────────────────────────────

    [RelayCommand(CanExecute = nameof(CanInteract))]
    private async Task OpenFileAsync()
    {
        var dialog = new OpenFileDialog
        {
            Title = "오디오 파일 열기",
            Filter = "오디오 파일 (*.m4a;*.mp4;*.aac;*.mp3;*.wav)|*.m4a;*.mp4;*.aac;*.mp3;*.wav|모든 파일 (*.*)|*.*"
        };
        if (dialog.ShowDialog() != true)
            return;

        await OpenFileAsync(dialog.FileName);
    }

    private async Task OpenFileAsync(string path)
    {
        _player.Stop();
        _cursorTimer.Stop();
        IsPlaying = false;
        IsBusy = true;
        try
        {
            Status = "불러오는 중...";
            var document = await _editor.LoadAsync(path);
            var peaks = await Task.Run(() => document.ComputePeaks(WaveformResolution));

            _document = document;
            _filePath = path;
            FileName = Path.GetFileName(path);
            DurationSeconds = document.Duration.TotalSeconds;
            StartSeconds = 0;
            EndSeconds = 0;
            Peaks = peaks;
            PlayPositionSeconds = 0;
            WaveformResetView++;
            _player.LoadSamples(document.Samples, document.SampleRate, document.Channels);

            Status = $"불러옴 · {document.Duration:mm\\:ss} · {document.SampleRate / 1000.0:0.#}kHz · {document.Channels}ch";
            RefreshCommands();
        }
        catch (Exception ex)
        {
            Status = $"파일을 열지 못했습니다: {ex.Message}";
            MessageBox.Show(ex.Message, "열기 실패", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally
        {
            IsBusy = false;
        }
    }

    // ── 편집: 구간 잘라내기 / 실행 취소 ─────────────────────────────

    private bool CanCut() => !IsBusy && HasDocument && EndSeconds > StartSeconds;

    [RelayCommand(CanExecute = nameof(CanCut))]
    private async Task CutAsync()
    {
        _player.Stop();
        _cursorTimer.Stop();
        IsPlaying = false;
        IsBusy = true;
        try
        {
            var cutAt = StartSeconds;
            await Task.Run(() =>
                _document!.Cut(TimeSpan.FromSeconds(StartSeconds), TimeSpan.FromSeconds(EndSeconds)));
            await RefreshAfterEditAsync(cutAt);
            Status = $"잘라냄 · 남은 길이 {DurationSeconds:0.##}초 (Ctrl+Z로 실행 취소)";
        }
        finally
        {
            IsBusy = false;
        }
    }

    private bool CanUndo() => !IsBusy && _document?.CanUndo == true;

    [RelayCommand(CanExecute = nameof(CanUndo))]
    private async Task UndoAsync()
    {
        _player.Stop();
        _cursorTimer.Stop();
        IsPlaying = false;
        IsBusy = true;
        try
        {
            await Task.Run(() => _document!.Undo());
            await RefreshAfterEditAsync(StartSeconds);
            Status = $"실행 취소됨 · 길이 {DurationSeconds:0.##}초";
        }
        finally
        {
            IsBusy = false;
        }
    }

    private async Task RefreshAfterEditAsync(double selectionPoint)
    {
        DurationSeconds = _document!.Duration.TotalSeconds;
        Peaks = await Task.Run(() => _document.ComputePeaks(WaveformResolution));
        _player.LoadSamples(_document.Samples, _document.SampleRate, _document.Channels);

        // 편집 후 선택은 잘라낸 지점에 접어 둔다(빈 선택).
        var point = Math.Clamp(selectionPoint, 0, DurationSeconds);
        StartSeconds = point;
        EndSeconds = point;
        PlayPositionSeconds = point;
        RefreshCommands();
    }

    private void RefreshCommands()
    {
        CutCommand.NotifyCanExecuteChanged();
        UndoCommand.NotifyCanExecuteChanged();
        ExportCommand.NotifyCanExecuteChanged();
        PlayPauseCommand.NotifyCanExecuteChanged();
        StopCommand.NotifyCanExecuteChanged();
    }

    // ── 재생 ────────────────────────────────────────────────────

    [RelayCommand(CanExecute = nameof(CanPlay))]
    private void PlayPause()
    {
        if (IsPlaying)
        {
            _player.Pause();
            _cursorTimer.Stop();
            IsPlaying = false;
            return;
        }

        var from = PlayPositionSeconds;
        if (from < 0 || from >= DurationSeconds)
            from = 0;

        _player.Play(TimeSpan.FromSeconds(from));
        _cursorTimer.Start();
        IsPlaying = true;
    }

    [RelayCommand(CanExecute = nameof(CanPlay))]
    private void Stop()
    {
        _cursorTimer.Stop();
        _player.Stop();
        IsPlaying = false;
        PlayPositionSeconds = 0;
    }

    private void OnCursorTick()
    {
        var pos = _player.CurrentTime.TotalSeconds;
        _suppressCursorSeek = true;
        PlayPositionSeconds = pos;
        _suppressCursorSeek = false;

        if (pos >= DurationSeconds)
            Stop();
    }

    partial void OnPlayPositionSecondsChanged(double value)
    {
        if (_suppressCursorSeek || !IsPlaying)
            return;

        _player.Play(TimeSpan.FromSeconds(value));
    }

    private void OnPlaybackStopped()
    {
        if (IsPlaying)
        {
            _cursorTimer.Stop();
            IsPlaying = false;
        }
    }

    // ── 저장(내보내기) ────────────────────────────────────────────

    private bool CanExport() => !IsBusy && HasDocument;

    [RelayCommand(CanExecute = nameof(CanExport))]
    private async Task ExportAsync()
    {
        var defaultFormat = AudioFormatExtensions.FromExtension(Path.GetExtension(_filePath ?? ""));
        var dialog = new SaveFileDialog
        {
            Title = "잘라낸 파일 저장",
            Filter = "AAC 오디오 (*.m4a)|*.m4a|MP3 오디오 (*.mp3)|*.mp3|WAV 오디오 (*.wav)|*.wav",
            FilterIndex = defaultFormat switch { AudioFormat.Mp3 => 2, AudioFormat.Wav => 3, _ => 1 },
            FileName = Path.GetFileName(_filePath) ?? "trimmed.m4a",
            InitialDirectory = Path.GetDirectoryName(_filePath),
        };
        if (dialog.ShowDialog() != true)
            return;

        var format = Path.GetExtension(dialog.FileName).ToLowerInvariant() switch
        {
            ".mp3" => AudioFormat.Mp3,
            ".wav" => AudioFormat.Wav,
            _ => AudioFormat.M4a,
        };

        IsBusy = true;
        Status = "저장하는 중...";
        try
        {
            // 재생 중이면 대상 파일(원본과 같은 경로일 수 있음)을 잡고 있어 저장이 실패할 수 있다.
            _player.Stop();
            _cursorTimer.Stop();
            IsPlaying = false;

            await _editor.ExportAsync(_document!, dialog.FileName, format);
            Status = $"저장 완료: {Path.GetFileName(dialog.FileName)}";
        }
        catch (Exception ex)
        {
            Status = $"저장 실패: {ex.Message}";
            MessageBox.Show(ex.Message, "저장 실패", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally
        {
            IsBusy = false;
        }
    }

    public void Dispose()
    {
        _cursorTimer.Stop();
        _player.Dispose();
    }
}

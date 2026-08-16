using System.Windows;
using System.Windows.Controls;
using YoutubeMp3.Forms.ViewModels;
using YoutubeMp3.Support.UI.Units;

namespace YoutubeMp3.Forms.UI.Views;

public class MainWindow : YoutubeMp3Window
{
    static MainWindow()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(MainWindow),
            new FrameworkPropertyMetadata(typeof(MainWindow)));
    }

    private readonly AppSettings _settings;

    public MainWindow(
        MainWindowViewModel viewModel,
        PlayerViewModel playerViewModel,
        FileTransferViewModel fileTransferViewModel,
        AppSettings settings)
    {
        DataContext = viewModel;
        _settings = settings;

        // 타이틀바에 릴리즈 버전을 표시한다(YoutubeMp3.csproj의 <Version>).
        var version = System.Reflection.Assembly.GetEntryAssembly()?.GetName().Version;
        if (version is not null)
            Title = $"YoutubeMp3 v{version.ToString(3)}";

        // LazyRegion으로 전환할 페이지들을 만들어 넘긴다.
        // 추출·데시벨 화면은 이 창의 DataContext(MainWindowViewModel)를 상속하고,
        // 플레이어·전송 화면은 각자 자체 ViewModel을 DataContext로 쓴다(페이지 전환과 무관하게 상태 유지).
        var playerView = new PlayerView { DataContext = playerViewModel };
        var fileTransferView = new FileTransferView { DataContext = fileTransferViewModel };
        viewModel.InitializePages(new ExtractionView(), new VolumeAdjustView(), playerView, fileTransferView);

        // 재생목록을 넉넉히 보여주려면 플레이어 화면은 창이 어느 정도 높아야 한다.
        // 화면 전환은 VM이 하지만 창 크기는 뷰의 몫이라 여기서 처리한다.
        viewModel.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(MainWindowViewModel.IsPlayerActive) && viewModel.IsPlayerActive)
                ApplyPlayerHeight();
        };

        // 설정에서 높이를 바꿨는데 지금 플레이어를 보고 있다면 바로 반영한다.
        viewModel.SettingsChanged += () =>
        {
            if (viewModel.IsPlayerActive)
                ApplyPlayerHeight();
        };

        // 시작하자마자 FFmpeg 등 필수 파일을 백그라운드에서 준비한다(없으면 앱을 못 쓰므로).
        Loaded += async (_, _) => await viewModel.InitializeAsync();
    }

    // 플레이어 화면에서 보장할 최소 높이(설정 창에서 조절, 기본 800). 이미 이보다 크면 그대로 두고,
    // 다른 화면으로 돌아가도 되돌리지 않는다(왔다 갔다 해도 더는 안 커진다).
    private void ApplyPlayerHeight()
    {
        // 최대화 상태에선 어차피 화면 전체를 쓰므로 건드리지 않는다.
        if (WindowState != System.Windows.WindowState.Normal)
            return;

        // 작업 표시줄을 뺀 화면 높이를 넘지 않도록 잘라 준다.
        var workArea = SystemParameters.WorkArea;
        var target = Math.Min(_settings.PlayerMinimumHeight, workArea.Height);
        if (ActualHeight >= target)
            return;

        Height = target;

        // 늘어난 만큼 아래로 삐져나가면 위로 끌어올려 화면 안에 둔다.
        if (Top + target > workArea.Bottom)
            Top = Math.Max(workArea.Top, workArea.Bottom - target);
    }

    public override void OnApplyTemplate()
    {
        base.OnApplyTemplate();

        var minimizeButton = GetTemplateChild("PART_MinimizeButton") as Button;
        if (minimizeButton != null)
            minimizeButton.Click += (s, e) => WindowState = System.Windows.WindowState.Minimized;

        var maximizeButton = GetTemplateChild("PART_MaximizeButton") as Button;
        if (maximizeButton != null)
            maximizeButton.Click += (s, e) =>
                WindowState = WindowState == System.Windows.WindowState.Maximized
                    ? System.Windows.WindowState.Normal
                    : System.Windows.WindowState.Maximized;

        var closeButton = GetTemplateChild("PART_CloseButton") as Button;
        if (closeButton != null)
            closeButton.Click += (s, e) => Close();
    }
}

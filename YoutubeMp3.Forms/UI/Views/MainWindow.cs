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

    public MainWindow(
        MainWindowViewModel viewModel,
        PlayerViewModel playerViewModel,
        FileTransferViewModel fileTransferViewModel)
    {
        DataContext = viewModel;

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

        // 시작하자마자 FFmpeg 등 필수 파일을 백그라운드에서 준비한다(없으면 앱을 못 쓰므로).
        Loaded += async (_, _) => await viewModel.InitializeAsync();
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

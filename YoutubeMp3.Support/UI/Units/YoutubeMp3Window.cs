using System.Windows;

namespace YoutubeMp3.Support.UI.Units;

public class YoutubeMp3Window : Window
{
    static YoutubeMp3Window()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(YoutubeMp3Window),
            new FrameworkPropertyMetadata(typeof(YoutubeMp3Window)));
    }

    public YoutubeMp3Window()
    {
        // 창 모서리를 둥글게(3) 그리려면 창 자체가 투명해야 하고, 투명 창은 WindowStyle=None이어야 한다.
        // 테마 스타일은 창 핸들이 생긴 뒤에 붙을 수 있어 AllowsTransparency를 스타일로 주면 예외가 나므로 여기서 정한다.
        WindowStyle = WindowStyle.None;
        AllowsTransparency = true;
        Background = System.Windows.Media.Brushes.Transparent;
    }

    /// <summary>타이틀바 가운데에 표시할 앱별 콘텐츠(예: 화면 전환 버튼).</summary>
    public static readonly DependencyProperty TitleBarContentProperty =
        DependencyProperty.Register(
            nameof(TitleBarContent), typeof(object), typeof(YoutubeMp3Window),
            new PropertyMetadata(null));

    public object? TitleBarContent
    {
        get => GetValue(TitleBarContentProperty);
        set => SetValue(TitleBarContentProperty, value);
    }

    /// <summary>타이틀바에 창 제목을 표시할지 여부. 제목을 다른 곳(메뉴 등)에 두는 창은 끈다.</summary>
    public static readonly DependencyProperty ShowTitleTextProperty =
        DependencyProperty.Register(
            nameof(ShowTitleText), typeof(bool), typeof(YoutubeMp3Window),
            new PropertyMetadata(true));

    public bool ShowTitleText
    {
        get => (bool)GetValue(ShowTitleTextProperty);
        set => SetValue(ShowTitleTextProperty, value);
    }

    /// <summary>타이틀바 맨 왼쪽(제목 앞)에 표시할 앱별 콘텐츠(예: 설정 버튼).</summary>
    public static readonly DependencyProperty TitleBarLeftContentProperty =
        DependencyProperty.Register(
            nameof(TitleBarLeftContent), typeof(object), typeof(YoutubeMp3Window),
            new PropertyMetadata(null));

    public object? TitleBarLeftContent
    {
        get => GetValue(TitleBarLeftContentProperty);
        set => SetValue(TitleBarLeftContentProperty, value);
    }

    protected override void OnStateChanged(EventArgs e)
    {
        base.OnStateChanged(e);
        if (WindowState == WindowState.Maximized)
            MaxHeight = SystemParameters.WorkArea.Height;
        else
            MaxHeight = double.PositiveInfinity;
    }
}

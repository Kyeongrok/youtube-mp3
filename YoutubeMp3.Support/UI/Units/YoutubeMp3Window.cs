using System.Windows;

namespace YoutubeMp3.Support.UI.Units;

public class YoutubeMp3Window : Window
{
    static YoutubeMp3Window()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(YoutubeMp3Window),
            new FrameworkPropertyMetadata(typeof(YoutubeMp3Window)));
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

    protected override void OnStateChanged(EventArgs e)
    {
        base.OnStateChanged(e);
        if (WindowState == WindowState.Maximized)
            MaxHeight = SystemParameters.WorkArea.Height;
        else
            MaxHeight = double.PositiveInfinity;
    }
}

using System.Windows;

namespace YoutubeMp3.Support.UI.Units;

public class YoutubeMp3Window : Window
{
    static YoutubeMp3Window()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(YoutubeMp3Window),
            new FrameworkPropertyMetadata(typeof(YoutubeMp3Window)));
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

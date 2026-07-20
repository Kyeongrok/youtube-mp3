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

    public MainWindow(MainWindowViewModel viewModel)
    {
        DataContext = viewModel;
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

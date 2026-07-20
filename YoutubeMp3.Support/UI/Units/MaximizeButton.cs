using System.Windows;
using System.Windows.Controls;

namespace YoutubeMp3.Support.UI.Units;

public class MaximizeButton : Button
{
    static MaximizeButton()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(MaximizeButton),
            new FrameworkPropertyMetadata(typeof(MaximizeButton)));
    }
}

using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;

namespace YoutubeMp3.Forms.UI.Views;

public partial class AppMenuButton : UserControl
{
    public AppMenuButton()
    {
        InitializeComponent();

        // 앱 이름·버전(YoutubeMp3.csproj의 <Version>)을 메뉴 맨 위에 보여 준다.
        var version = System.Reflection.Assembly.GetEntryAssembly()?.GetName().Version;
        AppInfoHeader.Header = version is null ? "YoutubeMp3" : $"YoutubeMp3 v{version.ToString(3)}";
    }

    // ContextMenu는 보통 우클릭에만 열리므로, 햄버거 버튼답게 왼쪽 클릭으로 열어 준다.
    private void OnMenuButtonClick(object sender, RoutedEventArgs e)
    {
        if (MenuButton.ContextMenu is not { } menu)
            return;

        // ContextMenu는 시각 트리 밖이라 DataContext가 상속되지 않는다. 창의 것을 직접 넘긴다.
        menu.DataContext = DataContext;
        menu.PlacementTarget = MenuButton;
        menu.Placement = PlacementMode.Bottom;
        menu.IsOpen = true;
    }
}

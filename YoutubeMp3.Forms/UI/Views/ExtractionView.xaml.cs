using System.Windows.Controls;

namespace YoutubeMp3.Forms.UI.Views;

// 클립보드 감지("붙여넣고 추출" 버튼 표시)는 플레이어 화면에서도 쓰므로 MainWindow가 창 단위로 맡는다.
public partial class ExtractionView : UserControl
{
    public ExtractionView()
    {
        InitializeComponent();
    }
}

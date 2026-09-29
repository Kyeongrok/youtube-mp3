using System.Windows;
using System.Windows.Input;
using System.Windows.Media;

namespace YoutubeMp3.Forms.UI.Views;

/// <summary>재생목록에서 고른 한 곡의 전송 QR만 띄우는 작은 창. 서버 수명은 호출한 쪽이 관리한다.</summary>
public partial class PhoneTransferWindow : Window
{
    public PhoneTransferWindow()
    {
        InitializeComponent();
        KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape)
                Close();
        };
    }

    /// <summary>표시할 곡을 바꾼다. 창이 이미 떠 있으면 다른 곡을 골랐을 때 내용만 갈아끼운다.</summary>
    public void SetContent(string fileName, string url, ImageSource qrImage)
    {
        FileNameText.Text = fileName;
        FileNameText.ToolTip = fileName;
        UrlText.Text = url;
        QrImage.Source = qrImage;
    }
}

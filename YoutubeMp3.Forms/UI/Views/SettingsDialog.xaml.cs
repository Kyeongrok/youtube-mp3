using System.Globalization;
using System.Windows;
using System.Windows.Input;

namespace YoutubeMp3.Forms.UI.Views;

public partial class SettingsDialog : Window
{
    public SettingsDialog(AppSettings settings)
    {
        InitializeComponent();
        PlayerHeightTextBox.Text = settings.PlayerMinimumHeight.ToString("0.#", CultureInfo.CurrentCulture);
        Loaded += (_, _) =>
        {
            PlayerHeightTextBox.Focus();
            PlayerHeightTextBox.SelectAll();
        };
    }

    // 확인을 눌러 검증까지 통과한 값. 다이얼로그가 닫힌 뒤 Edit에서 읽는다.
    private double _acceptedPlayerHeight;

    private void OnOkClick(object sender, RoutedEventArgs e)
    {
        if (!TryReadPlayerHeight(out var height))
            return;

        _acceptedPlayerHeight = height;
        DialogResult = true;
    }

    private void OnCancelClick(object sender, RoutedEventArgs e) => DialogResult = false;

    private void OnTextBoxKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            DialogResult = false;
            e.Handled = true;
        }
    }

    private bool TryReadPlayerHeight(out double height)
    {
        if (!double.TryParse(PlayerHeightTextBox.Text, NumberStyles.Float, CultureInfo.CurrentCulture, out height) ||
            height < AppSettings.MinimumAllowedPlayerHeight)
        {
            MessageBox.Show(
                this,
                $"플레이어 화면 최소 높이는 {AppSettings.MinimumAllowedPlayerHeight:0} 이상의 숫자로 입력하세요.",
                "설정",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
            PlayerHeightTextBox.Focus();
            PlayerHeightTextBox.SelectAll();
            return false;
        }

        return true;
    }

    /// <summary>설정 창을 띄운다. 확인을 누르면 값을 settings에 반영·저장하고 true를 반환한다.</summary>
    public static bool Edit(Window? owner, AppSettings settings)
    {
        var dialog = new SettingsDialog(settings);
        if (owner is not null)
            dialog.Owner = owner;

        if (dialog.ShowDialog() != true)
            return false;

        settings.PlayerMinimumHeight = dialog._acceptedPlayerHeight;
        settings.Save();
        return true;
    }
}

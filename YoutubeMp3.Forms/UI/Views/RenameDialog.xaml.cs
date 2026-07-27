using System.Windows;
using System.Windows.Input;

namespace YoutubeMp3.Forms.UI.Views;

public partial class RenameDialog : Window
{
    public RenameDialog(string currentName)
    {
        InitializeComponent();
        NameTextBox.Text = currentName;
        Loaded += (_, _) =>
        {
            NameTextBox.Focus();
            NameTextBox.SelectAll();
        };
    }

    private void OnOkClick(object sender, RoutedEventArgs e) => DialogResult = true;

    private void OnCancelClick(object sender, RoutedEventArgs e) => DialogResult = false;

    private void OnNameTextBoxKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            DialogResult = false;
            e.Handled = true;
        }
    }

    /// <summary>파일명 변경 다이얼로그를 띄우고 확인을 누르면 새 이름을, 취소/빈 값이면 null을 반환한다.</summary>
    public static string? PromptForName(Window? owner, string currentName)
    {
        var dialog = new RenameDialog(currentName);
        if (owner is not null)
            dialog.Owner = owner;

        if (dialog.ShowDialog() != true)
            return null;

        var newName = dialog.NameTextBox.Text.Trim();
        return string.IsNullOrWhiteSpace(newName) ? null : newName;
    }
}

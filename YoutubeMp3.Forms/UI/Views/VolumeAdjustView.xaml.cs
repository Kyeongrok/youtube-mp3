using System.Windows;
using System.Windows.Controls;
using YoutubeMp3.Forms.ViewModels;

namespace YoutubeMp3.Forms.UI.Views;

public partial class VolumeAdjustView : UserControl
{
    public VolumeAdjustView()
    {
        InitializeComponent();
    }

    // 탐색기에서 끌어온 파일만 받는다(목록 내부 드래그 같은 건 없음).
    private void OnDragOver(object sender, DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(DataFormats.FileDrop)
            ? DragDropEffects.Copy
            : DragDropEffects.None;
        e.Handled = true;
    }

    private void OnDrop(object sender, DragEventArgs e)
    {
        if (DataContext is not MainWindowViewModel viewModel)
            return;

        if (e.Data.GetDataPresent(DataFormats.FileDrop) &&
            e.Data.GetData(DataFormats.FileDrop) is string[] paths)
            viewModel.SetVolumeFiles(paths);
    }
}

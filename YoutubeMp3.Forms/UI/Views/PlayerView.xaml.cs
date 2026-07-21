using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using YoutubeMp3.Forms.ViewModels;

namespace YoutubeMp3.Forms.UI.Views;

public partial class PlayerView : UserControl
{
    private Point _dragStart;
    private PlaylistItem? _dragItem;

    public PlayerView()
    {
        InitializeComponent();
    }

    // ── 드롭: 외부 파일 추가 / 내부 순서 변경 ────────────────────

    private void OnDragOver(object sender, DragEventArgs e)
    {
        if (e.Data.GetDataPresent(DataFormats.FileDrop))
            e.Effects = DragDropEffects.Copy;      // 탐색기에서 파일 추가
        else if (e.Data.GetDataPresent(typeof(PlaylistItem)))
            e.Effects = DragDropEffects.Move;      // 목록 내 순서 변경
        else
            e.Effects = DragDropEffects.None;
        e.Handled = true;
    }

    private void OnDrop(object sender, DragEventArgs e)
    {
        if (DataContext is not PlayerViewModel viewModel)
            return;

        if (e.Data.GetDataPresent(DataFormats.FileDrop) &&
            e.Data.GetData(DataFormats.FileDrop) is string[] paths)
        {
            viewModel.AddFiles(paths);
            return;
        }

        if (e.Data.GetDataPresent(typeof(PlaylistItem)) &&
            e.Data.GetData(typeof(PlaylistItem)) is PlaylistItem dragged)
        {
            var target = ItemUnderPoint(e.GetPosition(PlaylistBox));
            viewModel.MoveItem(dragged, target);
        }
    }

    // ── 목록 내 드래그로 순서 변경 시작 ──────────────────────────

    private void OnListPreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        _dragStart = e.GetPosition(null);
        _dragItem = ItemFromElement(e.OriginalSource as DependencyObject);
    }

    private void OnListPreviewMouseMove(object sender, MouseEventArgs e)
    {
        if (e.LeftButton != MouseButtonState.Pressed || _dragItem is null)
            return;

        var pos = e.GetPosition(null);
        if (Math.Abs(pos.X - _dragStart.X) < SystemParameters.MinimumHorizontalDragDistance &&
            Math.Abs(pos.Y - _dragStart.Y) < SystemParameters.MinimumVerticalDragDistance)
            return;

        var item = _dragItem;
        _dragItem = null;
        DragDrop.DoDragDrop(PlaylistBox, item, DragDropEffects.Move);
    }

    // 우클릭 위치의 곡을 먼저 선택해 둬야 컨텍스트 메뉴의 삭제/볼륨 조정이 그 곡에 적용된다
    // (ListBoxItem은 왼쪽 버튼에만 기본 선택 처리를 한다).
    private void OnListPreviewMouseRightButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (DataContext is not PlayerViewModel viewModel)
            return;

        var item = ItemFromElement(e.OriginalSource as DependencyObject);
        if (item is not null)
            viewModel.SelectedItem = item;
    }

    private void OnItemDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (DataContext is PlayerViewModel viewModel && viewModel.SelectedItem is not null)
            viewModel.PlayItemCommand.Execute(viewModel.SelectedItem);
    }

    // ── 진행바 탐색: 잡고 있는 동안 타이머가 위치를 덮어쓰지 않게 ──

    private void OnSeekStart(object sender, MouseButtonEventArgs e)
    {
        if (DataContext is PlayerViewModel viewModel)
            viewModel.IsSeeking = true;
    }

    private void OnSeekEnd(object sender, MouseButtonEventArgs e)
    {
        if (DataContext is PlayerViewModel viewModel)
            viewModel.IsSeeking = false;
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private static PlaylistItem? ItemFromElement(DependencyObject? source)
    {
        while (source is not null and not ListBoxItem)
            source = VisualTreeHelper.GetParent(source);
        return (source as ListBoxItem)?.DataContext as PlaylistItem;
    }

    private PlaylistItem? ItemUnderPoint(Point point)
    {
        var hit = VisualTreeHelper.HitTest(PlaylistBox, point);
        return ItemFromElement(hit?.VisualHit);
    }
}

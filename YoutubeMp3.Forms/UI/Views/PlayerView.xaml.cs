using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shapes;
using YoutubeMp3.Forms.ViewModels;

namespace YoutubeMp3.Forms.UI.Views;

public partial class PlayerView : UserControl
{
    // 물결(ripple) 원의 기본 반지름. 클릭 지점에서 이만큼 크기로 시작해 행 전체를 덮을 때까지 커진다.
    private const double RippleRadius = 10;

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
        if (DataContext is not PlayerViewModel viewModel || viewModel.SelectedItem is null)
            return;

        if (ContainerFromElement(e.OriginalSource as DependencyObject) is { } container)
            PlayRipple(container, e.GetPosition(container));

        viewModel.PlayItemCommand.Execute(viewModel.SelectedItem);
    }

    // 클릭한 지점에서 원이 퍼지며 사라진다(머티리얼 ripple). 재생이 시작됐다는 즉각적인 반응.
    private static void PlayRipple(ListBoxItem container, Point center)
    {
        if (container.Template?.FindName("Ripple", container) is not Ellipse ripple)
            return;

        // 템플릿 안에 둔 Freezable은 얼어 있어 애니메이션을 걸 수 없다. 매번 새 변환을 붙인다.
        var scale = new ScaleTransform(0, 0);
        ripple.RenderTransform = scale;

        Canvas.SetLeft(ripple, center.X - RippleRadius);
        Canvas.SetTop(ripple, center.Y - RippleRadius);

        // 클릭 지점에서 가장 먼 모서리까지 덮을 만큼 키운다.
        var dx = Math.Max(center.X, container.ActualWidth - center.X);
        var dy = Math.Max(center.Y, container.ActualHeight - center.Y);
        var target = Math.Sqrt((dx * dx) + (dy * dy)) / RippleRadius;

        var duration = new Duration(TimeSpan.FromMilliseconds(900));
        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        var grow = new DoubleAnimation(0, target, duration) { EasingFunction = ease };

        scale.BeginAnimation(ScaleTransform.ScaleXProperty, grow);
        scale.BeginAnimation(ScaleTransform.ScaleYProperty, grow);
        ripple.BeginAnimation(UIElement.OpacityProperty, new DoubleAnimation(0.35, 0, duration) { EasingFunction = ease });
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

    private static PlaylistItem? ItemFromElement(DependencyObject? source) =>
        ContainerFromElement(source)?.DataContext as PlaylistItem;

    private static ListBoxItem? ContainerFromElement(DependencyObject? source)
    {
        while (source is not null and not ListBoxItem)
            source = VisualTreeHelper.GetParent(source);
        return source as ListBoxItem;
    }

    private PlaylistItem? ItemUnderPoint(Point point)
    {
        var hit = VisualTreeHelper.HitTest(PlaylistBox, point);
        return ItemFromElement(hit?.VisualHit);
    }
}

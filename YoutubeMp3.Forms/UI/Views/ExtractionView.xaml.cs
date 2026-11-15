using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using YoutubeMp3.Forms.ViewModels;

namespace YoutubeMp3.Forms.UI.Views;

public partial class ExtractionView : UserControl
{
    private const int WmClipboardUpdate = 0x031D;

    private HwndSource? _hwndSource;

    public ExtractionView()
    {
        InitializeComponent();
        Loaded += OnLoaded;
        Unloaded += OnUnloaded;
        DataContextChanged += (_, _) => RefreshClipboard();
    }

    // 클립보드가 바뀔 때마다 알림을 받아 "붙여넣고 추출" 버튼 표시 여부를 갱신한다.
    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        RefreshClipboard();

        if (_hwndSource != null || PresentationSource.FromVisual(this) is not HwndSource source)
            return;

        _hwndSource = source;
        _hwndSource.AddHook(WndProc);
        AddClipboardFormatListener(_hwndSource.Handle);
    }

    private void OnUnloaded(object sender, RoutedEventArgs e)
    {
        if (_hwndSource == null)
            return;

        RemoveClipboardFormatListener(_hwndSource.Handle);
        _hwndSource.RemoveHook(WndProc);
        _hwndSource = null;
    }

    private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WmClipboardUpdate)
            RefreshClipboard();

        return IntPtr.Zero;
    }

    private void RefreshClipboard() => (DataContext as MainWindowViewModel)?.RefreshClipboardUrl();

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool AddClipboardFormatListener(IntPtr hwnd);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool RemoveClipboardFormatListener(IntPtr hwnd);
}

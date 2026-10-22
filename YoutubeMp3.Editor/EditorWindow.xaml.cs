using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using YoutubeMp3.Editor.ViewModels;

namespace YoutubeMp3.Editor;

/// <summary>파형에서 구간을 골라 잘라내는 편집기 창. 재생목록의 "편집" 메뉴가 이 창을 띄운다.</summary>
public partial class EditorWindow : Window
{
    private readonly EditorViewModel _viewModel;

    public EditorWindow(EditorViewModel viewModel)
    {
        InitializeComponent();

        _viewModel = viewModel;
        DataContext = viewModel;

        // Ctrl+Z → 실행 취소
        InputBindings.Add(new KeyBinding(viewModel.UndoCommand, Key.Z, ModifierKeys.Control));

        Closing += (_, _) => _viewModel.Dispose();
        SourceInitialized += (_, _) => ApplyDarkTitleBar();
    }

    // 창 본문(#1E1E1E)과 어울리게 OS 타이틀바를 어둡게 칠한다.
    // 다크 모드 속성은 Windows 10 20H1+, 캡션 색 지정은 Windows 11부터 먹고, 그 밖에서는 조용히 무시된다.
    private const int DwmwaUseImmersiveDarkMode = 20;
    private const int DwmwaCaptionColor = 35;
    private const int CaptionColor = 0x001E1E1E; // COLORREF(0x00BBGGRR)

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    private void ApplyDarkTitleBar()
    {
        var hwnd = new WindowInteropHelper(this).Handle;
        var enabled = 1;
        DwmSetWindowAttribute(hwnd, DwmwaUseImmersiveDarkMode, ref enabled, sizeof(int));
        var color = CaptionColor;
        DwmSetWindowAttribute(hwnd, DwmwaCaptionColor, ref color, sizeof(int));
    }
}

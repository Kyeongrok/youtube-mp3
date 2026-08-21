using System.Windows;
using System.Windows.Input;
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
    }
}

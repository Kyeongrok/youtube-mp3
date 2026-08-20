using System.Windows;
using System.Windows.Input;
using YoutubeMp3.Editor.ViewModels;

namespace YoutubeMp3.Editor;

public partial class MainWindow : Window
{
    private readonly EditorViewModel _viewModel;

    public MainWindow(EditorViewModel viewModel)
    {
        InitializeComponent();

        _viewModel = viewModel;
        DataContext = viewModel;

        // Ctrl+Z → 실행 취소
        InputBindings.Add(new KeyBinding(viewModel.UndoCommand, Key.Z, ModifierKeys.Control));

        Closing += (_, _) => _viewModel.Dispose();
    }
}

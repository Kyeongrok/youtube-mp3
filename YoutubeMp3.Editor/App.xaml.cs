using System.Windows;
using YoutubeMp3.Editor.ViewModels;

namespace YoutubeMp3.Editor;

public partial class App : Application
{
    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // YoutubeMp3에서 편집할 파일 경로를 첫 번째 인자로 넘겨준다.
        var initialFile = e.Args.Length > 0 ? e.Args[0] : null;

        var viewModel = new EditorViewModel();
        var window = new MainWindow(viewModel);
        MainWindow = window;
        window.Show();

        await viewModel.LoadFromArgsAsync(initialFile);
    }
}

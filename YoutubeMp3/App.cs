using System.Windows;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using YoutubeMp3.Forms;
using YoutubeMp3.Forms.UI.Views;
using YoutubeMp3.Forms.ViewModels;
using YoutubeMp3.Main.Services;

namespace YoutubeMp3;

public class App : Application
{
    private IHost? _host;

    public static IServiceProvider? ServiceProvider { get; private set; }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        _host = Host.CreateDefaultBuilder()
            .ConfigureServices((context, services) =>
            {
                ConfigureServices(services);
            })
            .Build();

        ServiceProvider = _host.Services;
        AppServices.Current = _host.Services;

        var mainWindow = ServiceProvider.GetRequiredService<MainWindow>();
        mainWindow.Width = 1000;
        mainWindow.Height = 300;
        mainWindow.Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _host?.Dispose();
        base.OnExit(e);
    }

    private void ConfigureServices(IServiceCollection services)
    {
        // 설정은 시작할 때 한 번 읽어 앱 전체가 같은 인스턴스를 본다(설정 창에서 바꾸면 즉시 반영).
        services.AddSingleton(AppSettings.Load());
        services.AddSingleton<IYoutubeService, YoutubeService>();
        services.AddSingleton<IAudioGainService, AudioGainService>();
        services.AddSingleton<IFileTransferService, FileTransferService>();
        services.AddSingleton<MainWindowViewModel>();
        services.AddSingleton<PlayerViewModel>();
        services.AddSingleton<FileTransferViewModel>();
        services.AddSingleton<MainWindow>();
    }
}

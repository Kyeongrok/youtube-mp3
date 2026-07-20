using Microsoft.Extensions.DependencyInjection;

namespace YoutubeMp3.Forms;

public static class AppServices
{
    public static IServiceProvider? Current { get; set; }

    public static T GetRequired<T>() where T : notnull
        => Current!.GetRequiredService<T>();
}

using System;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace DynamicIslandSettings
{
    public static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            Application.Start(_ => new App());
        }
    }

    public class App : Application
    {
        private Window _window;

        public App()
        {
            // WinUI 3 控件默认样式必须（等效于 XAML 里的 XamlControlsResources）
            Resources.MergedDictionaries.Add(new XamlControlsResources());
        }

        protected override void OnLaunched(LaunchActivatedEventArgs args)
        {
            _window = new MainWindow();
            _window.Activate();
        }
    }
}

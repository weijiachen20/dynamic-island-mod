using System;
using System.IO;
using System.Threading.Tasks;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace DynamicIslandSettings
{
    public static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            // 把未处理异常写入 exe 旁的 crash.log，便于定位崩溃原因
            AppDomain.CurrentDomain.UnhandledException += (s, e) => WriteCrashLog(e.ExceptionObject as Exception);
            TaskScheduler.UnobservedTaskException += (s, e) => WriteCrashLog(e.Exception);

            try
            {
                Application.Start(_ => new App());
            }
            catch (Exception ex)
            {
                WriteCrashLog(ex);
                throw;
            }
        }

        private static void WriteCrashLog(Exception ex)
        {
            try
            {
                File.WriteAllText(
                    Path.Combine(AppContext.BaseDirectory, "crash.log"),
                    DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + Environment.NewLine + ex);
            }
            catch { /* 写日志失败不影响主流程 */ }
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

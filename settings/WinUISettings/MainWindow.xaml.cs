using System;
using System.IO;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Graphics;
using WinRT.Interop;

namespace DynamicIslandSettings
{
    public sealed partial class MainWindow : Window
    {
        private static readonly JsonSerializerOptions JsonOpts = new JsonSerializerOptions
        {
            WriteIndented = true,
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        };

        private string _path;
        private IslandConfig _cfg = new IslandConfig();
        private bool _loading;
        private bool _dirty;
        private AppWindow _appWindow;
        private OverlappedPresenter _presenter;

        public MainWindow()
        {
            InitializeComponent();
            _path = ResolvePath();
            PathText.Text = _path;

            SetupWindowChrome();
            FillComboBoxes();
            WireEvents();
            LoadConfig();
        }

        // ---------------------------------------------------------------- 窗口外观：无边框 + 云母 + 置顶覆盖
        private void SetupWindowChrome()
        {
            var hwnd = WindowNative.GetWindowHandle(this);
            var windowId = Win32Interop.GetWindowIdFromWindow(hwnd);
            _appWindow = AppWindow.GetFromWindowId(windowId);

            _appWindow.Resize(new SizeInt32(560, 820));
            _presenter = _appWindow.Presenter as OverlappedPresenter;
            if (_presenter != null)
            {
                _presenter.IsResizable = true;
                _presenter.IsMaximizable = false;
                _presenter.IsMinimizable = true;
            }

            // 覆盖在游戏界面上：窗口置顶 + 云母背板
            _appWindow.IsAlwaysOnTop = true;

            var titleBar = _appWindow.TitleBar;
            titleBar.ExtendsContentIntoTitleBar = true;
            titleBar.ButtonBackgroundColor = Colors.Transparent;
            titleBar.ButtonInactiveBackgroundColor = Colors.Transparent;

            try
            {
                SystemBackdrop = new MicaBackdrop();
            }
            catch
            {
                Background = new SolidColorBrush(ColorHelper.FromArgb(255, 20, 22, 28));
            }

            Loaded += (_, _) =>
            {
                // 让标题栏整条可拖动，右侧按钮区除外
                double w = Math.Max(AppTitleBar.ActualWidth - 150, 80);
                titleBar.SetDragRectangles(new[] { new RectInt32 { X = 0, Y = 0, Width = (int)w, Height = (int)AppTitleBar.ActualHeight } });
            };
        }

        private void FillComboBoxes()
        {
            CmbPosition.Items.Add("顶部居中");
            CmbPosition.Items.Add("左上角");
            CmbPosition.Items.Add("右上角");
            CmbScale.Items.Add("0.75x");
            CmbScale.Items.Add("1.00x");
            CmbScale.Items.Add("1.25x");
            CmbScale.Items.Add("1.50x");
            CmbScale.Items.Add("1.75x");
            CmbDisplay.Items.Add("2.0s");
            CmbDisplay.Items.Add("3.0s");
            CmbDisplay.Items.Add("3.5s");
            CmbDisplay.Items.Add("4.5s");
            CmbDisplay.Items.Add("6.0s");
            CmbSensitivity.Items.Add("1");
            CmbSensitivity.Items.Add("2");
            CmbSensitivity.Items.Add("3");
            CmbSensitivity.Items.Add("4");
            CmbJumpThreshold.Items.Add("0.05");
            CmbJumpThreshold.Items.Add("0.10");
            CmbJumpThreshold.Items.Add("0.16");
            CmbJumpThreshold.Items.Add("0.25");
            CmbJumpThreshold.Items.Add("0.40");
        }

        // ---------------------------------------------------------------- 事件挂接
        private void WireEvents()
        {
            BtnPick.Click += async (_, _) => await PickFileAsync();
            BtnReload.Click += (_, _) => { LoadConfig(); _dirty = false; UpdateTitle(); };
            BtnSave.Click += (_, _) => SaveConfig();
            BtnClose.Click += (_, _) => _appWindow.Close();
            BtnMin.Click += (_, _) => _presenter?.Minimize();
            BtnPin.Click += (_, _) => { _appWindow.IsAlwaysOnTop = !_appWindow.IsAlwaysOnTop; UpdatePinButton(); };

            Toggle(ChkEnabled, v => _cfg.Enabled = v);
            Toggle(ChkRainbow, v => _cfg.RainbowEnabled = v);
            Toggle(ChkC0, v => _cfg.Potions = v);
            Toggle(ChkC1, v => _cfg.Music = v);
            Toggle(ChkC2, v => _cfg.Weather = v);
            Toggle(ChkC3, v => _cfg.Advancement = v);
            Toggle(ChkC4, v => _cfg.Health = v);
            Toggle(ChkC5, v => _cfg.Hunger = v);
            Toggle(ChkC6, v => _cfg.Daynight = v);
            Toggle(ChkC7, v => _cfg.Damage = v);
            Toggle(ChkC8, v => _cfg.LevelUp = v);
            Toggle(ChkC9, v => _cfg.Liquidbounce = v);
            Toggle(ChkC10, v => _cfg.Netease = v);
            Toggle(ChkC11, v => _cfg.NeteaseLyric = v);
            Toggle(ChkS0, v => _cfg.StatusFps = v);
            Toggle(ChkS1, v => _cfg.StatusIp = v);
            Toggle(ChkS2, v => _cfg.StatusLbVersion = v);
            Toggle(ChkS3, v => _cfg.StatusLyric = v);
            Toggle(ChkS4, v => _cfg.StatusScaffold = v);
            Toggle(ChkS5, v => _cfg.StatusModules = v);
            Toggle(ChkS6, v => _cfg.StatusKa = v);
            Toggle(ChkS7, v => _cfg.StatusSpeed = v);
            Toggle(ChkS8, v => _cfg.StatusArmor = v);
            Toggle(ChkS9, v => _cfg.StatusPing = v);
            Toggle(ChkJumpReset, v => _cfg.JumpReset = v);

            Combo(CmbPosition, v => _cfg.Position = v);
            Combo(CmbScale, v => _cfg.Scale = new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }[v]);
            Combo(CmbDisplay, v => _cfg.DisplayTime = new[] { 2f, 3f, 3.5f, 4.5f, 6f }[v]);
            Combo(CmbSensitivity, v => _cfg.Sensitivity = v + 1);
            Combo(CmbJumpThreshold, v => _cfg.JumpResetThreshold = new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }[v]);

            SliderBg.ValueChanged += (_, e) => { int v = (int)Math.Round(e.NewValue / 5.0) * 5; _cfg.BgOpacity = v; BgValue.Text = v + "%"; MarkDirty(); };
            SliderRainbow.ValueChanged += (_, e) => { int v = (int)Math.Round(e.NewValue / 12.0) * 12; _cfg.RainbowSpeed = v; RainbowValue.Text = v + "°/s"; MarkDirty(); };

            NumRadius.ValueChanged += (_, e) => { if (e.NewValue.HasValue) { _cfg.CornerRadius = (int)e.NewValue.Value; MarkDirty(); } };
            NumHealth.ValueChanged += (_, e) => { if (e.NewValue.HasValue) { _cfg.HealthThreshold = (int)e.NewValue.Value; MarkDirty(); } };
            NumHunger.ValueChanged += (_, e) => { if (e.NewValue.HasValue) { _cfg.HungerThreshold = (int)e.NewValue.Value; MarkDirty(); } };
        }

        private void Toggle(ToggleSwitch sw, Action<bool> setter)
        {
            sw.Toggled += (_, _) => { if (!_loading) { setter(sw.IsOn); MarkDirty(); } };
        }

        private void Combo(ComboBox cmb, Action<int> setter)
        {
            cmb.SelectionChanged += (_, _) =>
            {
                if (!_loading && cmb.SelectedIndex >= 0)
                {
                    setter(cmb.SelectedIndex);
                    MarkDirty();
                }
            };
        }

        // ---------------------------------------------------------------- 数据
        private void LoadConfig()
        {
            try
            {
                if (File.Exists(_path))
                {
                    string json = File.ReadAllText(_path, Encoding.UTF8);
                    _cfg = JsonSerializer.Deserialize<IslandConfig>(json) ?? new IslandConfig();
                }
                else
                {
                    _cfg = new IslandConfig();
                }
            }
            catch (Exception ex)
            {
                _cfg = new IslandConfig();
                ShowMessage("读取配置失败，已使用默认值。\n" + ex.Message, true);
            }
            RefreshUI();
        }

        private void RefreshUI()
        {
            _loading = true;

            ChkEnabled.IsOn = _cfg.Enabled;
            ChkRainbow.IsOn = _cfg.RainbowEnabled;
            ChkC0.IsOn = _cfg.Potions;
            ChkC1.IsOn = _cfg.Music;
            ChkC2.IsOn = _cfg.Weather;
            ChkC3.IsOn = _cfg.Advancement;
            ChkC4.IsOn = _cfg.Health;
            ChkC5.IsOn = _cfg.Hunger;
            ChkC6.IsOn = _cfg.Daynight;
            ChkC7.IsOn = _cfg.Damage;
            ChkC8.IsOn = _cfg.LevelUp;
            ChkC9.IsOn = _cfg.Liquidbounce;
            ChkC10.IsOn = _cfg.Netease;
            ChkC11.IsOn = _cfg.NeteaseLyric;
            ChkS0.IsOn = _cfg.StatusFps;
            ChkS1.IsOn = _cfg.StatusIp;
            ChkS2.IsOn = _cfg.StatusLbVersion;
            ChkS3.IsOn = _cfg.StatusLyric;
            ChkS4.IsOn = _cfg.StatusScaffold;
            ChkS5.IsOn = _cfg.StatusModules;
            ChkS6.IsOn = _cfg.StatusKa;
            ChkS7.IsOn = _cfg.StatusSpeed;
            ChkS8.IsOn = _cfg.StatusArmor;
            ChkS9.IsOn = _cfg.StatusPing;
            ChkJumpReset.IsOn = _cfg.JumpReset;

            CmbPosition.SelectedIndex = Clamp(_cfg.Position, 0, 2);
            CmbScale.SelectedIndex = IndexOf(new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }, _cfg.Scale);
            CmbDisplay.SelectedIndex = IndexOf(new[] { 2f, 3f, 3.5f, 4.5f, 6f }, _cfg.DisplayTime);
            CmbSensitivity.SelectedIndex = Clamp(_cfg.Sensitivity - 1, 0, 3);
            CmbJumpThreshold.SelectedIndex = IndexOf(new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }, _cfg.JumpResetThreshold);

            SliderBg.Value = Clamp(_cfg.BgOpacity, 60, 100);
            BgValue.Text = _cfg.BgOpacity + "%";
            SliderRainbow.Value = Clamp((int)Math.Round(_cfg.RainbowSpeed), 0, 180);
            RainbowValue.Text = _cfg.RainbowSpeed + "°/s";

            NumRadius.Value = Clamp(_cfg.CornerRadius, 0, 40);
            NumHealth.Value = Clamp(_cfg.HealthThreshold, 1, 20);
            NumHunger.Value = Clamp(_cfg.HungerThreshold, 1, 20);

            _loading = false;
        }

        private void SaveConfig()
        {
            try
            {
                string dir = Path.GetDirectoryName(_path);
                if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                string json = JsonSerializer.Serialize(_cfg, JsonOpts);
                File.WriteAllText(_path, json, new UTF8Encoding(false));
                _dirty = false;
                UpdateTitle();
                ShowMessage("已保存到：\n" + _path, false);
            }
            catch (Exception ex)
            {
                ShowMessage("保存失败：\n" + ex.Message, true);
            }
        }

        private async Task PickFileAsync()
        {
            var picker = new Windows.Storage.Pickers.FileOpenPicker { SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary };
            picker.FileTypeFilter.Add(".json");
            var hwnd = WindowNative.GetWindowHandle(this);
            InitializeWithWindow.Initialize(picker, hwnd);
            var file = await picker.PickSingleFileAsync();
            if (file != null)
            {
                _path = file.Path;
                PathText.Text = _path;
                LoadConfig();
            }
        }

        private void ShowMessage(string text, bool isError)
        {
            var dialog = new ContentDialog
            {
                Title = "Dynamic Island",
                Content = text,
                CloseButtonText = "确定",
                XamlRoot = this.Content.XamlRoot,
            };
            _ = dialog.ShowAsync();
        }

        private void MarkDirty()
        {
            if (_loading) return;
            _dirty = true;
            UpdateTitle();
        }

        private void UpdateTitle()
        {
            TitleText.Text = "Dynamic Island Settings (WinUI 3)" + (_dirty ? " *" : "");
        }

        private void UpdatePinButton()
        {
            BtnPin.Content = _appWindow.IsAlwaysOnTop ? "📌" : "📍";
        }

        // ---------------------------------------------------------------- helpers
        private static int IndexOf(float[] arr, float value)
        {
            for (int i = 0; i < arr.Length; i++)
                if (Math.Abs(arr[i] - value) < 0.001f)
                    return i;
            return 0;
        }

        private static int Clamp(int v, int min, int max) => Math.Max(min, Math.Min(max, v));

        private static string ResolvePath()
        {
            string[] args = Environment.GetCommandLineArgs();
            if (args.Length > 1 && File.Exists(args[1]))
                return Path.GetFullPath(args[1]);

            string appdata = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string[] candidates =
            {
                Path.Combine(appdata, ".minecraft", "config", "dynamicisland.json"),
                Path.Combine(Environment.CurrentDirectory, "config", "dynamicisland.json"),
                Path.Combine(Environment.CurrentDirectory, "dynamicisland.json"),
            };
            foreach (string c in candidates)
                if (File.Exists(c))
                    return c;
            return Path.Combine(appdata, ".minecraft", "config", "dynamicisland.json");
        }
    }
}

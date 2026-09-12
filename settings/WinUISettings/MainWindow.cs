using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Graphics;
using WinRT.Interop;

namespace DynamicIslandSettings
{
    public sealed class MainWindow : Window
    {
        private static readonly JsonSerializerOptions JsonOpts = new JsonSerializerOptions
        {
            WriteIndented = true,
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        };

        private readonly SolidColorBrush _labelBrush = new(ColorHelper.FromArgb(255, 230, 232, 238));
        private readonly SolidColorBrush _headerBrush = new(ColorHelper.FromArgb(255, 111, 177, 255));
        private readonly SolidColorBrush _cardBrush = new(ColorHelper.FromArgb(255, 30, 34, 43));
        private readonly SolidColorBrush _grayBrush = new(Colors.Gray);
        private readonly SolidColorBrush _accentBrush = new(ColorHelper.FromArgb(255, 76, 194, 255));

        private string _path;
        private IslandConfig _cfg = new IslandConfig();
        private bool _loading;
        private bool _dirty;
        private AppWindow _appWindow;
        private OverlappedPresenter _presenter;
        private bool _alwaysOnTop = true;

        // 控件引用
        private TextBlock _pathText, _titleText;
        private ToggleSwitch _chkEnabled, _chkRainbow;
        private ToggleSwitch[] _chkC = new ToggleSwitch[12];
        private ToggleSwitch[] _chkS = new ToggleSwitch[10];
        private ToggleSwitch _chkJumpReset;
        private ComboBox _cmbPosition, _cmbScale, _cmbDisplay, _cmbSensitivity, _cmbJumpThreshold;
        private Slider _sliderBg, _sliderRadius, _sliderRainbow, _sliderHealth, _sliderHunger;
        private TextBlock _bgValue, _radiusValue, _rainbowValue, _healthValue, _hungerValue;

        public MainWindow()
        {
            _path = ResolvePath();
            Content = BuildRoot();
            SetupWindowChrome();
            FillComboBoxes();
            LoadConfig();
        }

        // ---------------------------------------------------------------- 布局
        private FrameworkElement BuildRoot()
        {
            var root = new Grid { Background = new SolidColorBrush(ColorHelper.FromArgb(240, 27, 30, 38)) };
            root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(42) });
            root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            // 标题栏
            var titleBar = new Grid { Background = new SolidColorBrush(Colors.Transparent) };
            var dot = new Microsoft.UI.Xaml.Shapes.Ellipse { Width = 10, Height = 10, Fill = _accentBrush, VerticalAlignment = VerticalAlignment.Center };
            _titleText = new TextBlock { Text = "Dynamic Island Settings", Foreground = _labelBrush, FontSize = 13, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(8, 0, 0, 0) };
            var titleStack = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(16, 0, 0, 0) };
            titleStack.Children.Add(dot);
            titleStack.Children.Add(_titleText);

            var pinBtn = new Button { Content = "📌", Width = 34, Height = 28, Margin = new Thickness(0, 0, 6, 0) };
            pinBtn.Click += (_, _) => ToggleAlwaysOnTop();
            var minBtn = new Button { Content = "—", Width = 34, Height = 28, Margin = new Thickness(0, 0, 6, 0) };
            minBtn.Click += (_, _) => _presenter?.Minimize();
            var closeBtn = new Button { Content = "✕", Width = 34, Height = 28 };
            closeBtn.Click += (_, _) => Close();
            var btnStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 10, 0) };
            btnStack.Children.Add(pinBtn);
            btnStack.Children.Add(minBtn);
            btnStack.Children.Add(closeBtn);
            titleBar.Children.Add(titleStack);
            titleBar.Children.Add(btnStack);
            root.Children.Add(titleBar);

            // 工具条
            var bar = new Grid { Margin = new Thickness(16, 4, 16, 8) };
            bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            _pathText = new TextBlock { VerticalAlignment = VerticalAlignment.Center, Foreground = _grayBrush, FontSize = 11, TextTrimming = TextTrimming.CharacterEllipsis };
            var pickBtn = new Button { Content = "选择…", Width = 70, Height = 28, Margin = new Thickness(0, 0, 8, 0) };
            pickBtn.Click += async (_, _) => await PickFileAsync();
            var reloadBtn = new Button { Content = "重新加载", Width = 84, Height = 28, Margin = new Thickness(0, 0, 8, 0) };
            reloadBtn.Click += (_, _) => { LoadConfig(); _dirty = false; UpdateTitle(); };
            var saveBtn = new Button { Content = "保存", Width = 70, Height = 28, Background = new SolidColorBrush(ColorHelper.FromArgb(255, 45, 108, 181)), Foreground = new SolidColorBrush(Colors.White) };
            saveBtn.Click += (_, _) => SaveConfig();
            var barStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
            barStack.Children.Add(pickBtn);
            barStack.Children.Add(reloadBtn);
            barStack.Children.Add(saveBtn);
            bar.Children.Add(_pathText);
            bar.Children.Add(barStack);
            Grid.SetColumn(barStack, 1);
            Grid.SetRow(bar, 1);
            root.Children.Add(bar);

            // 主体
            var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Padding = new Thickness(16, 0, 16, 16) };
            var body = new StackPanel { Spacing = 12 };
            body.Children.Add(Card("基本", stack =>
            {
                _chkEnabled = Toggle("启用灵动岛", v => _cfg.Enabled = v);
                stack.Children.Add(_chkEnabled);
            }));
            body.Children.Add(Card("外观", stack =>
            {
                _cmbPosition = RowCombo(stack, "位置", v => _cfg.Position = v);
                _cmbScale = RowCombo(stack, "缩放比例", v => _cfg.Scale = new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }[v]);
                (_sliderBg, _bgValue) = RowSlider(stack, "背景不透明度", 60, 100, 5, v => { _cfg.BgOpacity = v; _bgValue.Text = v + "%"; });
                (_sliderRadius, _radiusValue) = RowSlider(stack, "圆角半径", 0, 40, 2, v => { _cfg.CornerRadius = v; _radiusValue.Text = v.ToString(); });
                _chkRainbow = Toggle("流动彩虹色", v => _cfg.RainbowEnabled = v);
                (_sliderRainbow, _rainbowValue) = RowSlider(stack, "彩虹流动速度 (°/s)", 0, 180, 12, v => { _cfg.RainbowSpeed = v; _rainbowValue.Text = v + "°/s"; });
            }));
            body.Children.Add(Card("事件行为", stack =>
            {
                _cmbDisplay = RowCombo(stack, "事件显示时长", v => _cfg.DisplayTime = new[] { 2f, 3f, 3.5f, 4.5f, 6f }[v]);
                _cmbSensitivity = RowCombo(stack, "事件灵敏度", v => _cfg.Sensitivity = v + 1);
                (_sliderHealth, _healthValue) = RowSlider(stack, "低血量阈值（心）", 1, 20, 1, v => { _cfg.HealthThreshold = v; _healthValue.Text = v + " ❤"; });
                (_sliderHunger, _hungerValue) = RowSlider(stack, "低饱食度阈值（鸡腿）", 1, 20, 1, v => { _cfg.HungerThreshold = v; _hungerValue.Text = v + " 🍗"; });
            }));
            body.Children.Add(Card("事件类别", stack =>
            {
                string[] names = { "显示药水效果", "显示唱片音乐", "显示天气变化", "显示成就达成", "显示低血量告警", "显示低饱食度告警", "显示昼夜交替", "显示受伤告警", "显示升级告警", "显示 LiquidBounce 模块开关", "显示网易云音乐", "显示歌词（需开启桌面歌词）" };
                Action<bool>[] setters = { v => _cfg.Potions = v, v => _cfg.Music = v, v => _cfg.Weather = v, v => _cfg.Advancement = v, v => _cfg.Health = v, v => _cfg.Hunger = v, v => _cfg.Daynight = v, v => _cfg.Damage = v, v => _cfg.LevelUp = v, v => _cfg.Liquidbounce = v, v => _cfg.Netease = v, v => _cfg.NeteaseLyric = v };
                for (int i = 0; i < names.Length; i++) { _chkC[i] = Toggle(names[i], setters[i]); stack.Children.Add(_chkC[i]); }
            }));
            body.Children.Add(Card("状态栏（折叠态常驻信息）", stack =>
            {
                string[] names = { "一直显示帧率", "一直显示服务器 IP", "一直显示 LiquidBounce 版本", "一直显示歌词", "Scaffold 开启时显示 BPS 和方块进度", "显示模块激活仪表盘", "KillAura 开启时显示 APS 和目标", "显示实时速度计", "显示盔甲完整度 + 低耐久告警", "显示延迟（ping ms）" };
                Action<bool>[] setters = { v => _cfg.StatusFps = v, v => _cfg.StatusIp = v, v => _cfg.StatusLbVersion = v, v => _cfg.StatusLyric = v, v => _cfg.StatusScaffold = v, v => _cfg.StatusModules = v, v => _cfg.StatusKa = v, v => _cfg.StatusSpeed = v, v => _cfg.StatusArmor = v, v => _cfg.StatusPing = v };
                for (int i = 0; i < names.Length; i++) { _chkS[i] = Toggle(names[i], setters[i]); stack.Children.Add(_chkS[i]); }
            }));
            body.Children.Add(Card("跳跃重置", stack =>
            {
                _chkJumpReset = Toggle("跳跃重置（受击落地自动跳）", v => _cfg.JumpReset = v);
                stack.Children.Add(_chkJumpReset);
                _cmbJumpThreshold = RowCombo(stack, "击退判定阈值", v => _cfg.JumpResetThreshold = new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }[v]);
            }));

            scroll.Content = body;
            Grid.SetRow(scroll, 2);
            root.Children.Add(scroll);
            return root;
        }

        // ---------------------------------------------------------------- 布局辅助
        private static FrameworkElement Card(string header, Action<StackPanel> fill)
        {
            var stack = new StackPanel { Spacing = 8 };
            stack.Children.Add(new TextBlock { Text = header, Foreground = new SolidColorBrush(ColorHelper.FromArgb(255, 111, 177, 255)), FontSize = 14, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, Margin = new Thickness(0, 0, 0, 2) });
            fill(stack);
            return new Border { Background = new SolidColorBrush(ColorHelper.FromArgb(255, 30, 34, 43)), CornerRadius = new CornerRadius(10), Padding = new Thickness(14), BorderBrush = new SolidColorBrush(ColorHelper.FromArgb(42, 255, 255, 255)), BorderThickness = new Thickness(1), Child = stack };
        }

        private ToggleSwitch Toggle(string header, Action<bool> setter)
        {
            var sw = new ToggleSwitch { Header = header, OnContent = "开", OffContent = "关", Foreground = _labelBrush };
            sw.Toggled += (_, _) => { if (!_loading) { setter(sw.IsOn); MarkDirty(); } };
            return sw;
        }

        private ComboBox RowCombo(StackPanel stack, string label, Action<int> setter)
        {
            var grid = new Grid { Margin = new Thickness(0, 4, 0, 0) };
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(170) });
            grid.Children.Add(new TextBlock { Text = label, VerticalAlignment = VerticalAlignment.Center, Foreground = _labelBrush });
            var cmb = new ComboBox { HorizontalAlignment = HorizontalAlignment.Stretch };
            Grid.SetColumn(cmb, 1);
            cmb.SelectionChanged += (_, _) => { if (!_loading && cmb.SelectedIndex >= 0) { setter(cmb.SelectedIndex); MarkDirty(); } };
            grid.Children.Add(cmb);
            stack.Children.Add(grid);
            return cmb;
        }

        private (Slider, TextBlock) RowSlider(StackPanel stack, string label, int min, int max, int step, Action<int> onValue)
        {
            var grid = new Grid { Margin = new Thickness(0, 4, 0, 0) };
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(44) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(150) });
            grid.Children.Add(new TextBlock { Text = label, VerticalAlignment = VerticalAlignment.Center, Foreground = _labelBrush });
            var value = new TextBlock { Width = 44, VerticalAlignment = VerticalAlignment.Center, Foreground = _grayBrush };
            Grid.SetColumn(value, 1);
            var slider = new Slider { Minimum = min, Maximum = max, StepFrequency = step, TickFrequency = step };
            Grid.SetColumn(slider, 2);
            slider.ValueChanged += (_, e) => { if (!_loading) { onValue((int)Math.Round(e.NewValue)); MarkDirty(); } };
            grid.Children.Add(value);
            grid.Children.Add(slider);
            stack.Children.Add(grid);
            return (slider, value);
        }

        private void FillComboBoxes()
        {
            _cmbPosition.Items.Add("顶部居中");
            _cmbPosition.Items.Add("左上角");
            _cmbPosition.Items.Add("右上角");
            _cmbScale.Items.Add("0.75x");
            _cmbScale.Items.Add("1.00x");
            _cmbScale.Items.Add("1.25x");
            _cmbScale.Items.Add("1.50x");
            _cmbScale.Items.Add("1.75x");
            _cmbDisplay.Items.Add("2.0s");
            _cmbDisplay.Items.Add("3.0s");
            _cmbDisplay.Items.Add("3.5s");
            _cmbDisplay.Items.Add("4.5s");
            _cmbDisplay.Items.Add("6.0s");
            _cmbSensitivity.Items.Add("1");
            _cmbSensitivity.Items.Add("2");
            _cmbSensitivity.Items.Add("3");
            _cmbSensitivity.Items.Add("4");
            _cmbJumpThreshold.Items.Add("0.05");
            _cmbJumpThreshold.Items.Add("0.10");
            _cmbJumpThreshold.Items.Add("0.16");
            _cmbJumpThreshold.Items.Add("0.25");
            _cmbJumpThreshold.Items.Add("0.40");
        }

        // ---------------------------------------------------------------- 窗口外观：置顶覆盖 + 云母
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
                _presenter.IsAlwaysOnTop = _alwaysOnTop;
            }

            var titleBar = _appWindow.TitleBar;
            titleBar.ExtendsContentIntoTitleBar = true;
            titleBar.ButtonBackgroundColor = Colors.Transparent;
            titleBar.ButtonInactiveBackgroundColor = Colors.Transparent;

            try { SystemBackdrop = new MicaBackdrop(); }
            catch { /* Win10 无云母，回退纯色背景 */ }

            ((FrameworkElement)Content).Loaded += (_, _) =>
            {
                double w = Math.Max(((FrameworkElement)Content).ActualWidth - 150, 80);
                titleBar.SetDragRectangles(new[] { new RectInt32 { X = 0, Y = 0, Width = (int)w, Height = 42 } });
            };
        }

        private void ToggleAlwaysOnTop()
        {
            _alwaysOnTop = !_alwaysOnTop;
            if (_presenter != null) _presenter.IsAlwaysOnTop = _alwaysOnTop;
        }

        // ---------------------------------------------------------------- 数据
        private void LoadConfig()
        {
            try
            {
                if (File.Exists(_path))
                    _cfg = JsonSerializer.Deserialize<IslandConfig>(File.ReadAllText(_path, Encoding.UTF8)) ?? new IslandConfig();
                else
                    _cfg = new IslandConfig();
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

            _chkEnabled.IsOn = _cfg.Enabled;
            _chkRainbow.IsOn = _cfg.RainbowEnabled;
            bool[] cats = { _cfg.Potions, _cfg.Music, _cfg.Weather, _cfg.Advancement, _cfg.Health, _cfg.Hunger, _cfg.Daynight, _cfg.Damage, _cfg.LevelUp, _cfg.Liquidbounce, _cfg.Netease, _cfg.NeteaseLyric };
            for (int i = 0; i < 12; i++) _chkC[i].IsOn = cats[i];
            bool[] stats = { _cfg.StatusFps, _cfg.StatusIp, _cfg.StatusLbVersion, _cfg.StatusLyric, _cfg.StatusScaffold, _cfg.StatusModules, _cfg.StatusKa, _cfg.StatusSpeed, _cfg.StatusArmor, _cfg.StatusPing };
            for (int i = 0; i < 10; i++) _chkS[i].IsOn = stats[i];
            _chkJumpReset.IsOn = _cfg.JumpReset;

            _cmbPosition.SelectedIndex = Clamp(_cfg.Position, 0, 2);
            _cmbScale.SelectedIndex = IndexOf(new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }, _cfg.Scale);
            _cmbDisplay.SelectedIndex = IndexOf(new[] { 2f, 3f, 3.5f, 4.5f, 6f }, _cfg.DisplayTime);
            _cmbSensitivity.SelectedIndex = Clamp(_cfg.Sensitivity - 1, 0, 3);
            _cmbJumpThreshold.SelectedIndex = IndexOf(new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }, _cfg.JumpResetThreshold);

            _sliderBg.Value = Clamp(_cfg.BgOpacity, 60, 100);
            _bgValue.Text = _cfg.BgOpacity + "%";
            _sliderRadius.Value = Clamp(_cfg.CornerRadius, 0, 40);
            _radiusValue.Text = _cfg.CornerRadius.ToString();
            _sliderRainbow.Value = Clamp((int)Math.Round(_cfg.RainbowSpeed), 0, 180);
            _rainbowValue.Text = _cfg.RainbowSpeed + "°/s";
            _sliderHealth.Value = Clamp(_cfg.HealthThreshold, 1, 20);
            _healthValue.Text = _cfg.HealthThreshold + " ❤";
            _sliderHunger.Value = Clamp(_cfg.HungerThreshold, 1, 20);
            _hungerValue.Text = _cfg.HungerThreshold + " 🍗";

            _loading = false;
        }

        private void SaveConfig()
        {
            try
            {
                string dir = Path.GetDirectoryName(_path);
                if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                File.WriteAllText(_path, JsonSerializer.Serialize(_cfg, JsonOpts), new UTF8Encoding(false));
                _dirty = false;
                UpdateTitle();
                ShowMessage("已保存到：\n" + _path, false);
            }
            catch (Exception ex)
            {
                ShowMessage("保存失败：\n" + ex.Message, true);
            }
        }

        private async System.Threading.Tasks.Task PickFileAsync()
        {
            var picker = new Windows.Storage.Pickers.FileOpenPicker { SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary };
            picker.FileTypeFilter.Add(".json");
            var hwnd = WindowNative.GetWindowHandle(this);
            InitializeWithWindow.Initialize(picker, hwnd);
            var file = await picker.PickSingleFileAsync();
            if (file != null)
            {
                _path = file.Path;
                _pathText.Text = _path;
                LoadConfig();
            }
        }

        private void ShowMessage(string text, bool isError)
        {
            var dialog = new ContentDialog { Title = "Dynamic Island", Content = text, CloseButtonText = "确定", XamlRoot = this.Content.XamlRoot };
            _ = dialog.ShowAsync();
        }

        private void MarkDirty()
        {
            if (_loading) return;
            _dirty = true;
            UpdateTitle();
        }

        private void UpdateTitle() => _titleText.Text = "Dynamic Island Settings" + (_dirty ? " *" : "");

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

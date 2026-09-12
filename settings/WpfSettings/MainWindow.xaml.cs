using System;
using System.IO;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace DynamicIslandSettings
{
    public partial class MainWindow : Window
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

        public MainWindow()
        {
            InitializeComponent();
            _path = ResolvePath();
            PathText.Text = _path;

            FillComboBoxes();
            LoadConfig();
        }

        // ---------------------------------------------------------------- 窗口
        private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            if (e.OriginalSource is Button) return;
            if (e.LeftButton == MouseButtonState.Pressed) DragMove();
        }

        private void BtnPin_Click(object sender, RoutedEventArgs e)
        {
            Topmost = !Topmost;
            BtnPin.Content = Topmost ? "📌" : "📍";
        }

        private void BtnMin_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

        private void BtnClose_Click(object sender, RoutedEventArgs e) => Close();

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

        // ---------------------------------------------------------------- 控件事件
        private void Bool_Changed(object sender, RoutedEventArgs e)
        {
            if (_loading) return;
            var cb = (CheckBox)sender;
            bool v = cb.IsChecked == true;
            switch (cb.Name)
            {
                case "ChkEnabled": _cfg.Enabled = v; break;
                case "ChkRainbow": _cfg.RainbowEnabled = v; break;
                case "ChkC0": _cfg.Potions = v; break;
                case "ChkC1": _cfg.Music = v; break;
                case "ChkC2": _cfg.Weather = v; break;
                case "ChkC3": _cfg.Advancement = v; break;
                case "ChkC4": _cfg.Health = v; break;
                case "ChkC5": _cfg.Hunger = v; break;
                case "ChkC6": _cfg.Daynight = v; break;
                case "ChkC7": _cfg.Damage = v; break;
                case "ChkC8": _cfg.LevelUp = v; break;
                case "ChkC9": _cfg.Liquidbounce = v; break;
                case "ChkC10": _cfg.Netease = v; break;
                case "ChkC11": _cfg.NeteaseLyric = v; break;
                case "ChkS0": _cfg.StatusFps = v; break;
                case "ChkS1": _cfg.StatusIp = v; break;
                case "ChkS2": _cfg.StatusLbVersion = v; break;
                case "ChkS3": _cfg.StatusLyric = v; break;
                case "ChkS4": _cfg.StatusScaffold = v; break;
                case "ChkS5": _cfg.StatusModules = v; break;
                case "ChkS6": _cfg.StatusKa = v; break;
                case "ChkS7": _cfg.StatusSpeed = v; break;
                case "ChkS8": _cfg.StatusArmor = v; break;
                case "ChkS9": _cfg.StatusPing = v; break;
                case "ChkJumpReset": _cfg.JumpReset = v; break;
            }
            MarkDirty();
        }

        private void Cmb_Changed(object sender, SelectionChangedEventArgs e)
        {
            if (_loading) return;
            var cmb = (ComboBox)sender;
            if (cmb.SelectedIndex < 0) return;
            int i = cmb.SelectedIndex;
            switch (cmb.Name)
            {
                case "CmbPosition": _cfg.Position = i; break;
                case "CmbScale": _cfg.Scale = new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }[i]; break;
                case "CmbDisplay": _cfg.DisplayTime = new[] { 2f, 3f, 3.5f, 4.5f, 6f }[i]; break;
                case "CmbSensitivity": _cfg.Sensitivity = i + 1; break;
                case "CmbJumpThreshold": _cfg.JumpResetThreshold = new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }[i]; break;
            }
            MarkDirty();
        }

        private void SliderBg_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_loading) return;
            int v = (int)Math.Round(e.NewValue / 5.0) * 5;
            _cfg.BgOpacity = v;
            BgValue.Text = v + "%";
            MarkDirty();
        }

        private void SliderRadius_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_loading) return;
            _cfg.CornerRadius = (int)Math.Round(e.NewValue);
            RadiusValue.Text = _cfg.CornerRadius.ToString();
            MarkDirty();
        }

        private void SliderRainbow_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_loading) return;
            int v = (int)Math.Round(e.NewValue / 12.0) * 12;
            _cfg.RainbowSpeed = v;
            RainbowValue.Text = v + "°/s";
            MarkDirty();
        }

        private void SliderHealth_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_loading) return;
            _cfg.HealthThreshold = (int)Math.Round(e.NewValue);
            HealthValue.Text = _cfg.HealthThreshold + " ❤";
            MarkDirty();
        }

        private void SliderHunger_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (_loading) return;
            _cfg.HungerThreshold = (int)Math.Round(e.NewValue);
            HungerValue.Text = _cfg.HungerThreshold + " 🍗";
            MarkDirty();
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
                MessageBox.Show(this, "读取配置失败，已使用默认值。\n" + ex.Message, "Dynamic Island", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
            RefreshUI();
        }

        private void RefreshUI()
        {
            _loading = true;

            ChkEnabled.IsChecked = _cfg.Enabled;
            ChkRainbow.IsChecked = _cfg.RainbowEnabled;
            ChkC0.IsChecked = _cfg.Potions;
            ChkC1.IsChecked = _cfg.Music;
            ChkC2.IsChecked = _cfg.Weather;
            ChkC3.IsChecked = _cfg.Advancement;
            ChkC4.IsChecked = _cfg.Health;
            ChkC5.IsChecked = _cfg.Hunger;
            ChkC6.IsChecked = _cfg.Daynight;
            ChkC7.IsChecked = _cfg.Damage;
            ChkC8.IsChecked = _cfg.LevelUp;
            ChkC9.IsChecked = _cfg.Liquidbounce;
            ChkC10.IsChecked = _cfg.Netease;
            ChkC11.IsChecked = _cfg.NeteaseLyric;
            ChkS0.IsChecked = _cfg.StatusFps;
            ChkS1.IsChecked = _cfg.StatusIp;
            ChkS2.IsChecked = _cfg.StatusLbVersion;
            ChkS3.IsChecked = _cfg.StatusLyric;
            ChkS4.IsChecked = _cfg.StatusScaffold;
            ChkS5.IsChecked = _cfg.StatusModules;
            ChkS6.IsChecked = _cfg.StatusKa;
            ChkS7.IsChecked = _cfg.StatusSpeed;
            ChkS8.IsChecked = _cfg.StatusArmor;
            ChkS9.IsChecked = _cfg.StatusPing;
            ChkJumpReset.IsChecked = _cfg.JumpReset;

            CmbPosition.SelectedIndex = Clamp(_cfg.Position, 0, 2);
            CmbScale.SelectedIndex = IndexOf(new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }, _cfg.Scale);
            CmbDisplay.SelectedIndex = IndexOf(new[] { 2f, 3f, 3.5f, 4.5f, 6f }, _cfg.DisplayTime);
            CmbSensitivity.SelectedIndex = Clamp(_cfg.Sensitivity - 1, 0, 3);
            CmbJumpThreshold.SelectedIndex = IndexOf(new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }, _cfg.JumpResetThreshold);

            SliderBg.Value = Clamp(_cfg.BgOpacity, 60, 100);
            BgValue.Text = _cfg.BgOpacity + "%";
            SliderRadius.Value = Clamp(_cfg.CornerRadius, 0, 40);
            RadiusValue.Text = _cfg.CornerRadius.ToString();
            SliderRainbow.Value = Clamp((int)Math.Round(_cfg.RainbowSpeed), 0, 180);
            RainbowValue.Text = _cfg.RainbowSpeed + "°/s";
            SliderHealth.Value = Clamp(_cfg.HealthThreshold, 1, 20);
            HealthValue.Text = _cfg.HealthThreshold + " ❤";
            SliderHunger.Value = Clamp(_cfg.HungerThreshold, 1, 20);
            HungerValue.Text = _cfg.HungerThreshold + " 🍗";

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
                MessageBox.Show(this, "已保存到：\n" + _path, "Dynamic Island", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "保存失败：\n" + ex.Message, "Dynamic Island", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnSave_Click(object sender, RoutedEventArgs e) => SaveConfig();

        private void BtnReload_Click(object sender, RoutedEventArgs e)
        {
            LoadConfig();
            _dirty = false;
            UpdateTitle();
        }

        private void BtnPick_Click(object sender, RoutedEventArgs e)
        {
            var dlg = new Microsoft.Win32.OpenFileDialog
            {
                Title = "选择 dynamicisland.json",
                Filter = "JSON 文件 (*.json)|*.json|所有文件 (*.*)|*.*",
                FileName = _path,
            };
            if (dlg.ShowDialog(this) == true)
            {
                _path = dlg.FileName;
                PathText.Text = _path;
                LoadConfig();
            }
        }

        private void MarkDirty()
        {
            if (_loading) return;
            _dirty = true;
            UpdateTitle();
        }

        private void UpdateTitle() => TitleText.Text = "Dynamic Island Settings" + (_dirty ? " *" : "");

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

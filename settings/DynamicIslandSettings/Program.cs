using System;
using System.Drawing;
using System.IO;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Windows.Forms;

namespace DynamicIslandSettings
{
    /// <summary>与 mod 端 IslandConfig 字段一一对应的配置模型（JSON 键名为 Java 小驼峰字段名）。</summary>
    public class IslandConfig
    {
        [JsonPropertyName("enabled")] public bool Enabled { get; set; } = true;
        [JsonPropertyName("position")] public int Position { get; set; }
        [JsonPropertyName("sensitivity")] public int Sensitivity { get; set; } = 2;
        [JsonPropertyName("scale")] public float Scale { get; set; } = 1.0f;
        [JsonPropertyName("potions")] public bool Potions { get; set; } = true;
        [JsonPropertyName("music")] public bool Music { get; set; } = true;
        [JsonPropertyName("weather")] public bool Weather { get; set; } = true;
        [JsonPropertyName("advancement")] public bool Advancement { get; set; } = true;
        [JsonPropertyName("health")] public bool Health { get; set; } = true;
        [JsonPropertyName("hunger")] public bool Hunger { get; set; } = true;
        [JsonPropertyName("daynight")] public bool Daynight { get; set; } = true;
        [JsonPropertyName("damage")] public bool Damage { get; set; } = true;
        [JsonPropertyName("levelUp")] public bool LevelUp { get; set; } = true;
        [JsonPropertyName("liquidbounce")] public bool Liquidbounce { get; set; } = true;
        [JsonPropertyName("netease")] public bool Netease { get; set; } = true;
        [JsonPropertyName("neteaseLyric")] public bool NeteaseLyric { get; set; } = true;
        [JsonPropertyName("statusFps")] public bool StatusFps { get; set; } = true;
        [JsonPropertyName("statusIp")] public bool StatusIp { get; set; } = true;
        [JsonPropertyName("statusLbVersion")] public bool StatusLbVersion { get; set; } = true;
        [JsonPropertyName("statusLyric")] public bool StatusLyric { get; set; } = true;
        [JsonPropertyName("statusScaffold")] public bool StatusScaffold { get; set; } = true;
        [JsonPropertyName("statusModules")] public bool StatusModules { get; set; } = true;
        [JsonPropertyName("statusKa")] public bool StatusKa { get; set; } = true;
        [JsonPropertyName("statusSpeed")] public bool StatusSpeed { get; set; } = true;
        [JsonPropertyName("statusArmor")] public bool StatusArmor { get; set; } = true;
        [JsonPropertyName("statusPing")] public bool StatusPing { get; set; } = true;
        [JsonPropertyName("jumpReset")] public bool JumpReset { get; set; }
        [JsonPropertyName("jumpResetThreshold")] public float JumpResetThreshold { get; set; } = 0.16f;
        [JsonPropertyName("rainbowEnabled")] public bool RainbowEnabled { get; set; } = true;
        [JsonPropertyName("rainbowSpeed")] public float RainbowSpeed { get; set; } = 72f;
        [JsonPropertyName("healthThreshold")] public int HealthThreshold { get; set; } = 5;
        [JsonPropertyName("hungerThreshold")] public int HungerThreshold { get; set; } = 3;
        [JsonPropertyName("displayTime")] public float DisplayTime { get; set; } = 3.5f;
        [JsonPropertyName("bgOpacity")] public int BgOpacity { get; set; } = 88;
        [JsonPropertyName("cornerRadius")] public int CornerRadius { get; set; } = 12;
    }

    internal static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm(ResolvePath(args)));
        }

        /// <summary>自动定位 Minecraft 的配置文件；找不到则退回官方启动器默认路径。</summary>
        private static string ResolvePath(string[] args)
        {
            if (args.Length > 0 && File.Exists(args[0]))
                return Path.GetFullPath(args[0]);

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

    public class MainForm : Form
    {
        private static readonly JsonSerializerOptions JsonOpts = new JsonSerializerOptions
        {
            WriteIndented = true,
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        };

        private string _path;
        private IslandConfig _cfg = new IslandConfig();
        private bool _dirty;
        private bool _loading;

        // 顶部工具条
        private Label _pathLabel;
        private Button _btnPick, _btnReload, _btnSave;

        // 控件引用（用于重载时刷新）
        private CheckBox _chkEnabled;
        private ComboBox _cmbPosition, _cmbScale, _cmbDisplayTime, _cmbSensitivity, _cmbJumpThreshold;
        private TrackBar _trkBg, _trkRainbowSpeed;
        private Label _lblBg, _lblRainbowSpeed;
        private NumericUpDown _numRadius, _numHealth, _numHunger;
        private CheckBox[] _chkCats = new CheckBox[12];
        private CheckBox[] _chkStatus = new CheckBox[10];
        private CheckBox _chkJumpReset;

        public MainForm(string path)
        {
            _path = path;
            Text = "Dynamic Island Settings (.NET)";
            Font = new Font("Microsoft YaHei UI", 9F);
            ClientSize = new Size(580, 720);
            MinimumSize = new Size(560, 600);
            StartPosition = FormStartPosition.CenterScreen;

            BuildToolbar();
            BuildBody();
            LoadConfig();
        }

        // ---------------------------------------------------------------- 顶部工具条
        private void BuildToolbar()
        {
            var bar = new Panel { Dock = DockStyle.Top, Height = 34, Padding = new Padding(6, 4, 6, 4) };
            _pathLabel = new Label
            {
                AutoSize = false,
                Anchor = AnchorStyles.Left | AnchorStyles.Top | AnchorStyles.Right,
                Location = new Point(8, 9),
                Size = new Size(300, 18),
                ForeColor = Color.DimGray,
                Text = _path,
            };
            _btnPick = new Button { Text = "选择…", Width = 70, Anchor = AnchorStyles.Top | AnchorStyles.Right, Location = new Point(ClientSize.Width - 306, 5) };
            _btnReload = new Button { Text = "重新加载", Width = 84, Anchor = AnchorStyles.Top | AnchorStyles.Right, Location = new Point(ClientSize.Width - 230, 5) };
            _btnSave = new Button { Text = "保存", Width = 70, Anchor = AnchorStyles.Top | AnchorStyles.Right, Location = new Point(ClientSize.Width - 140, 5), BackColor = Color.FromArgb(225, 240, 255) };

            _btnPick.Click += (_, _) => PickFile();
            _btnReload.Click += (_, _) => { LoadConfig(); _dirty = false; UpdateTitle(); };
            _btnSave.Click += (_, _) => SaveConfig();

            bar.Controls.AddRange(new Control[] { _pathLabel, _btnPick, _btnReload, _btnSave });
            Controls.Add(bar);
        }

        // ---------------------------------------------------------------- 主体
        private void BuildBody()
        {
            var scroll = new Panel { Dock = DockStyle.Fill, AutoScroll = true, Padding = new Padding(10) };
            int y = 4;

            y = BuildGroup(scroll, y, "基本");
            _chkEnabled = AddCheck(scroll, y, "启用灵动岛", () => _cfg.Enabled, v => _cfg.Enabled = v);
            y += 26;

            y = BuildGroup(scroll, y, "外观");
            _cmbPosition = AddCombo(scroll, y, "位置",
                new[] { "顶部居中", "左上角", "右上角" },
                () => _cfg.Position, v => _cfg.Position = v);
            y += 26;
            _cmbScale = AddCombo(scroll, y, "缩放比例",
                new[] { "0.75x", "1.00x", "1.25x", "1.50x", "1.75x" },
                () => ScaleIndex(_cfg.Scale), v => _cfg.Scale = new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }[v]);
            y += 26;
            _lblBg = new Label();
            _trkBg = AddTrack(scroll, y, "背景不透明度", 60, 100, 5, () => _cfg.BgOpacity, v => _cfg.BgOpacity = v, _lblBg);
            y += 44;
            _numRadius = AddNum(scroll, y, "圆角半径", 0, 40, () => _cfg.CornerRadius, v => _cfg.CornerRadius = v);
            y += 26;
            AddCheck(scroll, y, "流动彩虹色", () => _cfg.RainbowEnabled, v => _cfg.RainbowEnabled = v);
            y += 26;
            _lblRainbowSpeed = new Label();
            _trkRainbowSpeed = AddTrack(scroll, y, "彩虹流动速度 (°/s)", 0, 180, 12, () => (int)Math.Round(_cfg.RainbowSpeed), v => _cfg.RainbowSpeed = v, _lblRainbowSpeed);
            y += 44;

            y = BuildGroup(scroll, y, "事件行为");
            _cmbDisplayTime = AddCombo(scroll, y, "事件显示时长",
                new[] { "2.0s", "3.0s", "3.5s", "4.5s", "6.0s" },
                () => IndexOf(new[] { 2f, 3f, 3.5f, 4.5f, 6f }, _cfg.DisplayTime), v => _cfg.DisplayTime = new[] { 2f, 3f, 3.5f, 4.5f, 6f }[v]);
            y += 26;
            _cmbSensitivity = AddCombo(scroll, y, "事件灵敏度",
                new[] { "1", "2", "3", "4" },
                () => _cfg.Sensitivity - 1, v => _cfg.Sensitivity = v + 1);
            y += 26;
            _numHealth = AddNum(scroll, y, "低血量阈值（心）", 1, 20, () => _cfg.HealthThreshold, v => _cfg.HealthThreshold = v);
            y += 26;
            _numHunger = AddNum(scroll, y, "低饱食度阈值（鸡腿）", 1, 20, () => _cfg.HungerThreshold, v => _cfg.HungerThreshold = v);
            y += 26;

            string[] catNames =
            {
                "显示药水效果", "显示唱片音乐", "显示天气变化", "显示成就达成",
                "显示低血量告警", "显示低饱食度告警", "显示昼夜交替", "显示受伤告警",
                "显示升级告警", "显示 LiquidBounce 模块开关", "显示网易云音乐", "显示歌词",
            };
            Func<bool>[] catGetters =
            {
                () => _cfg.Potions, () => _cfg.Music, () => _cfg.Weather, () => _cfg.Advancement,
                () => _cfg.Health, () => _cfg.Hunger, () => _cfg.Daynight, () => _cfg.Damage,
                () => _cfg.LevelUp, () => _cfg.Liquidbounce, () => _cfg.Netease, () => _cfg.NeteaseLyric,
            };
            Action<bool>[] catSetters =
            {
                v => _cfg.Potions = v, v => _cfg.Music = v, v => _cfg.Weather = v, v => _cfg.Advancement = v,
                v => _cfg.Health = v, v => _cfg.Hunger = v, v => _cfg.Daynight = v, v => _cfg.Damage = v,
                v => _cfg.LevelUp = v, v => _cfg.Liquidbounce = v, v => _cfg.Netease = v, v => _cfg.NeteaseLyric = v,
            };
            y = BuildGroup(scroll, y, "事件类别");
            for (int i = 0; i < catNames.Length; i++)
            {
                _chkCats[i] = AddCheck(scroll, y, catNames[i], catGetters[i], catSetters[i]);
                y += 26;
            }

            string[] statusNames =
            {
                "一直显示帧率", "一直显示服务器 IP", "一直显示 LiquidBounce 版本", "一直显示歌词",
                "Scaffold 开启时显示 BPS 和方块进度", "显示模块激活仪表盘",
                "KillAura 开启时显示 APS 和目标", "显示实时速度计",
                "显示盔甲完整度 + 低耐久告警", "显示延迟（ping ms）",
            };
            Func<bool>[] statusGetters =
            {
                () => _cfg.StatusFps, () => _cfg.StatusIp, () => _cfg.StatusLbVersion, () => _cfg.StatusLyric,
                () => _cfg.StatusScaffold, () => _cfg.StatusModules, () => _cfg.StatusKa, () => _cfg.StatusSpeed,
                () => _cfg.StatusArmor, () => _cfg.StatusPing,
            };
            Action<bool>[] statusSetters =
            {
                v => _cfg.StatusFps = v, v => _cfg.StatusIp = v, v => _cfg.StatusLbVersion = v, v => _cfg.StatusLyric = v,
                v => _cfg.StatusScaffold = v, v => _cfg.StatusModules = v, v => _cfg.StatusKa = v, v => _cfg.StatusSpeed = v,
                v => _cfg.StatusArmor = v, v => _cfg.StatusPing = v,
            };
            y = BuildGroup(scroll, y, "状态栏（折叠态常驻信息）");
            for (int i = 0; i < statusNames.Length; i++)
            {
                _chkStatus[i] = AddCheck(scroll, y, statusNames[i], statusGetters[i], statusSetters[i]);
                y += 26;
            }

            y = BuildGroup(scroll, y, "跳跃重置");
            _chkJumpReset = AddCheck(scroll, y, "跳跃重置（受击落地自动跳）", () => _cfg.JumpReset, v => _cfg.JumpReset = v);
            y += 26;
            _cmbJumpThreshold = AddCombo(scroll, y, "击退判定阈值",
                new[] { "0.05", "0.10", "0.16", "0.25", "0.40" },
                () => IndexOf(new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }, _cfg.JumpResetThreshold),
                v => _cfg.JumpResetThreshold = new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }[v]);
            y += 30;

            Controls.Add(scroll);
        }

        // ---------------------------------------------------------------- 控件构建 helpers
        private int BuildGroup(Panel parent, int y, string title)
        {
            var box = new GroupBox
            {
                Text = title,
                Location = new Point(2, y),
                Width = parent.ClientSize.Width - 30,
                Height = 30,
            };
            parent.Controls.Add(box);
            return y + 26;
        }

        private CheckBox AddCheck(Panel parent, int y, string label, Func<bool> getter, Action<bool> setter)
        {
            var chk = new CheckBox { Text = label, AutoSize = true, Location = new Point(18, y + 3) };
            chk.Checked = getter();
            chk.CheckedChanged += (_, _) => { setter(chk.Checked); MarkDirty(); };
            parent.Controls.Add(chk);
            return chk;
        }

        private ComboBox AddCombo(Panel parent, int y, string label, string[] items, Func<int> getter, Action<int> setter)
        {
            var lbl = new Label { Text = label, AutoSize = true, Location = new Point(18, y + 4) };
            var cmb = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                Location = new Point(200, y),
                Width = 150,
            };
            cmb.Items.AddRange(items);
            cmb.SelectedIndex = Math.Max(0, getter());
            cmb.SelectedIndexChanged += (_, _) => { if (cmb.SelectedIndex >= 0) { setter(cmb.SelectedIndex); MarkDirty(); } };
            parent.Controls.Add(lbl);
            parent.Controls.Add(cmb);
            return cmb;
        }

        private TrackBar AddTrack(Panel parent, int y, string label, int min, int max, int step,
            Func<int> getter, Action<int> setter, Label valueLabel)
        {
            var lbl = new Label { Text = label, AutoSize = true, Location = new Point(18, y + 6) };
            valueLabel.Text = getter() + (label.Contains("°/s") ? "°/s" : "%");
            valueLabel.AutoSize = true;
            valueLabel.Location = new Point(370, y + 6);
            valueLabel.ForeColor = Color.DimGray;
            var trk = new TrackBar
            {
                Minimum = min,
                Maximum = max,
                SmallChange = step,
                LargeChange = step,
                TickFrequency = Math.Max(1, (max - min) / 5),
                Value = Math.Max(min, Math.Min(max, getter())),
                Location = new Point(190, y),
                Width = 170,
            };
            trk.ValueChanged += (_, _) =>
            {
                int v = (int)Math.Round(trk.Value / (double)step) * step;
                if (trk.Value != v) trk.Value = v;
                valueLabel.Text = v + (label.Contains("°/s") ? "°/s" : "%");
                setter(v);
                MarkDirty();
            };
            parent.Controls.Add(lbl);
            parent.Controls.Add(valueLabel);
            parent.Controls.Add(trk);
            return trk;
        }

        private NumericUpDown AddNum(Panel parent, int y, string label, int min, int max, Func<int> getter, Action<int> setter)
        {
            var lbl = new Label { Text = label, AutoSize = true, Location = new Point(18, y + 4) };
            var num = new NumericUpDown
            {
                Minimum = min,
                Maximum = max,
                Value = Math.Max(min, Math.Min(max, getter())),
                Location = new Point(200, y),
                Width = 150,
            };
            num.ValueChanged += (_, _) => { setter((int)num.Value); MarkDirty(); };
            parent.Controls.Add(lbl);
            parent.Controls.Add(num);
            return num;
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
                MessageBox.Show(this, "读取配置失败，已使用默认值。\n" + ex.Message, "Dynamic Island", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                _cfg = new IslandConfig();
            }
            RefreshUI();
        }

        private void RefreshUI()
        {
            _loading = true;
            _chkEnabled.Checked = _cfg.Enabled;
            _cmbPosition.SelectedIndex = Math.Max(0, Math.Min(2, _cfg.Position));
            _cmbScale.SelectedIndex = ScaleIndex(_cfg.Scale);
            _trkBg.Value = Math.Max(60, Math.Min(100, _cfg.BgOpacity));
            _numRadius.Value = Math.Max(0, Math.Min(40, _cfg.CornerRadius));
            _lblBg.Text = _cfg.BgOpacity + "%";
            _trkRainbowSpeed.Value = Math.Max(0, Math.Min(180, (int)Math.Round(_cfg.RainbowSpeed)));
            _lblRainbowSpeed.Text = _cfg.RainbowSpeed + "°/s";
            _cmbDisplayTime.SelectedIndex = IndexOf(new[] { 2f, 3f, 3.5f, 4.5f, 6f }, _cfg.DisplayTime);
            _cmbSensitivity.SelectedIndex = Math.Max(0, _cfg.Sensitivity - 1);
            _numHealth.Value = Math.Max(1, Math.Min(20, _cfg.HealthThreshold));
            _numHunger.Value = Math.Max(1, Math.Min(20, _cfg.HungerThreshold));
            bool[] cats = { _cfg.Potions, _cfg.Music, _cfg.Weather, _cfg.Advancement, _cfg.Health, _cfg.Hunger, _cfg.Daynight, _cfg.Damage, _cfg.LevelUp, _cfg.Liquidbounce, _cfg.Netease, _cfg.NeteaseLyric };
            for (int i = 0; i < cats.Length; i++) _chkCats[i].Checked = cats[i];
            bool[] stats = { _cfg.StatusFps, _cfg.StatusIp, _cfg.StatusLbVersion, _cfg.StatusLyric, _cfg.StatusScaffold, _cfg.StatusModules, _cfg.StatusKa, _cfg.StatusSpeed, _cfg.StatusArmor, _cfg.StatusPing };
            for (int i = 0; i < stats.Length; i++) _chkStatus[i].Checked = stats[i];
            _chkJumpReset.Checked = _cfg.JumpReset;
            _cmbJumpThreshold.SelectedIndex = IndexOf(new[] { 0.05f, 0.10f, 0.16f, 0.25f, 0.40f }, _cfg.JumpResetThreshold);
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
                MessageBox.Show(this, "已保存到：\n" + _path, "Dynamic Island", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "保存失败：\n" + ex.Message, "Dynamic Island", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void PickFile()
        {
            using var dlg = new OpenFileDialog
            {
                Title = "选择 dynamicisland.json",
                Filter = "JSON 文件 (*.json)|*.json|所有文件 (*.*)|*.*",
                FileName = _path,
            };
            if (dlg.ShowDialog(this) == DialogResult.OK)
            {
                _path = dlg.FileName;
                _pathLabel.Text = _path;
                LoadConfig();
            }
        }

        private void MarkDirty()
        {
            if (_loading) return;
            _dirty = true;
            UpdateTitle();
        }

        private void UpdateTitle()
        {
            Text = "Dynamic Island Settings (.NET)" + (_dirty ? " *" : "");
        }

        private static int ScaleIndex(float scale)
        {
            return IndexOf(new[] { 0.75f, 1f, 1.25f, 1.5f, 1.75f }, scale);
        }

        private static int IndexOf(float[] arr, float value)
        {
            for (int i = 0; i < arr.Length; i++)
                if (Math.Abs(arr[i] - value) < 0.001f)
                    return i;
            return 0;
        }
    }
}

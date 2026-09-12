using System.Text.Json.Serialization;

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
}

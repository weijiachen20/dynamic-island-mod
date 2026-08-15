🎮 项目概述
Dynamic Island Mod 是一款为 Minecraft 26.1.2 (Fabric) 打造的客户端 HUD 模组，灵感源自苹果 iPhone 的灵动岛（Dynamic Island）设计。它在屏幕顶部提供一个药丸形覆盖层，以动画化、自适应的方式实时展示游戏内状态与事件，让 HUD 显示更现代、更沉浸。

GitHub 仓库： https://github.com/weijiachen20/dynamic-island-mod

✨ 核心功能
🏝️ 灵动岛基础形态
折叠态（常驻）：显示 FPS、服务器 IP、LiquidBounce 版本、延迟（Ping）、歌词等核心信息
展开态：检测到游戏事件时，灵动岛平滑展开并显示详细内容
拆分态：多个事件同时触发时可拆分为左右两部分
🎯 游戏事件检测与显示
🧪 药水效果：新获得或药水到期时显示效果图标与剩余时间
🎵 唱片机音乐：进入有唱片机加载音乐的区域时提示播放内容
🌦️ 天气变化：下雨、下雪、雷暴、晴天切换时提醒
🏆 成就/进度：解锁进度或获得成就时弹出祝贺动画
❤️ 生命值警报：血量低于阈值（默认 5 点）时紧急提示
🛡️ 盔甲耐久警报：盔甲装备耐久低于 20% 时红色预警
🎵 网易云音乐客户端集成
通过 Windows API 读取网易云音乐窗口标题
异步虚拟线程轮询，不阻塞游戏主线程
双行歌词显示：第二行优先显示即将唱到的歌词，过长自动截断加省略号
💥 LiquidBounce Nextgen 深度集成
支持版本：6c1bbea

功能	说明
模块激活仪表盘	实时显示已开启模块列表，带图标前缀与彩色高亮
KillAura 面板	显示目标、距离、CPS、攻击冷却等核心数据
速度计 (Speedometer)	实时显示 BPS（方块/秒）移动速度，带渐变配色
盔甲完整度	百分比显示四件盔甲的耐久合计状态
Scaffold 常驻面板	开启 Scaffold 时显示放置 BPS、方块消耗进度条、20tick 波形条
手持物品旋转	按下 R 键（可重绑定）后，手持物品绕 Y 轴以 1.5 圈/秒 旋转，视觉效果酷炫
延迟 (Ping) 显示	单行尾部显示延迟，分四档配色：🟢绿<50ms / 🟡黄<150ms / 🟠金<300ms / 🔴红≥300ms
🎨 OPAI 风格外观
采用 OPAI（现代化 HUD）视觉规范设计：


Plain Text

┌─────────────────────────────────────────────────────┐
│  ◎◎◎ 外霓虹发光（紫→青→白三层光晕）                    │
│  ┌─────────────────────────────────────────────┐    │
│  │  纯白色 1px 内描边（药丸标志性轮廓）            │    │
│  │  ┌─────────────────────────────────────────┐  │    │
│  │  │  纯深黑底色 + 底部 5% 提亮渐变            │  │    │
│  │  │  顶部镜面高光（亚克力质感）                 │  │    │
│  │  │  FPS · IP · LB版本 · 歌词 · Ping · 延迟   │  │    │
│  │  └─────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
跑道形圆角（两端半圆）
三层霓虹发光环（紫 0xA855FF → 青 0x32DCDC → 白 0xE8F0FF）
纯深黑主体（0x05070B → 0x0B0F18 渐变）
1px 白色内描边 + 顶部镜面高光条
EasedValue 缓动系统：尺寸/位置全部平滑过渡，无硬切换
🔧 技术实现
版本与依赖
组件	版本
Minecraft	26.1.2
Fabric Loader	0.19.3
Fabric Loom	1.17-SNAPSHOT
Fabric API	0.155.2+26.1.2
Gradle	9.0.0
映射	Mojang 官方映射（26.1 未混淆）
关键架构
HudElementRegistry：替代已弃用的 HudRenderCallback，在聊天框前渲染灵动岛
GuiGraphicsExtractor：MC 26.1 的图形上下文
IslandState + EasedValue：状态机 + 缓动值，管理折叠/展开/拆分三种形态的平滑过渡
EventDetector：每 tick 扫描药水、天气、成就、血量、盔甲等事件
StatusInfo：常驻状态信息聚合（FPS、IP、LB版本、歌词、Ping、盔甲、Scaffold），0.1 秒刷新
ScaffoldTracker：滚动窗口计算 BPS + 20tick 波形条数据
LiquidBounceCompat：反射访问 LB 模块与字段，失败静默降级
ItemInHandRendererMixin：@At(value = "INVOKE", target = "...renderItem...") 注入 Y 轴旋转
虚拟线程：网易云窗口轮询、歌词获取走异步虚拟线程，UI 零卡顿
配置系统
配置文件：config/dynamicisland.json


JSON

{
  "statusLyrics": true,          // 歌词显示开关
  "statusScaffold": true,        // Scaffold 面板开关
  "statusPing": true,            // 延迟显示开关
  "statusDashboard": true,       // 模块仪表盘开关
  "statusKillAura": true,        // KillAura 面板开关
  "statusSpeedometer": true,     // 速度计开关
  "statusArmor": true,           // 盔甲完整度开关
  "lowHealthThreshold": 5.0      // 低血量报警阈值
}
配置界面通过 ModMenu 集成（可选，由 gradle.properties 控制），也可在游戏中访问设置界面逐项开关。

📁 关键文件结构

Plain Text

src/main/java/com/example/dynamicisland/
├── DynamicIslandMod.java           # 模组主类（MOD_ID、初始化）
├── client/
│   ├── DynamicIslandClient.java    # 客户端入口：HUD注册、tick事件、R键绑定
│   ├── DynamicIslandHud.java       # 核心渲染：OPAI药丸、动画、波形条、进度条
│   ├── StatusInfo.java             # 常驻信息：FPS/IP/LB/歌词/Ping/盔甲
│   ├── IslandState.java            # 状态机 + 几何尺寸计算
│   ├── EventDetector.java          # 事件检测：药水/天气/成就/血量/盔甲
│   ├── ScaffoldTracker.java        # Scaffold BPS + 波形条 + 方块计数
│   ├── LiquidBounceCompat.java     # LiquidBounce 反射兼容层
│   ├── SpinToggler.java            # R 键旋转开关状态
│   └── config/                     # 配置 + 配置GUI
└── mixin/
    └── ItemInHandRendererMixin.java  # 手持物品 Y 轴旋转 Mixin

src/main/resources/
├── fabric.mod.json                  # Fabric 模组元数据
├── dynamicisland.mixins.json        # Mixin 配置
└── assets/dynamicisland/lang/
    ├── zh_cn.json                   # 中文翻译
    └── en_us.json                   # 英文翻译
🎮 控制与使用
操作	说明
R 键	切换手持物品旋转（开关），可在「控制 → Dynamic Island」中重绑定
ModMenu	打开模组列表 → Dynamic Island → 配置 进入配置界面，逐项开关各项功能
常驻刷新：状态信息每0.1秒刷新一次；Ping值1秒缓存一次
灵动岛尺寸会根据显示内容自适应：

单行：高度16px
+歌词：28px 高
+Scaffold：46px 高
+歌词 + Scaffold：58px 高
宽度限制：200px ~ 400px
🚀 编译与构建

Bash

# Windows PowerShell
cd <项目目录>
./gradlew build
构建产物：

build/libs/dynamic-island-1.0.0.jar — 安装到 mods/ 目录即可使用
build/libs/dynamic-island-1.0.0-sources.jar — 源码包（开发调试用，不是运行必需）
📝 开发历程亮点
Mojang 映射迁移：因 MC 26.1 无 Yarn 映射，全量切换到 Mojang 官方名称
API 升级：HudRenderCallback → HudElementRegistry.attachElementBefore，适配 26.1 新渲染管线
反射兼容：LB 模块、Scaffold 字段、Inventory.selected 私有字段均通过反射访问，失败时静默降级不崩溃
性能优化：歌词轮询用虚拟线程，反射结果全量缓存，Ping 1 秒内复用
视觉迭代：毛玻璃 → OPAI 风格，新增霓虹发光环、纯白描边、纯黑底渐变、顶部高光
GitHub 发布：全流程 Git 初始化 → 提交 → 创建仓库 → 推送，仓库地址见顶部
🛡️ 稳定性保障
所有外部模组交互（LiquidBounce、网易云）均有 try/catch 降级
Scaffold 反射失败只静默关闭面板，不影响其他功能
Ping 读取异常回退为 -1（不显示），不抛异常
进度条宽度、岛高度、岛宽度全部硬限制在合理区间，防止布局崩坏
欢迎使用 Dynamic Island Mod！🎉

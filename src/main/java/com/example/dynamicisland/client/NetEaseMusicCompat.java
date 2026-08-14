package com.example.dynamicisland.client;

import com.example.dynamicisland.DynamicIslandMod;
import com.example.dynamicisland.client.config.IslandConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 网易云音乐客户端兼容层（含歌词）。
 *
 * <p>通过读取网易云音乐 Windows 客户端的主窗口标题获取当前播放的歌曲信息，
 * 同时通过 Win32 API {@code FindWindow} + {@code GetWindowText} 读取桌面歌词
 * 窗口（类名 {@code OrpheusLyricWindow}）获取实时歌词文本。
 *
 * <p>使用长时间运行的 PowerShell 进程（内部 P/Invoke user32.dll），每 600ms
 * 输出一行 {@code TITLE|||LYRIC}。Java 端在虚拟线程上持续读取 stdout，
 * 通过 volatile 字段将最新结果传递给主线程。
 *
 * <p>网易云音乐未安装、未运行或桌面歌词未开启时，该兼容层自动静默，
 * 不会影响游戏。进程意外退出时会自动重启。
 */
public final class NetEaseMusicCompat {

    /** 进程名，覆盖不同版本的客户端。 */
    private static final String PROCESS_NAME = "CloudMusic";

    /** PowerShell 轮询脚本（循环输出 TITLE|||LYRIC）。 */
    private static final String PS_SCRIPT =
            "$ErrorActionPreference = 'SilentlyContinue'\n" +
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
            "Add-Type @\"\n" +
            "using System;\n" +
            "using System.Runtime.InteropServices;\n" +
            "using System.Text;\n" +
            "public class U32 {\n" +
            "    [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)]\n" +
            "    public static extern IntPtr FindWindow(string c, string t);\n" +
            "    [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)]\n" +
            "    public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);\n" +
            "    [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)]\n" +
            "    public static extern int GetWindowTextLength(IntPtr h);\n" +
            "}\n" +
            "\"@\n" +
            "while ($true) {\n" +
            "    $title = ''\n" +
            "    try {\n" +
            "        $p = Get-Process '" + PROCESS_NAME + "' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -ne '' } | Select-Object -First 1\n" +
            "        if ($p) { $title = $p.MainWindowTitle }\n" +
            "    } catch {}\n" +
            "    $lyric = ''\n" +
            "    try {\n" +
            "        $h = [U32]::FindWindow('OrpheusLyricWindow', $null)\n" +
            "        if ($h -ne [IntPtr]::Zero) {\n" +
            "            $len = [U32]::GetWindowTextLength($h)\n" +
            "            if ($len -gt 0) {\n" +
            "                $sb = New-Object System.Text.StringBuilder($len + 1)\n" +
            "                [U32]::GetWindowText($h, $sb, $sb.Capacity) | Out-Null\n" +
            "                $lyric = $sb.ToString()\n" +
            "            }\n" +
            "        }\n" +
            "    } catch {}\n" +
            "    [Console]::WriteLine($title + '|||' + $lyric)\n" +
            "    [Console]::Out.Flush()\n" +
            "    Start-Sleep -Milliseconds 600\n" +
            "}\n";

    // ---- 后台进程状态 ----
    private volatile Process process;
    private volatile boolean running = false;
    private volatile boolean started = false;

    // ---- 最新数据（后台线程写，主线程读） ----
    private volatile String latestTitle = "";
    private volatile String latestLyric = "";
    private volatile boolean clientRunning = false;

    /** 当前已推送事件的歌曲名，用于变化检测。 */
    private String currentSong = "";

    /**
     * 启动后台 PowerShell 轮询进程。若已在运行则跳过。
     * 在第一次 tick 时惰性调用。
     */
    private synchronized void startPolling() {
        if (started) return;
        started = true;
        running = true;
        Thread.startVirtualThread(this::pollingLoop);
        DynamicIslandMod.LOGGER.info("Dynamic Island: 网易云音乐歌词轮询进程已启动。");
    }

    /** 停止后台进程，释放资源。在 mod 卸载或配置关闭时调用。 */
    public synchronized void stop() {
        running = false;
        started = false;
        Process p = process;
        if (p != null) p.destroyForcibly();
    }

    /** 后台轮询循环：启动 PowerShell 进程，读取输出，崩溃后自动重启。 */
    private void pollingLoop() {
        while (running) {
            try {
                String encoded = Base64.getEncoder().encodeToString(
                        PS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell", "-NoProfile", "-NonInteractive",
                        "-EncodedCommand", encoded);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                process = p;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        parseOutput(line);
                    }
                }
            } catch (Exception e) {
                DynamicIslandMod.LOGGER.debug(
                        "Dynamic Island: 网易云音乐轮询进程异常: {}", e.getMessage());
            }

            // 进程结束，等待 2 秒后重启
            if (running) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    /** 解析 PowerShell 输出的一行：TITLE|||LYRIC。 */
    private void parseOutput(String line) {
        int sep = line.indexOf("|||");
        if (sep < 0) return;
        latestTitle = line.substring(0, sep).trim();
        latestLyric = line.substring(sep + 3).trim();
        clientRunning = !latestTitle.isEmpty();
    }

    /**
     * 每个 client tick 调用。首次调用时惰性启动后台进程，
     * 然后基于最新读取的窗口标题和歌词推送灵动岛事件。
     *
     * @param state 灵动岛状态
     */
    public void tick(IslandState state) {
        // 惰性启动后台进程
        if (!started) {
            startPolling();
        }

        String title = latestTitle;
        String lyric = latestLyric;

        String song = parseSongFromTitle(title);

        if (song.isEmpty()) {
            // 无歌曲播放，收回事件
            if (!currentSong.isEmpty()) {
                state.retract("netease:music");
                currentSong = "";
            }
            return;
        }

        // 构建副标题：优先显示歌词，无歌词时显示"网易云音乐"
        Component subtitle;
        IslandConfig cfg = IslandConfig.get();
        String lyricLine = cleanLyric(lyric);

        if (cfg.neteaseLyric && !lyricLine.isEmpty()) {
            subtitle = Component.literal(lyricLine).withStyle(ChatFormatting.LIGHT_PURPLE);
        } else {
            subtitle = Component.translatable("dynamicisland.event.netease.playing")
                    .withStyle(ChatFormatting.DARK_AQUA);
        }

        // 有歌曲播放，推送/刷新事件（persistent = true，每 tick 刷新保持显示）
        state.offer(new IslandEvent(
                IslandEvent.Type.NETEASE_MUSIC,
                IslandEvent.Priority.INFO,
                new ItemStack(Items.MUSIC_DISC_CHIRP),
                Component.literal(song).withStyle(ChatFormatting.AQUA),
                subtitle,
                (int) (IslandState.displaySeconds() * 20),
                "netease:music", true));
        currentSong = song;
    }

    /** 客户端是否正在运行。 */
    public boolean isClientRunning() {
        return clientRunning;
    }

    /** 获取当前歌词行（已清理，取第一行）。无歌词时返回空字符串。 */
    public String getCurrentLyric() {
        return cleanLyric(latestLyric);
    }

    /**
     * 从窗口标题解析歌曲名。
     *
     * <p>窗口标题格式：
     * <ul>
     *   <li>"网易云音乐" — 空闲，无歌曲播放</li>
     *   <li>"歌曲名 - 歌手名 - 网易云音乐" — 播放中</li>
     * </ul>
     */
    private String parseSongFromTitle(String title) {
        if (title == null || title.isBlank()) return "";
        String cleaned = title.replace(" - 网易云音乐", "").replace("网易云音乐", "").trim();
        return cleaned;
    }

    /**
     * 清理歌词文本：取第二行（下一行），去除空白字符。
     * 网易云音乐桌面歌词窗口返回多行文本（当前行 + 下一行），
     * 取第二行作为即将唱的歌词；若只有一行则取该行。
     */
    private String cleanLyric(String lyric) {
        if (lyric == null || lyric.isBlank()) return "";
        String[] lines = lyric.split("\\r?\\n");
        int found = 0;
        String firstLine = "";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            found++;
            if (found == 1) firstLine = trimmed;
            if (found == 2) return trimmed;
        }
        // 不足两行时退回第一行
        return firstLine;
    }
}

@echo off
rem 在 Windows 上构建 WinUI 3 版设置器（Linux 无法编译 WinUI 3）。
rem 需要：.NET SDK 8（https://dotnet.microsoft.com/download）
set DOTNET_CLI_TELEMETRY_OPTOUT=1
cd /d %~dp0
dotnet publish -c Release -r win-x64 --self-contained -p:WindowsAppSDKSelfContained=true -o out
echo.
echo Done. Output: out\DynamicIslandSettings.exe
pause

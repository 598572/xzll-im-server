@echo off
REM 交互式测试客户端启动脚本 (Windows版)
REM Author: hzz
REM Date: 2025-10-29

chcp 65001 > nul
cls

echo.
echo ╔════════════════════════════════════════════════════╗
echo ║     交互式 IM 测试客户端 - 快速启动脚本           ║
echo ╚════════════════════════════════════════════════════╝
echo.

REM 检查是否在正确的目录
if not exist "pom.xml" (
    echo ❌ 错误: 请在 im-client 目录下运行此脚本
    pause
    exit /b 1
)

REM 检查 Java 环境
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 未找到 Java 环境，请先安装 JDK
    pause
    exit /b 1
)

REM 显示 Java 版本
echo ✅ Java 环境:
java -version 2>&1 | findstr /R "version"
echo.

REM 编译项目（如果需要）
if "%1"=="--rebuild" (
    echo 🔨 正在编译项目...
    call mvn clean compile -DskipTests
    
    if errorlevel 1 (
        echo ❌ 编译失败，请检查错误信息
        pause
        exit /b 1
    )
    echo ✅ 编译成功
    echo.
)

if not exist "target\classes" (
    echo 🔨 正在编译项目...
    call mvn clean compile -DskipTests
    
    if errorlevel 1 (
        echo ❌ 编译失败，请检查错误信息
        pause
        exit /b 1
    )
    echo ✅ 编译成功
    echo.
)

REM 运行客户端
echo 🚀 正在启动交互式测试客户端...
echo.

call mvn exec:java -Dexec.mainClass="com.xzll.client.protobuf.interactive.InteractiveTestClient"

REM 退出时的提示
echo.
echo 👋 测试客户端已退出
pause


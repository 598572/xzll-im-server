package com.xzll.client.protobuf.interactive;

import com.xzll.common.constant.ImConstant;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群聊交互式测试客户端 - 用于灵活测试群聊消息发送
 *
 * 功能特性：
 * 1. 启动时输入发送方用户ID
 * 2. 可以加入多个群组进行测试
 * 3. 支持群聊消息发送
 * 4. 实时显示收到的群聊消息
 * 5. 支持多群快速切换
 */
public class GroupChatTestClient {

//    public static final String IP = "127.0.0.1";
//    public static final String PORT = "10001";

    public static final String IP = "47.93.209.60";
    public static final String PORT = "8090";

    private static String currentUserId;
    private static Long currentGroupId;
    private static Channel channel;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        // ====================================================================
        // 步骤 1: 输入当前用户ID（发送方）
        // ====================================================================
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║        群聊交互式 IM 测试客户端 - 启动配置         ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.print("请输入当前用户ID（发送方，例如: 123729024000）: ");
        currentUserId = scanner.nextLine().trim();

        if (currentUserId.isEmpty()) {
            currentUserId = "123729024000";
        }

        System.out.println("✅ 当前用户: " + currentUserId);
        System.out.println();

        // ====================================================================
        // 步骤 2: 输入默认群ID
        // ====================================================================
        System.out.print("请输入默认群ID（例如: 1000000000000000001）: ");
        String groupIdStr = scanner.nextLine().trim();

        if (groupIdStr.isEmpty()) {
            currentGroupId = 1000000000000000001L;
        } else {
            currentGroupId = Long.parseLong(groupIdStr);
        }

        System.out.println("✅ 默认群ID: " + currentGroupId);
        System.out.println();

        // ====================================================================
        // 步骤 3: 连接WebSocket服务器
        // ====================================================================
        System.out.println("正在连接服务器...");

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // WebSocket URI (需要带上 userId 参数)
            URI uri = new URI("ws://" + IP + ":" + PORT + "/websocket?userId=" + currentUserId);

            // 设置 HTTP Headers
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.set("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJoeHkxMTIyMzMiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxMjQ5NDg1NjcwNDAsImV4cCI6MTc2NDY2MzExNywiYXV0aG9yaXRpZXMiOlsiQURNSU4iXSwianRpIjoiN2UxNjQwNTYtMWI1Yy00MDEyLWJjOGEtMmM4OWI2YWI4NGQ1IiwiY2xpZW50X2lkIjoiY2xpZW50LWFwcCJ9.ARqj38fm0oxk1K47uHLBCoQzuqk6JzNMHTCoffZfHyr1PelbrnE-uXnJ3A1TrXA9K7uQ0XCkfefwhnhoqDu4xV4crxNPycSbIxnY7pud_agATAqQXR-UIlZr9V1KNUZs7sZYumZu8l-rcr-sDjob65WNxavc1vsC20CNPTYHqFjiiexonidgyhbH0BPPq8fV9AdocIojeTMq0g0kWtNjFG-ACg2CvAEt3ArqtKWPgEqWYRqJWOWRJ_qhyJ5YPuHfm4-T4weEA-W6H5siDCZHZKOgeBqPlLREkaC9s09eX40Hlnzehji1yohfaY_FObm7dBzcU5ODvUTKfdbMR2Tojg");
            headers.set("uid", currentUserId);

            // 创建 WebSocket 握手器
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri,
                WebSocketVersion.V13,
                null,
                true,  // allowExtensions
                headers,
                100 * 1024 * 1024  // maxFramePayloadLength
            );

            // 创建处理器
            GroupChatClientHandler handler = new GroupChatClientHandler(handshaker, currentUserId);

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpClientCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast("heart-notice", new IdleStateHandler(10, 0, 0, TimeUnit.SECONDS));
                            pipeline.addLast(handler);
                        }
                    });

            // 连接服务器
            channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();

            // 设置用户ID属性
            channel.attr(ImConstant.USER_ID_KEY).set(currentUserId);

            // 等待握手完成
            handler.handshakeFuture().sync();

            System.out.println("✅ 连接成功！");
            System.out.println();

            // ====================================================================
            // 步骤 4: 显示帮助信息
            // ====================================================================
            printHelp();

            // ====================================================================
            // 步骤 5: 进入命令循环
            // ====================================================================
            commandLoop(scanner, handler);

        } finally {
            group.shutdownGracefully();
            scanner.close();
        }
    }

    /**
     * 命令循环 - 处理用户输入的命令
     */
    private static void commandLoop(Scanner scanner, GroupChatClientHandler handler) {
        while (true) {
            try {
                System.out.print("\n[群:" + currentGroupId + "] > ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                // 解析命令
                String[] parts = input.split("\\s+", 2);
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "send":
                    case "s":
                        handleSendCommand(scanner, handler);
                        break;

                    case "group":
                    case "g":
                        if (parts.length < 2) {
                            System.out.println("❌ 格式错误！用法: group <群ID> 或 g <群ID>");
                            break;
                        }
                        try {
                            currentGroupId = Long.parseLong(parts[1]);
                            System.out.println("✅ 已切换到群: " + currentGroupId);
                        } catch (NumberFormatException e) {
                            System.out.println("❌ 群ID格式错误！");
                        }
                        break;

                    case "quick":
                    case "q":
                        handleQuickSendCommand(scanner, handler);
                        break;

                    case "multi":
                    case "m":
                        if (parts.length < 2) {
                            System.out.println("❌ 格式错误！用法: multi <群ID> 或 m <群ID>");
                            break;
                        }
                        try {
                            Long targetGroupId = Long.parseLong(parts[1]);
                            handleMultiSendCommand(scanner, handler, targetGroupId);
                        } catch (NumberFormatException e) {
                            System.out.println("❌ 群ID格式错误！");
                        }
                        break;

                    case "help":
                    case "h":
                        printHelp();
                        break;

                    case "status":
                        printStatus(handler);
                        break;

                    case "clear":
                    case "cls":
                        clearScreen();
                        break;

                    case "batch":
                    case "b":
                        if (parts.length < 2) {
                            System.out.println("❌ 格式错误！用法: batch <数量> 或 b <数量>");
                            break;
                        }
                        try {
                            int count = Integer.parseInt(parts[1]);
                            handleBatchSend(handler, count);
                        } catch (NumberFormatException e) {
                            System.out.println("❌ 数量格式错误！");
                        }
                        break;

                    case "exit":
                    case "quit":
                        System.out.println("👋 再见！");
                        channel.close().sync();
                        System.exit(0);
                        break;

                    default:
                        // 默认当作消息内容发送到当前群
                        handler.sendGroupMessage(currentGroupId, input);
                        System.out.println("  ✓ 已发送到群 " + currentGroupId);
                }

            } catch (Exception e) {
                System.err.println("❌ 命令执行失败: " + e.getMessage());
            }
        }
    }

    /**
     * 处理发送消息命令（完整模式）
     */
    private static void handleSendCommand(Scanner scanner, GroupChatClientHandler handler) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│      发送群聊消息（完整模式）       │");
        System.out.println("└─────────────────────────────────────┘");

        // 输入群ID（可选，默认使用当前群）
        System.out.print("群ID (直接回车使用当前群 " + currentGroupId + "): ");
        String groupIdStr = scanner.nextLine().trim();

        Long groupId;
        if (groupIdStr.isEmpty()) {
            groupId = currentGroupId;
        } else {
            groupId = Long.parseLong(groupIdStr);
        }

        // 输入消息内容
        System.out.print("消息内容: ");
        String content = scanner.nextLine().trim();

        if (content.isEmpty()) {
            System.out.println("❌ 消息内容不能为空！");
            return;
        }

        // 发送消息
        handler.sendGroupMessage(groupId, content);

        System.out.println("✅ 消息已发送");
        System.out.println("   发送方: " + currentUserId);
        System.out.println("   群ID: " + groupId);
        System.out.println("   内容: " + content);
    }

    /**
     * 处理快速发送命令（连续发送到当前群）
     */
    private static void handleQuickSendCommand(Scanner scanner, GroupChatClientHandler handler) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│      快速发送模式（连续发送）       │");
        System.out.println("│      当前群: " + currentGroupId + "                   │");
        System.out.println("│      输入 'back' 返回主菜单         │");
        System.out.println("└─────────────────────────────────────┘");
        System.out.println();

        while (true) {
            System.out.print("[群 " + currentGroupId + "] > ");
            String content = scanner.nextLine().trim();

            if (content.isEmpty()) {
                continue;
            }

            if (content.equalsIgnoreCase("back") || content.equalsIgnoreCase("exit")) {
                System.out.println("✅ 退出快速发送模式");
                break;
            }

            // 发送消息
            handler.sendGroupMessage(currentGroupId, content);

            System.out.println("  ✓ 已发送");
        }
    }

    /**
     * 处理多群发送命令（指定群ID快速发送）
     */
    private static void handleMultiSendCommand(Scanner scanner, GroupChatClientHandler handler, Long targetGroupId) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│      多群快速发送模式                │");
        System.out.println("│      目标群: " + targetGroupId + "                      │");
        System.out.println("│      输入 'back' 返回主菜单         │");
        System.out.println("└─────────────────────────────────────┘");
        System.out.println();

        while (true) {
            System.out.print("[群 " + targetGroupId + "] > ");
            String content = scanner.nextLine().trim();

            if (content.isEmpty()) {
                continue;
            }

            if (content.equalsIgnoreCase("back") || content.equalsIgnoreCase("exit")) {
                System.out.println("✅ 退出多群发送模式");
                break;
            }

            // 发送消息
            handler.sendGroupMessage(targetGroupId, content);

            System.out.println("  ✓ 已发送到群 " + targetGroupId);
        }
    }

    /**
     * 处理批量发送命令
     */
    private static void handleBatchSend(GroupChatClientHandler handler, int count) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│      批量发送测试                    │");
        System.out.println("│      群ID: " + currentGroupId + "                       │");
        System.out.println("│      数量: " + count + " 条                       │");
        System.out.println("└─────────────────────────────────────┘");
        System.out.println();

        for (int i = 1; i <= count; i++) {
            String content = "批量测试消息 #" + i + " - " + System.currentTimeMillis();
            handler.sendGroupMessage(currentGroupId, content);

            System.out.println("  [" + i + "/" + count + "] 已发送: " + content);

            // 避免发送过快
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("✅ 批量发送完成，共 " + count + " 条");
    }

    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║                   群聊命令列表                     ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║  send / s                  - 发送群聊消息（完整）   ║");
        System.out.println("║  group <群ID> / g <群ID>    - 切换默认群            ║");
        System.out.println("║  quick / q                  - 快速发送到当前群      ║");
        System.out.println("║  multi <群ID> / m <群ID>    - 快速发送到指定群      ║");
        System.out.println("║  batch <数量> / b <数量>    - 批量发送测试          ║");
        System.out.println("║  status                    - 查看连接状态          ║");
        System.out.println("║  help / h                  - 显示帮助信息          ║");
        System.out.println("║  clear / cls               - 清屏                  ║");
        System.out.println("║  exit / quit               - 退出程序              ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║  💡 直接输入内容将发送到当前群                  ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("💡 使用示例:");
        System.out.println("  1. 完整模式: 输入 'send'，按提示输入群ID和内容");
        System.out.println("  2. 切换群: 输入 'group 1000000000000000002'");
        System.out.println("  3. 快速模式: 输入 'quick'，然后连续发送消息");
        System.out.println("  4. 多群模式: 输入 'multi 1000000000000000002'");
        System.out.println("  5. 批量测试: 输入 'batch 10' 发送10条测试消息");
        System.out.println("  6. 快捷发送: 直接输入内容，发送到当前群");
        System.out.println();
    }

    /**
     * 打印状态信息
     */
    private static void printStatus(GroupChatClientHandler handler) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│            当前连接状态             │");
        System.out.println("├─────────────────────────────────────┤");
        System.out.println("│  当前用户: " + currentUserId);
        System.out.println("│  当前群: " + currentGroupId);
        System.out.println("│  连接状态: " + (channel.isActive() ? "✅ 已连接" : "❌ 已断开"));
        System.out.println("│  已发送: " + handler.getSentCount() + " 条");
        System.out.println("│  已接收: " + handler.getReceivedCount() + " 条");
        System.out.println("└─────────────────────────────────────┘");
    }

    /**
     * 清屏
     */
    private static void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // 清屏失败，打印多行空行
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }
}

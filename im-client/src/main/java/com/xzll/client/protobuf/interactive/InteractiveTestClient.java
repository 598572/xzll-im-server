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
import io.netty.util.AttributeKey;

import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hzz
 * @Date: 2025/10/29
 * @Description: 交互式测试客户端 - 用于灵活测试消息发送
 * 
 * 功能特性：
 * 1. 启动时输入发送方用户ID
 * 2. 发送消息时可以随时指定接收方
 * 3. 支持多种命令操作
 * 4. 实时显示收到的消息
 */
public class InteractiveTestClient {

//    public static final String IP = "127.0.0.1";
//    public static final String PORT = "10001";


//    public static final String IP = "www.okim.site";
    public static final String IP = "120.46.85.43";
    public static final String PORT = "80";


    private static String currentUserId;
    private static Channel channel;
    
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        // ====================================================================
        // 步骤 1: 输入当前用户ID（发送方）
        // ====================================================================
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║     交互式 IM 测试客户端 - 启动配置               ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.print("请输入当前用户ID（发送方，例如: user1）: ");
        currentUserId = scanner.nextLine().trim();
        
        if (currentUserId.isEmpty()) {
            System.err.println("❌ 用户ID不能为空！");
            return;
        }
        
        System.out.println("✅ 当前用户: " + currentUserId);
        System.out.println();
        
        // ====================================================================
        // 步骤 2: 连接WebSocket服务器
        // ====================================================================
        System.out.println("正在连接服务器...");
        
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // WebSocket URI (需要带上 userId 参数)
            URI uri = new URI("ws://" + IP + ":" + PORT + "/websocket?userId=" + currentUserId);

            // 设置 HTTP Headers
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.set("token", "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJoeHkxMTIyMzMiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxMjQ5NDg1NjcwNDAsImV4cCI6MTc2NDY2MzExNywiYXV0aG9yaXRpZXMiOlsiQURNSU4iXSwianRpIjoiN2UxNjQwNTYtMWI1Yy00MDEyLWJjOGEtMmM4OWI2YWI4NGQ1IiwiY2xpZW50X2lkIjoiY2xpZW50LWFwcCJ9.ARqj38fm0oxk1K47uHLBCoQzuqk6JzNMHTCoffZfHyr1PelbrnE-uXnJ3A1TrXA9K7uQ0XCkfefwhnhoqDu4xV4crxNPycSbIxnY7pud_agATAqQXR-UIlZr9V1KNUZs7sZYumZu8l-rcr-sDjob65WNxavc1vsC20CNPTYHqFjiiexonidgyhbH0BPPq8fV9AdocIojeTMq0g0kWtNjFG-ACg2CvAEt3ArqtKWPgEqWYRqJWOWRJ_qhyJ5YPuHfm4-T4weEA-W6H5siDCZHZKOgeBqPlLREkaC9s09eX40Hlnzehji1yohfaW_FObm7dBzcU5ODvUTKfdbMR2Tojg");
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
            InteractiveClientHandler handler = new InteractiveClientHandler(handshaker, currentUserId);
            
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
                            pipeline.addLast("heart-notice", new IdleStateHandler(11, 0, 0, TimeUnit.SECONDS));
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
            // 步骤 3: 显示帮助信息
            // ====================================================================
            printHelp();
            
            // ====================================================================
            // 步骤 4: 进入命令循环
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
    private static void commandLoop(Scanner scanner, InteractiveClientHandler handler) {
        while (true) {
            try {
                System.out.print("\n> ");
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
                    
                    case "quick":
                    case "q":
                        if (parts.length < 2) {
                            System.out.println("❌ 格式错误！用法: quick <接收方ID> 或 q <接收方ID>");
                            break;
                        }
                        handleQuickSendCommand(scanner, handler, parts[1]);
                        break;
                    
                    case "help":
                    case "h":
                        printHelp();
                        break;
                    
                    case "friend":
                    case "f":
                        if (parts.length < 2) {
                            System.out.println("❌ 格式错误！用法: friend <accept|reject|list> [requestId]");
                            break;
                        }
                        handleFriendCommand(handler, parts[1], input);
                        break;
                    
                    case "status":
                        printStatus(handler);
                        break;
                    
                    case "clear":
                    case "cls":
                        clearScreen();
                        break;
                    
                    case "exit":
                    case "quit":
                        System.out.println("👋 再见！");
                        channel.close().sync();
                        System.exit(0);
                        break;
                    
                    default:
                        System.out.println("❌ 未知命令: " + command);
                        System.out.println("💡 输入 'help' 查看帮助");
                }
                
            } catch (Exception e) {
                System.err.println("❌ 命令执行失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理发送消息命令（完整模式）
     */
    private static void handleSendCommand(Scanner scanner, InteractiveClientHandler handler) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│        发送消息（完整模式）         │");
        System.out.println("└─────────────────────────────────────┘");
        
        // 输入接收方
        System.out.print("接收方用户ID: ");
        String toUserId = scanner.nextLine().trim();
        
        if (toUserId.isEmpty()) {
            System.out.println("❌ 接收方ID不能为空！");
            return;
        }
        
        // 输入消息内容
        System.out.print("消息内容: ");
        String content = scanner.nextLine().trim();
        
        if (content.isEmpty()) {
            System.out.println("❌ 消息内容不能为空！");
            return;
        }
        
        // 发送消息
        handler.sendTextMessage(toUserId, content);
        
        System.out.println("✅ 消息已发送");
        System.out.println("   发送方: " + currentUserId);
        System.out.println("   接收方: " + toUserId);
        System.out.println("   内容: " + content);
    }
    
    /**
     * 处理快速发送命令（省略接收方输入）
     */
    private static void handleQuickSendCommand(Scanner scanner, InteractiveClientHandler handler, String toUserId) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│      快速发送模式（连续发送）       │");
        System.out.println("│      接收方: " + toUserId + "                      │");
        System.out.println("│      输入 'back' 返回主菜单         │");
        System.out.println("└─────────────────────────────────────┘");
        System.out.println();
        
        while (true) {
            System.out.print("[发送给 " + toUserId + "] > ");
            String content = scanner.nextLine().trim();
            
            if (content.isEmpty()) {
                continue;
            }
            
            if (content.equalsIgnoreCase("back") || content.equalsIgnoreCase("exit")) {
                System.out.println("✅ 退出快速发送模式");
                break;
            }
            
            // 发送消息
            handler.sendTextMessage(toUserId, content);
            
            System.out.println("  ✓ 已发送");
        }
    }
    
    /**
     * 处理好友相关命令
     */
    private static void handleFriendCommand(InteractiveClientHandler handler, String subCommand, String fullInput) {
        String[] parts = fullInput.split("\\s+");
        
        // 从subCommand中提取真正的子命令（第一个单词）
        String realSubCommand = subCommand.split("\\s+")[0].toLowerCase();
        
        switch (realSubCommand) {
            case "accept":
            case "a":
                if (parts.length < 3) {
                    System.out.println("❌ 格式错误！用法: friend accept <requestId>");
                    break;
                }
                handler.handleFriendRequestAction(parts[2], 1); // 1=同意
                break;
            
            case "reject":
            case "r":
                if (parts.length < 3) {
                    System.out.println("❌ 格式错误！用法: friend reject <requestId>");
                    break;
                }
                handler.handleFriendRequestAction(parts[2], 2); // 2=拒绝
                break;
            
            case "list":
            case "l":
                handler.listPendingFriendRequests();
                break;
            
            default:
                System.out.println("❌ 未知子命令: " + realSubCommand);
                System.out.println("💡 可用命令:");
                System.out.println("   friend accept <requestId>  - 同意好友请求");
                System.out.println("   friend reject <requestId>  - 拒绝好友请求");
                System.out.println("   friend list                - 查看待处理请求");
        }
    }
    
    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║                   可用命令列表                      ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║  send / s                  - 发送消息（完整模式）   ║");
        System.out.println("║  quick <接收方ID> / q      - 快速发送模式          ║");
        System.out.println("║  friend <子命令> / f       - 好友请求管理          ║");
        System.out.println("║    accept <requestId>      - 同意好友请求          ║");
        System.out.println("║    reject <requestId>      - 拒绝好友请求          ║");
        System.out.println("║    list                    - 查看待处理请求        ║");
        System.out.println("║  status                    - 查看连接状态          ║");
        System.out.println("║  help / h                  - 显示帮助信息          ║");
        System.out.println("║  clear / cls               - 清屏                  ║");
        System.out.println("║  exit / quit               - 退出程序              ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("💡 使用示例:");
        System.out.println("  1. 完整模式: 输入 'send'，然后按提示输入接收方和内容");
        System.out.println("  2. 快速模式: 输入 'quick user2'，然后可以连续发送消息");
        System.out.println("  3. 好友请求: 输入 'friend list' 查看，'friend accept <id>' 同意");
        System.out.println();
    }
    
    /**
     * 打印状态信息
     */
    private static void printStatus(InteractiveClientHandler handler) {
        System.out.println();
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│            当前连接状态             │");
        System.out.println("├─────────────────────────────────────┤");
        System.out.println("│  当前用户: " + currentUserId);
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


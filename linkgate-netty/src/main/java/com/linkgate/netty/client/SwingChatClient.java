package com.linkgate.netty.client;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckRequest;
import com.linkgate.core.protocol.ChatRequest;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * 极简 Swing 桌面客户端：连接网关、登录、收发点对点消息，用于本地演示。
 *
 * <p>线程模型：Netty 入站帧在后台 reader 线程取回，经 {@link SwingUtilities#invokeLater} 投递到 EDT 刷新界面。</p>
 */
public final class SwingChatClient {

    private final JFrame frame;
    private final JTextField hostField = new JTextField("127.0.0.1", 9);
    private final JTextField portField = new JTextField("8080", 5);
    private final JTextField userIdField = new JTextField(10);
    private final JTextField deviceIdField = new JTextField("default", 8);
    private final JTextField toField = new JTextField(10);
    private final JTextField textField = new JTextField(24);
    private final JTextField rawField = new JTextField(40);
    private final JTextArea log = new JTextArea();
    private final JLabel status = new JLabel("disconnected");

    private final AtomicLong seq = new AtomicLong(1);
    private volatile TestClient client;

    public SwingChatClient() {
        frame = new JFrame("LinkGate Swing Client");
        frame.setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("host"));
        top.add(hostField);
        top.add(new JLabel("port"));
        top.add(portField);
        top.add(new JLabel("userId"));
        top.add(userIdField);
        top.add(new JLabel("deviceId"));
        top.add(deviceIdField);
        JButton connect = new JButton("连接登录");
        connect.addActionListener(e -> connectAndLogin());
        top.add(connect);
        top.add(status);
        frame.add(top, BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        frame.add(new JScrollPane(log), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(2, 1));
        JPanel chatRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chatRow.add(new JLabel("to"));
        chatRow.add(toField);
        chatRow.add(new JLabel("text"));
        chatRow.add(textField);
        JButton send = new JButton("发送");
        send.addActionListener(e -> sendChat());
        chatRow.add(send);
        bottom.add(chatRow);

        JPanel rawRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rawRow.add(new JLabel("命令"));
        rawField.addActionListener(e -> execRaw());
        rawRow.add(rawField);
        JButton exec = new JButton("执行");
        exec.addActionListener(e -> execRaw());
        rawRow.add(exec);
        bottom.add(rawRow);
        frame.add(bottom, BorderLayout.SOUTH);

        frame.setSize(780, 540);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logout();
            }
        });
    }

    public void start() {
        frame.setVisible(true);
        append("host/port/userId 填好后点「连接登录」，或用底部命令框执行 login/chat/ack/raw");
    }

    private void connectAndLogin() {
        if (client != null && client.isActive()) {
            append("already connected");
            return;
        }
        setStatus("connecting...");
        new Thread(() -> {
            try {
                TestClient c = new TestClient(host(), port()).connect();
                client = c;
                SwingUtilities.invokeLater(() -> {
                    setStatus("connected");
                    append("connected to " + host() + ":" + port());
                });
                startReader(c);
                String userId = userIdField.getText().trim();
                if (!userId.isEmpty()) {
                    String deviceId = deviceIdField.getText().trim();
                    sendLogin(c, userId, deviceId.isEmpty() ? "default" : deviceId);
                    SwingUtilities.invokeLater(() -> append("> login " + userId + " " + deviceId));
                    startHeartbeat(c);
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("failed");
                    append("connect failed: " + e.getMessage());
                });
            }
        }, "gateway-connect").start();
    }

    private void startReader(TestClient c) {
        new Thread(() -> {
            while (c.isActive()) {
                try {
                    Frame frame = c.awaitFrame(200);
                    if (frame != null && !isHeartbeat(frame)) {
                        String text = formatInbound(frame);
                        SwingUtilities.invokeLater(() -> append(text));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "gateway-reader").start();
    }

    /** 每 25 秒发一次心跳，避免服务端 60 秒读空闲超时把连接断开。 */
    private void startHeartbeat(TestClient c) {
        new Thread(() -> {
            while (c.isActive()) {
                try {
                    Thread.sleep(25000);
                    if (c.isActive()) {
                        c.send(Frame.of(MsgType.HEARTBEAT.code(), seq.getAndIncrement()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "gateway-heartbeat").start();
    }

    private void sendLogin(TestClient c, String userId, String deviceId) {
        c.send(Frame.of(MsgType.LOGIN.code(), seq.getAndIncrement(),
                JsonCodec.encode(new LoginRequest(userId, deviceId))));
    }

    /** 登出：优雅断开网关连接并重置状态，窗口保持打开可再次登录。 */
    private void logout() {
        TestClient c = client;
        if (c != null) {
            c.close();
            client = null;
            append("logged out");
            setStatus("disconnected");
        }
    }

    private void sendChat() {
        TestClient c = client;
        if (c == null || !c.isActive()) {
            append("not connected");
            return;
        }
        String to = toField.getText().trim();
        String text = textField.getText();
        if (to.isEmpty() || text.isEmpty()) {
            append("to / text required");
            return;
        }
        c.send(Frame.of(MsgType.CHAT.code(), seq.getAndIncrement(),
                JsonCodec.encode(new ChatRequest(UUID.randomUUID().toString(), to, text))));
        append("> chat " + to + " " + text);
        textField.setText("");
    }

    private void execRaw() {
        String line = rawField.getText().trim();
        rawField.setText("");
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase();
        if (command.equals("quit") || command.equals("exit")) {
            logout();
            frame.dispose();
            System.exit(0);
            return;
        }
        TestClient c = client;
        if (c == null || !c.isActive()) {
            append("not connected");
            return;
        }
        append("> " + line);
        switch (command) {
            case "login" -> {
                if (parts.length >= 2) {
                    sendLogin(c, parts[1], parts.length > 2 ? parts[2] : "default");
                }
            }
            case "chat" -> {
                if (parts.length >= 3) {
                    c.send(Frame.of(MsgType.CHAT.code(), seq.getAndIncrement(),
                            JsonCodec.encode(new ChatRequest(UUID.randomUUID().toString(), parts[1],
                                    String.join(" ", Arrays.copyOfRange(parts, 2, parts.length))))));
                }
            }
            case "ack" -> {
                if (parts.length >= 2) {
                    c.send(Frame.of(MsgType.ACK.code(), seq.getAndIncrement(),
                            JsonCodec.encode(new AckRequest(parts[1]))));
                }
            }
            case "raw" -> {
                if (parts.length >= 3) {
                    byte type = (byte) Integer.parseInt(parts[1]);
                    c.send(Frame.of(type, seq.getAndIncrement(),
                            String.join(" ", Arrays.copyOfRange(parts, 2, parts.length))
                                    .getBytes(StandardCharsets.UTF_8)));
                }
            }
            case "help" -> append("login <userId> [deviceId] | chat <to> <text> | ack <messageId> | raw <type> <json> | quit");
            default -> append("unknown: " + command);
        }
    }

    private String host() {
        return hostField.getText().trim();
    }

    private int port() {
        return Integer.parseInt(portField.getText().trim());
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void append(String text) {
        log.append(text + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private static String formatInbound(Frame frame) {
        MsgType type = MsgType.fromCodeOrNull(frame.type());
        String name = type == null ? "UNKNOWN(" + (frame.type() & 0xFF) + ")" : type.name();
        String body = new String(frame.body(), StandardCharsets.UTF_8);
        return "<< " + name + " seq=" + frame.seq() + " " + body;
    }

    private static boolean isHeartbeat(Frame frame) {
        return frame.type() == MsgType.HEARTBEAT.code() || frame.type() == MsgType.HEARTBEAT_ACK.code();
    }

    public static void main(String[] args) {
        System.setProperty("logback.configurationFile", "linkgate-logback.xml");
        SwingUtilities.invokeLater(() -> new SwingChatClient().start());
    }
}

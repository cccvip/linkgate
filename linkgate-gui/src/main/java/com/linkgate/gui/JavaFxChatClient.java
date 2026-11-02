package com.linkgate.gui;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.AckRequest;
import com.linkgate.core.protocol.ChatRequest;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.netty.client.TestClient;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX 桌面客户端：连接网关、登录、收发点对点消息。
 *
 * <p>线程模型：Netty 入站帧在后台 reader 线程取回，经 {@link Platform#runLater} 投递到 JavaFX 线程刷新界面。</p>
 */
public final class JavaFxChatClient extends Application {

    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("8080");
    private final TextField userIdField = new TextField();
    private final TextField deviceIdField = new TextField("default");
    private final TextField toField = new TextField();
    private final TextField textField = new TextField();
    private final TextField commandField = new TextField();
    private final TextArea log = new TextArea();
    private final Label status = new Label("disconnected");

    private final AtomicLong seq = new AtomicLong(1);
    private volatile TestClient client;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("LinkGate Client");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        HBox top = new HBox(8);
        top.getChildren().addAll(
                new Label("host"), hostField,
                new Label("port"), portField,
                new Label("userId"), userIdField,
                new Label("deviceId"), deviceIdField);
        Button connect = new Button("连接登录");
        connect.setOnAction(e -> connectAndLogin());
        top.getChildren().addAll(connect, status);
        root.setTop(top);

        log.setEditable(false);
        log.setWrapText(true);
        VBox.setVgrow(log, Priority.ALWAYS);
        root.setCenter(log);

        HBox chatRow = new HBox(8);
        chatRow.getChildren().addAll(
                new Label("to"), toField,
                new Label("text"), textField);
        Button send = new Button("发送");
        send.setOnAction(e -> sendChat());
        HBox.setHgrow(textField, Priority.ALWAYS);
        chatRow.getChildren().add(send);

        HBox commandRow = new HBox(8);
        commandRow.getChildren().add(new Label("命令"));
        HBox.setHgrow(commandField, Priority.ALWAYS);
        commandField.setOnAction(e -> execCommand());
        Button exec = new Button("执行");
        exec.setOnAction(e -> execCommand());
        commandRow.getChildren().addAll(commandField, exec);

        VBox bottom = new VBox(6, chatRow, commandRow);
        bottom.setPadding(new Insets(8, 0, 0, 0));
        root.setBottom(bottom);

        Scene scene = new Scene(root, 820, 560);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> logout());
        stage.show();

        append("host/port/userId 填好后点「连接登录」，或用底部命令框执行 login/chat/ack/raw");
    }

    private void connectAndLogin() {
        if (client != null && client.isActive()) {
            append("already connected");
            return;
        }
        setStatus("connecting...");
        Thread thread = new Thread(() -> {
            try {
                TestClient c = new TestClient(host(), port()).connect();
                client = c;
                Platform.runLater(() -> {
                    setStatus("connected");
                    append("connected to " + host() + ":" + port());
                });
                startReader(c);
                String userId = userIdField.getText().trim();
                if (!userId.isEmpty()) {
                    String deviceId = deviceIdField.getText().trim();
                    sendLogin(c, userId, deviceId.isEmpty() ? "default" : deviceId);
                    Platform.runLater(() -> append("> login " + userId + " " + deviceId));
                    startHeartbeat(c);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatus("failed");
                    append("connect failed: " + e.getMessage());
                });
            }
        }, "gateway-connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void startReader(TestClient c) {
        Thread thread = new Thread(() -> {
            while (c.isActive()) {
                try {
                    Frame frame = c.awaitFrame(200);
                    if (frame != null && !isHeartbeat(frame)) {
                        String text = formatInbound(frame);
                        Platform.runLater(() -> append(text));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "gateway-reader");
        thread.setDaemon(true);
        thread.start();
    }

    /** 每 25 秒发一次心跳，避免服务端 60 秒读空闲超时把连接断开。 */
    private void startHeartbeat(TestClient c) {
        Thread thread = new Thread(() -> {
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
        }, "gateway-heartbeat");
        thread.setDaemon(true);
        thread.start();
    }

    private void sendLogin(TestClient c, String userId, String deviceId) {
        c.send(Frame.of(MsgType.LOGIN.code(), seq.getAndIncrement(),
                JsonCodec.encode(new LoginRequest(userId, deviceId))));
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
        textField.clear();
    }

    private void execCommand() {
        TestClient c = client;
        String line = commandField.getText().trim();
        commandField.clear();
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase();
        if (command.equals("quit") || command.equals("exit")) {
            logout();
            Platform.exit();
            return;
        }
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
                                    String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length))))));
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
                            String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length))
                                    .getBytes(StandardCharsets.UTF_8)));
                }
            }
            case "help" -> append("login <userId> [deviceId] | chat <to> <text> | ack <messageId> | raw <type> <json> | quit");
            default -> append("unknown: " + command);
        }
    }

    /** 登出：优雅断开网关连接并重置状态，可再次登录。 */
    private void logout() {
        TestClient c = client;
        if (c != null) {
            c.close();
            client = null;
            append("logged out");
            setStatus("disconnected");
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
        status.setStyle(text.equals("connected") ? "-fx-text-fill:#3ddc84;" : "-fx-text-fill:#8a94a3;");
    }

    private void append(String text) {
        log.appendText(text + "\n");
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
}

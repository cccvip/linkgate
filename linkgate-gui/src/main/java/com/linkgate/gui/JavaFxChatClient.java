package com.linkgate.gui;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.ChatDelivery;
import com.linkgate.core.protocol.ChatRequest;
import com.linkgate.core.protocol.GroupChatRequest;
import com.linkgate.core.protocol.GroupJoinRequest;
import com.linkgate.core.protocol.GroupLeaveRequest;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.netty.client.TestClient;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX 聊天客户端（微信夜间风格）：左侧竖排导航，中间列表，右侧气泡聊天区。
 */
public final class JavaFxChatClient extends Application {

    private static final String SYSTEM = "系统";
    private static final String NAV_CHAT = "聊天";
    private static final String NAV_CONTACTS = "通讯录";
    private static final String NAV_GROUPS = "群组";
    private static final String NAV_CONN = "连接";

    private final Label status = new Label("未连接");

    private final ToggleButton chatNav = nav(NAV_CHAT);
    private final ToggleButton contactNav = nav(NAV_CONTACTS);
    private final ToggleButton groupNav = nav(NAV_GROUPS);
    private final ToggleButton connNav = nav(NAV_CONN);

    // 连接面板
    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("8080");
    private final TextField userIdField = new TextField();
    private final TextField deviceIdField = new TextField("default");

    // 会话列表
    private final ListView<String> conversationList = new ListView<>();
    private final ObservableList<String> conversations = FXCollections.observableArrayList(SYSTEM);

    // 通讯录
    private final TextField contactField = new TextField();
    private final ObservableList<String> contacts = FXCollections.observableArrayList();

    // 群组
    private final TextField groupField = new TextField();
    private final ObservableList<String> joinedGroups = FXCollections.observableArrayList();

    // 聊天区
    private final ListView<ChatMessage> chatList = new ListView<>();
    private final Map<String, ObservableList<ChatMessage>> messages = new HashMap<>();
    private final TextField inputField = new TextField();
    private final Button sendButton = new Button("发送");

    private final StackPane middlePane = new StackPane();
    private final VBox chatPanel = new VBox();
    private final Label chatPlaceholder = new Label("请在「连接」登录，然后在「通讯录」或「群组」发起会话");

    private final AtomicLong seq = new AtomicLong(1);
    private volatile TestClient client;
    private volatile String myUserId;
    private String currentConversation = SYSTEM;

    private record ChatMessage(String from, String text, boolean mine) {
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        messages.put(SYSTEM, FXCollections.observableArrayList());
        stage.setTitle("LinkGate");

        BorderPane root = new BorderPane();
        root.setTop(buildTop());
        root.setCenter(buildCenter());

        Scene scene = new Scene(root, 1000, 640);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> logout());
        stage.show();

        showNav(NAV_CONN);
        system("请先在「连接」页登录");
    }

    private HBox buildTop() {
        Label title = new Label("LinkGate");
        title.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#07c160;");
        status.getStyleClass().add("status-disconnected");
        HBox top = new HBox(title);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(spacer, status);
        top.setPadding(new Insets(10, 14, 10, 14));
        top.setStyle("-fx-background-color:#171a20;-fx-border-color:#2a313c;-fx-border-width:0 0 1 0;");
        return top;
    }

    private HBox buildCenter() {
        VBox navRail = new VBox(chatNav, contactNav, groupNav, connNav);
        navRail.setPrefWidth(76);
        navRail.setStyle("-fx-background-color:#171a20;-fx-border-color:#2a313c;-fx-border-width:0 1 0 0;");

        ToggleGroup navGroup = new ToggleGroup();
        chatNav.setToggleGroup(navGroup);
        contactNav.setToggleGroup(navGroup);
        groupNav.setToggleGroup(navGroup);
        connNav.setToggleGroup(navGroup);

        chatNav.setOnAction(e -> showNav(NAV_CHAT));
        contactNav.setOnAction(e -> showNav(NAV_CONTACTS));
        groupNav.setOnAction(e -> showNav(NAV_GROUPS));
        connNav.setOnAction(e -> showNav(NAV_CONN));

        middlePane.setPrefWidth(230);

        buildChatPanel();

        HBox center = new HBox(navRail, middlePane, chatPanel);
        HBox.setHgrow(chatPanel, Priority.ALWAYS);
        return center;
    }

    private void buildChatPanel() {
        chatList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatMessage m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                String text = m.mine() ? m.text() : (m.from() + ": " + m.text());
                Label bubble = new Label(text);
                bubble.setWrapText(true);
                bubble.setMaxWidth(440);
                bubble.getStyleClass().add(m.mine() ? "bubble-mine" : "bubble-theirs");

                HBox row = new HBox(bubble);
                row.setAlignment(m.mine() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                row.setPadding(new Insets(5, 12, 5, 12));
                row.setMaxWidth(Double.MAX_VALUE);
                setGraphic(row);
            }
        });
        chatList.setItems(messages.get(SYSTEM));

        HBox inputRow = new HBox(8, inputField, sendButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> sendMessage());
        sendButton.setOnAction(e -> sendMessage());
        inputRow.setPadding(new Insets(10, 12, 10, 12));
        inputRow.setStyle("-fx-background-color:#171a20;");

        VBox.setVgrow(chatList, Priority.ALWAYS);
        chatPanel.getChildren().addAll(chatList, inputRow);
    }

    private void showNav(String nav) {
        switch (nav) {
            case NAV_CHAT -> middlePane.getChildren().setAll(chatListPane());
            case NAV_CONTACTS -> middlePane.getChildren().setAll(contactPane());
            case NAV_GROUPS -> middlePane.getChildren().setAll(groupPane());
            case NAV_CONN -> middlePane.getChildren().setAll(connectionPane());
        }
    }

    private VBox chatListPane() {
        conversationList.setItems(conversations);
        conversationList.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> selectConversation(n));
        VBox box = panelWithTitle("会话", conversationList);
        conversationList.getSelectionModel().select(currentConversation);
        return box;
    }

    private VBox contactPane() {
        Button start = new Button("开始聊天");
        start.setOnAction(e -> newChat());
        HBox row = new HBox(6, contactField, start);
        HBox.setHgrow(contactField, Priority.ALWAYS);

        ListView<String> contactList = new ListView<>(contacts);
        contactList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                openConversation(n);
            }
        });
        VBox box = panelWithTitle("联系人", row, contactList);
        VBox.setVgrow(contactList, Priority.ALWAYS);
        return box;
    }

    private VBox groupPane() {
        Button join = new Button("加入");
        join.setOnAction(e -> joinGroup());
        HBox row = new HBox(6, groupField, join);
        HBox.setHgrow(groupField, Priority.ALWAYS);

        ListView<String> groupList = new ListView<>(joinedGroups);
        Button enter = new Button("进入");
        enter.setOnAction(e -> {
            String g = groupList.getSelectionModel().getSelectedItem();
            if (g != null) {
                openConversation("#" + g);
            }
        });
        Button leave = new Button("退出");
        leave.setOnAction(e -> {
            String g = groupList.getSelectionModel().getSelectedItem();
            if (g != null) {
                leaveGroup(g);
            }
        });
        HBox actions = new HBox(6, enter, leave);

        VBox box = panelWithTitle("群组", row, groupList, actions);
        VBox.setVgrow(groupList, Priority.ALWAYS);
        return box;
    }

    private VBox connectionPane() {
        Label hint = new Label("登录配置");
        hint.getStyleClass().add("panel-title");
        Button connect = new Button("连接登录");
        connect.setOnAction(e -> connectAndLogin());

        VBox box = new VBox(8,
                hint,
                fieldRow("host", hostField),
                fieldRow("port", portField),
                fieldRow("userId", userIdField),
                fieldRow("device", deviceIdField),
                connect);
        box.setPadding(new Insets(12));
        return box;
    }

    private static HBox fieldRow(String label, TextField field) {
        Label l = new Label(label);
        l.setPrefWidth(54);
        HBox row = new HBox(8, l, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private static VBox panelWithTitle(String title, javafx.scene.Node... content) {
        Label t = new Label(title);
        t.getStyleClass().add("panel-title");
        VBox box = new VBox(8, t);
        box.getChildren().addAll(content);
        box.setPadding(new Insets(12));
        return box;
    }

    private static ToggleButton nav(String text) {
        ToggleButton b = new ToggleButton(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("nav-button");
        return b;
    }

    private void openConversation(String conv) {
        if (!conversations.contains(conv)) {
            conversations.add(conv);
        }
        chatNav.setSelected(true);
        showNav(NAV_CHAT);
        conversationList.getSelectionModel().select(conv);
        selectConversation(conv);
    }

    private void newChat() {
        String peer = contactField.getText().trim();
        if (peer.isEmpty()) {
            return;
        }
        if (!contacts.contains(peer)) {
            contacts.add(peer);
        }
        openConversation(peer);
        contactField.clear();
    }

    private void joinGroup() {
        TestClient c = client;
        String groupId = groupField.getText().trim();
        if (groupId.isEmpty()) {
            return;
        }
        if (c == null || !c.isActive()) {
            system("not connected");
            return;
        }
        c.send(Frame.of(MsgType.GROUP_JOIN.code(), seq.getAndIncrement(),
                JsonCodec.encode(new GroupJoinRequest(groupId))));
        if (!joinedGroups.contains(groupId)) {
            joinedGroups.add(groupId);
        }
        openConversation("#" + groupId);
        groupField.clear();
    }

    private void leaveGroup(String groupId) {
        TestClient c = client;
        if (c == null || !c.isActive()) {
            system("not connected");
            return;
        }
        c.send(Frame.of(MsgType.GROUP_LEAVE.code(), seq.getAndIncrement(),
                JsonCodec.encode(new GroupLeaveRequest(groupId))));
        joinedGroups.remove(groupId);
        conversations.remove("#" + groupId);
        system("> leave " + groupId);
    }

    private void connectAndLogin() {
        if (client != null && client.isActive()) {
            system("already connected");
            return;
        }
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        String userId = userIdField.getText().trim();
        String deviceId = deviceIdField.getText().trim();

        setStatus("connecting...");
        Thread thread = new Thread(() -> {
            try {
                TestClient c = new TestClient(host, port).connect();
                client = c;
                Platform.runLater(() -> {
                    myUserId = userId;
                    setStatus("connected");
                    system("connected to " + host + ":" + port);
                });
                startReader(c);
                if (!userId.isEmpty()) {
                    sendLogin(c, userId, deviceId.isEmpty() ? "default" : deviceId);
                    Platform.runLater(() -> system("> login " + userId));
                    startHeartbeat(c);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatus("failed");
                    system("connect failed: " + e.getMessage());
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
                        Platform.runLater(() -> handleInbound(frame));
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

    private void sendMessage() {
        TestClient c = client;
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (c == null || !c.isActive()) {
            system("not connected");
            return;
        }
        String conv = currentConversation;
        if (conv.equals(SYSTEM)) {
            system("请先选择一个会话");
            return;
        }
        if (conv.startsWith("#")) {
            String groupId = conv.substring(1);
            c.send(Frame.of(MsgType.GROUP_CHAT.code(), seq.getAndIncrement(),
                    JsonCodec.encode(new GroupChatRequest(UUID.randomUUID().toString(), groupId, text))));
        } else {
            c.send(Frame.of(MsgType.CHAT.code(), seq.getAndIncrement(),
                    JsonCodec.encode(new ChatRequest(UUID.randomUUID().toString(), conv, text))));
        }
        appendMessage(conv, new ChatMessage(myUserId == null ? "我" : myUserId, text, true));
        inputField.clear();
    }

    private void handleInbound(Frame frame) {
        MsgType type = MsgType.fromCodeOrNull(frame.type());
        if (type == null) {
            system("UNKNOWN " + (frame.type() & 0xFF) + " " + body(frame));
            return;
        }
        switch (type) {
            case CHAT -> {
                try {
                    ChatDelivery d = JsonCodec.decode(frame.body(), ChatDelivery.class);
                    String conv = d.groupId() != null ? "#" + d.groupId() : d.fromUserId();
                    boolean mine = myUserId != null && myUserId.equals(d.fromUserId());
                    appendMessage(conv, new ChatMessage(d.fromUserId(), d.text(), mine));
                    if (d.groupId() == null && !contacts.contains(d.fromUserId())) {
                        contacts.add(d.fromUserId());
                    }
                } catch (RuntimeException e) {
                    system("bad CHAT: " + body(frame));
                }
            }
            case LOGIN_ACK -> {
                setStatus("connected");
                system("LOGIN_ACK " + body(frame));
            }
            case CHAT_ACK -> system("CHAT_ACK " + body(frame));
            case GROUP_ACK -> system("GROUP_ACK " + body(frame));
            case ERROR -> system("ERROR " + body(frame));
            default -> system(type.name() + " " + body(frame));
        }
    }

    private void selectConversation(String conv) {
        if (conv == null) {
            return;
        }
        currentConversation = conv;
        chatList.setItems(messages.computeIfAbsent(conv, k -> FXCollections.observableArrayList()));
        chatList.scrollTo(Math.max(0, chatList.getItems().size() - 1));
    }

    private void appendMessage(String conv, ChatMessage message) {
        ObservableList<ChatMessage> list = messages.computeIfAbsent(conv, k -> FXCollections.observableArrayList());
        list.add(message);
        if (!conversations.contains(conv)) {
            conversations.add(conv);
        }
        if (conv.equals(currentConversation)) {
            chatList.scrollTo(Math.max(0, list.size() - 1));
        }
    }

    private void system(String text) {
        appendMessage(SYSTEM, new ChatMessage(SYSTEM, text, false));
    }

    private void logout() {
        TestClient c = client;
        if (c != null) {
            c.close();
            client = null;
            system("logged out");
            setStatus("disconnected");
        }
    }

    private void setStatus(String text) {
        status.setText(text);
        status.getStyleClass().removeAll("status-connected", "status-disconnected");
        if (text.equals("connected")) {
            status.setText("已连接");
            status.getStyleClass().add("status-connected");
        } else if (text.equals("disconnected")) {
            status.setText("未连接");
            status.getStyleClass().add("status-disconnected");
        } else {
            status.getStyleClass().add("status-disconnected");
        }
    }

    private static String body(Frame frame) {
        return new String(frame.body(), StandardCharsets.UTF_8);
    }

    private static boolean isHeartbeat(Frame frame) {
        return frame.type() == MsgType.HEARTBEAT.code() || frame.type() == MsgType.HEARTBEAT_ACK.code();
    }
}

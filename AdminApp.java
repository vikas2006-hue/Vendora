package com.vendora.admin;

import com.google.gson.*;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Vendora desktop admin: talks to the Spring Boot REST API. Only the single admin account can log in. */
public class AdminApp extends Application {
    private static final Gson G = new Gson();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final HttpClient http = HttpClient.newHttpClient();
    private String base = "http://localhost:8080";
    private String token;
    private Stage stage;

    private final ObservableList<JsonObject> logins = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> users = FXCollections.observableArrayList();
    private final ObservableList<JsonObject> listings = FXCollections.observableArrayList();
    private final Label stats = new Label();

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Vendora Admin");
        stage.setScene(loginScene());
        stage.show();
    }

    /* ---------------- HTTP helpers ---------------- */
    private JsonElement request(String method, String path, Object body, boolean auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json");
        if (auth) b.header("Authorization", "Bearer " + token);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(G.toJson(body)));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonElement e = r.body() == null || r.body().isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(r.body());
        if (r.statusCode() >= 400) {
            String m = "HTTP " + r.statusCode();
            if (e.isJsonObject() && e.getAsJsonObject().has("error")) m = e.getAsJsonObject().get("error").getAsString();
            throw new RuntimeException(m);
        }
        return e;
    }

    private <T> void async(Callable<T> work, Consumer<T> ok) {
        Task<T> t = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        t.setOnSucceeded(ev -> ok.accept(t.getValue()));
        t.setOnFailed(ev -> alert(Alert.AlertType.ERROR, t.getException().getMessage()));
        Thread th = new Thread(t); th.setDaemon(true); th.start();
    }

    private void alert(Alert.AlertType type, String msg) { new Alert(type, msg).showAndWait(); }

    private static String s(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
    private static String time(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "—" : FMT.format(Instant.ofEpochMilli(e.getAsLong()));
    }

    /* ---------------- Login screen ---------------- */
    private Scene loginScene() {
        TextField server = new TextField(base);
        TextField email = new TextField(); email.setPromptText("Admin email");
        PasswordField pass = new PasswordField(); pass.setPromptText("Password");
        PasswordField pass2 = new PasswordField(); pass2.setPromptText("Confirm password");
        pass2.setManaged(false); pass2.setVisible(false);
        Label msg = new Label(); msg.setStyle("-fx-text-fill:#D64545");
        Button go = new Button("Login"); go.setMaxWidth(Double.MAX_VALUE); go.setDefaultButton(true);
        Hyperlink setup = new Hyperlink("First-time setup (create the admin)");
        boolean[] setupMode = {false};

        setup.setOnAction(e -> {
            setupMode[0] = !setupMode[0];
            pass2.setManaged(setupMode[0]); pass2.setVisible(setupMode[0]);
            go.setText(setupMode[0] ? "Create admin account" : "Login");
            setup.setText(setupMode[0] ? "Back to login" : "First-time setup (create the admin)");
        });
        go.setOnAction(e -> {
            msg.setText("");
            base = server.getText().trim().replaceAll("/+$", "");
            String em = email.getText().trim().toLowerCase(), pw = pass.getText();
            if (em.isEmpty() || pw.length() < 8) { msg.setText("Enter email and a password of 8+ characters."); return; }
            if (setupMode[0] && !pw.equals(pass2.getText())) { msg.setText("Passwords do not match."); return; }
            JsonObject body = new JsonObject(); body.addProperty("email", em); body.addProperty("password", pw);
            String path = setupMode[0] ? "/api/admin/setup" : "/api/admin/login";
            go.setDisable(true);
            Task<String> t = new Task<>() {
                @Override protected String call() throws Exception {
                    return request("POST", path, G.fromJson(body, Object.class), false).getAsJsonObject().get("token").getAsString();
                }
            };
            t.setOnSucceeded(ev -> { token = t.getValue(); go.setDisable(false); stage.setScene(mainScene()); refresh(); });
            t.setOnFailed(ev -> { go.setDisable(false); msg.setText(t.getException().getMessage()); });
            Thread th = new Thread(t); th.setDaemon(true); th.start();
        });

        Label title = new Label("vendora Admin"); title.setStyle("-fx-font-size:24px;-fx-font-weight:bold");
        Label sub = new Label("Restricted area. Only the one admin can login."); sub.setStyle("-fx-text-fill:#6B6E7A");
        VBox box = new VBox(10, title, sub, new Label("Server URL"), server, new Label("Admin"), email, pass, pass2, go, setup, msg);
        box.setPadding(new Insets(28)); box.setMaxWidth(380);
        StackPane root = new StackPane(box); root.setAlignment(Pos.CENTER);
        return new Scene(root, 520, 560);
    }

    /* ---------------- Main screen ---------------- */
    private Scene mainScene() {
        TextField search = new TextField(); search.setPromptText("Search name, email, product…"); HBox.setHgrow(search, Priority.ALWAYS);
        Button refresh = new Button("↻ Refresh"); refresh.setOnAction(e -> refresh());
        Button logout = new Button("Logout"); logout.setOnAction(e -> { token = null; stage.setScene(loginScene()); });
        Label title = new Label("vendora Admin"); title.setStyle("-fx-font-size:18px;-fx-font-weight:bold");
        HBox top = new HBox(10, title, search, refresh, logout); top.setAlignment(Pos.CENTER_LEFT); top.setPadding(new Insets(10));

        FilteredList<JsonObject> fLogins = new FilteredList<>(logins, x -> true);
        FilteredList<JsonObject> fUsers = new FilteredList<>(users, x -> true);
        FilteredList<JsonObject> fListings = new FilteredList<>(listings, x -> true);
        search.textProperty().addListener((o, a, b) -> {
            String q = b.trim().toLowerCase();
            fLogins.setPredicate(r -> q.isEmpty() || (s(r, "name") + s(r, "email") + s(r, "phone")).toLowerCase().contains(q));
            fUsers.setPredicate(r -> q.isEmpty() || (s(r, "name") + s(r, "email") + s(r, "phone") + s(r, "city")).toLowerCase().contains(q));
            fListings.setPredicate(r -> q.isEmpty() || (s(r, "t") + s(r, "type") + s(r, "c") + s(r, "sName") + s(r, "owner") + s(r, "sCity")).toLowerCase().contains(q));
        });

        /* Logins tab */
        TableView<JsonObject> tLogins = new TableView<>(fLogins);
        tLogins.getColumns().add(col("Name", 160, r -> s(r, "name")));
        tLogins.getColumns().add(col("Email", 220, r -> s(r, "email")));
        tLogins.getColumns().add(col("Mobile", 130, r -> s(r, "phone")));
        tLogins.getColumns().add(col("Type", 100, r -> s(r, "type").equals("register") ? "New signup" : "Login"));
        tLogins.getColumns().add(col("When", 160, r -> time(r, "at")));

        /* Users tab */
        TableView<JsonObject> tUsers = new TableView<>(fUsers);
        tUsers.getColumns().add(col("Name", 160, r -> s(r, "name")));
        tUsers.getColumns().add(col("Email", 220, r -> s(r, "email")));
        tUsers.getColumns().add(col("Mobile", 130, r -> s(r, "phone")));
        tUsers.getColumns().add(col("City", 110, r -> s(r, "city")));
        tUsers.getColumns().add(col("Uploads", 80, r -> s(r, "uploads")));
        tUsers.getColumns().add(col("Last login", 160, r -> time(r, "lastLogin")));

        /* Uploads tab: table + preview + delete */
        TableView<JsonObject> tList = new TableView<>(fListings);
        tList.getColumns().add(col("Title", 170, r -> s(r, "t")));
        tList.getColumns().add(col("Price", 80, r -> "₹" + s(r, "p")));
        tList.getColumns().add(col("Category", 110, r -> s(r, "c")));
        tList.getColumns().add(col("Seller", 130, r -> s(r, "sName")));
        tList.getColumns().add(col("Owner email", 190, r -> s(r, "owner")));
        tList.getColumns().add(col("Uploaded", 150, r -> time(r, "at")));
        HBox.setHgrow(tList, Priority.ALWAYS);

        ImageView img = new ImageView(); img.setFitWidth(240); img.setFitHeight(240); img.setPreserveRatio(true);
        Label info = new Label("Select a product"); info.setWrapText(true); info.setMaxWidth(240);
        Button del = new Button("🗑 Delete product"); del.setStyle("-fx-background-color:#D64545;-fx-text-fill:white;-fx-font-weight:bold");
        del.setDisable(true);
        VBox side = new VBox(10, img, info, del); side.setPadding(new Insets(10)); side.setPrefWidth(260);

        tList.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> {
            del.setDisable(r == null);
            if (r == null) { img.setImage(null); info.setText("Select a product"); return; }
            info.setText(s(r, "t") + " · ₹" + s(r, "p") + "\n" + s(r, "c") + " / " + s(r, "type") + " · " + s(r, "cond")
                    + "\nSeller: " + s(r, "sName") + "\n" + s(r, "sPhone") + " · " + s(r, "sCity") + "\n" + s(r, "owner") + "\n" + time(r, "at"));
            img.setImage(decode(s(r, "img")));
        });
        del.setOnAction(e -> {
            JsonObject r = tList.getSelectionModel().getSelectedItem();
            if (r == null) return;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete \"" + s(r, "t") + "\" permanently?", ButtonType.YES, ButtonType.NO);
            a.showAndWait().ifPresent(bt -> {
                if (bt != ButtonType.YES) return;
                async(() -> request("DELETE", "/api/admin/listings/" + s(r, "id"), null, true), x -> refresh());
            });
        });

        TabPane tabs = new TabPane(
                new Tab("🔐 Logins", tLogins),
                new Tab("📦 Uploads", new HBox(tList, side)),
                new Tab("👥 Users", tUsers));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane root = new BorderPane(tabs);
        root.setTop(top);
        stats.setPadding(new Insets(6, 10, 6, 10));
        root.setBottom(stats);
        return new Scene(root, 1100, 650);
    }

    private TableColumn<JsonObject, String> col(String title, double w, Function<JsonObject, String> f) {
        TableColumn<JsonObject, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new SimpleStringProperty(f.apply(d.getValue())));
        return c;
    }

    private Image decode(String dataUrl) {
        try {
            if (dataUrl == null || !dataUrl.contains(",")) return null;
            byte[] b = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
            return new Image(new ByteArrayInputStream(b), 240, 240, true, true);
        } catch (Exception e) { return null; }
    }

    private void refresh() {
        async(() -> new JsonElement[]{
                request("GET", "/api/admin/logins", null, true),
                request("GET", "/api/admin/users", null, true),
                request("GET", "/api/admin/listings", null, true)
        }, r -> {
            logins.setAll(toList(r[0])); users.setAll(toList(r[1])); listings.setAll(toList(r[2]));
            stats.setText("Users: " + users.size() + "   |   Products: " + listings.size() + "   |   Logins: " + logins.size());
        });
    }

    private static java.util.List<JsonObject> toList(JsonElement e) {
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsJsonObject());
        return out;
    }
}

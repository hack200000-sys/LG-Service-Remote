package com.dxbmr.lgservice;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONArray;
import org.json.JSONObject;

/** Minimal LG webOS SSAP client (the protocol the LG ThinQ app uses) on port 3000 / 3001. */
public class LgSsap {
    public interface Listener { void log(String msg); void onPaired(String clientKey); }
    interface Cb { void on(JSONObject msg); }

    private final Listener listener;
    private final OkHttpClient http;
    private WebSocket ws, pointer;
    private int nextId = 1;
    private final Map<String, Cb> pending = new HashMap<>();

    public LgSsap(Listener l) { listener = l; http = buildClient(); }

    private static OkHttpClient buildClient() {
        try {
            // The TV uses a self-signed certificate on 3001; trust it only for this local connection.
            X509TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{tm}, null);
            return new OkHttpClient.Builder()
                    .sslSocketFactory(ssl.getSocketFactory(), tm)
                    .hostnameVerifier((h, s) -> true)
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .pingInterval(20, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void connect(String ip, boolean secure, String clientKey) {
        disconnect();
        String url = secure ? "wss://" + ip + ":3001" : "ws://" + ip + ":3000";
        listener.log("Connecting " + url + " …");
        ws = http.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket s, Response r) {
                listener.log("Connected. Registering (accept the prompt on the TV if it appears)…");
                s.send(registerMsg(clientKey).toString());
            }
            @Override public void onMessage(WebSocket s, String text) { handle(text); }
            @Override public void onFailure(WebSocket s, Throwable t, Response r) {
                listener.log("Connection failed: " + t.getMessage()
                        + (secure ? "" : "  → try ticking 'Secure (3001)'"));
            }
            @Override public void onClosed(WebSocket s, int code, String reason) { listener.log("Closed: " + reason); }
        });
    }

    public void disconnect() {
        if (pointer != null) { pointer.close(1000, null); pointer = null; }
        if (ws != null) { ws.close(1000, null); ws = null; }
    }

    private JSONObject registerMsg(String key) {
        try {
            JSONArray perms = new JSONArray();
            for (String p : new String[]{"LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO",
                    "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV",
                    "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER", "READ_INSTALLED_APPS",
                    "READ_RUNNING_APPS", "READ_TV_CURRENT_TIME", "READ_INPUT_DEVICE_LIST"}) perms.put(p);
            JSONObject manifest = new JSONObject()
                    .put("manifestVersion", 1).put("appVersion", "1.0")
                    .put("permissions", perms);
            JSONObject payload = new JSONObject().put("forcePairing", false)
                    .put("pairingType", "PROMPT").put("manifest", manifest);
            if (key != null && !key.isEmpty()) payload.put("client-key", key);
            return new JSONObject().put("type", "register").put("id", "reg0").put("payload", payload);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void handle(String text) {
        try {
            JSONObject m = new JSONObject(text);
            String type = m.optString("type"), id = m.optString("id");
            if ("registered".equals(type)) {
                String key = m.getJSONObject("payload").optString("client-key");
                listener.log("Paired ✔");
                listener.onPaired(key);
                return;
            }
            if ("error".equals(type)) listener.log("TV error: " + m.optString("error"));
            Cb cb = pending.remove(id);
            if (cb != null) cb.on(m); else if (!"reg0".equals(id)) listener.log("TV: " + text);
        } catch (Exception e) { listener.log("Bad message: " + text); }
    }

    private void request(String uri, JSONObject payload, Cb cb) {
        if (ws == null) { listener.log("Not connected"); return; }
        try {
            String id = "r" + (nextId++);
            JSONObject msg = new JSONObject().put("type", "request").put("id", id).put("uri", uri);
            if (payload != null) msg.put("payload", payload);
            if (cb != null) pending.put(id, cb);
            ws.send(msg.toString());
        } catch (Exception e) { listener.log(e.toString()); }
    }

    /** Opens LG's hidden factory app with a service key: "inStart" or "ezAdjust". */
    public void openServiceMenu(String irKey) {
        try {
            JSONObject p = new JSONObject().put("id", "com.webos.app.factorywin")
                    .put("params", new JSONObject().put("id", "executeFactory").put("irKey", irKey));
            listener.log("Launching factory menu: " + irKey);
            request("ssap://system.launcher/launch", p, m -> listener.log(
                    m.optJSONObject("payload") != null && m.optJSONObject("payload").optBoolean("returnValue")
                            ? "Factory menu launched — check the TV (password is usually 0413 or 0000)"
                            : "TV refused: " + m));
        } catch (Exception e) { listener.log(e.toString()); }
    }

    /** Sends a remote button over the network (HOME, BACK, UP, DOWN, LEFT, RIGHT, ENTER, MENU, 0-9…). */
    public void button(String name) {
        if (pointer != null) { pointer.send("type:button\nname:" + name + "\n\n"); return; }
        request("ssap://com.webos.service.networkinput/getPointerInputSocket", null, m -> {
            String path = m.optJSONObject("payload") == null ? null : m.optJSONObject("payload").optString("socketPath", null);
            if (path == null) { listener.log("No pointer socket: " + m); return; }
            pointer = http.newWebSocket(new Request.Builder().url(path).build(), new WebSocketListener() {
                @Override public void onOpen(WebSocket s, Response r) { s.send("type:button\nname:" + name + "\n\n"); }
                @Override public void onFailure(WebSocket s, Throwable t, Response r) { pointer = null; listener.log("Pointer failed: " + t.getMessage()); }
            });
        });
    }
}

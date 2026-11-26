package com.dxbmr.lgservice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity implements LgSsap.Listener {
    private TextView logView;
    private SharedPreferences prefs;
    private LgSsap ssap;
    private LgIr ir;
    private EditText ipField, codeField;
    private CheckBox secure;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("lg", MODE_PRIVATE);
        ssap = new LgSsap(this);
        ir = new LgIr(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 24);

        // ---------- Infrared (works even when the hotel TV blocks network control) ----------
        header(root, "Infrared " + (ir.available() ? "(IR blaster found ✔)" : "(no IR blaster on this phone ✖)"));
        codeField = new EditText(this);
        codeField.setHint("Installer password (LG hotel default 1105)");
        codeField.setText(prefs.getString("code", "1105"));
        codeField.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(codeField);
        row(root, btn("Open INSTALLER MENU\n(hold Settings + code)", this::installerMacro));
        row(root, btn("IR In-Start", () -> irSend(LgIr.IN_START)), btn("IR EZ-Adjust", () -> irSend(LgIr.EZ_ADJUST)),
                btn("IR In-Stop", () -> irSend(LgIr.IN_STOP)));
        row(root, btn("Power", () -> irSend(LgIr.POWER)), btn("Settings", () -> irSend(LgIr.SETTINGS)),
                btn("Hold Settings 7s", () -> irBg(() -> ir.hold(LgIr.SETTINGS, 7000))));
        row(root, btn("Home", () -> irSend(LgIr.HOME)), btn("▲", () -> irSend(LgIr.UP)), btn("Back", () -> irSend(LgIr.BACK)));
        row(root, btn("◀", () -> irSend(LgIr.LEFT)), btn("OK", () -> irSend(LgIr.OK)), btn("▶", () -> irSend(LgIr.RIGHT)));
        row(root, btn("Exit", () -> irSend(LgIr.EXIT)), btn("▼", () -> irSend(LgIr.DOWN)), btn("", () -> {}));
        for (int r = 0; r < 4; r++) {
            LinearLayout line = new LinearLayout(this);
            for (int c = 0; c < 3; c++) {
                int d = r < 3 ? r * 3 + c + 1 : (c == 1 ? 0 : -1);
                if (d < 0) { line.addView(btn("", () -> {}), weight()); continue; }
                final int digit = d;
                line.addView(btn(String.valueOf(digit), () -> irSend(LgIr.DIGITS[digit])), weight());
            }
            root.addView(line);
        }

        // ---------- Network (webOS SSAP) ----------
        header(root, "Network (TV and phone on the same LAN)");
        ipField = new EditText(this);
        ipField.setHint("TV IP address, e.g. 192.168.1.50");
        ipField.setText(prefs.getString("ip", ""));
        ipField.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(ipField);
        secure = new CheckBox(this);
        secure.setText("Secure (port 3001) — newer firmware");
        secure.setChecked(prefs.getBoolean("secure", false));
        root.addView(secure);
        row(root, btn("Connect / Pair", this::connect), btn("Forget pairing", () -> {
            prefs.edit().remove("key").apply(); log("Pairing key cleared");
        }));
        row(root, btn("NET In-Start", () -> ssap.openServiceMenu("inStart")),
                btn("NET EZ-Adjust", () -> ssap.openServiceMenu("ezAdjust")));
        row(root, btn("Home", () -> ssap.button("HOME")), btn("▲", () -> ssap.button("UP")), btn("Back", () -> ssap.button("BACK")));
        row(root, btn("◀", () -> ssap.button("LEFT")), btn("OK", () -> ssap.button("ENTER")), btn("▶", () -> ssap.button("RIGHT")));
        row(root, btn("Menu", () -> ssap.button("MENU")), btn("▼", () -> ssap.button("DOWN")), btn("Exit", () -> ssap.button("EXIT")));

        header(root, "Log");
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        root.addView(logView);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        if (!prefs.getBoolean("warned", false)) {
            new AlertDialog.Builder(this).setTitle("Read before using")
                    .setMessage("Factory (In-Start / EZ-Adjust) menus change low-level settings. A wrong value can "
                            + "disable inputs, change the panel/model profile or stop the TV booting. Photograph every "
                            + "screen before you change anything, and change only what you need.\n\n"
                            + "For hotel TVs, use the INSTALLER MENU first: casting / Screen Share settings are normally there.")
                    .setPositiveButton("I understand", (d, w) -> prefs.edit().putBoolean("warned", true).apply())
                    .show();
        }
        log(ir.available() ? "IR ready." : "This phone has no IR blaster: only the Network section will work.");
        handleAdb(getIntent());
    }

    @Override protected void onNewIntent(android.content.Intent i) { super.onNewIntent(i); handleAdb(i); }

    /**
     * ADB entry point, e.g.
     *   adb shell am start -n com.dxbmr.lgservice/.MainActivity --es cmd installer --es code 1105
     * cmd: installer | instart | ezadjust | instop | key (--es key OK|BACK|UP|DOWN|LEFT|RIGHT|SETTINGS|HOME|EXIT|POWER|0-9)
     *      digits (--es code 1234) | net_instart | net_ezadjust (--es ip 192.168.1.50 [--ez secure true])
     */
    private void handleAdb(android.content.Intent i) {
        if (i == null || !i.hasExtra("cmd")) return;
        String cmd = i.getStringExtra("cmd");
        i.removeExtra("cmd");
        android.util.Log.i("LGSR", "ADB cmd=" + cmd);
        log("ADB: " + cmd);
        switch (cmd) {
            case "installer":
                if (i.hasExtra("code")) codeField.setText(i.getStringExtra("code"));
                installerMacro(); break;
            case "instart": irSend(LgIr.IN_START); break;
            case "ezadjust": irSend(LgIr.EZ_ADJUST); break;
            case "instop": irSend(LgIr.IN_STOP); break;
            case "digits": { String c = i.getStringExtra("code"); irBg(() -> ir.typeDigits(c == null ? "" : c)); break; }
            case "key": {
                Integer code = keyCode(i.getStringExtra("key"));
                if (code == null) log("Unknown key"); else irSend(code);
                break;
            }
            case "net_instart": case "net_ezadjust": {
                String ip = i.getStringExtra("ip");
                if (ip != null) ipField.setText(ip);
                if (i.hasExtra("secure")) secure.setChecked(i.getBooleanExtra("secure", false));
                connect();
                String k = cmd.equals("net_instart") ? "inStart" : "ezAdjust";
                logView.postDelayed(() -> ssap.openServiceMenu(k), 4000); // wait for pairing
                break;
            }
            default: log("Unknown ADB cmd: " + cmd);
        }
    }

    private static Integer keyCode(String k) {
        if (k == null) return null;
        switch (k.toUpperCase()) {
            case "OK": case "ENTER": return LgIr.OK;
            case "BACK": return LgIr.BACK;   case "EXIT": return LgIr.EXIT;
            case "UP": return LgIr.UP;       case "DOWN": return LgIr.DOWN;
            case "LEFT": return LgIr.LEFT;   case "RIGHT": return LgIr.RIGHT;
            case "SETTINGS": case "MENU": return LgIr.SETTINGS;
            case "HOME": return LgIr.HOME;   case "POWER": return LgIr.POWER;
            default:
                if (k.length() == 1 && Character.isDigit(k.charAt(0))) return LgIr.DIGITS[k.charAt(0) - '0'];
                return null;
        }
    }

    private void installerMacro() {
        String code = codeField.getText().toString().trim();
        prefs.edit().putString("code", code).apply();
        log("Holding Settings ~7 s, then entering " + code + " …  (point the phone at the TV)");
        irBg(() -> {
            ir.hold(LgIr.SETTINGS, 7000);
            Thread.sleep(1200);
            ir.typeDigits(code);
            Thread.sleep(400);
            ir.send(LgIr.OK);
            runOnUiThread(() -> log("Sequence sent. If no menu appears, try holding again or use code 0413 / 9876 / 0000."));
        });
    }

    interface IrJob { void run() throws Exception; }

    private void irBg(IrJob job) {
        if (!ir.available()) { log("No IR emitter on this phone"); return; }
        new Thread(() -> { try { job.run(); } catch (Exception e) { runOnUiThread(() -> log("IR error: " + e)); } }).start();
    }

    private void irSend(int code) { irBg(() -> ir.send(code)); }

    private void connect() {
        String ip = ipField.getText().toString().trim();
        if (ip.isEmpty()) { log("Enter the TV IP"); return; }
        prefs.edit().putString("ip", ip).putBoolean("secure", secure.isChecked()).apply();
        ssap.connect(ip, secure.isChecked(), prefs.getString("key", null));
    }

    @Override public void onPaired(String key) { prefs.edit().putString("key", key).apply(); }

    @Override public void log(String msg) {
        android.util.Log.i("LGSR", msg);
        runOnUiThread(() -> logView.append(msg + "\n"));
    }

    private void header(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(17);
        t.setPadding(0, 28, 0, 8);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t);
    }

    private Button btn(String label, Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        if (label.isEmpty()) b.setEnabled(false);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void row(LinearLayout root, Button... bs) {
        LinearLayout line = new LinearLayout(this);
        for (Button b : bs) line.addView(b, weight());
        root.addView(line);
    }

    @Override protected void onDestroy() { ssap.disconnect(); super.onDestroy(); }
}

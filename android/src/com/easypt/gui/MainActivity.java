package com.easypt.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Easy-platform-tools-GUI Android 版。
 * 三种模式：OTG 主机（刷别人）/ 本机 root（管自己）/ 遥控 PC（指挥电脑上的同名程序）。
 */
public class MainActivity extends Activity {

    private static final int MODE_OTG = 0;
    private static final int MODE_LOCAL = 1;
    private static final int MODE_REMOTE = 2;

    private static final int REQ_PICK_FLASH = 1001;
    private static final int REQ_PICK_ROM = 1002;

    private static final String ACTION_USB_PERM = "com.easypt.gui.USB_PERMISSION";

    // 配色（与桌面版同一套）
    private static final int C_BG = 0xFF101318;
    private static final int C_PANEL = 0xFF1A1E26;
    private static final int C_TEXT = 0xFFD6DBE4;
    private static final int C_DIM = 0xFF8A8F9A;
    private static final int C_ACCENT = 0xFF7AB2FF;
    private static final int C_OK = 0xFF7ED6A0;
    private static final int C_ERR = 0xFFF08C8C;
    private static final int C_BTN = 0xFF262B35;
    private static final int C_RUN = 0xFF2E5C96;

    private int mode = MODE_OTG;
    private SharedPreferences sp;

    private TextView logView;
    private TextView statusView;
    private Spinner modeSpinner;
    private GridLayout actionGrid;
    private EditText input;
    private LinearLayout rootLayout;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private FastbootUsb fb;
    private UsbDevice pendingDevice;
    private int pendingPick = 0;
    private String pendingPartition = "";

    private final List<String[]> lastParts = new ArrayList<String[]>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("easypT", MODE_PRIVATE);
        mode = sp.getInt("mode", -1);

        buildUi();

        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERM), Build.VERSION.SDK_INT >= 33 ? RECEIVER_EXPORTED : 0);
        fb = new FastbootUsb(new FastbootUsb.Logger() {
            public void log(String line) { append(line); }
        });

        if (mode < 0) {
            chooseModeFirstRun();
        } else {
            applyMode(mode);
        }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(usbReceiver); } catch (Exception ignored) { }
        if (fb != null) { fb.close(); }
        super.onDestroy();
    }

    // ---------------- UI ----------------

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(C_BG);
        rootLayout.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(this);
        title.setText("Easy-platform-tools");
        title.setTextColor(C_TEXT);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        rootLayout.addView(title);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        modeRow.setPadding(0, dp(8), 0, dp(6));

        modeSpinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                new String[]{ "OTG 主机：刷别人", "本机 root：管自己", "遥控 PC" });
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(ad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        modeRow.addView(modeSpinner, lp);

        Button settings = mkButton("设置");
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSettings(); }
        });
        modeRow.addView(settings);
        rootLayout.addView(modeRow);

        statusView = new TextView(this);
        statusView.setTextColor(C_DIM);
        statusView.setTextSize(12);
        statusView.setText("就绪");
        rootLayout.addView(statusView);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(6);
        scroll.setLayoutParams(slp);
        scroll.setBackgroundColor(0xFF0B0D11);

        logView = new TextView(this);
        logView.setTextColor(C_TEXT);
        logView.setTextSize(11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(8), dp(6), dp(8), dp(6));
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        rootLayout.addView(scroll);

        actionGrid = new GridLayout(this);
        actionGrid.setColumnCount(2);
        actionGrid.setPadding(0, dp(8), 0, 0);
        rootLayout.addView(actionGrid);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, dp(8), 0, 0);

        input = new EditText(this);
        input.setHint("输入命令");
        input.setHintTextColor(C_DIM);
        input.setTextColor(C_TEXT);
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputRow.addView(input, ilp);

        Button runBtn = mkButton("执行");
        runBtn.setBackgroundColor(C_RUN);
        runBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { runTypedCommand(); }
        });
        inputRow.addView(runBtn);
        rootLayout.addView(inputRow);

        setContentView(rootLayout);
    }

    private Button mkButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(C_TEXT);
        b.setBackgroundColor(C_BTN);
        b.setPadding(dp(6), dp(4), dp(6), dp(4));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private void addAction(String label, View.OnClickListener l) {
        Button b = mkButton(label);
        b.setOnClickListener(l);
        actionGrid.addView(b);
    }

    private void applyMode(int m) {
        mode = m;
        sp.edit().putInt("mode", m).apply();
        modeSpinner.setSelection(m);
        actionGrid.removeAllViews();
        lastParts.clear();

        if (m == MODE_OTG) {
            statusView.setText("OTG：用 OTG 线连接另一台手机，让它进入 fastboot");
            addAction("连接设备", new View.OnClickListener() { public void onClick(View v) { connectUsb(); } });
            addAction("读分区表", new View.OnClickListener() { public void onClick(View v) { otgPartitions(); } });
            addAction("刷入镜像", new View.OnClickListener() { public void onClick(View v) { pickImageForFlash(); } });
            addAction("擦除分区", new View.OnClickListener() { public void onClick(View v) { otgErase(); } });
            addAction("重启到系统", new View.OnClickListener() { public void onClick(View v) { otgReboot(""); } });
            addAction("重启到 Bootloader", new View.OnClickListener() { public void onClick(View v) { otgReboot("bootloader"); } });
        } else if (m == MODE_LOCAL) {
            statusView.setText("本机：需要 root（Magisk / 内核 root）");
            addAction("检测 root", new View.OnClickListener() { public void onClick(View v) { checkRoot(); } });
            addAction("读分区表", new View.OnClickListener() { public void onClick(View v) { localPartitions(); } });
            addAction("备份基带", new View.OnClickListener() { public void onClick(View v) { backupModem(); } });
            addAction("刷入镜像", new View.OnClickListener() { public void onClick(View v) { pickImageForFlash(); } });
            addAction("重启 Recovery", new View.OnClickListener() { public void onClick(View v) { localReboot("recovery"); } });
            addAction("重启 EDL", new View.OnClickListener() { public void onClick(View v) { localReboot("edl"); } });
        } else {
            statusView.setText("遥控：手机和电脑同一局域网，电脑上运行桌面版并开启服务");
            addAction("测试连接", new View.OnClickListener() { public void onClick(View v) { remoteStatus(); } });
            addAction("读分区表", new View.OnClickListener() { public void onClick(View v) { remotePartitions(); } });
            addAction("刷入镜像", new View.OnClickListener() { public void onClick(View v) { pickImageForFlash(); } });
            addAction("重启到 Bootloader", new View.OnClickListener() { public void onClick(View v) { remoteReboot("bootloader"); } });
        }
    }

    private void chooseModeFirstRun() {
        final String[] items = { "OTG 主机：用 OTG 线刷另一台手机", "本机 root：备份/刷写自己", "遥控 PC：指挥电脑上的桌面版" };
        new AlertDialog.Builder(this)
                .setTitle("选择使用模式")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) { applyMode(which); }
                })
                .setCancelable(false)
                .show();
    }

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText host = new EditText(this);
        host.setHint("电脑地址，例如 192.168.1.10:8765");
        host.setText(sp.getString("host", "192.168.1.10:8765"));
        host.setTextSize(13);
        box.addView(host);

        final EditText token = new EditText(this);
        token.setHint("配对码（桌面版设置里显示的 6 位码）");
        token.setText(sp.getString("token", ""));
        token.setTextSize(13);
        box.addView(token);

        final EditText defaultPart = new EditText(this);
        defaultPart.setHint("默认分区，例如 boot");
        defaultPart.setText(sp.getString("part", "boot"));
        defaultPart.setTextSize(13);
        box.addView(defaultPart);

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        sp.edit()
                                .putString("host", host.getText().toString().trim())
                                .putString("token", token.getText().toString().trim())
                                .putString("part", defaultPart.getText().toString().trim())
                                .apply();
                        append("设置已保存，PC 地址：" + host.getText());
                    }
                })
                .setNeutralButton("切换模式", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { chooseModeFirstRun(); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 日志 ----------------

    private void append(final String line) {
        ui.post(new Runnable() {
            public void run() {
                String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                logView.append("[" + ts + "] " + line + "\n");
                String all = logView.getText().toString();
                if (all.length() > 120000) { logView.setText(all.substring(all.length() - 80000)); }
            }
        });
    }

    private void runBg(final Runnable r) {
        new Thread(new Runnable() {
            public void run() {
                try { r.run(); } catch (final Exception e) { append("异常：" + e); }
            }
        }).start();
    }

    private RemoteClient remote() {
        return new RemoteClient(sp.getString("host", ""), sp.getString("token", ""));
    }

    // ---------------- OTG ----------------

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) {
            if (!ACTION_USB_PERM.equals(intent.getAction())) { return; }
            UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && d != null) {
                pendingDevice = d;
                runBg(new Runnable() { public void run() { fb.open(MainActivity.this, pendingDevice); } });
            } else {
                append("USB 权限被拒绝");
            }
        }
    };

    private void connectUsb() {
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDevice d = FastbootUsb.findFastbootDevice(um);
        if (d == null) {
            append("没找到 fastboot 设备。检查：\n1) OTG 线是否支持数据传输\n2) 另一台手机是否已进入 fastboot 界面");
            return;
        }
        if (!um.hasPermission(d)) {
            Intent i = new Intent(ACTION_USB_PERM);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) { flags |= PendingIntent.FLAG_IMMUTABLE; }
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, flags);
            um.requestPermission(d, pi);
            append("等待 USB 授权…");
            return;
        }
        final UsbDevice dev = d;
        runBg(new Runnable() { public void run() { fb.open(MainActivity.this, dev); } });
    }

    private void otgPartitions() {
        runBg(new Runnable() {
            public void run() {
                if (!fb.isOpen()) { append("请先点「连接设备」"); return; }
                List<String[]> parts = fb.getPartitions();
                lastParts.clear();
                lastParts.addAll(parts);
                append("共 " + parts.size() + " 个分区");
                for (String[] p : parts) { append("  " + p[0] + "  " + p[1] + "  " + p[2]); }
            }
        });
    }

    private void otgErase() {
        final EditText et = new EditText(this);
        et.setText(sp.getString("part", "boot"));
        new AlertDialog.Builder(this).setTitle("擦除分区").setView(et)
                .setPositiveButton("擦除", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        final String part = et.getText().toString().trim();
                        runBg(new Runnable() { public void run() { fb.erase(part); } });
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void otgReboot(final String target) {
        runBg(new Runnable() {
            public void run() {
                if (!fb.isOpen()) { append("请先连接设备"); return; }
                boolean ok = fb.reboot(target);
                append(ok ? "已发出重启指令" : "重启失败");
            }
        });
    }

    // ---------------- 本机 root ----------------

    private void checkRoot() {
        runBg(new Runnable() {
            public void run() {
                boolean ok = RootShell.hasRoot();
                append(ok ? "root 可用" : "没有 root：本机模式不可用");
            }
        });
    }

    private void localPartitions() {
        runBg(new Runnable() {
            public void run() {
                List<String[]> parts = RootShell.partitions();
                lastParts.clear();
                lastParts.addAll(parts);
                append("共 " + parts.size() + " 个分区");
                for (String[] p : parts) { append("  " + p[0] + "  " + p[1]); }
            }
        });
    }

    private void backupModem() {
        final String[] wanted = { "modem", "modemst1", "modemst2", "fsg", "fsc" };
        new AlertDialog.Builder(this).setTitle("备份基带")
                .setMessage("将备份 modem / modemst1 / modemst2 / fsg / fsc 到应用目录。\n需要 root，过程勿退出。")
                .setPositiveButton("开始", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        runBg(new Runnable() {
                            public void run() {
                                File dir = new File(getExternalFilesDir(null), "backup-" + System.currentTimeMillis());
                                dir.mkdirs();
                                for (String p : wanted) {
                                    File out = new File(dir, p + ".img");
                                    RootShell.Result r = RootShell.backup(p, out);
                                    append(p + " → " + (out.exists() ? (out.length() / 1048576) + " MB" : "失败") + (r.ok ? "" : " (" + r.out + ")"));
                                }
                                append("备份目录：" + dir.getAbsolutePath());
                            }
                        });
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void localReboot(final String target) {
        runBg(new Runnable() {
            public void run() {
                RootShell.Result r = RootShell.reboot(target);
                append(r.ok ? ("已请求重启：" + target) : ("重启失败：" + r.out));
            }
        });
    }

    // ---------------- 遥控 PC ----------------

    private void remoteStatus() {
        runBg(new Runnable() {
            public void run() {
                try {
                    String s = remote().ping();
                    append("PC 在线：" + s);
                } catch (Exception e) { append("连接失败：" + e.getMessage()); }
            }
        });
    }

    private void remotePartitions() {
        runBg(new Runnable() {
            public void run() {
                try { append(remote().partitions()); }
                catch (Exception e) { append("读取失败：" + e.getMessage()); }
            }
        });
    }

    private void remoteReboot(final String target) {
        runBg(new Runnable() {
            public void run() {
                try { append(remote().reboot(target)); }
                catch (Exception e) { append("失败：" + e.getMessage()); }
            }
        });
    }

    private void runTypedCommand() {
        final String cmd = input.getText().toString().trim();
        if (cmd.length() == 0) { return; }
        input.setText("");
        append("> " + cmd);
        runBg(new Runnable() {
            public void run() {
                if (mode == MODE_LOCAL) {
                    RootShell.Result r = RootShell.run(cmd);
                    append(r.out.length() > 0 ? r.out : ("(退出码 " + (r.ok ? 0 : 1) + ")"));
                } else if (mode == MODE_REMOTE) {
                    try { append(remote().run(cmd)); } catch (Exception e) { append("失败：" + e.getMessage()); }
                } else {
                    String r = fb.command(cmd);
                    append(r);
                }
            }
        });
    }

    // ---------------- 刷入镜像（三种模式共用入口） ----------------

    private void pickImageForFlash() {
        final EditText et = new EditText(this);
        et.setText(sp.getString("part", "boot"));
        new AlertDialog.Builder(this)
                .setTitle("刷入到哪个分区")
                .setView(et)
                .setPositiveButton("选镜像", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        pendingPartition = et.getText().toString().trim();
                        sp.edit().putString("part", pendingPartition).apply();
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, REQ_PICK_FLASH);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK_FLASH || res != RESULT_OK || data == null) { return; }
        Uri uri = data.getData();
        if (uri == null) { return; }
        final byte[] img;
        try {
            img = readAll(uri);
        } catch (Exception e) {
            append("读取文件失败：" + e.getMessage());
            return;
        }
        append("已选择镜像：" + img.length / 1048576.0 + " MB，分区 " + pendingPartition);
        new AlertDialog.Builder(this)
                .setTitle("确认刷入")
                .setMessage("将把该镜像写入分区 " + pendingPartition + "\n大小 " + img.length / 1048576.0 + " MB\n\n写错分区可能无法开机，确定？")
                .setPositiveButton("刷入", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { doFlash(pendingPartition, img); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private byte[] readAll(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) { bos.write(buf, 0, n); }
        in.close();
        return bos.toByteArray();
    }

    private void doFlash(final String part, final byte[] img) {
        runBg(new Runnable() {
            public void run() {
                if (mode == MODE_OTG) {
                    if (!fb.isOpen()) { append("请先连接设备"); return; }
                    fb.flash(part, img);
                } else if (mode == MODE_LOCAL) {
                    try {
                        File tmp = new File(getCacheDir(), "flash.img");
                        OutputStream os = new java.io.FileOutputStream(tmp);
                        os.write(img);
                        os.close();
                        RootShell.Result r = RootShell.flash(part, tmp);
                        append(r.ok ? ("刷入完成：" + part + "\n" + r.out) : ("刷入失败：" + r.out));
                        tmp.delete();
                    } catch (Exception e) { append("失败：" + e.getMessage()); }
                } else {
                    try { append(remote().flash(part, img)); }
                    catch (Exception e) { append("失败：" + e.getMessage()); }
                }
            }
        });
    }
}

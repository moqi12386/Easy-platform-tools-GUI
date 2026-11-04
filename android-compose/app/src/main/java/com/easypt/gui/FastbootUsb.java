package com.easypt.gui;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 USB 主机模式下直接说 fastboot 协议（不依赖 adb / 不依赖 root）。
 * 协议：64 字节命令包 "FASTBOOT" + 命令，64 字节响应包 "OKAY"/"FAIL"/"DATA"/"INFO"。
 */
public class FastbootUsb {

    private UsbDevice device;
    private UsbDeviceConnection conn;
    private UsbInterface intf;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    public interface Logger { void log(String line); }

    private final Logger logger;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    public FastbootUsb(Logger logger) { this.logger = logger; }

    private void say(String s) { if (logger != null) { logger.log(s); } }

    /** 找一个处于 fastboot 模式的 USB 设备（interface 0xFF/0x42/0x03） */
    public static UsbDevice findFastbootDevice(UsbManager um) {
        HashMap<String, UsbDevice> list = um.getDeviceList();
        for (Map.Entry<String, UsbDevice> e : list.entrySet()) {
            UsbDevice d = e.getValue();
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface it = d.getInterface(i);
                if (it.getInterfaceClass() == 0xFF
                        && it.getInterfaceSubclass() == 0x42
                        && it.getInterfaceProtocol() == 0x03) {
                    return d;
                }
            }
        }
        return null;
    }

    public String deviceName() {
        return device == null ? "-" : (device.getManufacturerName() == null ? device.getDeviceName() : device.getManufacturerName() + " " + device.getDeviceName());
    }

    public boolean open(Context ctx, UsbDevice d) {
        close();
        if (d == null) { say("没有找到处于 fastboot 模式的 USB 设备"); return false; }
        UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        if (!um.hasPermission(d)) { say("缺少 USB 权限，请重新插拔并授权"); return false; }

        UsbInterface target = null;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface it = d.getInterface(i);
            if (it.getInterfaceClass() == 0xFF && it.getInterfaceSubclass() == 0x42 && it.getInterfaceProtocol() == 0x03) {
                target = it;
                break;
            }
        }
        if (target == null) { say("设备的接口不是 fastboot"); return false; }

        conn = um.openDevice(d);
        if (conn == null) { say("打不开 USB 设备"); return false; }
        if (!conn.claimInterface(target, true)) { say("claimInterface 失败"); conn.close(); conn = null; return false; }

        UsbEndpoint in = null, out = null;
        for (int i = 0; i < target.getEndpointCount(); i++) {
            UsbEndpoint ep = target.getEndpoint(i);
            if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) { continue; }
            if (ep.getDirection() == UsbConstants.USB_DIR_IN) { in = ep; } else { out = ep; }
        }
        if (in == null || out == null) { say("没找到 bulk 端点"); return false; }

        device = d;
        intf = target;
        epIn = in;
        epOut = out;
        say("已连接：" + d.getDeviceName() + "（VID " + Integer.toHexString(d.getVendorId()) + "）");
        return true;
    }

    public void close() {
        try { if (conn != null) { conn.releaseInterface(intf); conn.close(); } } catch (Exception ignored) { }
        conn = null;
        device = null;
    }

    public boolean isOpen() { return conn != null; }

    // ---------------- 协议 ----------------

    private static byte[] packet(String text) {
        byte[] buf = new byte[64];
        byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, buf, 0, Math.min(raw.length, 64));
        return buf;
    }

    private boolean bulkOut(byte[] data, int len) {
        int off = 0;
        int max = epOut.getMaxPacketSize();
        while (off < len) {
            int chunk = Math.min(max, len - off);
            int n = conn.bulkTransfer(epOut, slice(data, off, chunk), chunk, 5000);
            if (n <= 0) { return false; }
            off += n;
        }
        return true;
    }

    private static byte[] slice(byte[] src, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }

    private String readResponse() {
        byte[] buf = new byte[64];
        int n = conn.bulkTransfer(epIn, buf, 64, 5000);
        if (n <= 0) { return null; }
        String s = new String(buf, 0, n, StandardCharsets.US_ASCII);
        return s.trim();
    }

    /** 发一条命令，收集所有 INFO，返回最后的状态（OKAY/FAIL...） */
    public String command(String cmd) {
        if (!isOpen()) { return "FAIL:not-open"; }
        if (!bulkOut(packet("FASTBOOT" + cmd), 64)) { return "FAIL:write"; }

        StringBuilder info = new StringBuilder();
        for (int guard = 0; guard < 200; guard++) {
            String resp = readResponse();
            if (resp == null) { return "FAIL:timeout"; }
            if (resp.startsWith("INFO")) {
                String t = resp.length() > 4 ? resp.substring(4).trim() : "";
                if (t.length() > 0) { say(t); info.append(t).append('\n'); }
                continue;
            }
            if (resp.startsWith("OKAY")) { return "OKAY"; }
            if (resp.startsWith("FAIL")) {
                String t = resp.length() > 4 ? resp.substring(4).trim() : "";
                say("失败：" + t);
                return "FAIL:" + t;
            }
            if (resp.startsWith("DATA")) { return resp; }
        }
        return "FAIL:loop";
    }

    /** 把数据 download 到设备 */
    public boolean download(byte[] data) {
        String head = command(String.format("download:%08x", data.length));
        if (!head.startsWith("DATA")) { return false; }
        if (!bulkOut(data, data.length)) { return false; }
        String resp = readResponse();
        return resp != null && resp.startsWith("OKAY");
    }

    public boolean flash(String partition, byte[] data) {
        say("上传 " + partition + "（" + (data.length / 1048576.0) + " MB）…");
        if (!download(data)) { say("上传失败"); return false; }
        String r = command("flash:" + partition);
        boolean ok = r.startsWith("OKAY");
        say(ok ? ("刷入成功：" + partition) : ("刷入失败：" + partition + " " + r));
        return ok;
    }

    /** 从文件流式上传（大镜像不占内存） */
    public boolean downloadStream(java.io.InputStream in, long size, Progress p) {
        String head = command(String.format("download:%08x", size));
        if (!head.startsWith("DATA")) { return false; }
        byte[] buf = new byte[65536];
        long sent = 0;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                if (!bulkOut(buf, n)) { return false; }
                sent += n;
                if (p != null) { p.onProgress(sent, size); }
            }
        } catch (Exception e) {
            say("读取镜像失败：" + e.getMessage());
            return false;
        }
        String resp = readResponse();
        return resp != null && resp.startsWith("OKAY");
    }

    public boolean flashFile(String partition, java.io.File img, Progress p) {
        say("上传 " + partition + "（" + (img.length() / 1048576.0) + " MB）…");
        java.io.InputStream in = null;
        try {
            in = new java.io.FileInputStream(img);
            if (!downloadStream(in, img.length(), p)) { say("上传失败"); return false; }
        } catch (Exception e) {
            say("打开镜像失败：" + e.getMessage());
            return false;
        } finally {
            try { if (in != null) { in.close(); } } catch (Exception ignored) { }
        }
        String r = command("flash:" + partition);
        boolean ok = r.startsWith("OKAY");
        say(ok ? ("刷入成功：" + partition) : ("刷入失败：" + partition + " " + r));
        return ok;
    }

    public interface Progress { void onProgress(long done, long total); }

    /** 取单个 getvar 值 */
    public String getvar(String name) {
        String r = command("getvar:" + name);
        return r;
    }
    public boolean erase(String partition) {
        String r = command("erase:" + partition);
        boolean ok = r.startsWith("OKAY");
        say(ok ? ("已擦除：" + partition) : ("擦除失败：" + partition + " " + r));
        return ok;
    }

    public boolean reboot(String target) {
        return command(target == null || target.length() == 0 ? "reboot" : "reboot-" + target).startsWith("OKAY");
    }

    public boolean oem(String cmd) { return command("oem " + cmd).startsWith("OKAY"); }

    /** getvar:all → 解析 partition-size / partition-type */
    public List<String[]> getPartitions() {
        List<String[]> out = new ArrayList<String[]>();
        if (!isOpen()) { return out; }

        // 直接读原始 INFO 行，不走 command() 的日志通道
        final StringBuilder raw = new StringBuilder();

        if (!bulkOut(packet("FASTBOOTgetvar:all"), 64)) { return out; }
        for (int guard = 0; guard < 2000; guard++) {
            String resp = readResponse();
            if (resp == null) { break; }
            if (resp.startsWith("INFO")) {
                raw.append(resp.length() > 4 ? resp.substring(4).trim() : "").append('\n');
                continue;
            }
            if (resp.startsWith("OKAY")) { break; }
            if (resp.startsWith("FAIL")) { break; }
        }

        Map<String, String> sizes = new HashMap<String, String>();
        Map<String, String> types = new HashMap<String, String>();
        for (String line : raw.toString().split("\n")) {
            String t = line.trim();
            int i = t.indexOf("partition-size:");
            boolean isSize = i >= 0;
            if (!isSize) { i = t.indexOf("partition-type:"); }
            if (i < 0) { continue; }
            String rest = t.substring(i + (isSize ? "partition-size:".length() : "partition-type:".length()));
            int c = rest.indexOf(':');
            if (c <= 0) { continue; }
            String name = rest.substring(0, c).trim();
            String val = rest.substring(c + 1).trim();
            if (isSize) { sizes.put(name, val); } else { types.put(name, val); }
        }

        List<String> names = new ArrayList<String>();
        for (String k : sizes.keySet()) { if (!names.contains(k)) { names.add(k); } }
        for (String k : types.keySet()) { if (!names.contains(k)) { names.add(k); } }
        java.util.Collections.sort(names);
        for (String n : names) {
            out.add(new String[]{ n, sizes.containsKey(n) ? sizes.get(n) : "", types.containsKey(n) ? types.get(n) : "" });
        }
        return out;
    }
}

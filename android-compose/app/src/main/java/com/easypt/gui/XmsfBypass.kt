package com.easypt.gui

import android.content.Context
import rikka.shizuku.Shizuku

/**
 * 绕过小米超级岛白名单。
 *
 * 原理：小米的岛白名单校验走 XMSF（com.xiaomi.xmsf，小米服务框架）联网查询。
 * 发通知前把这个包的网络临时掐掉，校验请求发不出去 → 校验失败 → 系统放行。
 *
 * 实现方式：走系统的 shell 命令（Shizuku 以 shell 身份执行），不需要 AIDL stub：
 *   cmd connectivity set-chain3-enabled true
 *   cmd connectivity set-package-networking-enabled false com.xiaomi.xmsf
 *   cmd connectivity set-package-networking-enabled true  com.xiaomi.xmsf
 *
 * 设备实测：deny → allow 切换正常。
 * 所有失败路径都吞掉，绝不影响刷机。
 */
object XmsfBypass {

    const val XMSF_PKG = "com.xiaomi.xmsf"

    /** onCreate 里设进来 */
    var appCtx: Context? = null

    fun hasShizuku(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(code: Int = 1001) {
        runCatching {
            if (hasShizuku() && !hasPermission()) Shizuku.requestPermission(code)
        }
    }

    /** 以 shell 身份跑一条命令，返回输出 */
    private fun shell(vararg cmd: String): String {
        // Shizuku.newProcess 在 13.1.5 里是 private，反射拿一下
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf(*cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out.trim()
    }

    /** 开/关 XMSF 的网络；返回 null 表示成功，否则返回原因 */
    private fun setXmsfNetworking(enabled: Boolean): String? = runCatching {
        // 链没开的话 deny 位不生效
        shell("cmd", "connectivity", "set-chain3-enabled", "true")
        shell(
            "cmd", "connectivity", "set-package-networking-enabled",
            enabled.toString(), XMSF_PKG
        )
        val state = shell("cmd", "connectivity", "get-package-networking-enabled", XMSF_PKG)
        val want = if (enabled) "allow" else "deny"
        if (state.contains(want)) null else "状态不对：$state"
    }.getOrElse { "${it.javaClass.simpleName} ${it.message}" }

    /**
     * 掐断 XMSF 网络 → 执行 block → 恢复。
     * 任何一步失败都直接执行 block，绝不抛给调用方。
     */
    fun <T> withXmsfBlocked(ctx: Context, holdMs: Long = 150, block: () -> T): T {
        if (!hasShizuku() || !hasPermission()) return block()
        if (setXmsfNetworking(false) != null) return block()

        return try {
            runCatching { Thread.sleep(holdMs) }
            block()
        } finally {
            runCatching { setXmsfNetworking(true) }
        }
    }

    /** 自检：断一次再恢复 */
    fun selfTest(ctx: Context): String = runCatching {
        if (!hasShizuku()) return "Shizuku 未运行"
        if (!hasPermission()) return "Shizuku 未授权"
        val off = setXmsfNetworking(false)
        if (off != null) return "断网失败：$off"
        runCatching { Thread.sleep(120) }
        val on = setXmsfNetworking(true)
        if (on != null) return "恢复失败：$on"
        "OK 断网→恢复成功"
    }.getOrElse { "失败：${it.javaClass.simpleName} ${it.message}" }

    /** 能否发「实况通知」（Android 16+）。false 时系统一律渲染成普通通知 */
    fun canPromote(ctx: Context): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT < 36) return false
        ctx.getSystemService(android.app.NotificationManager::class.java)
            ?.canPostPromotedNotifications() == true
    }.getOrDefault(false)

    /** 跳到本应用的通知设置，用户可以在那里打开「提升通知 / 实况通知」 */
    fun openPromotedSettings() {
        val c = appCtx ?: return
        runCatching {
            val i = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        }
    }

    /** 把自检结果写进 App 私有目录，方便 adb 拉取 */
    fun dumpToFile() {
        val c = appCtx ?: return
        runCatching {
            val nm = c.getSystemService(android.app.NotificationManager::class.java)
            val promoted = if (android.os.Build.VERSION.SDK_INT >= 36)
                runCatching { nm?.canPostPromotedNotifications() }.getOrNull().toString()
            else "API<36"
            java.io.File(c.getExternalFilesDir(null), "xmsf.txt")
                .writeText(
                    "selfTest: " + selfTest(c) +
                        "\ncanPostPromotedNotifications: " + promoted +
                        "\nSDK: " + android.os.Build.VERSION.SDK_INT
                )
        }
    }
}

package com.easypt.gui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

/**
 * 线刷进度前台服务 + 小米焦点通知（超级岛）。
 *
 * 关键事实（真机 dumpsys 验证过）：
 *  - 系统读的是通知 extras 里的 miui.focus.param(String/JSON) 和 miui.focus.pics(Bundle)
 *  - 载荷必须带图标（tickerPic / picProfile 指向 miui.focus.pic_*，且 pics 里真有图）
 *  - 必须有大岛区域 bigIslandArea，否则不上岛
 *  - 颜色是 8 位 ARGB，例如 #FF3B82F6
 *  - 本机 canShowFocus=true，不需要 root / Shizuku / 白名单
 */
class FlashService : Service() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_STEP = "step"
        const val EXTRA_MAX = "max"
        const val EXTRA_COLOR = "color"
        const val EXTRA_STOP = "stop"
        const val EXTRA_SHORT = "short"
        const val EXTRA_PART = "part"

        const val CHANNEL = "easypt_flash"
        const val ID = 4101

        /** 通道要在刷机之前就建好：首次运行时现建通道，系统不会马上把它当实况通知 */
        fun ensureChannel(ctx: android.content.Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            runCatching {
                val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
                val ch = NotificationChannel(CHANNEL, "线刷进度", NotificationManager.IMPORTANCE_DEFAULT)
                ch.setShowBadge(false)
                nm.createNotificationChannel(ch)
            }
        }
    }

    // 样本是媒体类应用（business=lyric_display），系统可能按类目校验。
    // 挂一个 MediaSession 试试是不是这道门。
    private var mediaSession: android.media.session.MediaSession? = null

    private fun ensureSession(title: String, text: String) {
        val s = mediaSession ?: runCatching {
            android.media.session.MediaSession(this, "EasyPT").also {
                it.isActive = true
                mediaSession = it
            }
        }.getOrNull() ?: return
        runCatching {
            s.setMetadata(
                android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, text)
                    .build()
            )
        }
    }

    private var wake: android.os.PowerManager.WakeLock? = null

    /**
     * 本次前台服务是否已经发过通知。
     *
     * 实测（HyperOS 3 / Android 17）：安装后第一次发实况通知时，SystemUI 的焦点插件
     * 还没缓存这个包，直接把通知吞掉——dumpsys notification 里连记录都没有，
     * 而 startForeground() 却返回成功。跑过一遍之后插件记住了（authResult=true），
     * 之后每次都正常。所以第一次先发一条普通通知把它「喂」一下。
     */
    private var foregrounded = false

    override fun onDestroy() {
        foregrounded = false
        runCatching { if (wake?.isHeld == true) wake?.release() }
        wake = null
        runCatching { mediaSession?.release() }
        mediaSession = null
        super.onDestroy()
    }

    /** 调试：把服务侧的每一步写文件，便于定位「第一次没有通知」 */
    private fun dbg(s: String) {
        runCatching {
            java.io.File(filesDir, "flashsvc.log").appendText(
                java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()) + " " + s + "\n"
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.getBooleanExtra(EXTRA_STOP, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        // 保活：刷写/命令期间持有 partial wakelock，避免灭屏后 CPU 休眠导致写盘中断
        runCatching {
            if (wake == null) {
                val pm = getSystemService(android.os.PowerManager::class.java)
                wake = pm?.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "EasyPT:flash"
                )?.apply { setReferenceCounted(false); acquire(30 * 60 * 1000L) }
            }
        }
        val n = buildNotification(intent)
        // 首次：先发一条普通通知（不带实况三件套）把 SystemUI 的焦点插件喂熟，
        // 否则第一条实况通知会被它静默吞掉（详见 foregrounded 的注释）。
        if (!foregrounded) {
            dbg("首次：发预热通知")
            runCatching {
                getSystemService(NotificationManager::class.java)
                    ?.notify(ID, primeNotification(intent))
            }
            runCatching { Thread.sleep(200) }
        }
        // 关键一步：发通知的瞬间掐断 XMSF 网络，让小米的岛白名单校验失败而放行。
        // 没有 Shizuku 授权时 withXmsfBlocked 会直接执行 block，不影响正常流程。
        dbg("准备 startForeground")
        try {
            XmsfBypass.withXmsfBlocked(applicationContext, 150) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(ID, n)
                }
            }
            dbg("startForeground 完成")
            foregrounded = true
        } catch (e: Throwable) {
            dbg("startForeground 抛异常: " + e.javaClass.simpleName + " " + e.message)
            // 至少把通知发出来，别让用户什么都看不到
            runCatching { getSystemService(NotificationManager::class.java)?.notify(ID, n) }
        }
        return START_STICKY
    }

    /** 把分区名画进图标位（胶囊左边那个圈里显示的就是小图标） */
    private fun labelIcon(txt: String): android.graphics.drawable.Icon {
        val size = 96
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 46f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val label = txt.lowercase()   // 小写可读性更好，和分区名原样一致
        // 全名塞进图标：字号自动缩到画布宽度内（最小 10px）
        var fs = 46f
        p.textSize = fs
        while (p.measureText(label) > size - 6 && fs > 10f) {
            fs -= 2f
            p.textSize = fs
        }
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(label, size / 2f, y, p)
        return android.graphics.drawable.Icon.createWithBitmap(bmp)
    }

    private fun buildNotification(intent: Intent): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 实况通知要求通知本身是「会被展示的」，LOW 通道可能被排除在提升之外，改 DEFAULT
            val ch = NotificationChannel(CHANNEL, "线刷进度", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "线刷中"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val step = intent.getIntExtra(EXTRA_STEP, 0)
        val max = intent.getIntExtra(EXTRA_MAX, 1)
        val color = intent.getIntExtra(EXTRA_COLOR, 0xFF3B82F6.toInt())
        val pct = if (max > 0) (step * 100 / max).coerceIn(0, 100) else 0
        // 胶囊上直接显示日志最新一行（实时进度），空间小所以截断
        val shortText = (intent.getStringExtra(EXTRA_SHORT) ?: text).trim().ifEmpty { title }.take(28)
        // 系统要求 8 位 ARGB
        val hex = String.format("#FF%06X", 0xFFFFFF and color)

        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        // 图标位试过画分区名：被系统遮罩染成黑块，而且和文字重复，撤回成默认图标
        val partForIcon = (intent.getStringExtra(EXTRA_PART) ?: "").trim()
        if (partForIcon.isNotEmpty()) {
            runCatching { b.setSmallIcon(labelIcon(partForIcon)) }
        } else {
            b.setSmallIcon(android.R.drawable.stat_sys_download)
        }
        b.setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // 必须 PUBLIC：PRIVATE 时系统不往实况通知/胶囊渲染
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(color)

        // 焦点通知 extras 已移除：它与 AOSP 实况通知是两条管线，
        // 带了 miui.focus.* 系统就按焦点通知处理，不会提升成实况通知

        // 媒体样式试过了：只会变成带 00:00 播放条的媒体卡片，不是岛，已弃用。
        // 恢复成正常的进度通知。
        if (Build.VERSION.SDK_INT >= 36) {
            runCatching {
                b.setStyle(
                    Notification.ProgressStyle()
                        .setProgress(max.coerceAtLeast(1))
                        .setProgress(if (step in 0..max) step else 0)
                )
            }
            // 实况通知三件套：进度样式 + 状态栏短文字 + 请求提升
            // 胶囊文字：setShortCriticalText 没生效（dumpsys 里是 null），
            // 直接把系统读的那个键写进 extras
            runCatching { b.setShortCriticalText(shortText) }
            runCatching { b.extras.putCharSequence("android.shortCriticalText", shortText) }
            runCatching { b.setRequestPromotedOngoing(true) }
        }
        val n = b.build()
        // 在最终对象上再写一次：Builder.extras 的写入会被 build() 覆盖
        runCatching { n.extras.putCharSequence("android.shortCriticalText", shortText) }
        return n
    }

    /**
     * 预热用的普通通知：和实况那条同 id、同标题，但**不带** ProgressStyle /
     * shortCriticalText / requestPromotedOngoing。
     *
     * 唯一作用是在第一次 startForeground 之前让 SystemUI 的焦点插件认识这个包，
     * 随后 200ms 内就会被真正的实况通知替换掉，用户看不到它。
     */
    private fun primeNotification(intent: Intent): Notification {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "准备中"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val color = intent.getIntExtra(EXTRA_COLOR, 0xFF3B82F6.toInt())
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        b.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(color)
        return b.build()
    }
}

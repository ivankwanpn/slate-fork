package moe.shizuku.manager.phonefix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import moe.shizuku.manager.R

/**
 * A minimal "null VPN": only the packages passed in [EXTRA_PACKAGES] are routed into
 * the tunnel, and every packet read from the tunnel is discarded. This blocks both
 * foreground and background traffic, over mobile data and Wi-Fi, without touching any
 * other app.
 */
class NetBlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "moe.shizuku.manager.phonefix.VPN_START"
        const val ACTION_STOP = "moe.shizuku.manager.phonefix.VPN_STOP"
        const val EXTRA_PACKAGES = "packages"
        const val CHANNEL_ID = "slate_vpn"
        const val NOTIFICATION_ID = 41

        @Volatile
        var running = false
            private set
    }

    private var tun: ParcelFileDescriptor? = null
    private var pump: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> {
                val packages = intent?.getStringArrayListExtra(EXTRA_PACKAGES) ?: arrayListOf()
                startVpn(packages)
            }
        }
        return START_STICKY
    }

    private fun startVpn(packages: List<String>) {
        stopVpn()
        if (packages.isEmpty()) return

        val builder = Builder()
            .setSession("Slate 網路封鎖")
            .setBlocking(true)
            .addAddress("10.111.0.1", 32)
            .addRoute("0.0.0.0", 0)
        try {
            builder.addAddress("fd00:1:2:3::1", 128)
            builder.addRoute("::", 0)
        } catch (t: Throwable) {
            // IPv6 not available; IPv4 blocking still applies.
        }
        for (pkg in packages) {
            try {
                builder.addAllowedApplication(pkg)
            } catch (t: Throwable) {
                // Package missing or not eligible; skip it.
            }
        }

        val fd = try {
            builder.establish()
        } catch (t: Throwable) {
            null
        } ?: return

        tun = fd
        running = true
        try {
            startForeground(NOTIFICATION_ID, buildNotification(packages.size))
        } catch (t: Throwable) {
        }
        pump = Thread {
            val input = FileInputStream(fd.fileDescriptor)
            val buffer = ByteArray(32767)
            while (running) {
                try {
                    val n = input.read(buffer)
                    if (n < 0) break
                    // Discard: no forwarding, so the app sees no network at all.
                } catch (t: Throwable) {
                    break
                }
            }
        }.also { it.start() }
    }

    private fun stopVpn() {
        running = false
        try {
            pump?.interrupt()
        } catch (t: Throwable) {
        }
        pump = null
        try {
            tun?.close()
        } catch (t: Throwable) {
        }
        tun = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (t: Throwable) {
        }
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun buildNotification(count: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "網路封鎖",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PhoneFixActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_slate)
            .setContentTitle("Slate 網路封鎖")
            .setContentText("已封鎖 $count 個應用程式的網路")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}

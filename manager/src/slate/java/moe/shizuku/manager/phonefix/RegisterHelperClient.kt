package moe.shizuku.manager.phonefix

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Client for the unprotected Binder exported by the preinstalled carrier DM component.
 *
 * The component declares
 *
 *     <service android:name=".service.RegisterHelperService" android:exported="true">
 *         <intent-filter>
 *             <action android:name="com.mobiletools.systemhelper.service.IRegisterHelperService" />
 *         </intent-filter>
 *     </service>
 *
 * with no permission, and its Stub only calls enforceInterface(). Transaction 5 is
 * setServerAddress(String), which writes the "serverAddress" key into the app's
 * SharedPreferences. Nothing else in the app writes that key, so the value survives
 * reboots and is not reset by enabling/disabling the package.
 */
object RegisterHelperClient {

    const val TARGET_PACKAGE = "com.mobiletools.systemhelper"
    const val DEFAULT_CARRIER_URL = "http://dm.wo.com.cn:18080/register"
    const val LOCAL_SINKHOLE_URL = "http://127.0.0.1:18080/register"

    private const val SERVICE_ACTION = "com.mobiletools.systemhelper.service.IRegisterHelperService"
    private const val SERVICE_CLASS = "com.mobiletools.systemhelper.service.RegisterHelperService"
    private const val INTERFACE_DESCRIPTOR = "com.mobiletools.systemhelper.service.IRegisterHelperService"
    private const val TRANSACTION_SET_SERVER_ADDRESS = 5

    data class Result(val ok: Boolean, val message: String)

    fun isPackageEnabled(context: Context): Boolean {
        return try {
            val state = context.packageManager.getApplicationEnabledSetting(TARGET_PACKAGE)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Binds the exported service and sends transaction 5.
     *
     * Must not run on the main thread: it blocks until the service connects.
     */
    fun setServerAddress(context: Context, url: String, timeoutMs: Long = 6000L): Result {
        if (!isPackageEnabled(context)) {
            return Result(
                false,
                "目標目前是停用狀態，無法綁定。請先到「還原啟用」把它啟用，套用完位址後可以再停用；偏好設定會保留。"
            )
        }

        val latch = CountDownLatch(1)
        var binder: IBinder? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                latch.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                latch.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                latch.countDown()
            }
        }

        var bound = false
        return try {
            val intent = Intent(SERVICE_ACTION).setComponent(
                ComponentName(TARGET_PACKAGE, SERVICE_CLASS)
            )
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                return Result(false, "bindService 失敗：目標未啟用，或服務不存在。")
            }

            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS) || binder == null) {
                return Result(false, "等待服務連線逾時（${timeoutMs}ms）。")
            }

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(INTERFACE_DESCRIPTOR)
                data.writeString(url)
                val ok = binder!!.transact(TRANSACTION_SET_SERVER_ADDRESS, data, reply, 0)
                Result(ok, if (ok) "已送出 serverAddress = $url" else "transact 回傳 false")
            } finally {
                reply.recycle()
                data.recycle()
            }
        } catch (t: Throwable) {
            Result(false, "例外：$t")
        } finally {
            if (bound) {
                try {
                    context.unbindService(connection)
                } catch (_: Throwable) {
                }
            }
        }
    }
}

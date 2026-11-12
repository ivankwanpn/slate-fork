package moe.shizuku.manager.phonefix

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import moe.shizuku.api.BinderContainer
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Slate is a Shizuku *client*: the official Shizuku app owns the server and sends its binder to
 * `<package>.shizuku`. This provider receives that binder and also handles the extra call the
 * server makes when a user service is requested.
 */
class SlateProvider : ShizukuProvider() {

    companion object {
        private const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
        private const val METHOD_SEND_USER_SERVICE = "sendUserService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        return super.onCreate()
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) {
            return null
        }
        if (method != METHOD_SEND_USER_SERVICE) {
            return super.call(method, arg, extras)
        }

        return try {
            extras.classLoader = BinderContainer::class.java.classLoader

            val token = extras.getString(USER_SERVICE_ARG_TOKEN) ?: return null
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            val container = extras.getParcelable<BinderContainer>(EXTRA_BINDER) ?: return null
            val userServiceBinder = container.binder ?: return null

            val latch = CountDownLatch(1)
            var reply: Bundle? = Bundle()

            val listener = object : Shizuku.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    try {
                        val args = Bundle()
                        args.putString(USER_SERVICE_ARG_TOKEN, token)
                        Shizuku.attachUserService(userServiceBinder, args)

                        val service = Shizuku.getBinder()
                        if (service != null) {
                            reply?.putParcelable(EXTRA_BINDER, BinderContainer(service))
                        }
                    } catch (t: Throwable) {
                        reply = null
                    }

                    Shizuku.removeBinderReceivedListener(this)
                    latch.countDown()
                }
            }

            Shizuku.addBinderReceivedListenerSticky(listener, mainHandler)

            try {
                latch.await(5, TimeUnit.SECONDS)
                reply
            } catch (t: InterruptedException) {
                null
            }
        } catch (t: Throwable) {
            null
        }
    }
}

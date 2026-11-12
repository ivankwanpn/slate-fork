package moe.shizuku.manager.phonefix

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneFixActivity : Activity() {

    private data class Pkg(
        val name: String,
        val version: String,
        val enabled: Boolean,
        val system: Boolean,
        val uid: Int,
        val granted: List<String>,
        val requested: List<String>,
        val exported: List<String>
    )

    private lateinit var content: FrameLayout
    private lateinit var navBar: LinearLayout
    private var shell: IShellService? = null
    private var tab = 0
    private var pkgs: List<Pkg> = emptyList()
    private val logs = mutableListOf<String>()
    private val target = "com.mobiletools.systemhelper"

    private val bg = Color.parseColor("#F5F7FA")
    private val cardBg = Color.WHITE
    private val ink = Color.parseColor("#1F2933")
    private val muted = Color.parseColor("#6B7A8F")
    private val accent = Color.parseColor("#3E5C76")
    private val warn = Color.parseColor("#B45309")

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("slate")
            .debuggable(true)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shell = IShellService.Stub.asInterface(binder)
            showTab(tab)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            showTab(tab)
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grant ->
        if (grant == android.content.pm.PackageManager.PERMISSION_GRANTED) bindService() else showTab(tab)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(cardBg)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        root.addView(navBar, LinearLayout.LayoutParams(-1, dp(60)))
        root.fitsSystemWindows = true
        setContentView(root)
        buildNav()
        scan()
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        Shizuku.addRequestPermissionResultListener(permissionListener)
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED && shell == null) bindService()
        } catch (t: Throwable) { }
        showTab(tab)
    }

    override fun onPause() {
        super.onPause()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    // ---------- helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float = 14f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(cardBg)
        }
    }

    private fun action(t: String, primary: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = t
            isAllCaps = false
            textSize = 14f
            setTextColor(if (primary) Color.WHITE else accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (primary) accent else Color.parseColor("#E4EAF1"))
            }
            setOnClickListener { onClick() }
        }

    private fun scroll(v: View): ScrollView = ScrollView(this).apply { addView(v) }

    private fun pad(v: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(24))
        addView(v)
    }

    private fun gap(v: View, h: Int = 12) {
        (v as LinearLayout).addView(View(this), LinearLayout.LayoutParams(0, dp(h)))
    }

    // ---------- navigation ----------

    private fun buildNav() {
        navBar.removeAllViews()
        val items = listOf("首頁", "元件", "記錄", "設定", "說明")
        items.forEachIndexed { i, name ->
            val tv = TextView(this).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(if (i == tab) accent else muted)
                if (i == tab) setTypeface(typeface, Typeface.BOLD)
                setOnClickListener { tab = i; showTab(i) }
            }
            navBar.addView(tv, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun showTab(i: Int) {
        tab = i
        buildNav()
        content.removeAllViews()
        content.addView(
            when (i) {
                0 -> homeScreen()
                1 -> componentsScreen()
                2 -> logScreen()
                3 -> settingsScreen()
                else -> aboutScreen()
            }
        )
    }

    // ---------- screens ----------

    private fun homeScreen(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        box.addView(label("Slate", 26f, ink, true))
        box.addView(label("裝置元件檢查與管理", 13f, muted))
        gap(box, 18)

        val status = card()
        val running = try { Shizuku.pingBinder() } catch (t: Throwable) { false }
        val granted = try { Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED } catch (t: Throwable) { false }
        status.addView(label(if (running) "服務已啟動" else "服務未啟動", 18f, if (running) accent else warn, true))
        status.addView(label(
            if (!running) "需要先啟動官方 Shizuku 服務，才能執行停用、還原等操作。"
            else if (!granted) "服務正在執行，但尚未授權給 Slate。"
            else if (shell == null) "已授權，正在建立連線。"
            else "已連線，可以執行操作。",
            13f, muted
        ))
        gap(status, 10)
        status.addView(action(if (!running) "開啟 Shizuku" else if (!granted) "授權 Slate" else "重新連線", true) {
            when {
                !running -> openShizuku()
                !granted -> Shizuku.requestPermission(0)
                else -> bindService()
            }
        })
        box.addView(status)
        gap(box, 14)

        val found = pkgs
        val summary = card()
        summary.addView(label("掃描結果", 15f, ink, true))
        summary.addView(label(
            if (found.isEmpty()) "未發現需要關注的元件。"
            else "發現 " + found.size + " 個元件，其中 " + found.count { !it.enabled } + " 個已停用。",
            13f, muted
        ))
        if (found.isNotEmpty()) {
            gap(summary, 8)
            for (p in found.take(3)) {
                val row = label(p.name, 13f, ink)
                summary.addView(row)
                summary.addView(label("v" + p.version + " · " + (if (p.system) "系統應用" else "使用者應用") + " · " + (if (p.enabled) "執行中" else "已停用"), 12f, muted))
            }
        }
        gap(summary, 10)
        summary.addView(action("立即掃描") { scan(); showTab(0) })
        box.addView(summary)
        return scroll(box)
    }

    private fun componentsScreen(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        box.addView(label("元件", 24f, ink, true))
        box.addView(label("點選任一元件查看權限、對外介面與可執行的動作。", 13f, muted))
        gap(box, 14)
        if (pkgs.isEmpty()) {
            box.addView(label("未發現符合條件的元件。", 14f, muted))
            return scroll(box)
        }
        for (p in pkgs) {
            val c = card()
            c.addView(label(p.name, 14f, ink, true))
            c.addView(label("v" + p.version + " · " + (if (p.system) "系統應用" else "使用者應用"), 12f, muted))
            val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(8)) }
            chips.addView(chip(if (p.enabled) "執行中" else "已停用", if (p.enabled) warn else accent))
            chips.addView(chip(p.granted.size.toString() + " 項權限", muted))
            chips.addView(chip(p.exported.size.toString() + " 個對外元件", muted))
            c.addView(chips)
            c.addView(action("查看詳情") { detail(p) })
            box.addView(c)
            gap(box, 12)
        }
        return scroll(box)
    }

    private fun chip(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(color)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor("#EDF1F6"))
        }
        (layoutParams as? LinearLayout.LayoutParams)?.rightMargin = dp(6)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.rightMargin = dp(6)
        layoutParams = lp
    }

    private fun detail(p: Pkg) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        box.addView(action("‹ 返回") { showTab(1) })
        gap(box, 12)
        box.addView(label(p.name, 20f, ink, true))
        box.addView(label("v" + p.version + " · " + (if (p.system) "系統應用" else "使用者應用") + " · UID " + p.uid, 12f, muted))

        gap(box, 14)
        val perms = card()
        perms.addView(label("權限", 15f, ink, true))
        val groups = listOf(
            "定位" to listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "ACCESS_MEDIA_LOCATION"),
            "電話" to listOf("READ_PHONE_STATE", "READ_PRECISE_PHONE_STATE", "READ_PRIVILEGED_PHONE_STATE", "CALL_PHONE", "READ_CALL_LOG", "READ_PHONE_NUMBERS"),
            "儲存" to listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO"),
            "網路與系統" to listOf("INTERNET", "ACCESS_NETWORK_STATE", "CHANGE_WIFI_STATE", "ACCESS_WIFI_STATE", "QUERY_ALL_PACKAGES", "PACKAGE_USAGE_STATS", "RECEIVE_BOOT_COMPLETED", "SCHEDULE_EXACT_ALARM", "FOREGROUND_SERVICE")
        )
        val req = p.requested.map { it.substringAfterLast('.') }
        val got = p.granted.map { it.substringAfterLast('.') }.toSet()
        val shown = mutableSetOf<String>()
        for ((title, keys) in groups) {
            val rows = req.filter { it in keys }
            if (rows.isEmpty()) continue
            shown.addAll(rows)
            gap(perms, 8)
            perms.addView(label(title, 13f, accent, true))
            for (r in rows) {
                val ok = r in got
                perms.addView(permRow(r, ok))
            }
        }
        val rest = req.filter { it !in shown }
        if (rest.isNotEmpty()) {
            gap(perms, 8)
            perms.addView(label("其他", 13f, accent, true))
            for (r in rest) {
                val ok = r in got
                perms.addView(permRow(r, ok))
            }
        }
        box.addView(perms)

        gap(box, 12)
        val exp = card()
        exp.addView(label("對外開放的元件", 15f, ink, true))
        exp.addView(label(if (p.exported.isEmpty()) "無" else p.exported.joinToString("\n"), 12f, if (p.exported.isEmpty()) muted else warn))
        box.addView(exp)

        gap(box, 12)
        val acts = card()
        acts.addView(label("可以做的事", 15f, ink, true))
        gap(acts, 6)
        acts.addView(action(if (p.enabled) "停用" else "還原啟用") {
            confirm(if (p.enabled) "停用後，這個元件不會再執行。系統更新後可能恢復，可隨時還原。" else "將重新啟用這個元件。") {
                run(if (p.enabled) "pm disable-user --user 0 " + p.name else "pm enable " + p.name, p.name)
            }
        })
        gap(acts, 6)
        acts.addView(action("對目前使用者卸載") {
            confirm("對目前使用者移除。系統分區的檔案仍在，更新或恢復出廠後可能回來；可用「還原」重新安裝。") {
                run("pm uninstall -k --user 0 " + p.name, p.name)
            }
        })
        gap(acts, 6)
        acts.addView(label(
            "只撤 runtime 權限；INTERNET、QUERY_ALL_PACKAGES、READ_PRIVILEGED_PHONE_STATE 這類安裝時權限撤不掉，GET_USAGE_STATS 改用 AppOps 攔。",
            11f, muted
        ))
        gap(acts, 6)
        acts.addView(action("撤銷敏感權限（保守）") {
            confirm("撤銷定位、電話狀態、儲存、相機、麥克風、聯絡人與使用統計；不會動 SMS／藍牙。") {
                saveSnapshot(p)
                runAll(buildPermissionCommands(p, false), p.name)
            }
        })
        gap(acts, 6)
        acts.addView(action("撤銷敏感權限（含 SMS／藍牙）") {
            confirm("額外撤銷 SEND_SMS、BLUETOOTH_SCAN/CONNECT；可能影響電信自動註冊與藍牙功能。") {
                saveSnapshot(p)
                runAll(buildPermissionCommands(p, true), p.name)
            }
        })
        gap(acts, 6)
        val snapshot = loadSnapshot(p)
        if (snapshot != null) {
            acts.addView(label(
                "撤銷前快照：${snapshot.first.size} 項（" +
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(snapshot.second)) + "）",
                11f, muted
            ))
            gap(acts, 6)
        }
        acts.addView(action("還原權限（grant + appops default）") {
            val granted = snapshot?.first
            val message = if (granted != null) {
                "將只 grant 撤銷前已授予的 ${granted.size} 項權限，AppOps 設回 default。"
            } else {
                "找不到撤銷前快照，將 grant 全部 requested 的 runtime 權限（可能多授權）。"
            }
            confirm(message) {
                val perms = granted ?: p.requested.filter { isDangerousPermission(it) }
                runAll(buildRestoreCommands(p, perms), p.name)
            }
        })
        if (p.name == "com.vivo.devicereg") {
            gap(acts, 6)
            acts.addView(label(
                "DeviceReg：撤定位可讓 Wi-Fi／基地台採集變空；GET_USAGE_STATS 可讓使用時長／流量變空；SMS／藍牙撤銷可能影響電信註冊。",
                11f, warn
            ))
        }
        box.addView(acts)
        if (p.name == RegisterHelperClient.TARGET_PACKAGE) {
            gap(box, 12)
            box.addView(redirectCard())
        }
        content.removeAllViews()
        content.addView(scroll(box))
    }

    private fun logScreen(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        box.addView(label("記錄", 24f, ink, true))
        gap(box, 12)
        val c = card()
        c.addView(label(if (logs.isEmpty()) "尚無操作記錄。" else logs.reversed().joinToString("\n\n"), 12f, muted))
        box.addView(c)
        return scroll(box)
    }

    private fun settingsScreen(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        box.addView(label("設定", 24f, ink, true))
        gap(box, 12)
        val c = card()
        c.addView(label("啟動方式", 15f, ink, true))
        c.addView(label(
            "1. 安裝官方 Shizuku（moe.shizuku.privileged.api）\n" +
            "2. 在 Shizuku 內用無線偵錯啟動服務\n" +
            "3. 回到 Slate，按「授權 Slate」或「重新連線」\n\n" +
            "重新開機後 Shizuku 也需要重新啟動，這是 Android 的限制。", 13f, muted))
        gap(c, 10)
        c.addView(action("開啟 Shizuku") { openShizuku() })
        box.addView(c)

        gap(box, 12)
        val about = card()
        about.addView(label("關於", 15f, ink, true))
        about.addView(label("Slate\n本工具只在你按下按鈕時執行操作，不背景常駐、不上傳資料。", 12f, muted))
        box.addView(about)
        return scroll(box)
    }

    // ---------- Shizuku client ----------

    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (t: Throwable) {
                // ignore
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle("未安裝 Shizuku")
                .setMessage("Slate 需要官方 Shizuku（moe.shizuku.privileged.api）才能取得 shell 權限。請先安裝並啟動 Shizuku。")
                .setPositiveButton("好", null)
                .show()
        }
    }

    // ---------- carrier DM redirect ----------

    private fun redirectCard(): LinearLayout {
        val c = card()
        c.addView(label("上報位址（實驗性）", 15f, ink, true))
        c.addView(label(
            "目標 App 的 serverAddress 存在它自己的 SharedPreferences。" +
                "寫入後跨開機保留；vivo daemon 只會改套件啟用狀態，不會清掉這個值。\n\n" +
                "需要目標處於啟用狀態才能綁定；套用完可以再停用。", 12f, muted
        ))
        gap(c, 8)
        val enabled = RegisterHelperClient.isPackageEnabled(this)
        c.addView(label(
            if (enabled) "目前狀態：可綁定" else "目前狀態：停用，請先按上面的「還原啟用」",
            12f, if (enabled) accent else warn
        ))
        gap(c, 8)
        val input = EditText(this).apply {
            setText(RegisterHelperClient.LOCAL_SINKHOLE_URL)
            textSize = 13f
        }
        c.addView(input)
        gap(c, 8)
        c.addView(action("套用這個位址", true) { applyRedirect(input.text.toString()) })
        gap(c, 6)
        c.addView(action("用本機黑洞") {
            input.setText(RegisterHelperClient.LOCAL_SINKHOLE_URL)
            applyRedirect(RegisterHelperClient.LOCAL_SINKHOLE_URL)
        })
        gap(c, 6)
        c.addView(action("還原電信商位址") {
            input.setText(RegisterHelperClient.DEFAULT_CARRIER_URL)
            applyRedirect(RegisterHelperClient.DEFAULT_CARRIER_URL)
        })
        return c
    }

    private fun applyRedirect(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            AlertDialog.Builder(this)
                .setTitle("位址格式")
                .setMessage("請輸入以 http:// 或 https:// 開頭的位址。")
                .setPositiveButton("好", null)
                .show()
            return
        }
        confirm("將寫入 " + RegisterHelperClient.TARGET_PACKAGE + " 的 serverAddress：\n\n" + trimmed) {
            Thread {
                val result = RegisterHelperClient.setServerAddress(this@PhoneFixActivity, trimmed)
                val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
                logs.add(stamp + "  serverAddress\n" + result.message)
                runOnUiThread { showTab(2) }
            }.start()
        }
    }

    // ---------- about ----------

    private val nonRevocable = setOf(
        "INTERNET", "QUERY_ALL_PACKAGES", "READ_PRIVILEGED_PHONE_STATE",
        "READ_PRECISE_PHONE_STATE", "RECEIVE_BOOT_COMPLETED", "FOREGROUND_SERVICE",
        "SCHEDULE_EXACT_ALARM", "ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE",
        "CHANGE_WIFI_STATE", "ACCESS_LOCATION_EXTRA_COMMANDS"
    )

    private fun permRow(name: String, granted: Boolean): TextView {
        val fixed = granted && name in nonRevocable
        val text = (if (granted) "\u2713  " else "\u2717  ") + name + (if (fixed) "   （系統權限，無法撤銷）" else "")
        return label(text, 12f, if (granted && !fixed) ink else muted)
    }

    private fun aboutScreen(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        box.addView(label("說明", 24f, ink, true))
        box.addView(label("開發與研究過程中發現的問題，以及目前做不到的事。", 13f, muted))
        gap(box, 14)

        val found = card()
        found.addView(label("發現的問題", 15f, ink, true))
        found.addView(label(
            "1. com.mobiletools.systemhelper 是中國聯通的終端管理組件。它會讀取裝置與 SIM 識別碼、基地台、位置與已安裝應用清單，並以明文 HTTP 上報。\n\n" +
            "2. 該元件對外開放一個沒有權限保護的 Binder 服務。任何應用程式都能連上它，改寫上報位址或觸發上報。Slate 在元件詳情頁提供「上報位址」功能，可直接改寫。\n\n" +
            "3. 它的啟用狀態由 vivo 的 daemon 在每次開機時還原。來源是 VivoDaemonService 內的 AppFilter，會依系統屬性把這個元件放進「啟用」清單。\n\n" +
            "4. 停用、卸載、暫停三種方式都會在重開機後被還原；只有權限與 AppOps 的變更會保留。\n\n" +
            "5. 同類的預裝元件還有 com.vivo.devicereg（vivo DeviceReg，內嵌彩訊 DM SDK，會採集應用清單、使用時長、分應用流量、基地台／Wi-Fi 與 SIM 標識，上報到 a.fxltsbl.com）、com.vivo.devicereg2、com.dmclient.debug、com.chinaunicom.registerhelper，以及其他廠商的 com.coloros.regservice、com.miui.dmregservice 等。Slate 的掃描已納入這些關鍵字。", 13f, muted))
        box.addView(found)

        gap(box, 12)
        val limits = card()
        limits.addView(label("目前的限制", 15f, ink, true))
        limits.addView(label(
            "· 重新開機後需要在 Shizuku 內重新啟動服務，這是 Android 的設計，沒有 root 無法繞過。\n" +
            "· 無法撤銷安裝時授予的權限（INTERNET、QUERY_ALL_PACKAGES、READ_PRIVILEGED_PHONE_STATE 等）。\n" +
            "· 無法停用其他應用程式的元件，shell 權限不足。\n" +
            "· 不會修改系統分區，也不會 root 或解鎖 bootloader。\n" +
            "· 系統更新可能重新加入元件，需要自行再檢查一次。", 13f, muted))
        box.addView(limits)

        gap(box, 12)
        val privacy = card()
        privacy.addView(label("資料處理", 15f, ink, true))
        privacy.addView(label(
            "本工具不是背景服務。只有在你按下按鈕時，才會透過本機的 shell 執行指令。\n" +
            "不連線到任何伺服器、不上傳資料；操作記錄只存在本次執行期間的記憶體中。", 13f, muted))
        box.addView(privacy)
        return scroll(box)
    }

    // ---------- permission hardening ----------

    private val conservativeAppOps = listOf(
        "FINE_LOCATION", "COARSE_LOCATION", "READ_PHONE_STATE", "READ_CONTACTS",
        "READ_CALL_LOG", "READ_CALENDAR", "CAMERA", "RECORD_AUDIO",
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "GET_USAGE_STATS",
        "ACTIVITY_RECOGNITION", "BODY_SENSORS"
    )

    private val extraAppOps = listOf(
        "SEND_SMS", "RECEIVE_SMS", "READ_SMS", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT"
    )

    private val skipInConservative = setOf(
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT"
    )

    private fun isDangerousPermission(perm: String): Boolean = try {
        val info = packageManager.getPermissionInfo(perm, 0)
        (info.protectionLevel and 0xf) == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
    } catch (t: Throwable) {
        false
    }

    private fun buildPermissionCommands(p: Pkg, includeSmsBluetooth: Boolean): List<String> {
        val cmds = mutableListOf<String>()
        for (perm in p.granted) {
            if (!includeSmsBluetooth && perm in skipInConservative) continue
            if (!isDangerousPermission(perm)) continue
            cmds.add("pm revoke " + p.name + " " + perm)
        }
        val ops = if (includeSmsBluetooth) conservativeAppOps + extraAppOps else conservativeAppOps
        for (op in ops) {
            cmds.add("cmd appops set " + p.name + " " + op + " ignore")
        }
        return cmds
    }

    private fun snapshotPrefs() =
        getSharedPreferences("slate_perm_snapshots", MODE_PRIVATE)

    private fun currentGrantedDangerous(p: Pkg): List<String> {
        val out = mutableListOf<String>()
        for (perm in p.requested) {
            if (!isDangerousPermission(perm)) continue
            val granted = try {
                packageManager.checkPermission(perm, p.name) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (t: Throwable) {
                false
            }
            if (granted) out.add(perm)
        }
        return out
    }

    private fun saveSnapshot(p: Pkg) {
        val granted = currentGrantedDangerous(p)
        val arr = org.json.JSONArray()
        for (perm in granted) arr.put(perm)
        snapshotPrefs().edit()
            .putString(p.name, arr.toString())
            .putLong(p.name + ".time", System.currentTimeMillis())
            .apply()
    }

    private fun loadSnapshot(p: Pkg): Pair<List<String>, Long>? {
        val raw = snapshotPrefs().getString(p.name, null) ?: return null
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            list to snapshotPrefs().getLong(p.name + ".time", 0L)
        } catch (t: Throwable) {
            null
        }
    }

    private fun buildRestoreCommands(p: Pkg, perms: List<String>): List<String> {
        val cmds = mutableListOf<String>()
        val ordered = perms.sortedBy {
            when (it) {
                "android.permission.ACCESS_FINE_LOCATION" -> 0
                "android.permission.ACCESS_COARSE_LOCATION" -> 1
                "android.permission.ACCESS_BACKGROUND_LOCATION" -> 3
                else -> 2
            }
        }
        for (perm in ordered) {
            cmds.add("pm grant " + p.name + " " + perm)
        }
        for (op in conservativeAppOps + extraAppOps) {
            cmds.add("cmd appops set " + p.name + " " + op + " default")
        }
        return cmds
    }

    // ---------- actions ----------

    private fun confirm(message: String, onOk: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("確認")
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton("執行") { _, _ -> onOk() }
            .show()
    }

    private fun run(cmd: String, pkg: String) = runAll(listOf(cmd), pkg)

    private fun runAll(cmds: List<String>, pkg: String) {
        val svc = shell
        if (svc == null) {
            showTab(0)
            return
        }
        Thread {
            val sb = StringBuilder()
            for (c in cmds) {
                val r = try { svc.runCommand(c) } catch (t: Throwable) { "error: " + t }
                val short = c.replace("android.permission.", "").take(64)
                sb.append("- ").append(short).append('\n').append("  ").append(r.trim().replace("\n", "\n  ")).append('\n')
            }
            val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
            logs.add(stamp + "  " + pkg + "\n" + sb.toString().trim())
            scan()
            runOnUiThread { showTab(1) }
        }.start()
    }

    private fun bindService() {
        try { Shizuku.bindUserService(userServiceArgs, connection) } catch (t: Throwable) { }
    }

    private fun scan() {
        val found = mutableListOf<Pkg>()
        try {
            val pm = packageManager
            val flags = android.content.pm.PackageManager.GET_PERMISSIONS or
                android.content.pm.PackageManager.GET_SERVICES or
                android.content.pm.PackageManager.GET_RECEIVERS or
                android.content.pm.PackageManager.GET_ACTIVITIES or
                android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
            val keywords = listOf(
                "systemhelper", "mobiletools", "devicereg", "dmclient", "regservice",
                "registerhelper", "ctautoregist", "registration", "ueinfocheck",
                "autoregsms", "dmreg", "guardprovider", "fanzha", "fraud"
            )
            for (pi in pm.getInstalledPackages(flags)) {
                if (keywords.none { pi.packageName.contains(it, true) }) continue
                val ai = pi.applicationInfo
                val granted = (pi.requestedPermissions ?: emptyArray())
                    .filter { pm.checkPermission(it, pi.packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED }
                val exported = mutableListOf<String>()
                pi.services?.forEach { if (it.exported) exported.add("Service: " + it.name.substringAfterLast('.')) }
                pi.receivers?.forEach { if (it.exported) exported.add("Receiver: " + it.name.substringAfterLast('.')) }
                pi.activities?.forEach { if (it.exported) exported.add("Activity: " + it.name.substringAfterLast('.')) }
                found.add(Pkg(pi.packageName, pi.versionName ?: "?", ai?.enabled == true,
                    ai != null && (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    ai?.uid ?: -1, granted, (pi.requestedPermissions ?: emptyArray()).toList(), exported))
            }
        } catch (t: Throwable) { }
        pkgs = found
    }
}

package moe.shizuku.manager.phonefix

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
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
import android.widget.ImageView
import android.widget.Switch
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import moe.shizuku.manager.R
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
        val exported: List<String>,
        val installed: Boolean
    )

    private lateinit var content: FrameLayout
    private lateinit var navBar: LinearLayout
    private var shell: IShellService? = null
    private var tab = 0
    private var subScreen = false
    private var showSystemPerms = false
    private lateinit var headerBack: TextView
    private var pkgs: List<Pkg> = emptyList()
    private val logs = mutableListOf<String>()
    private val target = "com.mobiletools.systemhelper"

    private var darkMode = false
    private var bg = 0
    private var cardBg = 0
    private var ink = 0
    private var muted = 0
    private var accent = 0
    private var accentSoft = 0
    private var warn = 0
    private var danger = 0
    private var success = 0
    private var line = 0

    private fun applyTheme() {
        if (darkMode) {
            bg = Color.parseColor("#0F1419")
            cardBg = Color.parseColor("#161D26")
            ink = Color.parseColor("#E6EAF0")
            muted = Color.parseColor("#98A2B3")
            accent = Color.parseColor("#7DB2E8")
            accentSoft = Color.parseColor("#1F2C3A")
            warn = Color.parseColor("#F5A524")
            danger = Color.parseColor("#F04438")
            success = Color.parseColor("#32D583")
            line = Color.parseColor("#24303D")
        } else {
            bg = Color.parseColor("#F3F5F9")
            cardBg = Color.WHITE
            ink = Color.parseColor("#111827")
            muted = Color.parseColor("#6B7280")
            accent = Color.parseColor("#2F5D8A")
            accentSoft = Color.parseColor("#E8EEF5")
            warn = Color.parseColor("#B45309")
            danger = Color.parseColor("#B91C1C")
            success = Color.parseColor("#15803D")
            line = Color.parseColor("#E5E7EB")
        }
    }

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
        darkMode = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        setTheme(
            if (darkMode) android.R.style.Theme_Material_NoActionBar
            else android.R.style.Theme_Material_Light_NoActionBar
        )
        applyTheme()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(10))
            setBackgroundColor(cardBg)
        }
        val headerTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerBack = TextView(this).apply {
            text = "‹"
            textSize = 26f
            setTextColor(accent)
            setPadding(0, 0, dp(12), 0)
            visibility = View.GONE
            setOnClickListener { showTab(1) }
        }
        headerTop.addView(headerBack)
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(label("Slate", 22f, ink, true))
        titleBox.addView(label("電信商元件檢查與管理", 11.5f, muted))
        headerTop.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(headerTop)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(cardBg)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        root.addView(navBar, LinearLayout.LayoutParams(-1, dp(66)))
        root.fitsSystemWindows = true
        setContentView(root)
        window.statusBarColor = cardBg
        window.navigationBarColor = cardBg
        var ui = window.decorView.systemUiVisibility
        ui = if (darkMode) {
            ui and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            ui or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            ui = if (darkMode) {
                ui and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                ui or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        window.decorView.systemUiVisibility = ui
        buildNav()
        scan()
        showTab(0)
        showOnboarding()
    }

    private fun showOnboarding() {
        val prefs = getSharedPreferences("slate_ui", MODE_PRIVATE)
        if (prefs.getBoolean("onboarded", false)) return
        AlertDialog.Builder(this)
            .setTitle("歡迎使用 Slate")
            .setMessage(
                "Slate 需要官方 Shizuku 提供 shell 權限。\n\n" +
                    "1. 安裝並啟動官方 Shizuku\n" +
                    "2. 回到 Slate 按「授權 Slate」\n" +
                    "3. 掃描電信商註冊／DM 元件\n" +
                    "4. 逐項停用、撤權限、限制背景資料\n\n" +
                    "所有操作都可逆，不需要 root。"
            )
            .setPositiveButton("開始") { _, _ ->
                prefs.edit().putBoolean("onboarded", true).apply()
            }
            .setCancelable(false)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (subScreen) {
            subScreen = false
            showTab(1)
        } else {
            super.onBackPressed()
        }
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
        elevation = dp(2).toFloat()
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(cardBg)
            setStroke(dp(1), line)
        }
    }

    private fun action(t: String, primary: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = t
            isAllCaps = false
            textSize = 14.5f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(48))
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (primary) Color.WHITE else accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (primary) accent else accentSoft)
            }
            setOnClickListener { onClick() }
        }

    private fun dangerAction(t: String, onClick: () -> Unit): Button =
        action(t, onClick = onClick).apply {
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(danger)
            }
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
        val items = listOf(
            R.drawable.ic_nav_home to "首頁",
            R.drawable.ic_nav_apps to "元件",
            R.drawable.ic_nav_log to "記錄",
            R.drawable.ic_nav_settings to "設定",
            R.drawable.ic_nav_info to "說明"
        )
        items.forEachIndexed { i, (iconRes, name) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (i == tab) accentSoft else Color.TRANSPARENT)
                }
                setOnClickListener { tab = i; showTab(i) }
            }
            val tint = if (i == tab) accent else muted
            val iconView = ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(tint)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            }
            val nameView = label(name, 10.5f, tint, i == tab).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            item.addView(iconView)
            item.addView(nameView)
            navBar.addView(item, LinearLayout.LayoutParams(0, -1, 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    private fun showTab(i: Int) {
        tab = i
        subScreen = false
        if (::headerBack.isInitialized) headerBack.visibility = View.GONE
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
        gap(box, 4)
        val statRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        statRow.addView(statChip("元件", pkgs.size.toString(), accent), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(8), 0) })
        statRow.addView(statChip("已停用", pkgs.count { !it.enabled }.toString(), warn), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(8), 0) })
        statRow.addView(statChip("對外元件", pkgs.sumOf { it.exported.size }.toString(), muted), LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(statRow)
        gap(box, 14)

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
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                    isClickable = true
                    setOnClickListener { detail(p) }
                }
                item.addView(label(p.name, 13f, ink, true))
                item.addView(label("v" + p.version + " · " + (if (p.system) "系統應用" else "使用者應用") + " · " + (if (p.enabled) "執行中" else "已停用"), 12f, muted))
                summary.addView(item)
            }
        }
        gap(summary, 10)
        summary.addView(action("立即掃描", true) { scan() })
        if (found.isNotEmpty()) {
            gap(summary, 6)
            summary.addView(action("查看全部元件") { showTab(1) })
        }
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
            c.isClickable = true
            c.setOnClickListener { detail(p) }
            c.addView(label(p.name, 14f, ink, true))
            c.addView(label("v" + p.version + " · " + (if (p.system) "系統應用" else "使用者應用"), 12f, muted))
            val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(8)) }
            val (stateText, stateColor) = packageState(p)
            chips.addView(chip(stateText, stateColor))
            chips.addView(chip(p.granted.size.toString() + " 項權限", muted))
            chips.addView(chip(p.exported.size.toString() + " 個對外元件", muted))
            c.addView(chips)
            c.addView(label("› 查看詳情", 12f, accent, true))
            box.addView(c)
            gap(box, 12)
        }
        return scroll(box)
    }

    private fun sectionTitle(t: String) = label(t, 13.5f, accent, true).apply {
        letterSpacing = 0.02f
    }

    private fun statChip(title: String, value: String, color: Int): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(cardBg)
                setStroke(dp(1), line)
            }
        }
        c.addView(label(value, 20f, color, true))
        c.addView(label(title, 11f, muted))
        return c
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
        subScreen = true
        headerBack.visibility = View.VISIBLE
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        val head = card()
        head.addView(label(p.name, 18f, ink, true))
        head.addView(label(
            "v" + p.version + " · " + (if (p.system) "系統應用" else "使用者應用") + " · UID " + p.uid,
            12f, muted
        ))
        gap(head, 10)
        val headChips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (stateText, stateColor) = packageState(p)
        headChips.addView(chip(stateText, stateColor))
        headChips.addView(chip(p.granted.size.toString() + " 項權限", muted))
        headChips.addView(chip(p.exported.size.toString() + " 個對外元件", muted))
        head.addView(headChips)
        box.addView(head)

        gap(box, 14)
        val perms = card()
        perms.addView(sectionTitle("權限狀態"))
        val runtimePerms = p.requested.filter { isDangerousPermission(it) }
        val grantedNow = runtimePerms.count { isPermissionGranted(p.name, it) }
        perms.addView(label(
            "$grantedNow / ${runtimePerms.size} 項 runtime 權限仍授予",
            12f, if (grantedNow == 0) success else warn, true
        ))
        perms.addView(label("開關即時反映目前狀態；切換後會重新整理。", 11f, muted))
        gap(perms, 8)

        val groups = listOf(
            "定位" to listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "ACCESS_MEDIA_LOCATION"),
            "電話" to listOf("READ_PHONE_STATE", "READ_PRECISE_PHONE_STATE", "READ_PRIVILEGED_PHONE_STATE", "CALL_PHONE", "READ_CALL_LOG", "READ_PHONE_NUMBERS"),
            "儲存" to listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO"),
            "網路與系統" to listOf("INTERNET", "ACCESS_NETWORK_STATE", "CHANGE_WIFI_STATE", "ACCESS_WIFI_STATE", "QUERY_ALL_PACKAGES", "PACKAGE_USAGE_STATS", "RECEIVE_BOOT_COMPLETED", "SCHEDULE_EXACT_ALARM", "FOREGROUND_SERVICE")
        )
        val req = p.requested.map { it.substringAfterLast('.') }
        val fullByShort = p.requested.associateBy { it.substringAfterLast('.') }
        val got = p.granted.map { it.substringAfterLast('.') }.toSet()
        val shown = mutableSetOf<String>()
        for ((title, keys) in groups) {
            val rows = req.filter { r ->
                r in keys && fullByShort[r]?.let { isDangerousPermission(it) } == true
            }
            if (rows.isEmpty()) continue
            shown.addAll(rows)
            perms.addView(label(title, 12.5f, accent, true))
            for (r in rows) {
                val full = fullByShort[r] ?: continue
                perms.addView(permissionSwitchRow(p, full) { detail(p) })
            }
            gap(perms, 6)
        }
        val rest = req.filter { r ->
            r !in shown && fullByShort[r]?.let { isDangerousPermission(it) } == true
        }
        if (rest.isNotEmpty()) {
            perms.addView(label("其他", 12.5f, accent, true))
            for (r in rest) {
                val full = fullByShort[r] ?: continue
                perms.addView(permissionSwitchRow(p, full) { detail(p) })
            }
        }

        gap(perms, 8)
        perms.addView(label("使用統計", 12.5f, accent, true))
        perms.addView(appOpsSwitchRow(p) { detail(p) })
        gap(perms, 8)
        perms.addView(label("背景資料", 12.5f, accent, true))
        perms.addView(backgroundSwitchRow(p) { detail(p) })

        val systemPerms = req.filter { r ->
            fullByShort[r]?.let { !isDangerousPermission(it) } == true
        }
        if (systemPerms.isNotEmpty()) {
            gap(perms, 8)
            val systemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (showSystemPerms) View.VISIBLE else View.GONE
            }
            for (r in systemPerms) systemBox.addView(permRow(r, r in got))
            val toggle = action(if (showSystemPerms) "隱藏系統授予的權限" else "顯示系統授予的權限（" + systemPerms.size + "）") { }
            toggle.setOnClickListener {
                showSystemPerms = !showSystemPerms
                systemBox.visibility = if (showSystemPerms) View.VISIBLE else View.GONE
                toggle.text = if (showSystemPerms) {
                    "隱藏系統授予的權限"
                } else {
                    "顯示系統授予的權限（" + systemPerms.size + "）"
                }
            }
            perms.addView(toggle)
            perms.addView(systemBox)
        }
        box.addView(perms)

        gap(box, 12)
        val exp = card()
        exp.addView(sectionTitle("對外開放的元件"))
        exp.addView(label(if (p.exported.isEmpty()) "無" else p.exported.joinToString("\n"), 12f, if (p.exported.isEmpty()) muted else warn))
        box.addView(exp)

        gap(box, 12)
        val acts = card()
        acts.addView(sectionTitle("可以做的事"))
        gap(acts, 6)
        if (!p.installed) {
            acts.addView(action("還原安裝", true) {
                confirm("重新安裝這個系統元件到目前使用者。") {
                    run("cmd package install-existing " + p.name, p.name)
                }
            })
        } else {
            if (p.enabled) {
                acts.addView(dangerAction("停用") {
                    confirm("停用後，這個元件不會再執行。系統更新後可能恢復，可隨時還原。") {
                        run("pm disable-user --user 0 " + p.name, p.name)
                    }
                })
            } else {
                acts.addView(action("還原啟用", true) {
                    confirm("將重新啟用這個元件。") {
                        run("pm enable " + p.name, p.name)
                    }
                })
            }
            gap(acts, 6)
            acts.addView(dangerAction("對目前使用者卸載") {
                confirm("對目前使用者移除。系統分區的檔案仍在，更新或恢復出廠後可能回來；可用「還原」重新安裝。") {
                    run("pm uninstall -k --user 0 " + p.name, p.name)
                }
            })
        }
        gap(acts, 6)
        acts.addView(label(
            "只撤 runtime 權限；INTERNET、QUERY_ALL_PACKAGES、READ_PRIVILEGED_PHONE_STATE 這類安裝時權限撤不掉，GET_USAGE_STATS 改用 AppOps 攔。",
            11f, muted
        ))
        gap(acts, 6)
        acts.addView(action("進階操作（批次／網路）") { showAdvanced(p) })
        val snapshot = loadSnapshot(p)
        if (snapshot != null) {
            gap(acts, 6)
            acts.addView(label(
                "撤銷前快照：${snapshot.first.size} 項（" +
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(snapshot.second)) + "）",
                11f, muted
            ))
        }
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

    // ---------- per-permission toggles ----------

    private fun appOpsMode(pkg: String, op: String): String? {
        val svc = shell ?: return null
        return try {
            val out = svc.runCommand("cmd appops get $pkg $op")
            when {
                out.contains("$op: ignore") -> "ignore"
                out.contains("$op: allow") -> "allow"
                out.contains("$op: default") -> "default"
                else -> "unknown"
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun permissionToggleScreen(p: Pkg): View {
        subScreen = true
        headerBack.visibility = View.VISIBLE
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        box.addView(label("權限開關", 24f, ink, true))
        box.addView(label(
            "逐項 grant／revoke。安裝時權限（INTERNET、QUERY_ALL_PACKAGES、READ_PRIVILEGED_PHONE_STATE 等）不在這裡，無法變更。",
            12f, muted
        ))
        gap(box, 12)

        val runtime = p.requested.filter { isDangerousPermission(it) }.sorted()
        val c = card()
        if (runtime.isEmpty()) {
            c.addView(label("這個套件沒有可切換的 runtime 權限。", 13f, muted))
        } else {
            for (perm in runtime) {
                c.addView(permissionToggleRow(p, perm))
            }
        }
        box.addView(c)

        gap(box, 12)
        val usage = card()
        usage.addView(label("使用統計（AppOps）", 15f, ink, true))
        usage.addView(label("GET_USAGE_STATS 設為 ignore，UsageStats／NetworkStats 查詢就會回空。", 12f, muted))
        gap(usage, 6)
        usage.addView(appOpsToggleRow(p))
        box.addView(usage)
        return scroll(box)
    }

    private fun permissionToggleRow(p: Pkg, perm: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(
            label(perm.removePrefix("android.permission."), 13f, ink),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val sw = Switch(this)
        sw.isChecked = try {
            packageManager.checkPermission(perm, p.name) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
        sw.setOnCheckedChangeListener { button, checked ->
            button.isEnabled = false
            Thread {
                val cmd = (if (checked) "pm grant " else "pm revoke ") + p.name + " " + perm
                val out = try {
                    shell?.runCommand(cmd) ?: "no shell"
                } catch (t: Throwable) {
                    "error: $t"
                }
                val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                logs.add(stamp + "  " + cmd + "\n" + out)
                runOnUiThread {
                    content.removeAllViews()
                    content.addView(permissionToggleScreen(p))
                }
            }.start()
        }
        row.addView(sw)
        return row
    }

    private fun appOpsToggleRow(p: Pkg): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(label("GET_USAGE_STATS", 13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this)
        sw.isEnabled = false
        row.addView(sw)
        Thread {
            val mode = appOpsMode(p.name, "GET_USAGE_STATS")
            runOnUiThread {
                sw.isChecked = mode == "ignore"
                sw.isEnabled = shell != null
                sw.setOnCheckedChangeListener { button, checked ->
                    button.isEnabled = false
                    Thread {
                        val cmd = "cmd appops set " + p.name + " GET_USAGE_STATS " +
                            (if (checked) "ignore" else "default")
                        val out = try {
                            shell?.runCommand(cmd) ?: "no shell"
                        } catch (t: Throwable) {
                            "error: $t"
                        }
                        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        logs.add(stamp + "  " + cmd + "\n" + out)
                        runOnUiThread {
                            content.removeAllViews()
                            content.addView(permissionToggleScreen(p))
                        }
                    }.start()
                }
            }
        }.start()
        return row
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

    private fun isPermissionGranted(pkg: String, perm: String): Boolean = try {
        packageManager.checkPermission(perm, pkg) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    private fun permissionSwitchRow(p: Pkg, perm: String, onChanged: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val granted = isPermissionGranted(p.name, perm)
        row.addView(
            label(perm.removePrefix("android.permission."), 13f, ink),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val status = label(if (granted) "已授予" else "已撤銷", 11f, if (granted) warn else success)
        status.setPadding(0, 0, dp(8), 0)
        row.addView(status)
        val sw = Switch(this)
        sw.isChecked = granted
        sw.setOnCheckedChangeListener { button, checked ->
            button.isEnabled = false
            Thread {
                val cmd = (if (checked) "pm grant " else "pm revoke ") + p.name + " " + perm
                val out = try {
                    shell?.runCommand(cmd) ?: "no shell"
                } catch (t: Throwable) {
                    "error: $t"
                }
                val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                logs.add(stamp + "  " + cmd + "\n" + out)
                runOnUiThread { onChanged() }
            }.start()
        }
        row.addView(sw)
        return row
    }

    private fun appOpsSwitchRow(p: Pkg, onChanged: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        row.addView(label("允許使用統計", 13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
        val status = label("查詢中…", 11f, muted)
        status.setPadding(0, 0, dp(8), 0)
        row.addView(status)
        val sw = Switch(this)
        sw.isEnabled = false
        row.addView(sw)
        Thread {
            val mode = appOpsMode(p.name, "GET_USAGE_STATS")
            runOnUiThread {
                if (mode == null) {
                    status.text = "查詢失敗"
                    status.setTextColor(muted)
                } else {
                    val allowed = mode != "ignore"
                    status.text = if (allowed) "已允許" else "已攔截"
                    status.setTextColor(if (allowed) success else danger)
                    sw.isChecked = allowed
                    sw.isEnabled = true
                    sw.setOnCheckedChangeListener { button, checked ->
                        button.isEnabled = false
                        Thread {
                            val cmd = "cmd appops set " + p.name + " GET_USAGE_STATS " +
                                (if (checked) "default" else "ignore")
                            val out = try {
                                shell?.runCommand(cmd) ?: "no shell"
                            } catch (t: Throwable) {
                                "error: $t"
                            }
                            val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                            logs.add(stamp + "  " + cmd + "\n" + out)
                            runOnUiThread { onChanged() }
                        }.start()
                    }
                }
            }
        }.start()
        return row
    }

    private fun queryBackgroundRestricted(p: Pkg): Boolean? {
        val svc = shell ?: return null
        if (p.uid <= 0) return null
        return try {
            val out = svc.runCommand("cmd netpolicy list restrict-background-blacklist")
            if (out.contains("exit=0")) {
                val body = out.substringAfter("Restrict background blacklisted UIDs:", out)
                val ids = Regex("\\d+").findAll(body).map { it.value }.toSet()
                p.uid.toString() in ids
            } else {
                val dump = svc.runCommand("dumpsys netpolicy")
                if (!dump.contains("exit=0")) return null
                Regex("UID=" + p.uid + " policy=1").containsMatchIn(dump)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun applyBackground(p: Pkg, allow: Boolean, mode: String): String {
        val svc = shell ?: return "no shell"
        if (mode == "appops") {
            val cmd = "cmd appops set " + p.name + " RUN_ANY_IN_BACKGROUND " +
                (if (allow) "allow" else "ignore")
            val out = try {
                svc.runCommand(cmd)
            } catch (t: Throwable) {
                "error: $t"
            }
            return "[" + cmd + "]\n" + out
        }
        val cmd = "cmd netpolicy " + (if (allow) "remove" else "add") +
            " restrict-background-blacklist " + p.uid
        val first = try {
            svc.runCommand(cmd)
        } catch (t: Throwable) {
            "error: $t"
        }
        val after = queryBackgroundRestricted(p)
        if (after == null || after == !allow) {
            val fallback = "cmd appops set " + p.name + " RUN_ANY_IN_BACKGROUND " +
                (if (allow) "allow" else "ignore")
            val second = try {
                svc.runCommand(fallback)
            } catch (t: Throwable) {
                "error: $t"
            }
            return "[" + cmd + "]\n" + first + "\n[fallback appops]\n[" + fallback + "]\n" + second
        }
        return "[" + cmd + "]\n" + first
    }

    private fun runBackground(p: Pkg, allow: Boolean) {
        val dlg = ProgressDialog(this).apply {
            setMessage("套用背景限制…")
            setCancelable(false)
            show()
        }
        Thread {
            val mode = if (queryBackgroundRestricted(p) == null) "appops" else "netpolicy"
            val out = applyBackground(p, allow, mode)
            val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logs.add(stamp + "  " + p.name + "\n" + out)
            runOnUiThread {
                try {
                    dlg.dismiss()
                } catch (t: Throwable) {
                }
                AlertDialog.Builder(this)
                    .setTitle(if (allow) "已解除背景限制" else "已套用背景限制")
                    .setMessage(out)
                    .setPositiveButton("好") { _, _ -> detail(p) }
                    .show()
            }
        }.start()
    }

    private fun backgroundSwitchRow(p: Pkg, onChanged: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        row.addView(label("允許背景資料", 13f, ink), LinearLayout.LayoutParams(0, -2, 1f))
        val status = label("查詢中…", 11f, muted)
        status.setPadding(0, 0, dp(8), 0)
        row.addView(status)
        val sw = Switch(this)
        sw.isEnabled = false
        row.addView(sw)
        Thread {
            val net = queryBackgroundRestricted(p)
            val appops = if (net == null) appOpsMode(p.name, "RUN_ANY_IN_BACKGROUND") else null
            runOnUiThread {
                val mode: String
                val allowed: Boolean
                if (net != null) {
                    mode = "netpolicy"
                    allowed = !net
                    status.text = if (net) "已限制（netpolicy）" else "不受限"
                    status.setTextColor(if (net) danger else success)
                } else if (appops != null) {
                    mode = "appops"
                    allowed = appops != "ignore"
                    status.text = if (allowed) "不受限" else "已限制（背景執行）"
                    status.setTextColor(if (allowed) success else danger)
                } else {
                    status.text = "查詢失敗"
                    status.setTextColor(muted)
                    return@runOnUiThread
                }
                sw.isChecked = allowed
                sw.isEnabled = true
                sw.setOnCheckedChangeListener { button, checked ->
                    button.isEnabled = false
                    Thread {
                        val out = applyBackground(p, checked, mode)
                        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        logs.add(stamp + "  " + p.name + "\n" + out)
                        runOnUiThread { onChanged() }
                    }.start()
                }
            }
        }.start()
        return row
    }

    private fun showAdvanced(p: Pkg) {
        val items = arrayOf(
            "撤銷敏感權限（保守）",
            "撤銷敏感權限（含 SMS／藍牙）",
            "還原權限（grant + appops default）",
            "限制背景資料",
            "解除背景限制"
        )
        AlertDialog.Builder(this)
            .setTitle("進階操作")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> confirm("撤銷定位、電話狀態、儲存、相機、麥克風、聯絡人與使用統計；不會動 SMS／藍牙。") {
                        saveSnapshot(p)
                        runAll(buildPermissionCommands(p, false), p.name)
                    }

                    1 -> confirm("額外撤銷 SEND_SMS、BLUETOOTH_SCAN/CONNECT；可能影響電信自動註冊與藍牙功能。") {
                        saveSnapshot(p)
                        runAll(buildPermissionCommands(p, true), p.name)
                    }

                    2 -> {
                        val granted = loadSnapshot(p)?.first
                        val message = if (granted != null) {
                            "將只 grant 撤銷前已授予的 ${granted.size} 項權限，AppOps 設回 default。"
                        } else {
                            "找不到撤銷前快照，將 grant 全部 requested 的 runtime 權限（可能多授權）。"
                        }
                        confirm(message) {
                            val perms = granted ?: p.requested.filter { isDangerousPermission(it) }
                            runAll(buildRestoreCommands(p, perms), p.name)
                        }
                    }

                    3 -> {
                        if (p.uid <= 0) {
                            noUid()
                        } else {
                            confirm("把 UID " + p.uid + " 加入 restrict-background-blacklist。只影響背景資料，不動 SMS／APN／IMS。") {
                                runBackground(p, false)
                            }
                        }
                    }

                    4 -> {
                        if (p.uid > 0) {
                            runBackground(p, true)
                        }
                    }
                }
            }
            .show()
    }

    private fun noUid() {
        AlertDialog.Builder(this)
            .setTitle("沒有 UID")
            .setMessage("這個套件沒有可用的 UID，無法套用 netpolicy。")
            .setPositiveButton("好", null)
            .show()
    }

    private fun packageState(p: Pkg): Pair<String, Int> = when {
        !p.installed -> "已卸載" to muted
        p.enabled -> "執行中" to warn
        else -> "已停用" to accent
    }

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
            AlertDialog.Builder(this)
                .setTitle("尚未連線")
                .setMessage("需要先啟動官方 Shizuku 並授權 Slate，才能執行操作。")
                .setPositiveButton("好", null)
                .show()
            return
        }
        val dlg = ProgressDialog(this).apply {
            setMessage("執行中…")
            setCancelable(false)
            show()
        }
        Thread {
            val results = mutableListOf<String>()
            var failures = 0
            for (c in cmds) {
                val r = try {
                    svc.runCommand(c)
                } catch (t: Throwable) {
                    "error: " + t
                }
                if (!r.contains("exit=0")) failures++
                val short = c.replace("android.permission.", "").take(72)
                results.add("- " + short + "\n  " + r.trim().replace("\n", "\n  "))
            }
            val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
            logs.add(stamp + "  " + pkg + "\n" + results.joinToString("\n"))
            runOnUiThread {
                try {
                    dlg.dismiss()
                } catch (t: Throwable) {
                }
                AlertDialog.Builder(this)
                    .setTitle(if (failures == 0) "完成" else "完成，但有 " + failures + " 條失敗")
                    .setMessage("已執行 " + cmds.size + " 條指令。")
                    .setPositiveButton("查看記錄") { _, _ ->
                        scan()
                        showTab(2)
                    }
                    .setNegativeButton("好") { _, _ -> scan() }
                    .show()
            }
        }.start()
    }

    private fun bindService() {
        try { Shizuku.bindUserService(userServiceArgs, connection) } catch (t: Throwable) { }
    }

    private fun scan() {
        val dlg = ProgressDialog(this).apply {
            setMessage("掃描中…")
            setCancelable(false)
            show()
        }
        Thread {
            val found = mutableListOf<Pkg>()
            try {
                val pm = packageManager
                val flags = android.content.pm.PackageManager.GET_PERMISSIONS or
                    android.content.pm.PackageManager.GET_SERVICES or
                    android.content.pm.PackageManager.GET_RECEIVERS or
                    android.content.pm.PackageManager.GET_ACTIVITIES or
                    android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                    0x00002000 // MATCH_UNINSTALLED_PACKAGES
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
                        ai?.uid ?: -1, granted, (pi.requestedPermissions ?: emptyArray()).toList(), exported,
                        ai != null && (ai.flags and (1 shl 23)) != 0))
                }
            } catch (t: Throwable) {
            }
            runOnUiThread {
                pkgs = found
                try {
                    dlg.dismiss()
                } catch (t: Throwable) {
                }
                showTab(tab)
            }
        }.start()
    }
}

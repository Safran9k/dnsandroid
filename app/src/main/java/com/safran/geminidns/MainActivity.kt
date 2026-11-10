package com.safran.geminidns

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs by lazy { getSharedPreferences("gemini_dns_prefs", Context.MODE_PRIVATE) }
    private val PREF_VERSION = 5

    private var dnsEntries = mutableListOf<DnsEntry>()
    private var selectedIndex = 0
    private var isTesting = false
    private val VPN_REQUEST_CODE = 701

    // UI Elements
    private lateinit var btnTabMain: Button
    private lateinit var btnTabSettings: Button
    private lateinit var frameMain: LinearLayout
    private lateinit var frameSettings: LinearLayout

    private lateinit var lampDot: View
    private lateinit var lampTextLbl: TextView
    private lateinit var serverSelectorBtn: TextView
    private lateinit var dnsDetailsLbl: TextView

    private lateinit var badgeGemini: TextView
    private lateinit var badgeChatgpt: TextView
    private lateinit var badgeClaude: TextView
    private lateinit var badgeGeo: TextView

    private lateinit var powerBtn: Button
    private lateinit var powerStatusLbl: TextView

    private lateinit var testBtn: Button
    private lateinit var discoverBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var testStatusLbl: TextView

    private lateinit var dnsEditText: EditText
    private lateinit var settingsInfoLbl: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#0D1117")
        window.navigationBarColor = Color.parseColor("#0D1117")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
        }

        loadConfig()
        buildUi()
        refreshServerSelector(0)
        syncVpnStateUi()
    }

    override fun onResume() {
        super.onResume()
        syncVpnStateUi()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun roundedBg(fillHex: String, strokeHex: String? = null, radiusDp: Int = 10, strokeDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(strokeDp), Color.parseColor(strokeHex))
            }
        }
    }

    private fun circleBg(fillHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(fillHex))
        }
    }

    private fun loadConfig() {
        val savedVer = prefs.getInt("config_version", 0)
        var rawText = prefs.getString("dns_text", null)
        if (savedVer != PREF_VERSION || rawText.isNullOrBlank()) {
            rawText = DnsTester.DEFAULT_DNS_TEXT
            saveConfig(rawText)
        }
        dnsEntries = DnsTester.parseDnsLines(rawText)
    }

    private fun saveConfig(rawText: String) {
        prefs.edit()
            .putInt("config_version", PREF_VERSION)
            .putString("dns_text", rawText)
            .apply()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(16), dp(14), dp(16), dp(10))
        }

        // 1. HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }

        val titleLbl = TextView(this).apply {
            text = "✦ GEMINI DNS SWITCHER"
            setTextColor(Color.parseColor("#F0F6FC"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val vpnBadge = TextView(this).apply {
            text = "🛡 ANDROID VPN DNS"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#238636", null, 6)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        header.addView(titleLbl)
        header.addView(vpnBadge)
        root.addView(header)

        // 2. TAB NAVIGATION BAR
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg("#161B22", "#30363D", 8)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }

        btnTabMain = Button(this).apply {
            text = "⚡ Основная"
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBg("#238636", null, 6)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener { switchTab(true) }
        }

        btnTabSettings = Button(this).apply {
            text = "⚙ Настройки (${dnsEntries.size} DNS)"
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8B949E"))
            background = roundedBg("#161B22", null, 6)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener { switchTab(false) }
        }

        navBar.addView(btnTabMain)
        navBar.addView(btnTabSettings)
        root.addView(navBar)

        // 3. CONTENT SCROLL AREA
        val contentWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val scrollMain = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        frameMain = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scrollMain.addView(frameMain)

        frameSettings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        buildMainTab()
        buildSettingsTab()

        contentWrapper.addView(scrollMain)
        contentWrapper.addView(frameSettings)
        root.addView(contentWrapper)

        // 4. FOOTER: автор TG @Safran2k
        val footerLbl = TextView(this).apply {
            text = "автор TG @Safran2k"
            setTextColor(Color.parseColor("#58A6FF"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(4))
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Safran2k")))
                } catch (_: Exception) {}
            }
        }
        root.addView(footerLbl)

        setContentView(root)
    }

    private fun switchTab(showMain: Boolean) {
        if (showMain) {
            frameSettings.visibility = View.GONE
            (frameMain.parent as View).visibility = View.VISIBLE
            btnTabMain.background = roundedBg("#238636", null, 6)
            btnTabMain.setTextColor(Color.WHITE)
            btnTabSettings.background = roundedBg("#161B22", null, 6)
            btnTabSettings.setTextColor(Color.parseColor("#8B949E"))
        } else {
            (frameMain.parent as View).visibility = View.GONE
            frameSettings.visibility = View.VISIBLE
            btnTabSettings.background = roundedBg("#238636", null, 6)
            btnTabSettings.setTextColor(Color.WHITE)
            btnTabMain.background = roundedBg("#161B22", null, 6)
            btnTabMain.setTextColor(Color.parseColor("#8B949E"))
        }
    }

    private fun buildMainTab() {
        // CARD 1: ABOVE POWER BUTTON - DROPDOWN OF SERVERS WITH LAMPS + AI BADGES
        val dnsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#161B22", "#30363D", 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }

        val selectTitle = TextView(this).apply {
            text = "Выбор Smart DNS сервера:"
            setTextColor(Color.parseColor("#F0F6FC"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val lampRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        lampDot = View(this).apply {
            background = circleBg("#8B949E")
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                rightMargin = dp(6)
            }
        }

        lampTextLbl = TextView(this).apply {
            text = "Не проверен"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }

        lampRow.addView(lampDot)
        lampRow.addView(lampTextLbl)
        topRow.addView(selectTitle)
        topRow.addView(lampRow)
        dnsCard.addView(topRow)

        // Custom Dropdown Selector Button (opens full scrollable list with lamps)
        serverSelectorBtn = TextView(this).apply {
            text = "⚪ Загрузка..."
            setTextColor(Color.parseColor("#F0F6FC"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#21262D", "#30363D", 8)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener { showServerPickerDialog() }
        }
        dnsCard.addView(serverSelectorBtn)

        dnsDetailsLbl = TextView(this).apply {
            text = "DNS: —"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        dnsCard.addView(dnsDetailsLbl)

        // AI Badges Row (Gemini, ChatGPT, Claude)
        val badgesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        badgeGemini = createAiBadge("✦ Gemini: —")
        badgeChatgpt = createAiBadge("◉ ChatGPT: —")
        badgeClaude = createAiBadge("✳ Claude: —")

        badgesRow.addView(badgeGemini)
        badgesRow.addView(badgeChatgpt)
        badgesRow.addView(badgeClaude)
        dnsCard.addView(badgesRow)

        badgeGeo = TextView(this).apply {
            text = "🌍 ИИ видят: —"
            setTextColor(Color.parseColor("#58A6FF"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        dnsCard.addView(badgeGeo)

        frameMain.addView(dnsCard)

        // CARD 2: CENTER - POWER ON/OFF BUTTON
        val powerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#161B22", "#30363D", 12)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        powerBtn = Button(this).apply {
            text = "⏻   ВКЛЮЧИТЬ DNS"
            isAllCaps = false
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#56D364"))
            background = roundedBg("#21262D", "#3FB950", 14, 2)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener { toggleDnsPower() }
        }
        powerCard.addView(powerBtn)

        powerStatusLbl = TextView(this).apply {
            text = "Статус: Выключено (используется стандартный DNS телефона)"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        powerCard.addView(powerStatusLbl)

        frameMain.addView(powerCard)

        // CARD 3: BELOW POWER BUTTON - TEST BUTTON & AUTO-DISCOVERY BUTTON
        val testCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#161B22", "#30363D", 12)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        testBtn = Button(this).apply {
            text = "🚀  ТЕСТИРОВАТЬ СЕРВЕРА (GEMINI / ИИ)"
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBg("#1F6FEB", null, 10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener { startTestingAll() }
        }
        testCard.addView(testBtn)

        discoverBtn = Button(this).apply {
            text = "🔍  НАЙТИ НОВЫЕ СЕРВЕРЫ (АВТОПОИСК УЗЛОВ)"
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBg("#9E6A03", null, 10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener { startAutoDiscovery() }
        }
        testCard.addView(discoverBtn)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3FB950"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply {
                bottomMargin = dp(8)
            }
        }
        testCard.addView(progressBar)

        testStatusLbl = TextView(this).apply {
            text = "Нажмите «Тестировать» для проверки всех ${dnsEntries.size} серверов и автовыбора лучшего"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        testCard.addView(testStatusLbl)

        frameMain.addView(testCard)
    }

    private fun createAiBadge(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#8B949E"))
            background = roundedBg("#21262D", null, 6)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(6)
            }
        }
    }

    private fun buildSettingsTab() {
        val hintLbl = TextView(this).apply {
            text = "Список всех DNS серверов (каждый с новой строки).\nФормат: Название | IP1, IP2 | DoH_URL"
            setTextColor(Color.parseColor("#C9D1D9"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        frameSettings.addView(hintLbl)

        dnsEditText = EditText(this).apply {
            setText(prefs.getString("dns_text", DnsTester.DEFAULT_DNS_TEXT))
            setTextColor(Color.parseColor("#E6EDF3"))
            setHintTextColor(Color.parseColor("#8B949E"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = roundedBg("#161B22", "#30363D", 10)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = dp(8)
            }
        }
        frameSettings.addView(dnsEditText)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }

        val saveBtn = Button(this).apply {
            text = "💾 Сохранить список"
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBg("#238636", null, 8)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener { saveSettingsList() }
        }

        val resetBtn = Button(this).apply {
            text = "↺ Сброс (42 DNS)"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#C9D1D9"))
            background = roundedBg("#21262D", "#30363D", 8)
            layoutParams = LinearLayout.LayoutParams(dp(140), dp(44))
            setOnClickListener {
                dnsEditText.setText(DnsTester.DEFAULT_DNS_TEXT)
                saveSettingsList()
            }
        }

        btnRow.addView(saveBtn)
        btnRow.addView(resetBtn)
        frameSettings.addView(btnRow)

        settingsInfoLbl = TextView(this).apply {
            text = "Всего серверов в базе: ${dnsEntries.size}"
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 11f
            gravity = Gravity.CENTER
        }
        frameSettings.addView(settingsInfoLbl)
    }

    private fun formatEntryLabel(entry: DnsEntry): String {
        return when {
            entry.working == true -> "🟢 ${entry.name}  [${entry.ping} мс • Оценка ${entry.quality}]"
            entry.partial -> "🟡 ${entry.name}  [Частично: ChatGPT/Claude]"
            entry.working == false -> "🔴 ${entry.name}  [Не работает]"
            else -> "⚪ ${entry.name}"
        }
    }

    private fun refreshServerSelector(selectIdx: Int) {
        if (dnsEntries.isEmpty()) {
            serverSelectorBtn.text = "🔴 Список пуст — добавьте DNS в Настройках"
            return
        }
        selectedIndex = selectIdx.coerceIn(0, dnsEntries.size - 1)
        val entry = dnsEntries[selectedIndex]
        serverSelectorBtn.text = "${formatEntryLabel(entry)}   ▼"
        updateSelectedInfo(entry)
    }

    private fun showServerPickerDialog() {
        if (dnsEntries.isEmpty()) return
        val items = dnsEntries.map { formatEntryLabel(it) }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Выберите DNS сервер (${dnsEntries.size})")
            .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                selectedIndex = which
                refreshServerSelector(which)
                dialog.dismiss()
                if (DnsVpnService.isRunning) {
                    applySelectedDnsVpn()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSelectedInfo(entry: DnsEntry) {
        val addrs = if (entry.ips.isNotEmpty()) entry.ips else entry.rawTargets
        dnsDetailsLbl.text = "DNS: ${addrs.joinToString(", ")} | ${entry.reason}"

        when {
            entry.working == true -> {
                lampDot.background = circleBg("#3FB950")
                lampTextLbl.text = "Работает • Оценка ${entry.quality}"
                lampTextLbl.setTextColor(Color.parseColor("#3FB950"))
            }
            entry.partial -> {
                lampDot.background = circleBg("#D29922")
                lampTextLbl.text = "Частично (без Gemini)"
                lampTextLbl.setTextColor(Color.parseColor("#D29922"))
            }
            entry.working == false -> {
                lampDot.background = circleBg("#F85149")
                lampTextLbl.text = "Не работает"
                lampTextLbl.setTextColor(Color.parseColor("#F85149"))
            }
            else -> {
                lampDot.background = circleBg("#8B949E")
                lampTextLbl.text = "Не проверен"
                lampTextLbl.setTextColor(Color.parseColor("#8B949E"))
            }
        }

        updateBadge(badgeGemini, "✦ Gemini", entry.geminiOk)
        updateBadge(badgeChatgpt, "◉ ChatGPT", entry.chatgptOk)
        updateBadge(badgeClaude, "✳ Claude", entry.claudeOk)
        badgeGeo.text = "🌍 ИИ видят: ${entry.country}"
    }

    private fun updateBadge(view: TextView, title: String, ok: Boolean?) {
        when (ok) {
            true -> {
                view.text = "$title: ОК"
                view.background = roundedBg("#122D1F", "#3FB950", 6)
                view.setTextColor(Color.parseColor("#3FB950"))
            }
            false -> {
                view.text = "$title: Нет"
                view.background = roundedBg("#2D1619", "#F85149", 6)
                view.setTextColor(Color.parseColor("#F85149"))
            }
            null -> {
                view.text = "$title: —"
                view.background = roundedBg("#21262D", null, 6)
                view.setTextColor(Color.parseColor("#8B949E"))
            }
        }
    }

    // --- POWER ON / OFF VIA ANDROID VPNSERVICE ---
    private fun toggleDnsPower() {
        if (dnsEntries.isEmpty()) return
        if (!DnsVpnService.isRunning) {
            val prepIntent = VpnService.prepare(this)
            if (prepIntent != null) {
                startActivityForResult(prepIntent, VPN_REQUEST_CODE)
            } else {
                applySelectedDnsVpn()
            }
        } else {
            disableDnsVpn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            applySelectedDnsVpn()
        }
    }

    private fun applySelectedDnsVpn() {
        val entry = dnsEntries[selectedIndex]
        powerBtn.isEnabled = false
        powerBtn.text = "⏳  ПОДКЛЮЧЕНИЕ..."

        scope.launch {
            val resolvedIps = withContext(Dispatchers.IO) {
                val list = entry.ips.toMutableList()
                if (list.isEmpty()) {
                    for (t in entry.rawTargets) {
                        list.addAll(DnsTester.resolveHostAllIps(t))
                    }
                }
                list.distinct().take(2)
            }

            val useDoh = entry.usedDoh || (resolvedIps.isEmpty() && entry.dohUrl.isNotEmpty())
            if (resolvedIps.isEmpty() && !useDoh) {
                powerBtn.isEnabled = true
                powerBtn.text = "⏻   ВКЛЮЧИТЬ DNS"
                powerStatusLbl.text = "🔴 Не удалось получить IP выбранного сервера"
                powerStatusLbl.setTextColor(Color.parseColor("#F85149"))
                return@launch
            }

            val intent = Intent(this@MainActivity, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_START
                putExtra(DnsVpnService.EXTRA_SERVER_NAME, entry.name)
                putStringArrayListExtra(DnsVpnService.EXTRA_DNS_IPS, ArrayList(resolvedIps))
                putExtra(DnsVpnService.EXTRA_DOH_URL, entry.dohUrl)
                putExtra(DnsVpnService.EXTRA_USE_DOH, useDoh)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            delay(450)
            powerBtn.isEnabled = true
            syncVpnStateUi()
        }
    }

    private fun disableDnsVpn() {
        val intent = Intent(this, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_STOP
        }
        startService(intent)
        scope.launch {
            delay(350)
            syncVpnStateUi()
        }
    }

    private fun syncVpnStateUi() {
        if (DnsVpnService.isRunning) {
            powerBtn.text = "⏻   ВЫКЛЮЧИТЬ DNS"
            powerBtn.setTextColor(Color.WHITE)
            powerBtn.background = roundedBg("#238636", "#56D364", 14, 2)
            powerStatusLbl.text = "🟢 ВКЛЮЧЕНО: ${DnsVpnService.activeServerName} (${DnsVpnService.activeDnsInfo})"
            powerStatusLbl.setTextColor(Color.parseColor("#3FB950"))
        } else {
            powerBtn.text = "⏻   ВКЛЮЧИТЬ DNS"
            powerBtn.setTextColor(Color.parseColor("#56D364"))
            powerBtn.background = roundedBg("#21262D", "#3FB950", 14, 2)
            powerStatusLbl.text = "⚪ Выключено (используется стандартный DNS телефона)"
            powerStatusLbl.setTextColor(Color.parseColor("#8B949E"))
        }
    }

    // --- CONCURRENT SERVER TESTING ---
    private fun startTestingAll() {
        if (isTesting || dnsEntries.isEmpty()) return
        isTesting = true
        testBtn.isEnabled = false
        discoverBtn.isEnabled = false
        testBtn.text = "⏳  ТЕСТИРУЕМ ВСЕ СЕРВЕРА..."
        progressBar.progress = 0
        testStatusLbl.text = "Проверяем открытие Gemini, ChatGPT и Claude через все DNS..."
        testStatusLbl.setTextColor(Color.parseColor("#58A6FF"))

        val snapshot = dnsEntries.map { it.copy() }
        val total = snapshot.size

        scope.launch {
            val completed = AtomicInteger(0)
            val workingCnt = AtomicInteger(0)
            val partialCnt = AtomicInteger(0)
            val pool = Executors.newFixedThreadPool(10).asCoroutineDispatcher()

            val deferreds = snapshot.map { item ->
                async(pool) {
                    val res = try {
                        DnsTester.evaluateDnsServer(item)
                    } catch (_: Exception) {
                        item.copy(working = false, partial = false, score = 99999, reason = "Ошибка теста")
                    }
                    val c = completed.incrementAndGet()
                    val w = if (res.working == true) workingCnt.incrementAndGet() else workingCnt.get()
                    val p = if (res.partial) partialCnt.incrementAndGet() else partialCnt.get()
                    val pct = (c * 100) / total
                    withContext(Dispatchers.Main) {
                        progressBar.progress = pct
                        testStatusLbl.text = "Проверено: $c/$total ($pct%) • 🟢 Gemini ОК: $w • 🟡 Частично: $p"
                    }
                    res
                }
            }

            val results = deferreds.awaitAll().sortedWith(
                compareBy<DnsEntry> { it.working != true }
                    .thenBy { !it.partial }
                    .thenBy { it.score }
            )
            pool.close()

            dnsEntries = results.toMutableList()
            isTesting = false
            testBtn.isEnabled = true
            discoverBtn.isEnabled = true
            testBtn.text = "🚀  ТЕСТИРОВАТЬ СЕРВЕРА (GEMINI / ИИ)"
            progressBar.progress = 100

            refreshServerSelector(0)

            val workList = results.filter { it.working == true }
            val partList = results.filter { it.partial }
            if (workList.isNotEmpty()) {
                val best = workList[0]
                testStatusLbl.text = "🟢 Работают с Gemini: ${workList.size} (частично: ${partList.size}) из $total. Выбран лучший: ${best.name} (${best.ping} мс)"
                testStatusLbl.setTextColor(Color.parseColor("#3FB950"))
            } else {
                testStatusLbl.text = "🔴 0 из $total открыли Gemini (частично: ${partList.size}). Нажмите «Найти новые серверы»!"
                testStatusLbl.setTextColor(Color.parseColor("#F85149"))
            }
        }
    }

    // --- AUTO-DISCOVERY OF NEW SMART DNS NODES ---
    private fun startAutoDiscovery() {
        if (isTesting) return
        isTesting = true
        discoverBtn.isEnabled = false
        testBtn.isEnabled = false
        discoverBtn.text = "⏳  ИЩЕМ НОВЫЕ УЗЛЫ SMART DNS..."
        progressBar.progress = 15
        testStatusLbl.text = "Сканируем кластеры Smart DNS и извлекаем новые SNI-прокси узлы..."
        testStatusLbl.setTextColor(Color.parseColor("#D29922"))

        scope.launch {
            val existingIps = dnsEntries.flatMap { it.ips }.toMutableSet()
            val candidateList = mutableListOf<Triple<String, String, String>>()

            withContext(Dispatchers.IO) {
                for ((title, host, dohUrl) in DnsTester.DISCOVERY_DOMAINS) {
                    val ips = DnsTester.resolveHostAllIps(host)
                    for (ip in ips) {
                        if (!existingIps.contains(ip) && !DnsTester.isBogon(ip)) {
                            candidateList.add(Triple(title, ip, dohUrl))
                        }
                    }
                    val gIps = DnsTester.queryDnsDoh(dohUrl, "gemini.google.com", 2000).first
                    val cIps = DnsTester.queryDnsDoh(dohUrl, "chatgpt.com", 2000).first
                    for (proxyIp in (gIps + cIps)) {
                        if (!existingIps.contains(proxyIp) &&
                            !DnsTester.isDirectGoogle(proxyIp) &&
                            !DnsTester.isDirectCloudflare(proxyIp) &&
                            !DnsTester.isBogon(proxyIp)
                        ) {
                            candidateList.add(Triple("SNI-Узел", proxyIp, ""))
                        }
                    }
                }
            }

            val uniqueCandidates = candidateList.distinctBy { it.second }.filter { !existingIps.contains(it.second) }
            testStatusLbl.text = "Найдено ${uniqueCandidates.size} новых кандидатов. Проверяем работу с Gemini..."

            val pool = Executors.newFixedThreadPool(8).asCoroutineDispatcher()
            val discovered = uniqueCandidates.map { (title, ip, doh) ->
                async(pool) {
                    val temp = DnsEntry(name = "$title ($ip)", rawTargets = listOf(ip), ips = listOf(ip), dohUrl = doh)
                    val res = DnsTester.evaluateDnsServer(temp)
                    if (res.working == true || res.partial) {
                        val (_, locStr) = DnsTester.getIpGeo(ip)
                        res.copy(name = "$locStr [НОВЫЙ] ($ip)")
                    } else null
                }
            }.awaitAll().filterNotNull()
            pool.close()

            isTesting = false
            discoverBtn.isEnabled = true
            testBtn.isEnabled = true
            discoverBtn.text = "🔍  НАЙТИ НОВЫЕ СЕРВЕРЫ (АВТОПОИСК УЗЛОВ)"
            progressBar.progress = 100

            if (discovered.isNotEmpty()) {
                val newLines = discovered.joinToString("\n") { "${it.name} | ${it.ips.joinToString(", ")}" }
                val currentRaw = dnsEditText.text.toString().trim()
                val updatedRaw = "$newLines\n$currentRaw"
                dnsEditText.setText(updatedRaw)
                saveConfig(updatedRaw)

                dnsEntries.addAll(0, discovered)
                dnsEntries.sortWith(
                    compareBy<DnsEntry> { it.working != true }
                        .thenBy { !it.partial }
                        .thenBy { it.score }
                )
                refreshServerSelector(0)
                btnTabSettings.text = "⚙ Настройки (${dnsEntries.size} DNS)"
                testStatusLbl.text = "🟢 Автопоиск нашёл и добавил новых рабочих серверов: ${discovered.size} шт.!"
                testStatusLbl.setTextColor(Color.parseColor("#3FB950"))
            } else {
                testStatusLbl.text = "Все доступные узлы кластеров уже есть в вашей базе (${dnsEntries.size} DNS)."
                testStatusLbl.setTextColor(Color.parseColor("#58A6FF"))
            }
        }
    }

    private fun saveSettingsList() {
        val raw = dnsEditText.text.toString().trim()
        val parsed = DnsTester.parseDnsLines(raw)
        if (parsed.isEmpty()) {
            Toast.makeText(this, "Ошибка: не найдено ни одного корректного DNS!", Toast.LENGTH_LONG).show()
            return
        }
        saveConfig(raw)
        dnsEntries = parsed
        refreshServerSelector(0)
        btnTabSettings.text = "⚙ Настройки (${parsed.size} DNS)"
        settingsInfoLbl.text = "✅ Сохранено! Всего серверов: ${parsed.size}"
        settingsInfoLbl.setTextColor(Color.parseColor("#3FB950"))
        Toast.makeText(this, "Список сохранён (${parsed.size} DNS)", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

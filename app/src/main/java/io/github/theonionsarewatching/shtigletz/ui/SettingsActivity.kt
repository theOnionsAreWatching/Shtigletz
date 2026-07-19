package io.github.theonionsarewatching.shtigletz.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.theonionsarewatching.shtigletz.FlavorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.input.SoftKeys
import io.github.theonionsarewatching.shtigletz.notify.Notifier
import io.github.theonionsarewatching.shtigletz.security.AccountStore

class SettingsActivity : SoftKeyActivity() {

    private val pageSizes = listOf(25, 50, 100, 200)
    // -1 = refresh on open only when stale (>15 min); 0 = off; 1440 = daily.
    private val refreshMinutes = listOf(0, -1, 1, 5, 15, 30, 60, 1440)
    private val prefetchCounts = listOf(0, 10, 25, 50)
    private val textScales = listOf(0.7f, 0.85f, 1f, 1.15f, 1.3f, 1.5f)
    private val viewModes = listOf("text", "textimg", "html")
    private val readBgs = listOf(
        "default", "#FFFFFF", "#F5EFE0", "#EEEEEE", "#E7F2E7",
        "#000000", "#1E1E1E", "#101828"
    )
    private val readFonts = listOf("sans", "serif", "mono")
    private val imagesAutoVals = listOf("never", "always")
    private val themes = listOf("system", "light", "dark")
    private val softkeyModes = listOf("off", "auto", "custom")
    private val notifyModes = listOf("off", "all", "selected")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindSpinner(
            R.id.themeSpinner,
            listOf(
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
            ),
            themes.indexOf(Settings.theme(this)).coerceAtLeast(0)
        ) {
            if (themes[it] != Settings.theme(this)) {
                Settings.setTheme(this, themes[it])
                AppCompatDelegate.setDefaultNightMode(Settings.nightMode(this))
            }
        }

        bindSpinner(
            R.id.textSizeSpinner,
            listOf(
                getString(R.string.settings_text_xsmall),
                getString(R.string.settings_text_small),
                getString(R.string.settings_text_default),
                getString(R.string.settings_text_large),
                getString(R.string.settings_text_xlarge),
                getString(R.string.settings_text_xxlarge)
            ),
            textScales.indexOf(Settings.textScale(this)).coerceAtLeast(0)
        ) {
            if (textScales[it] != Settings.textScale(this)) {
                Settings.setTextScale(this, textScales[it])
                recreate() // other screens pick it up when reopened
            }
        }

        bindSpinner(
            R.id.sortSpinner,
            listOf(getString(R.string.settings_sort_date), getString(R.string.settings_sort_unread)),
            if (Settings.sortUnreadFirst(this)) 1 else 0
        ) { Settings.setSortUnreadFirst(this, it == 1) }

        bindSpinner(
            R.id.pageSizeSpinner,
            pageSizes.map { it.toString() },
            pageSizes.indexOf(Settings.pageSize(this)).coerceAtLeast(0)
        ) { Settings.setPageSize(this, pageSizes[it]) }

        bindSpinner(
            R.id.readBgSpinner,
            listOf(
                getString(R.string.bg_default), getString(R.string.bg_white),
                getString(R.string.bg_cream), getString(R.string.bg_gray),
                getString(R.string.bg_mint), getString(R.string.bg_black),
                getString(R.string.bg_dgray), getString(R.string.bg_navy)
            ),
            readBgs.indexOf(Settings.readBg(this)).coerceAtLeast(0)
        ) { Settings.setReadBg(this, readBgs[it]) }

        bindSpinner(
            R.id.readFontSpinner,
            listOf(
                getString(R.string.font_sans), getString(R.string.font_serif),
                getString(R.string.font_mono)
            ),
            readFonts.indexOf(Settings.readFont(this)).coerceAtLeast(0)
        ) { Settings.setReadFont(this, readFonts[it]) }

        bindSpinner(
            R.id.autoRefreshSpinner,
            refreshMinutes.map {
                when (it) {
                    0 -> getString(R.string.settings_off)
                    -1 -> getString(R.string.settings_refresh_on_open)
                    1440 -> getString(R.string.settings_refresh_daily)
                    60 -> getString(R.string.settings_refresh_hourly)
                    else -> getString(R.string.settings_minutes, it)
                }
            },
            refreshMinutes.indexOf(Settings.autoRefreshMinutes(this)).coerceAtLeast(0)
        ) { Settings.setAutoRefreshMinutes(this, refreshMinutes[it]) }

        bindSpinner(
            R.id.linksSpinner,
            listOf(getString(R.string.settings_links_show), getString(R.string.settings_links_hide)),
            if (Settings.showLinks(this)) 0 else 1
        ) { Settings.setShowLinks(this, it == 0) }

        if (FlavorConfig.IMAGES) {
            findViewById<View>(R.id.viewModeLabel).visibility = View.VISIBLE
            findViewById<View>(R.id.viewModeSpinner).visibility = View.VISIBLE
            bindSpinner(
                R.id.viewModeSpinner,
                listOf(
                    getString(R.string.view_text),
                    getString(R.string.view_textimg),
                    getString(R.string.view_html)
                ),
                viewModes.indexOf(Settings.viewMode(this)).coerceAtLeast(0)
            ) { Settings.setViewMode(this, viewModes[it]) }

            findViewById<View>(R.id.imagesAutoLabel).visibility = View.VISIBLE
            findViewById<View>(R.id.imagesAutoSpinner).visibility = View.VISIBLE
            findViewById<View>(R.id.resetSendersButton).visibility = View.VISIBLE
            bindSpinner(
                R.id.imagesAutoSpinner,
                listOf(getString(R.string.images_auto_never), getString(R.string.images_auto_always)),
                imagesAutoVals.indexOf(Settings.imagesAuto(this)).coerceAtLeast(0)
            ) { Settings.setImagesAuto(this, imagesAutoVals[it]) }
            findViewById<android.widget.Button>(R.id.resetSendersButton).setOnClickListener {
                Settings.clearSenderRules(this)
                Toast.makeText(this, R.string.senders_cleared, Toast.LENGTH_SHORT).show()
            }
        }

        bindSpinner(
            R.id.tapContactsSpinner,
            listOf(getString(R.string.settings_on), getString(R.string.settings_off_label)),
            if (Settings.tapContacts(this)) 0 else 1
        ) { Settings.setTapContacts(this, it == 0) }

        bindSpinner(
            R.id.notifySpinner,
            listOf(
                getString(R.string.settings_notify_off),
                getString(R.string.settings_notify_all),
                getString(R.string.settings_notify_selected)
            ),
            notifyModes.indexOf(Settings.notifyMode(this)).coerceAtLeast(0)
        ) {
            val m = notifyModes[it]
            if (m == "selected") {
                Settings.setNotifyMode(this, m)
                pickNotifyAccounts()
            } else if (m != Settings.notifyMode(this)) {
                Settings.setNotifyMode(this, m)
            }
            if (m != "off") requestNotifyPermission()
            Notifier.ensureScheduled(this)
        }

        bindSpinner(
            R.id.notifyContentSpinner,
            listOf(getString(R.string.settings_notify_show), getString(R.string.settings_notify_hide)),
            if (Settings.notifyShowHeader(this)) 0 else 1
        ) { Settings.setNotifyShowHeader(this, it == 0) }

        bindSpinner(
            R.id.softkeySpinner,
            listOf(
                getString(R.string.settings_softkeys_off),
                getString(R.string.settings_softkeys_auto),
                getString(R.string.settings_softkeys_custom)
            ),
            softkeyModes.indexOf(SoftKeys.mode(this)).coerceAtLeast(0)
        ) {
            val m = softkeyModes[it]
            if (m == "custom") {
                if (SoftKeys.mode(this) != "custom") {
                    // Learn screen stores the codes and switches the mode on save.
                    startActivity(Intent(this, SoftKeyLearnActivity::class.java))
                }
            } else if (m != SoftKeys.mode(this)) {
                SoftKeys.setMode(this, m)
            }
        }

        bindSpinner(
            R.id.prefetchSpinner,
            prefetchCounts.map { if (it == 0) getString(R.string.settings_off) else getString(R.string.settings_newest, it) },
            prefetchCounts.indexOf(Settings.prefetchBodies(this)).coerceAtLeast(0)
        ) { Settings.setPrefetchBodies(this, prefetchCounts[it]) }

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        findViewById<android.widget.TextView>(R.id.versionText).text =
            getString(R.string.version_fmt, version)
        findViewById<android.widget.Button>(R.id.updateButton).setOnClickListener {
            checkForUpdates(version)
        }
        bindSpinner(
            R.id.deleteUpdateSpinner,
            listOf(getString(R.string.settings_on), getString(R.string.settings_off_label)),
            if (Settings.deleteUpdateApk(this)) 0 else 1
        ) { Settings.setDeleteUpdateApk(this, it == 0) }

        findViewById<android.widget.Button>(R.id.keyInfoButton).setOnClickListener { openKeyInfo() }
        updateKeyInfoVisibility()
    }

    override fun onResume() {
        super.onResume()
        updateKeyInfoVisibility() // appears once custom keys have been learned
    }

    /** The report entry only exists after custom soft keys are set up. */
    private fun updateKeyInfoVisibility() {
        val visible = SoftKeys.customCodes(this) != null
        findViewById<android.widget.Button>(R.id.keyInfoButton).visibility =
            if (visible) View.VISIBLE else View.GONE
        findViewById<android.view.View>(R.id.keyInfoNote).visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    /** Multi-choice picker for which accounts should notify. */
    private fun pickNotifyAccounts() {
        val accounts = AccountStore.list(this)
        if (accounts.isEmpty()) return
        val names = accounts.map { it.email }.toTypedArray()
        val selected = Settings.notifyAccountIds(this).toMutableSet()
        val checks = BooleanArray(accounts.size) { accounts[it].id in selected }
        AlertDialog.Builder(this)
            .setTitle(R.string.notify_pick_title)
            .setMultiChoiceItems(names, checks) { _, i, checked ->
                if (checked) selected.add(accounts[i].id) else selected.remove(accounts[i].id)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Settings.setNotifyAccountIds(this, selected)
                Notifier.ensureScheduled(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
        }
    }

    private fun openKeyInfo() {
        startActivity(Intent(this, KeyInfoActivity::class.java))
    }

    private fun checkForUpdates(currentVersion: String) {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) {
                io.github.theonionsarewatching.shtigletz.update.UpdateChecker.fetchLatest()
            }
            when {
                latest == null ->
                    Toast.makeText(this@SettingsActivity, R.string.update_error, Toast.LENGTH_LONG).show()
                !io.github.theonionsarewatching.shtigletz.update.UpdateChecker
                    .isNewer(latest.tag, currentVersion) ->
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.update_uptodate, currentVersion), Toast.LENGTH_SHORT
                    ).show()
                else -> {
                    val b = androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(R.string.update_available_title)
                        .setMessage(getString(R.string.update_available_msg, latest.tag, currentVersion))
                        .setNegativeButton(android.R.string.cancel, null)
                    if (latest.apkUrl != null) {
                        b.setPositiveButton(R.string.update_download) { _, _ ->
                            downloadAndInstall(latest.apkUrl)
                        }
                    }
                    if (latest.htmlUrl.isNotBlank()) {
                        b.setNeutralButton(R.string.update_open_page) { _, _ ->
                            runCatching {
                                startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(latest.htmlUrl)
                                    )
                                )
                            }.onFailure {
                                Toast.makeText(
                                    this@SettingsActivity, R.string.update_error, Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    b.show()
                }
            }
        }
    }

    private fun downloadAndInstall(url: String) {
        Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                io.github.theonionsarewatching.shtigletz.update.UpdateChecker.downloadApk(
                    this@SettingsActivity, url
                )
            }
            if (file == null) {
                Toast.makeText(this@SettingsActivity, R.string.update_failed, Toast.LENGTH_LONG).show()
            } else {
                runCatching {
                    startActivity(
                        io.github.theonionsarewatching.shtigletz.update.UpdateChecker
                            .installIntent(this@SettingsActivity, file)
                    )
                }.onFailure {
                    Toast.makeText(this@SettingsActivity, R.string.update_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindSpinner(id: Int, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val spinner = findViewById<Spinner>(id)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selected, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, idL: Long) {
                onPick(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}

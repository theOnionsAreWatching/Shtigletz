package io.github.theonionsarewatching.shtigletz.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatDelegate
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.Settings
import io.github.theonionsarewatching.shtigletz.input.SoftKeys
import io.github.theonionsarewatching.shtigletz.notify.Notifier
import io.github.theonionsarewatching.shtigletz.security.AccountStore

class SettingsActivity : SoftKeyActivity() {

    private val pageSizes = listOf(25, 50, 100, 200)
    private val refreshMinutes = listOf(0, 1, 5, 15, 30)
    private val prefetchCounts = listOf(0, 10, 25, 50)
    private val textScales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)
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
            R.id.autoRefreshSpinner,
            refreshMinutes.map { if (it == 0) getString(R.string.settings_off) else getString(R.string.settings_minutes, it) },
            refreshMinutes.indexOf(Settings.autoRefreshMinutes(this)).coerceAtLeast(0)
        ) { Settings.setAutoRefreshMinutes(this, refreshMinutes[it]) }

        bindSpinner(
            R.id.linksSpinner,
            listOf(getString(R.string.settings_links_show), getString(R.string.settings_links_hide)),
            if (Settings.showLinks(this)) 0 else 1
        ) { Settings.setShowLinks(this, it == 0) }

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

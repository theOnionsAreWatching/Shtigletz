package io.github.theonionsarewatching.shtigletz.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import io.github.theonionsarewatching.shtigletz.R
import io.github.theonionsarewatching.shtigletz.Settings

class SettingsActivity : AppCompatActivity() {

    private val pageSizes = listOf(25, 50, 100, 200)
    private val refreshMinutes = listOf(0, 1, 5, 15, 30)
    private val prefetchCounts = listOf(0, 10, 25, 50)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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
            R.id.prefetchSpinner,
            prefetchCounts.map { if (it == 0) getString(R.string.settings_off) else getString(R.string.settings_newest, it) },
            prefetchCounts.indexOf(Settings.prefetchBodies(this)).coerceAtLeast(0)
        ) { Settings.setPrefetchBodies(this, prefetchCounts[it]) }
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

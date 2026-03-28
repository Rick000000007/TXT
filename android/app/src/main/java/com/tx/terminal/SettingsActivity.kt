package com.tx.terminal

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.tx.terminal.service.TerminalService

/**
 * Settings activity for TX Terminal
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Settings fragment
     */
    class SettingsFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Set up userspace toggle
            val userspacePref = findPreference<SwitchPreferenceCompat>("use_userspace")
            userspacePref?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                TXApplication.getInstance().setUserspaceEnabled(enabled)
                true
            }

            // Set up wake lock toggle
            val wakeLockPref = findPreference<SwitchPreferenceCompat>("wake_lock")
            wakeLockPref?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                // Start service and manage wake lock
                if (enabled) {
                    val intent = android.content.Intent(requireContext(), TerminalService::class.java)
                    requireContext().startService(intent)
                }
                true
            }

            // Set up font size
            val fontSizePref = findPreference<SeekBarPreference>("font_size")
            fontSizePref?.setOnPreferenceChangeListener { _, newValue ->
                val size = newValue as Int
                // Font size will be applied to TerminalView
                true
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                "wake_lock" -> {
                    val enabled = sharedPreferences?.getBoolean(key, false) ?: false
                    // Handle wake lock through service
                    val intent = android.content.Intent(requireContext(), TerminalService::class.java)
                    if (enabled) {
                        requireContext().startService(intent)
                    }
                }
            }
        }
    }
}

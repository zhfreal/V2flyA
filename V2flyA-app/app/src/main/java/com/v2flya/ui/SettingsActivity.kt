package com.v2flya.ui

import android.content.Intent
import android.os.Bundle
import androidx.preference.*
import com.v2flya.R
import com.v2flya.AppConfig
import com.v2flya.extension.toast
import com.v2flya.util.Utils

class SettingsActivity : BaseActivity() {
    companion object {
        const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
        const val PREF_SPEED_ENABLED = "pref_speed_enabled"
        const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
        const val PREF_PROXY_SHARING = "pref_proxy_sharing_enabled"
        const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
        const val PREF_REMOTE_DNS = "pref_remote_dns"
        const val PREF_DOMESTIC_DNS = "pref_domestic_dns"
        const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
        const val PREF_ROUTING_MODE = "pref_routing_mode"
        const val PREF_ROUTING_CUSTOM = "pref_routing_custom"
        const val PREF_FORWARD_IPV6 = "pref_forward_ipv6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.title_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private val perAppProxy by lazy { findPreference<Preference>(PREF_PER_APP_PROXY)!! as
                CheckBoxPreference }
        private val sppedEnabled by lazy { findPreference<Preference>(PREF_SPEED_ENABLED) as CheckBoxPreference }
        private val sniffingEnabled by lazy { findPreference<Preference>(PREF_SNIFFING_ENABLED) as CheckBoxPreference }
        private val proxySharing by lazy { findPreference<Preference>(PREF_PROXY_SHARING) as CheckBoxPreference }
        private val domainStrategy by lazy { findPreference<Preference>(PREF_ROUTING_DOMAIN_STRATEGY) as ListPreference }
        private val routingMode by lazy { findPreference<Preference>(PREF_ROUTING_MODE) as ListPreference }

        private val forwardIpv6 by lazy { findPreference<Preference>(PREF_FORWARD_IPV6) as CheckBoxPreference }
        private val enableLocalDns by lazy { findPreference<Preference>(PREF_LOCAL_DNS_ENABLED) as CheckBoxPreference }
        private val domesticDns by lazy { findPreference<Preference>(PREF_DOMESTIC_DNS) as EditTextPreference }
        private val remoteDns by lazy { findPreference<Preference>(PREF_REMOTE_DNS) as EditTextPreference }
        private val routingCustom by lazy { findPreference<Preference>(PREF_ROUTING_CUSTOM) }

        private val mode by lazy { findPreference<Preference>(AppConfig.PREF_MODE) as ListPreference }

        private fun restartProxy() {
            Utils.stopVService(requireContext())
            Utils.startVService(requireContext())
        }

        private fun isRunning(): Boolean {
            return false //TODO no point of adding logic now since Settings will be changed soon
        }

        override fun onCreatePreferences(bundle: Bundle?, s: String?) {
            addPreferencesFromResource(R.xml.pref_settings)

            perAppProxy.setOnPreferenceClickListener {
                if (isRunning()) {
                    Utils.stopVService(requireContext())
                }
                startActivity(Intent(activity, PerAppProxyActivity::class.java))
                perAppProxy.isChecked = true
                true
            }
            sppedEnabled.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }
            sniffingEnabled.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }

            proxySharing.setOnPreferenceClickListener {
                if (proxySharing.isChecked)
                    activity?.toast(R.string.toast_warning_pref_proxysharing)
                if (isRunning())
                    restartProxy()
                true
            }

            domainStrategy.setOnPreferenceChangeListener { _, _ ->
                if (isRunning())
                    restartProxy()
                true
            }
            routingMode.setOnPreferenceChangeListener { _, _ ->
                if (isRunning())
                    restartProxy()
                true
            }

            routingCustom?.setOnPreferenceClickListener {
                if (isRunning())
                    Utils.stopVService(requireContext())
                startActivity(Intent(activity, RoutingSettingsActivity::class.java))
                false
            }

            forwardIpv6.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }

            enableLocalDns.setOnPreferenceClickListener {
                if (isRunning())
                    restartProxy()
                true
            }


            domesticDns.setOnPreferenceChangeListener { _, any ->
                val nval = any as String
                domesticDns.summary = if (nval == "") AppConfig.DNS_DIRECT else nval
                if (isRunning())
                    restartProxy()
                true
            }

            remoteDns.setOnPreferenceChangeListener { _, any ->
                // remoteDns.summary = any as String
                val nval = any as String
                remoteDns.summary = if (nval == "") AppConfig.DNS_AGENT else nval
                if (isRunning())
                    restartProxy()
                true
            }

            mode.setOnPreferenceChangeListener { _, newValue ->
                updatePerAppProxy(newValue.toString())
                true
            }
            mode.dialogLayoutResource = R.layout.preference_with_help_link
        }

        override fun onStart() {
            super.onStart()
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            updatePerAppProxy(defaultSharedPreferences.getString(AppConfig.PREF_MODE, "VPN"))
            remoteDns.summary = defaultSharedPreferences.getString(PREF_REMOTE_DNS, "")
            domesticDns.summary = defaultSharedPreferences.getString(PREF_DOMESTIC_DNS, "")

            if (remoteDns.summary == "") {
                remoteDns.summary = AppConfig.DNS_AGENT
            }

            if ( domesticDns.summary == "") {
                domesticDns.summary = AppConfig.DNS_DIRECT
            }
        }

        private fun updatePerAppProxy(mode: String?) {
            if (mode == "VPN") {
                perAppProxy.isEnabled = true
                perAppProxy.isChecked = PreferenceManager.getDefaultSharedPreferences(activity)
                        .getBoolean(PREF_PER_APP_PROXY, false)
            } else {
                perAppProxy.isEnabled = false
                perAppProxy.isChecked = false
            }
        }
    }

    fun onModeHelpClicked() {
        Utils.openUri(this, AppConfig.v2rayNGWikiMode)
    }
}

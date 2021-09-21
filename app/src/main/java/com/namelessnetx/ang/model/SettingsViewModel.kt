package com.namelessnetx.ang.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import com.namelessnetx.ang.app
import com.namelessnetx.ang.util.MmkvManager
import com.tencent.mmkv.MMKV

class SettingsViewModel(application: Application) : AndroidViewModel(application),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    fun startListenPreferenceChange() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCleared() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .unregisterOnSharedPreferenceChangeListener(this)
        Log.i(app.PACKAGE, "Settings ViewModel is cleared")
        super.onCleared()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(app.PACKAGE, "Observe settings changed: $key")
        when (key) {
            app.PREF_MODE,
            app.PREF_VPN_DNS,
            app.PREF_REMOTE_DNS,
            app.PREF_DOMESTIC_DNS,
            app.PREF_ROUTING_DOMAIN_STRATEGY,
            app.PREF_ROUTING_MODE,
            app.PREF_V2RAY_ROUTING_AGENT,
            app.PREF_V2RAY_ROUTING_BLOCKED,
            app.PREF_V2RAY_ROUTING_DIRECT,
            -> {
                settingsStorage?.encode(key, sharedPreferences.getString(key, ""))
            }
            app.PREF_SPEED_ENABLED,
            app.PREF_PROXY_SHARING,
            app.PREF_LOCAL_DNS_ENABLED,
            app.PREF_FAKE_DNS_ENABLED,
            app.PREF_FORWARD_IPV6,
            app.PREF_PER_APP_PROXY,
            app.PREF_BYPASS_APPS,
            -> {
                settingsStorage?.encode(key, sharedPreferences.getBoolean(key, false))
            }
            app.PREF_SNIFFING_ENABLED -> {
                settingsStorage?.encode(key, sharedPreferences.getBoolean(key, true))
            }
            app.PREF_PER_APP_PROXY_SET -> {
                settingsStorage?.encode(key, sharedPreferences.getStringSet(key, setOf()))
            }
        }
    }
}

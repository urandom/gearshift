package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.view.View
import org.sugr.gearshift.R
import org.sugr.gearshift.app
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.viewmodel.databinding.PropertyChangedCallback
import org.sugr.gearshift.viewmodel.databinding.debounce
import org.sugr.gearshift.viewmodel.databinding.observe
import org.sugr.gearshift.viewmodel.util.ResourceUtils

class ProfileEditorViewModel(prefs: SharedPreferences, private val profile: Profile) : RetainedViewModel<ProfileEditorViewModel.Consumer>(prefs) {
    val profileName = ObservableField("Default")
    val profileNameValid = ObservableBoolean(true)
    val host = ObservableField("")
    val hostValid = ObservableBoolean(true)
    val port = ObservableInt(9091)
    val portValid = ObservableBoolean(true)
    val useSSL = ObservableBoolean(false)

    val updateIntervalLabel = ObservableField("")
    val updateIntervalValue = ObservableInt(0)

    val fullUpdateLabel = ObservableField("")
    val fullUpdateValue = ObservableInt(0)

    val username = ObservableField("")
    val password = ObservableField("")

    val proxyHost = ObservableField("")
    val proxyHostValid = ObservableBoolean(true)
    val proxyPort = ObservableInt(8080)
    val proxyPortValid = ObservableBoolean(true)

    val timeout = ObservableInt(40)
    val timeoutValid = ObservableBoolean(true)
    val path = ObservableField("")

    val formValid = ObservableBoolean(false)

    private val updateIntervalEntries: Array<String>
    private val updateIntervalValues: IntArray

    private val fullUpdateEntries: Array<String>
    private val fullUpdateValues: IntArray

    interface Consumer {
        fun showUpdateIntervalPicker(current: Int) : rx.Observable<Int>
        fun showFullUpdatePicker(current: Int) : rx.Observable<Int>
    }

    init {
        val resources = app().resources
        updateIntervalEntries = resources.getStringArray(R.array.pref_update_interval_entries)
        updateIntervalValues = ResourceUtils.stringArrayAsInt(R.array.pref_update_interval_values)

        fullUpdateEntries = resources.getStringArray(R.array.pref_full_update_entries)
        fullUpdateValues = ResourceUtils.stringArrayAsInt(R.array.pref_full_update_values)

        updateIntervalLabel.set(updateIntervalEntries[0])
        updateIntervalValue.set(updateIntervalValues[0])

        fullUpdateLabel.set(fullUpdateEntries[0])
        fullUpdateValue.set(fullUpdateValues[0])

        // Unloaded profiles have default paths set
        path.set(profile.path)

        if (!profile.loaded) {
            profile.load()
        }

        // If a profile doesn't exist, it's not marked as loaded
        if (profile.loaded) {
            valuesFromProfile()
        }

        setupValidation()
    }

    fun onPickUpdateInterval(unused: View) {
        consumer?.showUpdateIntervalPicker(updateIntervalValue.get())
                ?.compose(takeUntilUnbind<Int>())
                ?.subscribe { update -> setUpdateInterval(update) }
    }

    fun onPickFullUpdate(unused: View) {
        consumer?.showFullUpdatePicker(fullUpdateValue.get())
            ?.compose(takeUntilUnbind<Int>())
            ?.subscribe { update -> setFullUpdate(update) }
    }

    fun save() {
        if (!formValid.get()) {
            return
        }

        profile.name = profileName.get()
        profile.host = host.get()
        profile.port = port.get()
        profile.useSSL = useSSL.get()

        profile.updateInterval = updateIntervalValue.get()
        profile.fullUpdate = fullUpdateValue.get()

        profile.username = username.get()
        profile.password = password.get()

        profile.proxyHost = proxyHost.get()
        profile.proxyPort = proxyPort.get()

        profile.timeout = timeout.get()
        profile.password = path.get()

        if (profile.valid) {
            profile.save()
        }
    }

    private fun valuesFromProfile() {
        profileName.set(profile.name)
        host.set(profile.host)
        port.set(profile.port)
        useSSL.set(profile.useSSL)

        setUpdateInterval(profile.updateInterval)
        setFullUpdate(profile.fullUpdate)

        username.set(profile.username)
        password.set(profile.password)
        proxyHost.set(profile.proxyHost)
        proxyPort.set(profile.proxyPort)
        timeout.set(profile.timeout)
        path.set(profile.path)
    }

    private fun setUpdateInterval(interval: Int) {
        for ((i, v) in updateIntervalValues.withIndex()) {
            if (v == interval) {
                updateIntervalLabel.set(updateIntervalEntries[i])
                updateIntervalValue.set(v)
            }
        }
    }

    private fun setFullUpdate(interval: Int) {
        for ((i, v) in fullUpdateValues.withIndex()) {
            if (v == interval) {
                fullUpdateLabel.set(fullUpdateEntries[i])
                fullUpdateValue.set(v)
            }
        }
    }

    private fun setupValidation() {
        profileName.observe().debounce().subscribe { o ->
            // Always reset the value to counter TextView's setError behavior
            profileNameValid.set(true)
            profileNameValid.set(profileName.get() != "")
        }

        host.observe().debounce().subscribe {
            hostValid.set(true)
            hostValid.set(host.get() != "" && !host.get().endsWith("example.com"))
        }

        port.observe().debounce().subscribe {
            portValid.set(true)
            portValid.set(port.get() > 0 && port.get() < 65535)
        }

        proxyHost.observe().debounce().subscribe {
            proxyHostValid.set(true)
            proxyHostValid.set(proxyHost.get() == "" || !proxyHost.get().endsWith("example.com"))
        }

        proxyPort.observe().debounce().subscribe {
            proxyPortValid.set(true)
            proxyPortValid.set(proxyPort.get() > 0 && proxyPort.get() < 65535)
        }

        timeout.observe().debounce().subscribe {
            timeoutValid.set(true)
            timeoutValid.set(timeout.get() >= 0)
        }

        PropertyChangedCallback {o ->
            formValid.set(profileNameValid.get() && hostValid.get() && portValid.get() &&
                    proxyHostValid.get() && proxyPortValid.get() && timeoutValid.get())
        }.addTo(profileNameValid, hostValid, portValid,
                proxyHostValid, proxyPortValid, timeoutValid)
    }
}

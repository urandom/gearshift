package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import android.view.View
import org.sugr.gearshift.R
import org.sugr.gearshift.app
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.viewmodel.databinding.ObservableField
import org.sugr.gearshift.viewmodel.databinding.PropertyChangedCallback
import org.sugr.gearshift.viewmodel.databinding.observe
import org.sugr.gearshift.viewmodel.rxutil.debounce
import org.sugr.gearshift.viewmodel.util.ResourceUtils

class ProfileEditorViewModel(tag: String, prefs: SharedPreferences, private val profile: Profile) :
        RetainedViewModel<ProfileEditorViewModel.Consumer>(tag, prefs) {
    val profileName = ObservableField("Default")
    val profileNameValid = ObservableBoolean(true)
    val host = ObservableField("")
    val hostValid = ObservableBoolean(true)
    val port = ObservableField("9091")
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
    val proxyPort = ObservableField("8080")
    val proxyPortValid = ObservableBoolean(true)

    val timeout = ObservableField("40")
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

    fun isValid() : Boolean {
        if (!formValid.get()) {
            return false
        }

        profile.name = profileName.get()
        profile.host = host.get()
        profile.port = port.get().toInt()
        profile.useSSL = useSSL.get()

        profile.updateInterval = updateIntervalValue.get()
        profile.fullUpdate = fullUpdateValue.get()

        profile.username = username.get()
        profile.password = password.get()

        profile.proxyHost = proxyHost.get()
        profile.proxyPort = proxyPort.get().toInt()

        profile.timeout = timeout.get().toInt()
        profile.password = path.get()

        return profile.valid
    }

    fun save() {
        if (isValid()) {
            profile.save()
        }
    }

    fun refreshValidity() {
        profileName.notifyChange()
        host.notifyChange()
        port.notifyChange()
        proxyHost.notifyChange()
        proxyPort.notifyChange()
        timeout.notifyChange()
    }

    private fun valuesFromProfile() {
        profileName.set(profile.name)
        host.set(profile.host)
        port.set(profile.port.toString())
        useSSL.set(profile.useSSL)

        setUpdateInterval(profile.updateInterval)
        setFullUpdate(profile.fullUpdate)

        username.set(profile.username)
        password.set(profile.password)
        proxyHost.set(profile.proxyHost)
        proxyPort.set(profile.proxyPort.toString())
        timeout.set(profile.timeout.toString())
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
            // Invert and set so that the observable will fire
            profileNameValid.set(!(profileName.get() != ""))
            profileNameValid.set(!profileNameValid.get())
        }

        host.observe().debounce().subscribe {
            hostValid.set(!(host.get() != "" && !host.get().endsWith("example.com")))
            hostValid.set(!hostValid.get())
        }

        port.observe().debounce().subscribe {
            try {
                val port = port.get().toInt()
                portValid.set(!(port > 0 && port < 65535))
                portValid.set(!portValid.get())
            } catch (e: NumberFormatException) {
                portValid.set(false)
            }
        }

        proxyHost.observe().debounce().subscribe {
            proxyHostValid.set(!(proxyHost.get() == "" || !proxyHost.get().endsWith("example.com")))
            proxyHostValid.set(!proxyHostValid.get())
        }

        proxyPort.observe().debounce().subscribe {
            try {
                val proxyPort = proxyPort.get().toInt()
                proxyPortValid.set(!(proxyPort > 0 && proxyPort < 65535))
                proxyPortValid.set(!proxyPortValid.get())
            } catch (e: NumberFormatException) {
                proxyPortValid.set(false)
            }
        }

        timeout.observe().debounce().subscribe {
            try {
                val timeout = timeout.get().toInt()
                timeoutValid.set(!(timeout >= 0))
                timeoutValid.set(!timeoutValid.get())
            } catch (e: NumberFormatException) {
                timeoutValid.set(false)
            }
        }

        PropertyChangedCallback {o ->
            formValid.set(profileNameValid.get() && hostValid.get() && portValid.get() &&
                    proxyHostValid.get() && proxyPortValid.get() && timeoutValid.get())
        }.addTo(profileNameValid, hostValid, portValid,
                proxyHostValid, proxyPortValid, timeoutValid)
    }
}

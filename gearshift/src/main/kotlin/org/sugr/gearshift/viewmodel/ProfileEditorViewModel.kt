package org.sugr.gearshift.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import com.google.gson.Gson
import io.reactivex.BackpressureStrategy
import io.reactivex.Single
import org.sugr.gearshift.BuildConfig
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.transmissionProfile
import org.sugr.gearshift.viewmodel.api.ApiFactory
import org.sugr.gearshift.viewmodel.api.apiOf
import org.sugr.gearshift.viewmodel.databinding.ObservableField
import org.sugr.gearshift.viewmodel.databinding.PropertyChangedCallback
import org.sugr.gearshift.viewmodel.databinding.observe
import org.sugr.gearshift.viewmodel.rxutil.debounce
import org.sugr.gearshift.viewmodel.rxutil.singleOf

class ProfileEditorViewModel(tag: String, log: Logger,
                             private val ctx: Context,
                             private val prefs : SharedPreferences,
                             private val gson: Gson,
                             private var profile: Profile = transmissionProfile(),
                             private val apiFactory : ApiFactory = ::apiOf) :
        RetainedViewModel<ProfileEditorViewModel.Consumer>(tag, log), LeaveBlocker {

    val profileName = ObservableField("Default")
    val profileNameValid = ObservableBoolean(true)
    val host = ObservableField("")
    val hostValid = ObservableBoolean(true)
    val port = ObservableField("9091")
    val portValid = ObservableBoolean(true)
    val useSSL = ObservableBoolean(false)

    val updateIntervalLabel = ObservableField("")
    val updateIntervalValue = ObservableInt(0)

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

    val sectionCollapse = ObservableArrayMap<String, Boolean>()

    private val updateIntervalEntries: Array<String>
    private val updateIntervalValues: IntArray

    interface Consumer {
        fun showUpdateIntervalPicker(current: Int) : Single<Int>
    }

    init {
        val resources = ctx.resources
        updateIntervalEntries = resources.getStringArray(R.array.pref_update_interval_entries)
        updateIntervalValues = resources.getIntArray(R.array.pref_update_interval_values)

        updateIntervalLabel.set(updateIntervalEntries[0])
        updateIntervalValue.set(updateIntervalValues[0])

        // Unloaded profiles have default paths set
        path.set(profile.path)

        if (!profile.loaded) {
            profile = profile.load(prefs)
        }

        // If a profile doesn't exist, it's not marked as loaded
        if (profile.loaded) {
            valuesFromProfile()
        }

        sectionCollapse.apply {
            put("updates", false)
            put("auth", true)
            put("proxy", true)
            put("advanced", true)
        }

        setupValidation()
    }

    override fun canLeave(): Boolean {
        if (isValid()) {
            return true
        }

        refreshValidity()
        return false
    }

    fun check() : Single<Boolean> {
        if (canLeave()) {
            return apiFactory(profile.copy(temporary = true), ctx, prefs, gson, log, BuildConfig.DEBUG).version()
                    .takeUntil(takeUntilUnbind().toFlowable(BackpressureStrategy.LATEST))
                    .map { version -> version.isNotEmpty() }
        } else {
            return singleOf(false)
        }
    }

    fun onPickUpdateInterval() {
        consumer?.showUpdateIntervalPicker(updateIntervalValue.get())
                ?.takeUntil(takeUntilUnbind().toFlowable(BackpressureStrategy.LATEST))
                ?.subscribe { update -> setUpdateInterval(update) }
    }

    fun isValid() : Boolean {
        if (!formValid.get()) {
            return false
        }

        profile = profile.copy(
                name = profileName.get(),
                host = host.get(),
                port = port.get().toInt(),
                useSSL = useSSL.get(),
                updateInterval = updateIntervalValue.get(),
                username = username.get(),
                password = password.get(),
                proxyHost = proxyHost.get(),
                proxyPort = proxyPort.get().toInt(),
                timeout = timeout.get().toInt(),
                path = path.get()
        )

        return profile.valid
    }

    fun save() {
        if (isValid()) {
            profile.save(prefs)
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

    fun toggleCollapseSection(section: String) {
        sectionCollapse.put(section, !(sectionCollapse.get(section) ?: true))
    }

    private fun valuesFromProfile() {
        profileName.set(profile.name)
        host.set(profile.host)
        port.set(profile.port.toString())
        useSSL.set(profile.useSSL)

        setUpdateInterval(profile.updateInterval)

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

    private fun setupValidation() {
        profileName.observe().debounce().subscribe {
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

        PropertyChangedCallback {
            formValid.set(profileNameValid.get() && hostValid.get() && portValid.get() &&
                    proxyHostValid.get() && proxyPortValid.get() && timeoutValid.get())
        }.addTo(profileNameValid, hostValid, portValid,
                proxyHostValid, proxyPortValid, timeoutValid)
    }
}

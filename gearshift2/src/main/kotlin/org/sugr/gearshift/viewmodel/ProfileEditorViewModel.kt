package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.databinding.Observable
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.view.View
import org.sugr.gearshift.R
import org.sugr.gearshift.app
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.viewmodel.databinding.PropertyChangedDebouncer
import org.sugr.gearshift.viewmodel.util.ResourceUtils
import rx.functions.Action1

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
    val proxyPort = ObservableField("")

    val timeout = ObservableInt(40)
    val path = ObservableField("")

    val formValid = ObservableBoolean(false)

    private val updateIntervalEntries: Array<String>
    private val updateIntervalValues: IntArray

    private val fullUpdateEntries: Array<String>
    private val fullUpdateValues: IntArray

    interface Consumer {
        fun showUpdateIntervalPicker(current: Int)
        fun showFullUpdatePicker(current: Int)
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

        path.set(profile.path)

        setupValidation()
    }

    fun onPickUpdateInterval(unused: View) {
        consumer?.showUpdateIntervalPicker(updateIntervalValue.get())
    }

    fun onPickFullUpdate(unused: View) {
        consumer?.showFullUpdatePicker(fullUpdateValue.get())
    }

    private fun setupValidation() {
        port.addOnPropertyChangedCallback(PropertyChangedDebouncer(takeUntilDestroy())
                .subscribeChain(Action1 { o ->
                    // Always reset the value to counter TextView's setError behavior
                    portValid.set(true)
                    portValid.set(port.get() > 0 && port.get() < 65535)
                }))

        val formValidator = object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(o: Observable?, i: Int) {
                formValid.set(portValid.get())
            }

        }

        portValid.addOnPropertyChangedCallback(formValidator)
    }
}

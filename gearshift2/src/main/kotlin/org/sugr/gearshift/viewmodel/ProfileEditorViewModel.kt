package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import android.view.View
import org.sugr.gearshift.App
import org.sugr.gearshift.R
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.viewmodel.util.ResourceUtils

class ProfileEditorViewModel(prefs: SharedPreferences, private val profile: Profile) : RetainedViewModel<ProfileEditorViewModel.Consumer>(prefs) {
    val profileName = ObservableField("Default")
    val host = ObservableField("")
    val port = ObservableInt(9091)
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

    private val updateIntervalEntries: Array<String>
    private val updateIntervalValues: IntArray

    private val fullUpdateEntries: Array<String>
    private val fullUpdateValues: IntArray

    interface Consumer

    init {
        val resources = App.get().resources
        updateIntervalEntries = resources.getStringArray(R.array.pref_update_interval_entries)
        updateIntervalValues = ResourceUtils.stringArrayAsInt(R.array.pref_update_interval_values)

        fullUpdateEntries = resources.getStringArray(R.array.pref_full_update_entries)
        fullUpdateValues = ResourceUtils.stringArrayAsInt(R.array.pref_full_update_values)

        updateIntervalLabel.set(updateIntervalEntries[0])
        updateIntervalValue.set(updateIntervalValues[0])

        fullUpdateLabel.set(fullUpdateEntries[0])
        fullUpdateValue.set(fullUpdateValues[0])

        path.set(profile.path)
    }

    fun onPickUpdateInterval(unused: View) {

    }

    fun onPickFullUpdate(unused: View) {

    }
}

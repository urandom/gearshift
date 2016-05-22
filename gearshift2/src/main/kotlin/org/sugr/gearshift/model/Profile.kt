package org.sugr.gearshift.model

import android.text.TextUtils

open class Profile {
    var id: String = ""
    var name = ""
    var host = ""
    var port = 0
    var path = ""
    var username = ""
    var password = ""

    var useSSL = false

    var timeout = 40
    var retries = 3

    var lastDirectory = ""
    var moveData = true
    var deleteLocal = false
    var startPaused = false

    var directories = mutableListOf(String)

    var proxyHost = ""
    var proxyPort = 8080

    var updateInterval = 1
    var fullUpdate = 2

    var color: Int = 0

    fun proxyEnabled(): Boolean {
        return !TextUtils.isEmpty(proxyHost) && proxyPort > 0
    }
}

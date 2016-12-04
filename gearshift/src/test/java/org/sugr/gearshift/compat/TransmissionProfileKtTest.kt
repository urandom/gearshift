package org.sugr.gearshift.compat

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nhaarman.mockito_kotlin.*
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger

class TransmissionProfileKtTest {
    val table = mapOf<String, Any>(
            "profile_name1" to "name1",
            "profile_host1" to "host1",
            "profile_port1" to "1",
            "profile_path1" to "path1",
            "profile_username1" to "user1",
            "profile_password1" to "pass1",
            "profile_user_ssl1" to false,
            "profile_timeout1" to "1",
            "profile_retries1" to "1",
            "profile_directories1" to setOf("dir1", "dir11"),
            "profile_last_directory1" to "dir1",
            "profile_move_data1" to false,
            "profile_start_paused1" to false,
            "profile_proxy_host1" to "proxy_host1",
            "profile_proxy_port1" to "1111",
            "profile_update_interval1" to "1",
            "profile_full_update1" to "1",
            "profile_color1" to 1,

            "profile_name2" to "name2",
            "profile_host2" to "host2",
            "profile_port2" to "2",
            "profile_path2" to "path2",
            "profile_username2" to "user2",
            "profile_password2" to "pass2",
            "profile_user_ssl2" to true,
            "profile_timeout2" to "2",
            "profile_retries2" to "2",
            "profile_directories2" to setOf("dir2", "dir22"),
            "profile_last_directory2" to "dir2",
            "profile_move_data2" to true,
            "profile_start_paused2" to true,
            "profile_proxy_host2" to "proxy_host2",
            "profile_proxy_port2" to "2222",
            "profile_update_interval2" to "2",
            "profile_full_update2" to "2",
            "profile_color2" to 2
    )

    @Test
    fun migrateTransmissionProfiles() {
        val gson = Gson()

        var defaultEditor = mock<SharedPreferences.Editor> {}
        whenever(defaultEditor.putString(any(), any())).thenAnswer {
            val obj = gson.fromJson<JsonObject>(it.arguments[1] as String)

            if (it.arguments[0] == "profile_1") {
                assertThat(obj["name"].string, `is`(table["profile_name1"]))
            } else if (it.arguments[0] == "profile_2") {
                assertThat(obj["name"].string, `is`(table["profile_name2"]))
            } else {
                assertThat("Invalid case", false)
            }

            defaultEditor
        }

        val defaultPrefs = mock<SharedPreferences> {
            on { contains(C.PREF_MIGRATION_V1) } doReturn false
            on { getStringSet(eq(C.PREF_PROFILES), any<Set<String>>()) } doReturn setOf("1", "2")
            on { edit() } doReturn defaultEditor
        }

        val compatEditor = mock<SharedPreferences.Editor> {}
        whenever(compatEditor.clear()).thenReturn(compatEditor)

        val compatPrefs = mock<SharedPreferences> {
            on { edit() } doReturn compatEditor
            on { contains("profile_name1") } doReturn true
            on { contains("profile_name2") } doReturn true
            on { getString(any(), any())}.thenAnswer { table[it.arguments[0] as String] }
            on { getBoolean(any(), any())}.thenAnswer { table[it.arguments[0] as String] }
            on { getStringSet(any(), any())}.thenAnswer { table[it.arguments[0] as String] }
            on { getInt(any(), any())}.thenAnswer { table[it.arguments[0] as String] }
        }

        val context = mock<Context> {
            on { getSharedPreferences("profiles", Activity.MODE_PRIVATE) } doReturn compatPrefs
        }

        val log = mock<Logger> {
            on { D(any<String>()) }.then { println(it.arguments[0]) }
        }

        migrateTransmissionProfiles(context, defaultPrefs, log)
    }

}


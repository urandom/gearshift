package org.sugr.gearshift.model

import android.content.SharedPreferences
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nhaarman.mockito_kotlin.*
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger

class ProfileTest {
    @Test
    fun getValid() {
        assertThat("Empty profile isn't valid", !Profile().valid)
        assertThat("Profile with example.com host isn't valid", !Profile(name = "default", host = "example.com", port = 8080).valid)
        assertThat("Profile with 0 port isn't valid", !Profile(name = "default", host = "sugr.org", port = 0).valid)
        assertThat("Profile with > 65535 port isn't valid", !Profile(name = "default", host = "sugr.org", port = 70000).valid)
        assertThat("Profile with regular host and port but no name isn't valid", !Profile(host = "sugr.org", port = 8080).valid)
        assertThat("Profile with regular host and port is valid", Profile(name = "default", host = "sugr.org", port = 8080).valid)
        assertThat("Profile with example.com proxy host isn't valid", !Profile(name = "default", host = "sugr.org", port = 8080, proxyHost = "example.com", proxyPort = 8080).valid)
        assertThat("Profile with regular (proxy)host and port is valid", Profile(name = "default", host = "sugr.org", port = 8080, proxyHost = "sugr.org", proxyPort = 10000).valid)
    }

    @Test
    fun proxyEnabled() {
        assertThat("Empty profile can't have an enabled proxy", !Profile().proxyEnabled)
        assertThat("Valid profile without a set proxy host", !Profile(name = "default", host = "sugr.org", port = 8080).proxyEnabled)
        assertThat("Profile without a set proxy host", !Profile(name = "default", host = "sugr.org", port = 8080, proxyHost = "sugr.org").proxyEnabled)
        assertThat("Profile with example.com proxy host", !Profile(name = "default", host = "sugr.org", port = 8080, proxyHost = "example.com", proxyPort = 8080).proxyEnabled)
        assertThat("Profile with 0 proxy port", !Profile(name = "default", host = "sugr.org", port = 8080, proxyHost = "sugr.org", proxyPort = 0).proxyEnabled)
        assertThat("Profile with > 65536 proxy port", !Profile(name = "default", host = "sugr.org", port = 8080, proxyHost = "sugr.org", proxyPort = 65536).proxyEnabled)
        assertThat("Profile with valid proxy", Profile(name = "default", host = "sugr.org", port = 8080, proxyHost = "sugr.org", proxyPort = 65535).proxyEnabled)
    }

    @Test
    fun load() {
        assertThat("Not loaded", !Profile(id = "id").loaded)

        val prefs1 = mock<SharedPreferences> {
            on { getString("profile_id", null) } doReturn jsonObject(
                    "id" to "id", "type" to "TRANSMISSION", "name" to "default",
                    "host" to "sugr.org", "port" to 8080
            ).toString()
        }

        val profile1 = Profile(id = "id").load(prefs1)

        assertThat("loaded", profile1.loaded)
        assertThat("id", `is`(profile1.id))
        assertThat(ProfileType.TRANSMISSION, `is`(profile1.type))
        assertThat("default", `is`(profile1.name))
        assertThat("sugr.org", `is`(profile1.host))
        assertThat(8080, `is`(profile1.port))
    }

    @Test
    fun loadNonExistent() {
        assertThat("Not loaded", !Profile(id = "id").loaded)

        val prefs1 = mock<SharedPreferences> {
            on { getString("profile_id", null) } doReturn null as String?
        }

        val profile1 = Profile(id = "id").load(prefs1)

        assertThat("loaded", !profile1.loaded)
    }

    @Test
    fun loadInvalid() {
        assertThat("Not loaded", !Profile(id = "id").loaded)

        val prefs1 = mock<SharedPreferences> {
            on { getString("profile_id", null) } doReturn "Nope"
        }

        val log = mock<Logger> {
            on { E(any<String>(), any()) }.then { println(it.arguments[0]) }
        }

        val profile1 = Profile(id = "id").load(prefs1, log)

        assertThat("loaded", !profile1.loaded)
    }

    @Test
    fun save() {
        val gson = Gson()

        val editor = mock<SharedPreferences.Editor> {}
        whenever(editor.putString(any(), any())).thenAnswer {
            val obj = gson.fromJson<JsonObject>(it.arguments[1] as String)

            assertThat("id1", `is`(obj["id"].string))
            assertThat("TRANSMISSION", `is`(obj["type"].string))
            assertThat("test1", `is`(obj["name"].string))
            assertThat("sugr.org", `is`(obj["host"].string))
            assertThat(9091, `is`(obj["port"].int))
            assertThat("/transmission/rpc", `is`(obj["path"].string))

            editor
        }

        val prefs1 = mock<SharedPreferences> {
            on { edit() } doReturn editor
        }

        transmissionProfile().copy(id = "id1", name = "test1", host = "sugr.org").save(prefs1)

        verify(prefs1, times(1)).getStringSet(eq(C.PREF_PROFILES), any())
        verify(editor, times(1)).putStringSet(eq(C.PREF_PROFILES), eq(mutableSetOf("id1")))
    }

    @Test
    fun saveTemporary() {
        val prefs1 = mock<SharedPreferences> {
            on { getString("profile_id", null) } doReturn "Nope"
        }

        Profile(id = "id", temporary = true).save(prefs1)

        verify(prefs1, never()).edit()
    }

    @Test
    fun delete() {
        val editor = mock<SharedPreferences.Editor> {}

        val prefs1 = mock<SharedPreferences> {
            on { edit() } doReturn editor
            on { getStringSet(eq(C.PREF_PROFILES), any()) } doReturn mutableSetOf("id1", "id2")
        }

        Profile(id = "id1").delete(prefs1)

        verify(editor).remove("profile_id1")
        verify(prefs1).getStringSet(eq(C.PREF_PROFILES), any())
        verify(editor).putStringSet(eq(C.PREF_PROFILES), eq(mutableSetOf("id2")))
    }

}
package org.sugr.gearshift.unit.util;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MockEditor implements SharedPreferences.Editor {
    public Map<String, Object> data = new HashMap<>();
    public boolean applyCalled = false;
    public boolean commitCalled = false;

    @Override
    public SharedPreferences.Editor putString(String key, String value) {
        data.put(key, value);
        return this;
    }

    @Override
    public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
        data.put(key, values);
        return this;
    }

    @Override
    public SharedPreferences.Editor putInt(String key, int value) {
        data.put(key, value);
        return this;
    }

    @Override
    public SharedPreferences.Editor putLong(String key, long value) {
        data.put(key, value);
        return this;
    }

    @Override
    public SharedPreferences.Editor putFloat(String key, float value) {
        data.put(key, value);
        return this;
    }

    @Override
    public SharedPreferences.Editor putBoolean(String key, boolean value) {
        data.put(key, value);
        return this;
    }

    @Override
    public SharedPreferences.Editor remove(String key) {
        data.remove(key);
        return this;
    }

    @Override
    public SharedPreferences.Editor clear() {
        data.clear();
        return this;
    }

    @Override
    public boolean commit() {
        commitCalled = true;
        return false;
    }

    @Override
    public void apply() {
        applyCalled = true;
    }
}

// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import com.osfans.trime.util.WeakHashSet
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class PreferenceDelegate<T : Any>(
    val sharedPreferences: SharedPreferences,
    val key: String,
    val defaultValue: T,
) : ReadWriteProperty<Any?, T> {
    @Suppress("UNCHECKED_CAST")
    open fun getValue(): T = try {
        when (defaultValue) {
            is Int -> sharedPreferences.getInt(key, defaultValue)
            is Long -> sharedPreferences.getLong(key, defaultValue)
            is Float -> sharedPreferences.getFloat(key, defaultValue)
            is Boolean -> sharedPreferences.getBoolean(key, defaultValue)
            is String -> sharedPreferences.getString(key, defaultValue) ?: defaultValue
            else -> null
        } as T
    } catch (e: Exception) {
        setValue(defaultValue)
        defaultValue
    }

    open fun setValue(value: T) {
        sharedPreferences.edit {
            when (value) {
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value.map { it.toString() }.toHashSet())
            }
        }
    }

    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = getValue()

    override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) = setValue(value)

    interface Serializer<T : Any> {
        fun serialize(t: T): String

        fun deserialize(raw: String): T?
    }

    class SerializableDelegate<T : Any>(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: T,
        private val serializer: Serializer<T>,
    ) : PreferenceDelegate<T>(sharedPreferences, key, defaultValue) {
        override fun setValue(value: T) {
            sharedPreferences.edit { putString(key, serializer.serialize(value)) }
        }

        override fun getValue(): T = try {
            sharedPreferences.getString(key, null)?.let {
                serializer.deserialize(it)
            } ?: defaultValue
        } catch (e: Exception) {
            setValue(defaultValue)
            defaultValue
        }
    }

    fun interface OnChangeListener<in T : Any> {
        fun onChange(
            key: String,
            value: T,
        )
    }

    private lateinit var listeners: MutableSet<OnChangeListener<T>>

    /**
     * **WARN:** No anonymous listeners, please **KEEP** the reference!
     *
     * You may need to reference the listener once outside of it's container's constructor,
     * to prevent R8 from removing the field;
     * or simply mark the listener with [@Keep][androidx.annotation.Keep] .
     */
    fun registerOnChangeListener(listener: OnChangeListener<T>) {
        if (!::listeners.isInitialized) {
            listeners = WeakHashSet()
        }
        listeners.add(listener)
    }

    fun unregisterOnChangeListener(listener: OnChangeListener<T>) {
        if (!::listeners.isInitialized || listeners.isEmpty()) return
        listeners.remove(listener)
    }

    fun notifyChange() {
        if (!::listeners.isInitialized || listeners.isEmpty()) return
        val newValue = getValue()
        listeners.forEach { it.onChange(key, newValue) }
    }
}

/**
 * A boolean preference that is always `true`, regardless of what was persisted.
 *
 * Writes are forced back to `true` so the settings UI cannot leave the switch
 * showing an off state while the feature is actually enabled.
 */
class ForceOnPreferenceDelegate(
    sharedPreferences: SharedPreferences,
    key: String,
) : PreferenceDelegate<Boolean>(sharedPreferences, key, true) {
    override fun getValue(): Boolean {
        // Repair a stale "false" left behind by earlier versions so the
        // settings screen renders the switch as checked.
        if (!sharedPreferences.getBoolean(key, true)) setValue(true)
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    override fun setValue(value: Boolean) {
        // Keep the stored value in sync so the settings switch renders as checked.
        sharedPreferences.edit { putBoolean(key, true) }
    }
}

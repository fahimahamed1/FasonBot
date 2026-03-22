package com.fasonbot.app.bot

import android.content.Context
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class StateManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "bot_state"
        private const val KEY_CURRENT_STATE = "current_state"
        private const val KEY_STATE_DATA_PREFIX = "data_"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = ReentrantReadWriteLock()
    private val currentState = AtomicReference<String>("")

    fun saveState(state: String) {
        lock.write {
            currentState.set(state)
            prefs.edit().putString(KEY_CURRENT_STATE, state).apply()
        }
    }

    fun getState(): String {
        return lock.read {
            currentState.get().ifEmpty {
                prefs.getString(KEY_CURRENT_STATE, "") ?: ""
            }
        }
    }

    fun saveStateData(key: String, value: String) {
        lock.write {
            prefs.edit().putString(KEY_STATE_DATA_PREFIX + key, value).apply()
        }
    }

    fun getStateData(key: String): String {
        return lock.read {
            prefs.getString(KEY_STATE_DATA_PREFIX + key, "") ?: ""
        }
    }

    fun clearState() {
        lock.write {
            currentState.set("")
            prefs.edit()
                .remove(KEY_CURRENT_STATE)
                .apply()
        }
    }

    fun clearAllStateData() {
        lock.write {
            currentState.set("")
            prefs.edit().clear().apply()
        }
    }

    fun hasActiveState(): Boolean = getState().isNotEmpty()
}

package com.example.hrapp.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "hrapp_preferences"
        private const val KEY_LOGGED_IN_EMPLOYEE_ID = "logged_in_employee_id"
        private const val KEY_THEME = "app_theme" // 0: Auto/System, 1: Light, 2: Dark
        private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_IS_ADMIN_ROLE = "is_admin_role"
    }

    var loggedInEmployeeId: Long
        get() = prefs.getLong(KEY_LOGGED_IN_EMPLOYEE_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_LOGGED_IN_EMPLOYEE_ID, value).apply()

    var appTheme: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    var isBiometricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRICS_ENABLED, value).apply()

    var isAutoLoginEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOGIN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOGIN, value).apply()

    var isAdminRoleActive: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN_ROLE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN_ROLE, value).apply()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_LOGGED_IN_EMPLOYEE_ID)
            .remove(KEY_IS_ADMIN_ROLE)
            .apply()
    }
}

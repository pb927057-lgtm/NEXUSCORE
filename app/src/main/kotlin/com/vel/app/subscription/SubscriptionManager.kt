package com.vel.app.subscription

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de la cuenta y suscripción de usuario para VEL.
 * Permite consultar el estado de la suscripción, fecha de expiración y los dispositivos autorizados.
 */
class SubscriptionManager(private val context: Context? = null) {

    companion object {
        private const val PREFS_NAME = "vel_subscription_prefs"
        private const val KEY_PROFILE_JSON = "key_profile_json"

        @Volatile
        private var instance: SubscriptionManager? = null

        fun getInstance(context: Context? = null): SubscriptionManager {
            return instance ?: synchronized(this) {
                instance ?: SubscriptionManager(context?.applicationContext ?: context).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var cachedProfile: UserSubscriptionProfile? = null

    @Synchronized
    fun getSubscriptionProfile(): UserSubscriptionProfile {
        cachedProfile?.let { return it }

        val savedJson = prefs?.getString(KEY_PROFILE_JSON, null)
        val jsonToParse = savedJson ?: UserSubscriptionProfile.DEFAULT_JSON
        val profile = try {
            UserSubscriptionProfile.fromJson(jsonToParse)
        } catch (e: Exception) {
            UserSubscriptionProfile.fromJson(UserSubscriptionProfile.DEFAULT_JSON)
        }

        cachedProfile = profile
        return profile
    }

    @Synchronized
    fun updateSubscriptionProfile(jsonString: String): UserSubscriptionProfile {
        val updated = UserSubscriptionProfile.fromJson(jsonString)
        cachedProfile = updated
        prefs?.edit()?.putString(KEY_PROFILE_JSON, jsonString)?.apply()
        return updated
    }

    fun isDeviceAuthorized(deviceId: String): Boolean {
        val profile = getSubscriptionProfile()
        if (!profile.isActive) return false
        val device = profile.linkedDevices[deviceId] ?: return false
        return device.isActive
    }
}

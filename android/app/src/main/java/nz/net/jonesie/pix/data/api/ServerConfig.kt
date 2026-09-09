package nz.net.jonesie.pix.data.api

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Persists the self-hosted pix server URL the user enters on first run. Replaces the
 * old compile-time BASE_URL constant so one APK build works against any deployment.
 */
object ServerConfig {
    private const val PREFS = "pix_server_config"
    private const val KEY_BASE_URL = "base_url"

    fun getBaseUrl(context: Context): String? =
        prefs(context).getString(KEY_BASE_URL, null)

    fun setBaseUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_BASE_URL, url).apply()
    }

    /** Adds a scheme and trailing slash if missing so it can be used as a Retrofit base URL. */
    fun normalize(input: String): String {
        var url = input.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        if (!url.endsWith("/")) url += "/"
        return url
    }

    fun isValid(url: String): Boolean = url.toHttpUrlOrNull() != null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

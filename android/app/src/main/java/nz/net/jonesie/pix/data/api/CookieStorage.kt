package nz.net.jonesie.pix.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Persists cookies (the pix_admin session cookie) in EncryptedSharedPreferences so
 * login survives app restarts. Cookie.toString()/Cookie.parse() round-trip the full
 * Set-Cookie attributes (domain, path, expiry, secure), which is what makes this safe
 * to replay on later requests.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pix_cookies",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val existing = loadRaw(url.host).associateBy { it.name }.toMutableMap()
        for (cookie in cookies) {
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                existing.remove(cookie.name)
            } else {
                existing[cookie.name] = cookie
            }
        }
        prefs.edit()
            .putStringSet(url.host, existing.values.map { it.toString() }.toSet())
            .apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return loadRaw(url.host).filter { it.expiresAt > now }
    }

    private fun loadRaw(host: String): List<Cookie> {
        val raw = prefs.getStringSet(host, emptySet()) ?: emptySet()
        return raw.mapNotNull { Cookie.parse(HttpUrl.Builder().scheme("https").host(host).build(), it) }
    }
}

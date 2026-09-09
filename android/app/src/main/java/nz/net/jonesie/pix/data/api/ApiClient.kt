package nz.net.jonesie.pix.data.api

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {

    val json = Json { ignoreUnknownKeys = true }

    private var api: PixApi? = null
    private var cachedBaseUrl: String? = null

    /** Builds (or reuses) the Retrofit client for the currently configured server URL. */
    fun get(context: Context): PixApi {
        val baseUrl = ServerConfig.getBaseUrl(context)
            ?: error("No server configured yet.")
        val cached = api
        if (cached != null && cachedBaseUrl == baseUrl) return cached
        return buildApi(context.applicationContext, baseUrl).also {
            api = it
            cachedBaseUrl = baseUrl
        }
    }

    private fun buildApi(context: Context, baseUrl: String): PixApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .cookieJar(PersistentCookieJar(context))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES) // large video uploads
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(PixApi::class.java)
    }
}

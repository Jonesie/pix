package nz.net.jonesie.pix.data.api

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiConfig {
    const val BASE_URL = "https://pix.jonesie.net.nz/"
}

object ApiClient {

    val json = Json { ignoreUnknownKeys = true }

    private var api: PixApi? = null

    fun get(context: Context): PixApi {
        return api ?: buildApi(context.applicationContext).also { api = it }
    }

    private fun buildApi(context: Context): PixApi {
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
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(PixApi::class.java)
    }
}

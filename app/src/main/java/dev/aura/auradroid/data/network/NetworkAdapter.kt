package dev.aura.auradroid.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkAdapter {

    private const val BASE_URL = "http://localhost:8080/" // Default local server
    private var customBaseUrl: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private val retrofit: Retrofit
        get() = Retrofit.Builder()
            .baseUrl(customBaseUrl ?: BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val auraApiService: AuraApiService
        get() = retrofit.create(AuraApiService::class.java)

    fun setBaseUrl(baseUrl: String) {
        customBaseUrl = baseUrl
    }

    fun getBaseUrl(): String = customBaseUrl ?: BASE_URL
}

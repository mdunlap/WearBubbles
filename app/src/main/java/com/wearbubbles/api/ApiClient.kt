package com.wearbubbles.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var api: BlueBubblesApi? = null
    private var currentBaseUrl: String? = null

    fun getInstance(baseUrl: String): BlueBubblesApi {
        val normalizedUrl = baseUrl.trimEnd('/')
        if (api != null && currentBaseUrl == normalizedUrl) {
            return api!!
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$normalizedUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(BlueBubblesApi::class.java)
        currentBaseUrl = normalizedUrl
        return api!!
    }

    fun reset() {
        api = null
        currentBaseUrl = null
    }
}

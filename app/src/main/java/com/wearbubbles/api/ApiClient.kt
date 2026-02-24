package com.wearbubbles.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var api: BlueBubblesApi? = null
    private var currentBaseUrl: String? = null
    private var httpClient: OkHttpClient? = null

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    fun getInstance(baseUrl: String): BlueBubblesApi {
        val normalizedUrl = baseUrl.trimEnd('/')
        if (api != null && currentBaseUrl == normalizedUrl) {
            return api!!
        }

        val client = httpClient ?: OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        httpClient = client

        val retrofit = Retrofit.Builder()
            .baseUrl("$normalizedUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
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

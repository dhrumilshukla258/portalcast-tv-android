package com.stalker.tv.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClient {
    fun create(baseUrl: String): XtreamApi {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()

        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(XtreamApi::class.java)
    }
}

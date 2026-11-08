package com.chai.aiskin.core.network

import com.chai.aiskin.BuildConfig
import com.chai.aiskin.core.constants.AppConstants
import com.chai.aiskin.data.remote.SkinApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun okHttp(auth: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(AppConstants.ANALYSIS_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(AppConstants.ANALYSIS_READ_TIMEOUT_SECONDS + 30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(auth)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC = method, URL, status, size only. Never headers (token) or bodies (photos).
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun skinApi(retrofit: Retrofit): SkinApi = retrofit.create(SkinApi::class.java)
}

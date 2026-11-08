package com.chai.aiskin.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Abstraction so the network layer does not depend on Firebase directly. */
interface TokenProvider {
    suspend fun token(forceRefresh: Boolean): String?
    suspend fun appCheckToken(): String?
}

/**
 * Adds `Authorization: Bearer <Firebase ID token>` (and the App Check token when available).
 * On a 401 it retries exactly once with a force-refreshed token.
 * Runs on OkHttp's background threads, so blocking on the suspend provider is acceptable here.
 */
@Singleton
class AuthInterceptor @Inject constructor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.encodedPath.endsWith("/health")) return chain.proceed(original)

        val (token, appCheck) = kotlinx.coroutines.runBlocking { tokens.token(false) to tokens.appCheckToken() }
        val first = chain.proceed(original.withAuth(token, appCheck))
        if (first.code != 401 || token == null) return first

        val refreshed = kotlinx.coroutines.runBlocking { tokens.token(true) } ?: return first
        if (refreshed == token) return first
        first.close()
        return chain.proceed(original.withAuth(refreshed, appCheck))
    }

    private fun okhttp3.Request.withAuth(token: String?, appCheck: String?): okhttp3.Request {
        val b = newBuilder()
        if (token != null) b.header("Authorization", "Bearer $token")
        if (appCheck != null) b.header("X-Firebase-AppCheck", appCheck)
        return b.build()
    }
}

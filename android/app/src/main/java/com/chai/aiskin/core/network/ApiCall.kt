package com.chai.aiskin.core.network

import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.data.model.toDomain
import com.chai.aiskin.data.remote.dto.ApiEnvelope
import com.chai.aiskin.data.remote.dto.ApiErrorEnvelope
import com.chai.aiskin.data.remote.dto.InsufficientQualityDetailsDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Shared JSON config: tolerant of new fields, strict on types. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Executes a Retrofit call and maps transport, HTTP and parsing failures to [AppError]. */
suspend fun <T> apiCall(block: suspend () -> Response<ApiEnvelope<T>>): AppResult<T> = try {
    val response = block()
    val body = response.body()
    if (response.isSuccessful && body?.success == true && body.data != null) {
        AppResult.Success(body.data)
    } else if (response.isSuccessful) {
        AppResult.Failure(AppError.Server("Respons server tidak valid."))
    } else {
        AppResult.Failure(mapHttpError(response.code(), response.errorBody()?.string()))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: SocketTimeoutException) {
    AppResult.Failure(AppError.Timeout())
} catch (e: InterruptedIOException) {
    AppResult.Failure(AppError.Timeout())
} catch (e: IOException) {
    AppResult.Failure(AppError.Network())
} catch (e: SerializationException) {
    AppResult.Failure(AppError.Server("Respons server tidak dapat dibaca."))
} catch (e: IllegalArgumentException) {
    AppResult.Failure(AppError.Server("Respons server tidak dapat dibaca."))
}

fun mapHttpError(status: Int, rawBody: String?): AppError {
    val envelope = rawBody?.let { runCatching { ApiJson.decodeFromString(ApiErrorEnvelope.serializer(), it) }.getOrNull() }
    val code = envelope?.error?.code
    val msg = envelope?.error?.message?.takeIf { it.isNotBlank() }
    return when {
        status == 401 -> AppError.Unauthenticated(msg ?: AppError.Unauthenticated().message)
        status == 403 -> AppError.Forbidden(msg ?: AppError.Forbidden().message)
        status == 404 -> AppError.NotFound(msg ?: AppError.NotFound().message)
        status == 413 -> AppError.ImageTooLarge(msg ?: AppError.ImageTooLarge().message)
        status == 415 -> AppError.UnsupportedImage(msg ?: AppError.UnsupportedImage().message)
        status == 422 && code == "INSUFFICIENT_IMAGE_QUALITY" -> {
            val details = envelope.error.details?.let {
                runCatching { ApiJson.decodeFromJsonElement<InsufficientQualityDetailsDto>(it) }.getOrNull()
            }
            AppError.InsufficientQuality(
                message = msg ?: AppError.InsufficientQuality().message,
                imageQuality = details?.imageQuality?.toDomain(),
                hints = details?.imageQuality?.limitations.orEmpty(),
            )
        }
        status == 400 || status == 422 -> AppError.Validation(msg ?: "Permintaan tidak valid.")
        status == 429 -> AppError.RateLimited(msg ?: AppError.RateLimited().message)
        code == "NOT_CONFIGURED" -> AppError.NotConfigured(msg ?: "Layanan belum dikonfigurasi di server.")
        code == "AI_TIMEOUT" || status == 504 -> AppError.Timeout(msg ?: AppError.Timeout().message)
        code?.startsWith("AI_") == true || status == 502 || status == 503 ->
            AppError.AiUnavailable(msg ?: "Layanan AI sedang tidak tersedia. Silakan coba lagi.")
        else -> AppError.Server(msg ?: AppError.Server().message)
    }
}

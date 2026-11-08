package com.chai.aiskin.core.common

import com.chai.aiskin.domain.model.ImageQuality

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

/** Every failure the UI can show. Messages are user-facing Indonesian text. */
sealed class AppError(open val message: String) {
    data class Network(override val message: String = "Tidak dapat terhubung ke server. Periksa koneksi internet Anda.") : AppError(message)
    data class Timeout(override val message: String = "Permintaan memakan waktu terlalu lama. Silakan coba lagi.") : AppError(message)
    data class Unauthenticated(override val message: String = "Sesi Anda telah berakhir. Silakan masuk kembali.") : AppError(message)
    data class Forbidden(override val message: String = "Anda tidak memiliki akses ke data ini.") : AppError(message)
    data class NotFound(override val message: String = "Data tidak ditemukan.") : AppError(message)
    data class Validation(override val message: String) : AppError(message)
    data class ImageTooLarge(override val message: String = "Ukuran foto terlalu besar.") : AppError(message)
    data class UnsupportedImage(override val message: String = "Format foto tidak didukung.") : AppError(message)
    data class InsufficientQuality(
        override val message: String = "Kualitas foto belum cukup untuk dianalisis.",
        val imageQuality: ImageQuality? = null,
        val hints: List<String> = emptyList(),
    ) : AppError(message)
    data class RateLimited(override val message: String = "Terlalu banyak permintaan. Coba lagi beberapa saat lagi.") : AppError(message)
    data class AiUnavailable(override val message: String) : AppError(message)
    data class NotConfigured(override val message: String) : AppError(message)
    data class Server(override val message: String = "Terjadi kesalahan pada server.") : AppError(message)
    data class Auth(override val message: String) : AppError(message)
    data class Unknown(override val message: String = "Terjadi kesalahan yang tidak terduga.") : AppError(message)

    /** Whether retrying the same request could reasonably succeed. */
    val isRetryable: Boolean
        get() = this is Network || this is Timeout || this is RateLimited || this is AiUnavailable || this is Server
}

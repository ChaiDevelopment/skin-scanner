package com.chai.aiskin.data.repository

import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.common.DispatcherProvider
import com.chai.aiskin.core.common.map
import com.chai.aiskin.core.network.apiCall
import com.chai.aiskin.data.model.toDomain
import com.chai.aiskin.data.remote.SkinApi
import com.chai.aiskin.data.remote.dto.AccountDeletionRequestDto
import com.chai.aiskin.data.remote.dto.FeedbackRequestDto
import com.chai.aiskin.data.remote.dto.ProductsRequestDto
import com.chai.aiskin.domain.model.HistoryPage
import com.chai.aiskin.domain.model.ProductSuggestions
import com.chai.aiskin.domain.model.SkinAnalysis
import com.chai.aiskin.domain.repository.AnalysisRepository
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All analysis data goes through the backend, which verifies the Firebase token,
 * enforces ownership and reads/writes Firestore with the Admin SDK.
 */
@Singleton
class AnalysisRepositoryImpl @Inject constructor(
    private val api: SkinApi,
    private val dispatchers: DispatcherProvider,
) : AnalysisRepository {

    override suspend fun analyze(image: File, storePhoto: Boolean): AppResult<SkinAnalysis> = withContext(dispatchers.io) {
        val part = MultipartBody.Part.createFormData("image", "scan.jpg", image.asRequestBody("image/jpeg".toMediaType()))
        val flag = storePhoto.toString().toRequestBody("text/plain".toMediaType())
        apiCall { api.analyze(part, flag) }.map { it.analysis.toDomain() }
    }

    override suspend fun history(cursor: String?, limit: Int): AppResult<HistoryPage> = withContext(dispatchers.io) {
        apiCall { api.history(limit, cursor) }.map { it.toDomain() }
    }

    override suspend fun get(id: String): AppResult<SkinAnalysis> = withContext(dispatchers.io) {
        apiCall { api.get(id) }.map { it.toDomain() }
    }

    override suspend fun delete(id: String): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall { api.delete(id) }.map { }
    }

    override suspend fun deletePhoto(id: String): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall { api.deletePhoto(id) }.map { }
    }

    override suspend fun sendFeedback(id: String, helpful: Boolean, rating: Int?, comment: String?): AppResult<Unit> =
        withContext(dispatchers.io) {
            apiCall { api.feedback(id, FeedbackRequestDto(helpful, rating, comment?.trim()?.takeIf { it.isNotEmpty() })) }.map { }
        }

    override suspend fun findProducts(id: String, refresh: Boolean): AppResult<ProductSuggestions> = withContext(dispatchers.io) {
        apiCall { api.products(id, ProductsRequestDto(refresh)) }.map { it.toDomain() }
    }

    override suspend fun deleteAccount(): AppResult<Unit> = withContext(dispatchers.io) {
        apiCall { api.deleteAccount(AccountDeletionRequestDto()) }.map { }
    }
}

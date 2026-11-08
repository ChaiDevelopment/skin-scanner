package com.chai.aiskin.data.remote

import com.chai.aiskin.data.remote.dto.AccountDeletionRequestDto
import com.chai.aiskin.data.remote.dto.AnalysisDto
import com.chai.aiskin.data.remote.dto.AnalyzeResponseDto
import com.chai.aiskin.data.remote.dto.ApiEnvelope
import com.chai.aiskin.data.remote.dto.FeedbackRequestDto
import com.chai.aiskin.data.remote.dto.GenericAckDto
import com.chai.aiskin.data.remote.dto.HistoryResponseDto
import com.chai.aiskin.data.remote.dto.ProductSuggestionsDto
import com.chai.aiskin.data.remote.dto.ProductsRequestDto
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Auth header is added by [com.chai.aiskin.core.network.AuthInterceptor]. */
interface SkinApi {
    @GET("api/v1/health")
    suspend fun health(): Response<ApiEnvelope<JsonObject>>

    @Multipart
    @POST("api/v1/skin-analysis")
    suspend fun analyze(
        @Part image: MultipartBody.Part,
        @Part("storePhoto") storePhoto: RequestBody,
    ): Response<ApiEnvelope<AnalyzeResponseDto>>

    @GET("api/v1/skin-analysis/history")
    suspend fun history(@Query("limit") limit: Int, @Query("cursor") cursor: String?): Response<ApiEnvelope<HistoryResponseDto>>

    @GET("api/v1/skin-analysis/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiEnvelope<AnalysisDto>>

    @DELETE("api/v1/skin-analysis/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiEnvelope<GenericAckDto>>

    @DELETE("api/v1/skin-analysis/{id}/photo")
    suspend fun deletePhoto(@Path("id") id: String): Response<ApiEnvelope<GenericAckDto>>

    @POST("api/v1/skin-analysis/{id}/feedback")
    suspend fun feedback(@Path("id") id: String, @Body body: FeedbackRequestDto): Response<ApiEnvelope<GenericAckDto>>

    @POST("api/v1/skin-analysis/{id}/products")
    suspend fun products(@Path("id") id: String, @Body body: ProductsRequestDto): Response<ApiEnvelope<ProductSuggestionsDto>>

    @POST("api/v1/user/account-deletion")
    suspend fun deleteAccount(@Body body: AccountDeletionRequestDto): Response<ApiEnvelope<GenericAckDto>>
}

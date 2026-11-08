package com.chai.aiskin.data

import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.network.ApiJson
import com.chai.aiskin.core.network.mapHttpError
import com.chai.aiskin.data.model.toDomain
import com.chai.aiskin.data.remote.dto.AnalyzeResponseDto
import com.chai.aiskin.data.remote.dto.ApiEnvelope
import com.chai.aiskin.domain.model.FaceArea
import com.chai.aiskin.domain.model.ObservationCategory
import com.chai.aiskin.domain.model.RecommendationPriority
import com.chai.aiskin.domain.model.Severity
import com.chai.aiskin.domain.model.Texture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiParsingTest {

    /** Same shape the backend returns (backend/tests/api.test.ts). */
    private val analyzeJson = """
        {"success":true,"data":{"analysisId":"6f1c","status":"completed","analysis":{
          "analysisId":"6f1c","createdAt":"2026-11-08T09:00:00.000Z","status":"completed","analysisVersion":"1.0","model":"gemini-3.5-flash",
          "overallSummary":"Kulit tampak cukup merata.",
          "imageQuality":{"status":"good","lighting":"adequate","faceVisibility":"clear","limitations":[]},
          "observations":[{"category":"pores","severity":"moderate","confidence":0.85,"observation":"Pori terlihat","affectedArea":"cheeks"},
                          {"category":"brand_new_category","severity":"weird","confidence":3,"observation":"x","affectedArea":"elbow"}],
          "skinAppearance":{"texture":"slightly_uneven","visibleRedness":"mild","visibleDryness":"unknown","pigmentation":"mild","visibleShine":"none"},
          "recommendations":[{"category":"cleansing","title":"Pembersih","description":"d","keyIngredients":["glycerin"],"routineTime":"both","priority":"low","cautions":""},
                             {"category":"sun_protection","title":"Sunscreen","description":"d","keyIngredients":[],"routineTime":"morning","priority":"high","cautions":""}],
          "routine":{"morning":["a"],"evening":["b"]},
          "limitations":["Ini adalah observasi visual berbasis AI, bukan diagnosis medis."],
          "professionalConsultationRecommended":false,"professionalConsultationReason":"",
          "photoStorageEnabled":false,"photoUrl":null,"feedback":null,"futureField":123}}}
    """.trimIndent()

    @Test fun `parses analysis response and maps defensively`() {
        val env = ApiJson.decodeFromString(ApiEnvelope.serializer(AnalyzeResponseDto.serializer()), analyzeJson)
        val a = env.data!!.analysis.toDomain()
        assertEquals("6f1c", a.id)
        assertEquals(Texture.SLIGHTLY_UNEVEN, a.appearance.texture)
        assertEquals(Severity.MODERATE, a.observations[0].severity)
        assertEquals(FaceArea.CHEEKS, a.observations[0].area)
        // Unknown values degrade safely instead of crashing.
        assertEquals(ObservationCategory.OTHER, a.observations[1].category)
        assertEquals(Severity.UNKNOWN, a.observations[1].severity)
        assertEquals(FaceArea.UNSPECIFIED, a.observations[1].area)
        assertEquals(1.0, a.observations[1].confidence, 0.0)
        // High priority first.
        assertEquals(RecommendationPriority.HIGH, a.recommendations[0].priority)
    }

    @Test fun `maps insufficient quality 422 with details`() {
        val body = """{"success":false,"error":{"code":"INSUFFICIENT_IMAGE_QUALITY","message":"Kualitas foto belum cukup.",
            "details":{"imageQuality":{"status":"poor","lighting":"too_dark","faceVisibility":"unclear","limitations":["Foto terlalu gelap."]},"limitations":[]}}}"""
        val e = mapHttpError(422, body)
        assertTrue(e is AppError.InsufficientQuality)
        e as AppError.InsufficientQuality
        assertEquals("too_dark", e.imageQuality!!.lighting)
        assertEquals(listOf("Foto terlalu gelap."), e.hints)
    }

    @Test fun `maps status codes`() {
        assertTrue(mapHttpError(401, null) is AppError.Unauthenticated)
        assertTrue(mapHttpError(403, null) is AppError.Forbidden)
        assertTrue(mapHttpError(404, null) is AppError.NotFound)
        assertTrue(mapHttpError(413, null) is AppError.ImageTooLarge)
        assertTrue(mapHttpError(415, null) is AppError.UnsupportedImage)
        assertTrue(mapHttpError(429, null) is AppError.RateLimited)
        assertTrue(mapHttpError(502, """{"error":{"code":"AI_PROVIDER_ERROR","message":"x"}}""") is AppError.AiUnavailable)
        assertTrue(mapHttpError(504, """{"error":{"code":"AI_TIMEOUT","message":"x"}}""") is AppError.Timeout)
        assertTrue(mapHttpError(503, """{"error":{"code":"NOT_CONFIGURED","message":"x"}}""") is AppError.NotConfigured)
        assertTrue(mapHttpError(500, "<html>not json</html>") is AppError.Server)
    }

    @Test fun `server message is surfaced to the user`() {
        assertEquals("Pesan server", mapHttpError(400, """{"error":{"code":"INVALID_INPUT","message":"Pesan server"}}""").message)
    }
}

package com.chai.aiskin.data

import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.common.DispatcherProvider
import com.chai.aiskin.core.network.ApiJson
import com.chai.aiskin.data.remote.SkinApi
import com.chai.aiskin.data.repository.AnalysisRepositoryImpl
import com.chai.aiskin.domain.model.ProductType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class AnalysisRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AnalysisRepositoryImpl
    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType())).build()
            .create(SkinApi::class.java)
        repo = AnalysisRepositoryImpl(api, dispatchers)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `history sends cursor and limit, parses page`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"items":[{"analysisId":"x1","createdAt":"2026-01-01T00:00:00Z","status":"completed","overallSummary":"s","imageQualityStatus":"good"}],"nextCursor":"x1"}}"""))
        val r = repo.history("c0", 10)
        val req = server.takeRequest()
        assertEquals("/api/v1/skin-analysis/history?limit=10&cursor=c0", req.path)
        assertTrue(r is AppResult.Success)
        assertEquals("x1", (r as AppResult.Success).data.nextCursor)
    }

    @Test fun `analyze uploads multipart with image and storePhoto`() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"success":false,"error":{"code":"INSUFFICIENT_IMAGE_QUALITY","message":"Kurang jelas"}}"""))
        val f = File.createTempFile("scan", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)); deleteOnExit() }
        val r = repo.analyze(f, storePhoto = true)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("name=\"image\""))
        assertTrue(body.contains("name=\"storePhoto\""))
        assertTrue(body.contains("true"))
        assertTrue((r as AppResult.Failure).error is AppError.InsufficientQuality)
    }

    @Test fun `findProducts posts refresh flag and maps suggestions defensively`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"success":true,"data":{"analysisId":"a1","summary":"Ringkas","createdAt":"2026-11-08T10:00:00Z","searchGrounded":false,
             "disclaimer":"Cek BPOM","generalTips":["Patch test"],
             "sources":[{"title":"ok","url":"https://example.com/a"},{"title":"bad","url":"http://example.com/b"}],
             "products":[{"name":"Gel SPF 45","brand":"Azarine","productType":"sunscreen","keyIngredients":["niacinamide"],
               "whyItFits":"w","howToUse":"h","routineTime":"morning","priceRange":"Rp40.000","cautions":"",
               "searchUrl":"https://www.google.com/search?q=x","shopUrl":"https://shopee.co.id/search?keyword=x"},
              {"name":"Mystery","brand":"B","productType":"time_machine","routineTime":"whenever",
               "searchUrl":"javascript:alert(1)","shopUrl":"http://shop.example","futureField":1}]}}
        """.trimIndent()))
        val r = repo.findProducts("a1", refresh = true)
        val req = server.takeRequest()
        assertEquals("/api/v1/skin-analysis/a1/products", req.path)
        assertEquals("POST", req.method)
        assertEquals("""{"refresh":true}""", req.body.readUtf8())

        val s = (r as AppResult.Success).data
        assertEquals(false, s.searchGrounded)
        assertEquals(listOf("https://example.com/a"), s.sources.map { it.url })
        assertEquals(ProductType.SUNSCREEN, s.products[0].type)
        assertEquals("Pagi", s.products[0].whenToUse)
        assertEquals(ProductType.OTHER, s.products[1].type)
        assertEquals("Pagi & malam", s.products[1].whenToUse)
        assertEquals("", s.products[1].searchUrl) // non-https links are never opened
        assertEquals("", s.products[1].shopUrl)
    }

    @Test fun `network interruption maps to Network error`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val r = repo.get("id")
        assertTrue((r as AppResult.Failure).error.let { it is AppError.Network || it is AppError.Timeout })
    }

    @Test fun `delete returns success`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"analysisId":"a","deleted":true}}"""))
        assertTrue(repo.delete("a") is AppResult.Success)
        assertEquals("DELETE", server.takeRequest().method)
    }
}

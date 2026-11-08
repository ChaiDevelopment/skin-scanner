package com.chai.aiskin.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityRulesTest {
    private val w = 1200
    private val h = 1600
    private val goodLight = LightMetrics(meanLuma = 130.0, clippedFraction = 0.01, sharpness = 120.0)

    private fun face(l: Float = 300f, t: Float = 400f, r: Float = 900f, b: Float = 1200f, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f) =
        DetectedFace(FaceBox(l, t, r, b), yaw, pitch, roll)

    @Test fun `well framed face passes`() {
        val v = FaceQualityRules.evaluate(listOf(face()), w, h, goodLight)
        assertTrue(v.isAcceptable)
        assertTrue(v.issues.isEmpty())
    }

    @Test fun `no face is blocking`() {
        val v = FaceQualityRules.evaluate(emptyList(), w, h, goodLight)
        assertFalse(v.isAcceptable)
        assertEquals(FaceIssue.NO_FACE, v.primaryIssue)
    }

    @Test fun `two similar faces are blocking`() {
        val v = FaceQualityRules.evaluate(listOf(face(), face(l = 50f, r = 600f)), w, h, goodLight)
        assertEquals(FaceIssue.MULTIPLE_FACES, v.primaryIssue)
    }

    @Test fun `tiny background face is ignored`() {
        val v = FaceQualityRules.evaluate(listOf(face(), face(l = 10f, t = 10f, r = 60f, b = 70f)), w, h, goodLight)
        assertTrue(v.isAcceptable)
    }

    @Test fun `far face is blocking`() {
        val v = FaceQualityRules.evaluate(listOf(face(l = 550f, t = 700f, r = 750f, b = 950f)), w, h, goodLight)
        assertTrue(FaceIssue.FACE_TOO_FAR in v.blockingIssues)
    }

    @Test fun `small but usable face is only a warning`() {
        val v = FaceQualityRules.evaluate(listOf(face(l = 400f, t = 500f, r = 800f, b = 1050f)), w, h, goodLight)
        assertTrue(v.isAcceptable)
        assertTrue(FaceIssue.FACE_TOO_SMALL in v.warnings)
    }

    @Test fun `face outside frame is blocking`() {
        val v = FaceQualityRules.evaluate(listOf(face(l = -200f, r = 500f)), w, h, goodLight)
        assertTrue(FaceIssue.FACE_PARTIALLY_OUTSIDE in v.blockingIssues)
    }

    @Test fun `excessive yaw, pitch or roll is blocking`() {
        listOf(face(yaw = 30f), face(pitch = -25f), face(roll = 20f)).forEach {
            assertTrue(FaceIssue.EXCESSIVE_ANGLE in FaceQualityRules.evaluate(listOf(it), w, h, goodLight).blockingIssues)
        }
    }

    @Test fun `darkness and overexposure are detected`() {
        assertTrue(FaceIssue.TOO_DARK in FaceQualityRules.evaluate(listOf(face()), w, h, goodLight.copy(meanLuma = 30.0)).issues)
        assertTrue(FaceIssue.OVEREXPOSED in FaceQualityRules.evaluate(listOf(face()), w, h, goodLight.copy(meanLuma = 240.0)).issues)
        assertTrue(FaceIssue.OVEREXPOSED in FaceQualityRules.evaluate(listOf(face()), w, h, goodLight.copy(clippedFraction = 0.4)).issues)
        assertTrue(FaceIssue.UNEVEN_LIGHT in FaceQualityRules.evaluate(listOf(face()), w, h, goodLight.copy(meanLuma = 70.0)).warnings)
    }

    @Test fun `blur thresholds`() {
        assertTrue(FaceIssue.BLURRY in FaceQualityRules.evaluate(listOf(face()), w, h, goodLight.copy(sharpness = 5.0)).blockingIssues)
        assertTrue(FaceIssue.SLIGHTLY_BLURRY in FaceQualityRules.evaluate(listOf(face()), w, h, goodLight.copy(sharpness = 30.0)).warnings)
    }

    @Test fun `live preview without light metrics still validates framing`() {
        assertTrue(FaceQualityRules.evaluate(listOf(face()), w, h, null).isAcceptable)
    }

    @Test fun `off-center face is a warning`() {
        val v = FaceQualityRules.evaluate(listOf(face(l = 650f, r = 1180f)), w, h, goodLight)
        assertTrue(FaceIssue.NOT_CENTERED in v.warnings)
    }
}

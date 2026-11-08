package com.chai.aiskin.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityMetricsTest {
    @Test fun `luma of pure colours`() {
        assertEquals(255, ImageQualityMetrics.lumaOf(0xFFFFFFFF.toInt()))
        assertEquals(0, ImageQualityMetrics.lumaOf(0xFF000000.toInt()))
        assertEquals(76, ImageQualityMetrics.lumaOf(0xFFFF0000.toInt()))
    }

    @Test fun `mean and clipped fraction`() {
        val gray = intArrayOf(0, 100, 200, 255)
        assertEquals(138.75, ImageQualityMetrics.meanLuma(gray), 0.001)
        assertEquals(0.25, ImageQualityMetrics.clippedFraction(gray), 0.001)
        assertEquals(0.0, ImageQualityMetrics.meanLuma(IntArray(0)), 0.0)
    }

    @Test fun `flat image has zero laplacian variance`() {
        assertEquals(0.0, ImageQualityMetrics.laplacianVariance(IntArray(64 * 64) { 128 }, 64, 64), 1e-9)
    }

    @Test fun `checkerboard is much sharper than gradient`() {
        val n = 64
        val checker = IntArray(n * n) { i -> if (((i % n) + (i / n)) % 2 == 0) 0 else 255 }
        val gradient = IntArray(n * n) { i -> (i % n) * 4 }
        val sharp = ImageQualityMetrics.laplacianVariance(checker, n, n)
        val soft = ImageQualityMetrics.laplacianVariance(gradient, n, n)
        assertTrue(sharp > 1000 * (soft + 1))
    }
}

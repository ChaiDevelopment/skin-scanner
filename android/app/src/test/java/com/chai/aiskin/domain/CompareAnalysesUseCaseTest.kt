package com.chai.aiskin.domain

import com.chai.aiskin.TestFixtures
import com.chai.aiskin.domain.model.ImageQualityStatus
import com.chai.aiskin.domain.model.Severity
import com.chai.aiskin.domain.model.Texture
import com.chai.aiskin.domain.usecase.CompareAnalysesUseCase
import com.chai.aiskin.domain.usecase.VisualChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CompareAnalysesUseCaseTest {
    private val compare = CompareAnalysesUseCase()
    private val older = TestFixtures.analysis("old", Instant.parse("2026-01-01T00:00:00Z"), Texture.UNEVEN, Severity.MODERATE, Severity.PRONOUNCED)
    private val newer = TestFixtures.analysis("new", Instant.parse("2026-02-01T00:00:00Z"), Texture.SLIGHTLY_UNEVEN, Severity.MODERATE, Severity.UNKNOWN)

    @Test fun `orders by date regardless of argument order`() {
        val c = compare(newer, older)
        assertEquals("old", c.older.id)
        assertEquals("new", c.newer.id)
    }

    @Test fun `per-attribute visual change, unknown is not comparable`() {
        val rows = compare(older, newer).rows.associateBy { it.label }
        assertEquals(VisualChange.LESS_VISIBLE, rows["Tekstur"]!!.change)
        assertEquals(VisualChange.SIMILAR, rows["Kemerahan"]!!.change)
        assertEquals(VisualChange.NOT_COMPARABLE, rows["Pori-pori"]!!.change)
        assertEquals(VisualChange.NOT_COMPARABLE, rows["Kering / mengelupas"]!!.change)
    }

    @Test fun `always includes the lighting disclaimer and never claims improvement`() {
        val c = compare(older, newer)
        assertTrue(c.disclaimer.contains("pencahayaan"))
        assertFalse(VisualChange.entries.any { it.label.contains("membaik", true) || it.label.contains("perbaikan", true) })
    }

    @Test fun `warns when a photo had poor quality`() {
        assertNull(compare(older, newer).qualityWarning)
        assertNotNull(compare(older, newer.copy(imageQuality = newer.imageQuality.copy(status = ImageQualityStatus.POOR))).qualityWarning)
    }
}

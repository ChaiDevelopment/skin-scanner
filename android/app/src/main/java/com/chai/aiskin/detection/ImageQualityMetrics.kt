package com.chai.aiskin.detection

/** Pure pixel math on grayscale buffers; unit-testable without Android. */
object ImageQualityMetrics {

    /** ITU-R BT.601 luma from packed ARGB. */
    fun lumaOf(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    fun toGray(argb: IntArray): IntArray = IntArray(argb.size) { lumaOf(argb[it]) }

    fun meanLuma(gray: IntArray): Double = if (gray.isEmpty()) 0.0 else gray.sumOf { it.toLong() }.toDouble() / gray.size

    fun clippedFraction(gray: IntArray, threshold: Int = 250): Double =
        if (gray.isEmpty()) 0.0 else gray.count { it >= threshold }.toDouble() / gray.size

    /**
     * Variance of the 4-neighbour Laplacian — a standard focus measure.
     * Low values mean few edges (blur). Thresholds in [FaceQualityRules] assume ~256 px wide input.
     */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val lap = (gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width] - 4 * gray[i]).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    fun lightMetrics(gray: IntArray, width: Int, height: Int, includeSharpness: Boolean) = LightMetrics(
        meanLuma = meanLuma(gray),
        clippedFraction = clippedFraction(gray),
        sharpness = if (includeSharpness) laplacianVariance(gray, width, height) else null,
    )
}

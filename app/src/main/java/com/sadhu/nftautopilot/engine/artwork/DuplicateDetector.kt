package com.sadhu.nftautopilot.engine.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.sadhu.nftautopilot.data.database.dao.ArtworkDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DuplicateDetector @Inject constructor(
    private val artworkDao: ArtworkDao
) {
    /**
     * Returns true if this image is a duplicate of any existing artwork
     * in the collection (by SHA-256 exact match or perceptual hash similarity).
     */
    suspend fun isDuplicate(collectionId: String, sha256: String, pHash: Long): Boolean {
        // Exact hash check
        if (artworkDao.countByHash(sha256) > 0) return true

        // Perceptual similarity check (Hamming distance < threshold)
        val existingPHashes = artworkDao.getPHashValues(collectionId)
        return existingPHashes.any { existing -> hammingDistance(existing, pHash) < 10 }
    }

    /**
     * Compute 64-bit perceptual hash (pHash) of an image.
     * Algorithm: DCT-based perceptual hash (32x32 → 8x8 DCT average threshold)
     */
    fun computePHash(imageFile: File): Long {
        val src = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return 0L
        return try {
            computePHash(src)
        } finally {
            src.recycle()
        }
    }

    fun computePHash(bitmap: Bitmap): Long {
        val size = 32
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = FloatArray(size * size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = small.getPixel(x, y)
                // Convert to grayscale
                pixels[y * size + x] = (0.299f * Color.red(pixel) +
                        0.587f * Color.green(pixel) +
                        0.114f * Color.blue(pixel))
            }
        }
        small.recycle()

        // Apply 2D DCT (simplified: take 8x8 from top-left of 32x32 DCT)
        val dct = applyDCT(pixels, size)
        val dctSubset = FloatArray(64) { i -> dct[i] }

        val avg = (dctSubset.sum() - dctSubset[0]) / 63f
        var hash = 0L
        for (i in 1..63) {
            if (dctSubset[i] > avg) hash = hash or (1L shl i)
        }
        return hash
    }

    private fun applyDCT(pixels: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n * n)
        for (u in 0 until 8) {
            for (v in 0 until 8) {
                var sum = 0.0
                for (x in 0 until n) {
                    for (y in 0 until n) {
                        sum += pixels[y * n + x] *
                                Math.cos((2 * x + 1) * u * Math.PI / (2 * n)) *
                                Math.cos((2 * y + 1) * v * Math.PI / (2 * n))
                    }
                }
                result[u * n + v] = sum.toFloat()
            }
        }
        return result
    }

    private fun hammingDistance(a: Long, b: Long): Int {
        var xor = a xor b
        var count = 0
        while (xor != 0L) { count += (xor and 1L).toInt(); xor = xor ushr 1 }
        return count
    }
}

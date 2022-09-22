package video.api.upstream.utils

import kotlin.random.Random

object Utils {
    fun generateRandomArray(size: Int): ByteArray {
        return Random.nextBytes(size)
    }

    fun generateRandomArray(size: Long): ByteArray {
        return generateRandomArray(size.toInt())
    }
}
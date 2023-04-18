package video.api.upstream.models.storage

import java.io.File

/**
 * A part of a file.
 */
data class Part(val index: Int, var isLast: Boolean, val file: File) {
    init {
        require(index >= 0) { "Part index must be positive" }
        require(file.isFile) { "Part file must be a file" }
        require(file.exists()) { "Part file must exist" }
        require(file.canRead()) { "Part file must be readable" }
    }
}
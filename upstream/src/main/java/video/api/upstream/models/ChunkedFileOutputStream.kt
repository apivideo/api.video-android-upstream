package video.api.upstream.models

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ChunkedFileOutputStream(
    private val tmpParentFilePath: String,
    private val chunkMaxSize: Long,
    private val onChunkListener: OnChunkListener
) : OutputStream() {
    private var chuckSentByte = 0L
    private var chunkPartIndex = 0
    private var hasWritten = false

    private lateinit var currentFile: File
    private val nextFile: File
        get() {
            chunkPartIndex++
            currentFile = File(tmpParentFilePath, "$chunkPartIndex").apply {
                parentFile?.mkdirs()
            }
            return currentFile
        }

    private var outputStream: OutputStream = FileOutputStream(nextFile)
    private fun createNextOutputStream() {
        chuckSentByte = 0
        outputStream.close()
        onChunkListener.onChunkReady(chunkPartIndex, false, currentFile)
        outputStream = FileOutputStream(nextFile)
    }

    override fun write(b: Int) {
        hasWritten = true
        if (chuckSentByte >= chunkMaxSize) {
            createNextOutputStream()
        }
        outputStream.write(b)
        chuckSentByte++
    }

    override fun write(b: ByteArray) {
        hasWritten = true
        if (chuckSentByte >= chunkMaxSize) {
            createNextOutputStream()
        }
        outputStream.write(b)
        chuckSentByte += b.size
    }

    override fun close() {
        outputStream.close()
        if (hasWritten) {
            onChunkListener.onChunkReady(chunkPartIndex, true, currentFile)
        }
    }

    interface OnChunkListener {
        fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {}
    }
}
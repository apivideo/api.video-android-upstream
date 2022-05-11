package video.api.upstream.models

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ChunkedFileOutputStream(
    private val tmpParentFilePath: String,
    private val chunkMaxSize: Int,
    private val onChunkListener: OnChunkListener
) : OutputStream() {
    private var chuckSentByte = 0
    private var numOfChunks = 0
    private var hasWritten = false

    private lateinit var currentFile: File
    private val nextFile: File
        get() {
            currentFile = File(tmpParentFilePath, "${numOfChunks++}").apply {
                parentFile?.mkdirs()
            }
            return currentFile
        }

    private var outputStream: OutputStream = FileOutputStream(nextFile)
    private fun createNextOutputStream() {
        chuckSentByte = 0
        outputStream.close()
        onChunkListener.onChunkSizeReached(currentFile)
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
        super.close()
        if (hasWritten) {
            onChunkListener.onLastChunk(currentFile)
        }
    }

    interface OnChunkListener {
        fun onChunkSizeReached(file: File) {}
        fun onLastChunk(file: File) {}
    }
}
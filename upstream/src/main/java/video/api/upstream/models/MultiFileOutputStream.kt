package video.api.upstream.models

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * A class that allows to write to multiple files.
 * The files are created in the given directory.
 */
class MultiFileOutputStream(
    val filesDir: File,
    private val partSize: Long,
    private val namePrefix: String,
    private val listener: Listener
) : OutputStream() {
    private var currentFileBytesWritten = 0L
    private var bytesWritten = 0

    private var _isClosed = false
    val isClosed: Boolean
        get() = _isClosed

    private var _outputStream: FileOutputStream? = null
    private val outputStream: FileOutputStream
        get() {
            if (_isClosed) {
                throw IllegalStateException("Stream is closed")
            }
            synchronized(this) {
                if ((_outputStream == null) || (currentFileBytesWritten >= partSize)) {
                    _outputStream?.let {
                        it.close()
                        listener.onFileCreated(
                            numOfFileWritten,
                            false,
                            getFile(numOfFileWritten)
                        )
                    }

                    currentFileBytesWritten = 0
                    _numOfFileWritten++

                    _outputStream = FileOutputStream(getFile(numOfFileWritten))
                }
                return _outputStream!!
            }
        }

    var _numOfFileWritten: Int = 0
    val numOfFileWritten: Int
        get() = _numOfFileWritten

    init {
        require(partSize > 0) { "Part size must be greater than 0" }
        require(filesDir.isDirectory) { "Files directory must be a directory" }
        require(filesDir.canWrite()) { "Files directory must be writable" }
    }

    private fun getFile(fileIndex: Int): File {
        return File(filesDir, "$namePrefix$fileIndex")
    }

    override fun write(b: Int) {
        outputStream.write(b)
        currentFileBytesWritten++
        bytesWritten++
    }

    override fun write(b: ByteArray) {
        outputStream.write(b)
        currentFileBytesWritten += b.size
        bytesWritten += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        outputStream.write(b, off, len)
        currentFileBytesWritten += len
        bytesWritten += len
    }

    override fun close() {
        if (_isClosed) {
            return
        }
        _isClosed = true
        _outputStream?.let {
            it.close()
            listener.onFileCreated(numOfFileWritten, true, getFile(numOfFileWritten))
        }
        _outputStream = null
    }

    override fun flush() {
        _outputStream?.flush()
    }

    /**
     * Delete all files
     */
    fun delete() {
        filesDir.deleteRecursively()
    }

    interface Listener {
        fun onFileCreated(chunkIndex: Int, isLastChunk: Boolean, file: File) {}
    }
}
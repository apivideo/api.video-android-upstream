package video.api.upstream.models

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import video.api.upstream.utils.Utils
import java.io.File
import java.util.concurrent.CountDownLatch

class ChunkedOutputStreamTest {
    @get:Rule
    val rootFolder: TemporaryFolder = TemporaryFolder()
    private lateinit var chunkedFileOutputStream: ChunkedFileOutputStream

    @After
    fun tearDown() {
        chunkedFileOutputStream.close()
    }

    @Test
    fun `write data`() {
        val chunkReadyCountDownLatch = CountDownLatch(3)
        val isLastChunkCountDownLatch = CountDownLatch(1)
        var lastChunkId = 0
        val onChunkListener = object : ChunkedFileOutputStream.OnChunkListener {
            override fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {
                assertEquals(lastChunkId + 1, chunkIndex)
                chunkReadyCountDownLatch.countDown()
                if (isLastChunk) {
                    isLastChunkCountDownLatch.countDown()
                }
                lastChunkId = chunkIndex
            }
        }
        chunkedFileOutputStream =
            ChunkedFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, onChunkListener)

        chunkedFileOutputStream.write(Utils.generateRandomArray(2048))
        chunkedFileOutputStream.write(Utils.generateRandomArray(2048))
        chunkedFileOutputStream.write(Utils.generateRandomArray(600))
        chunkedFileOutputStream.close()

        assertEquals(0, chunkReadyCountDownLatch.count)
        assertEquals(0, isLastChunkCountDownLatch.count)
        assertEquals(3, lastChunkId)
    }

    @Test
    fun `write data size == chunk size`() {
        val countDownLatch = CountDownLatch(4)
        val onChunkListener = object : ChunkedFileOutputStream.OnChunkListener {
            override fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {
                countDownLatch.countDown()
            }
        }
        chunkedFileOutputStream =
            ChunkedFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, onChunkListener)

        chunkedFileOutputStream.write(Utils.generateRandomArray(CHUNK_SIZE))
        chunkedFileOutputStream.write(Utils.generateRandomArray(CHUNK_SIZE))
        chunkedFileOutputStream.write(Utils.generateRandomArray(CHUNK_SIZE))
        chunkedFileOutputStream.close() // Must not create an empty chunk

        assertEquals(1, countDownLatch.count)
    }

    @Test
    fun `multiple close test`() {
        val countDownLatch = CountDownLatch(3)
        val onChunkListener = object : ChunkedFileOutputStream.OnChunkListener {
            override fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {
                countDownLatch.countDown()
            }
        }
        chunkedFileOutputStream =
            ChunkedFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, onChunkListener)
        chunkedFileOutputStream.write(Utils.generateRandomArray(2048))
        chunkedFileOutputStream.write(Utils.generateRandomArray(600))
        chunkedFileOutputStream.close()
        chunkedFileOutputStream.close()
        chunkedFileOutputStream.close()
        chunkedFileOutputStream.close()

        assertEquals(1, countDownLatch.count)
    }

    @Test
    fun `close without writing data`() {
        val countDownLatch = CountDownLatch(1)
        val onChunkListener = object : ChunkedFileOutputStream.OnChunkListener {
            override fun onChunkReady(chunkIndex: Int, isLastChunk: Boolean, file: File) {
                countDownLatch.countDown()
            }
        }
        chunkedFileOutputStream =
            ChunkedFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, onChunkListener)
        chunkedFileOutputStream.close()

        assertEquals(1, countDownLatch.count)
    }

    companion object {
        const val CHUNK_SIZE = 1024L
    }
}
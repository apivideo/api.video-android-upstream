package video.api.upstream.models

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import video.api.upstream.utils.Utils
import java.io.File
import java.util.concurrent.CountDownLatch

class MultiFileOutputStreamTest {
    @get:Rule
    val rootFolder: TemporaryFolder = TemporaryFolder()
    private lateinit var multiFileOutputStream: MultiFileOutputStream

    @After
    fun tearDown() {
        multiFileOutputStream.close()
    }

    @Test
    fun `write data`() {
        val chunkReadyCountDownLatch = CountDownLatch(3)
        val isLastChunkCountDownLatch = CountDownLatch(1)
        var lastChunkId = 0
        val listener = object : MultiFileOutputStream.Listener {
            override fun onFileCreated(partIndex: Int, isLast: Boolean, file: File) {
                assertEquals(lastChunkId + 1, partIndex)
                chunkReadyCountDownLatch.countDown()
                if (isLast) {
                    isLastChunkCountDownLatch.countDown()
                }
                lastChunkId = partIndex
            }
        }
        multiFileOutputStream =
            MultiFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, "", listener)

        multiFileOutputStream.write(Utils.generateRandomArray(2048))
        multiFileOutputStream.write(Utils.generateRandomArray(2048))
        multiFileOutputStream.write(Utils.generateRandomArray(600))
        multiFileOutputStream.close()

        assertEquals(0, chunkReadyCountDownLatch.count)
        assertEquals(0, isLastChunkCountDownLatch.count)
        assertEquals(3, lastChunkId)
    }

    @Test
    fun `write data size == chunk size`() {
        val countDownLatch = CountDownLatch(4)
        val listener = object : MultiFileOutputStream.Listener {
            override fun onFileCreated(partIndex: Int, isLast: Boolean, file: File) {
                countDownLatch.countDown()
            }
        }
        multiFileOutputStream =
            MultiFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, "", listener)

        multiFileOutputStream.write(Utils.generateRandomArray(CHUNK_SIZE))
        multiFileOutputStream.write(Utils.generateRandomArray(CHUNK_SIZE))
        multiFileOutputStream.write(Utils.generateRandomArray(CHUNK_SIZE))
        multiFileOutputStream.close() // Must not create an empty chunk

        assertEquals(1, countDownLatch.count)
    }

    @Test
    fun `multiple close test`() {
        val countDownLatch = CountDownLatch(3)
        val listener = object : MultiFileOutputStream.Listener {
            override fun onFileCreated(partIndex: Int, isLast: Boolean, file: File) {
                countDownLatch.countDown()
            }
        }
        multiFileOutputStream =
            MultiFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, "", listener)
        multiFileOutputStream.write(Utils.generateRandomArray(2048))
        multiFileOutputStream.write(Utils.generateRandomArray(600))
        multiFileOutputStream.close()
        multiFileOutputStream.close()
        multiFileOutputStream.close()
        multiFileOutputStream.close()

        assertEquals(1, countDownLatch.count)
    }

    @Test
    fun `close without writing data`() {
        val countDownLatch = CountDownLatch(1)
        val listener = object : MultiFileOutputStream.Listener {
            override fun onFileCreated(partIndex: Int, isLast: Boolean, file: File) {
                countDownLatch.countDown()
            }
        }
        multiFileOutputStream =
            MultiFileOutputStream(rootFolder.newFolder(), CHUNK_SIZE, "", listener)
        multiFileOutputStream.close()

        assertEquals(1, countDownLatch.count)
    }

    companion object {
        const val CHUNK_SIZE = 1024L
    }
}
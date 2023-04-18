package video.api.upstream.models

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import video.api.client.ApiVideoClient
import video.api.client.api.models.VideoCreationPayload
import video.api.uploader.VideosApi
import video.api.uploader.api.models.Environment
import video.api.uploader.api.models.Video
import video.api.uploader.api.work.stores.VideosApiStore
import video.api.upstream.Utils
import video.api.upstream.mocks.MockUpstreamDao
import video.api.upstream.mocks.MutableUpstreamSessionEntity
import video.api.upstream.models.storage.FileUpstreamDao
import video.api.upstream.models.storage.Part
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MultiFileUploaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val apiClient: ApiVideoClient =
        ApiVideoClient(
            Utils.getApiKey(),
            video.api.client.api.models.Environment.SANDBOX
        ).apply {
            setApplicationName("upstream-integration-tests", "0")
        }

    private var videoId: String? = null

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        VideosApiStore.initialize(VideosApi( Utils.getApiKey(), Environment.SANDBOX))
        videoId = apiClient.videos().create(VideoCreationPayload().title("test")).videoId
    }

    @After
    fun tearDown() {
        videoId?.let {
            apiClient.videos().delete(it)
        }
    }

    @Test
    fun defaultUpstreamVideo() {
        val part1 = Utils.getFileFromAsset("10m.mp4.part.a")
        val part2 = Utils.getFileFromAsset("10m.mp4.part.b")
        val part3 = Utils.getFileFromAsset("10m.mp4.part.c")

        val sessionCompleteLatch = CountDownLatch(1)
        val sessionPartCompleteLatch = CountDownLatch(3)
        var numberOfParts = 0

        val sessionId = UUID.randomUUID().toString()
        val multiFileUploader = MultiFileUploader.createForVideoId(
            context = context,
            sessionId = sessionId,
            sessionStore = FileUpstreamDao(tempFolder.newFolder(sessionId)),
            videoId = videoId!!,
            sessionListener = object : SessionListener {
                override fun onNumberOfPartsChanged(session: MultiFileUploader, numOfParts: Int) {
                    numberOfParts = numOfParts
                }

                override fun onComplete(session: MultiFileUploader) {
                    sessionCompleteLatch.countDown()
                }
            },
            sessionUploadPartListener = object : SessionUploadPartListener {
                override fun onComplete(session: MultiFileUploader, partId: Int, video: Video) {
                    Log.i(TAG, "Part $partId complete")
                    sessionPartCompleteLatch.countDown()
                }
            }
        )

        multiFileUploader.upload(1, false, part1)
        multiFileUploader.upload(2, false, part2)
        multiFileUploader.upload(3, true, part3)

        sessionCompleteLatch.await(180, TimeUnit.SECONDS)

        assertEquals(3, multiFileUploader.totalNumOfParts)
        assertEquals(3, multiFileUploader.numOfPartsSent)
        assertEquals(3, multiFileUploader.numOfPartsFinished)
        assertEquals(0, multiFileUploader.numOfPartsFailed)
        assertEquals(0, multiFileUploader.numOfPartsWaiting)

        assertEquals(3, numberOfParts)
        assertEquals(0, sessionPartCompleteLatch.count)
        assertEquals(0, sessionCompleteLatch.count)
    }

    @Test
    fun testUpstreamVideoStorage() {
        val part1 = Utils.getFileFromAsset("10m.mp4.part.a")
        val part2 = Utils.getFileFromAsset("10m.mp4.part.b")
        val part3 = Utils.getFileFromAsset("10m.mp4.part.c")

        val sessionId = UUID.randomUUID().toString()

        val sessionCompleteLatch = CountDownLatch(1)
        val sessionPartCompleteLatch = CountDownLatch(3)

        val dao = MockUpstreamDao()

        val multiFileUploader = MultiFileUploader.createForVideoId(
            context = context,
            sessionId = sessionId,
            sessionStore = dao,
            videoId = videoId!!,
            sessionListener = object : SessionListener {
                override fun onComplete(session: MultiFileUploader) {
                    sessionCompleteLatch.countDown()
                }
            },
            sessionUploadPartListener = object : SessionUploadPartListener {
                override fun onComplete(session: MultiFileUploader, partId: Int, video: Video) {
                    assertNotNull(dao.getById(sessionId)!!.videoId)
                    Log.i(TAG, "Part $partId complete")
                    sessionPartCompleteLatch.countDown()
                }
            }
        )

        multiFileUploader.upload(1, false, part1)
        multiFileUploader.upload(2, false, part2)
        multiFileUploader.upload(3, true, part3)

        val session = dao.getById(sessionId)
        assertNotNull(session)
        assertEquals(videoId!!, session!!.videoId)
        assertEquals(3, session.parts.size)
        assertEquals(3, session.lastPart!!.index)

        sessionCompleteLatch.await(180, TimeUnit.SECONDS)
        assertEquals(0, sessionCompleteLatch.count)

        assertNull(dao.getById(sessionId))
    }

    @Test
    fun loadUpstreamVideoFromDao() {
        val part1 = Utils.getFileFromAsset("10m.mp4.part.a")
        val part2 = Utils.getFileFromAsset("10m.mp4.part.b")
        val part3 = Utils.getFileFromAsset("10m.mp4.part.c")
        val parts =
            mutableListOf(Part(1, false, part1), Part(2, false, part2), Part(3, true, part3))

        val sessionId = UUID.randomUUID().toString()
        val mockUpstreamSessionStore =
            MockUpstreamDao(
                mutableListOf(
                    MutableUpstreamSessionEntity(
                        id = sessionId,
                        videoId = videoId,
                        token = null,
                        parts = parts
                    )
                )
            )

        val sessionCompleteLatch = CountDownLatch(1)
        val sessionPartCompleteLatch = CountDownLatch(3)
        var numberOfParts = 0

        val multiFileUploader = MultiFileUploader.loadExistingSession(
            context = context,
            sessionId = sessionId,
            sessionStore = mockUpstreamSessionStore,
            sessionListener = object : SessionListener {
                override fun onNumberOfPartsChanged(session: MultiFileUploader, numOfParts: Int) {
                    numberOfParts = numOfParts
                }

                override fun onComplete(session: MultiFileUploader) {
                    sessionCompleteLatch.countDown()
                }
            },
            sessionUploadPartListener = object : SessionUploadPartListener {
                override fun onComplete(session: MultiFileUploader, partId: Int, video: Video) {
                    Log.i(TAG, "Part $partId complete")
                    sessionPartCompleteLatch.countDown()
                }
            }
        )

        sessionCompleteLatch.await(180, TimeUnit.SECONDS)

        assertEquals(3, multiFileUploader.totalNumOfParts)
        assertEquals(3, multiFileUploader.numOfPartsSent)
        assertEquals(3, multiFileUploader.numOfPartsFinished)
        assertEquals(0, multiFileUploader.numOfPartsFailed)
        assertEquals(0, multiFileUploader.numOfPartsWaiting)

        assertEquals(3, numberOfParts)
        assertEquals(0, sessionPartCompleteLatch.count)
        assertEquals(0, sessionCompleteLatch.count)
    }

    /**
     * Test that a session throws an exception if it is not in the store.
     */
    @Test
    fun loadNonExistingSession() {
        try {
            MultiFileUploader.loadExistingSession(
                context,
                "non-existing-session-id",
                sessionStore = MockUpstreamDao()
            )
            fail("Should throw an exception")
        } catch (_: Exception) {

        }
    }

    companion object {
        private const val TAG = "UpstreamSessionTest"
    }
}
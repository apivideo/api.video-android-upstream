package video.api.upstream.models

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import video.api.client.ApiVideoClient
import video.api.client.api.models.Environment
import video.api.client.api.models.TokenCreationPayload
import video.api.uploader.VideosApi
import video.api.uploader.api.models.Video
import video.api.uploader.api.work.stores.VideosApiStore
import video.api.upstream.Utils
import video.api.upstream.mocks.MockUpstreamDao
import video.api.upstream.mocks.SingleUpstreamSessionEntityMockStore
import video.api.upstream.models.storage.FileUpstreamDao
import video.api.upstream.models.storage.Part
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UpstreamSessionUploadTokenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val apiClient: ApiVideoClient =
        ApiVideoClient(Utils.getApiKey(), Environment.SANDBOX).apply {
            setApplicationName("upstream-integration-tests", "0")
        }

    private var uploadToken: String? = null

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        VideosApiStore.initialize(VideosApi())
        uploadToken = apiClient.uploadTokens().createToken(TokenCreationPayload()).token!!
    }

    @After
    fun tearDown() {
        uploadToken?.let {
            apiClient.uploadTokens().deleteToken(it)
        }
    }

    @Test
    fun upstreamVideoByToken() {
        val part1 = Utils.getFileFromAsset("10m.mp4.part.a")
        val part2 = Utils.getFileFromAsset("10m.mp4.part.b")
        val part3 = Utils.getFileFromAsset("10m.mp4.part.c")

        val sessionCompleteLatch = CountDownLatch(1)
        val sessionPartCompleteLatch = CountDownLatch(3)
        var numberOfParts = 0

        val sessionId = UUID.randomUUID().toString()
        val upstreamSession = UpstreamSession.createForUploadToken(
            context = context,
            sessionId = sessionId,
            sessionStore = FileUpstreamDao(tempFolder.newFolder(sessionId)),
            token = uploadToken!!,
            videoId = null,
            sessionListener = object : SessionListener {
                override fun onNumberOfPartsChanged(session: UpstreamSession, numOfParts: Int) {
                    numberOfParts = numOfParts
                }

                override fun onComplete(session: UpstreamSession) {
                    sessionCompleteLatch.countDown()
                }
            },
            sessionUploadPartListener = object : SessionUploadPartListener {
                override fun onComplete(session: UpstreamSession, partId: Int, video: Video) {
                    Log.i(TAG, "Part $partId complete")
                    sessionPartCompleteLatch.countDown()
                }
            }
        )

        upstreamSession.upload(1, false, part1)
        upstreamSession.upload(2, false, part2)
        upstreamSession.upload(3, true, part3)

        sessionCompleteLatch.await(180, TimeUnit.SECONDS)

        assertEquals(3, upstreamSession.totalNumOfParts)
        assertEquals(3, upstreamSession.numOfPartsSent)
        assertEquals(3, upstreamSession.numOfPartsFinished)
        assertEquals(0, upstreamSession.numOfPartsFailed)
        assertEquals(0, upstreamSession.numOfPartsWaiting)

        assertEquals(3, numberOfParts)
        assertEquals(0, sessionPartCompleteLatch.count)
        assertEquals(0, sessionCompleteLatch.count)
    }

    @Test
    fun loadUpstreamVideoByTokenFromStore() {
        val part1 = Utils.getFileFromAsset("10m.mp4.part.a")
        val part2 = Utils.getFileFromAsset("10m.mp4.part.b")
        val part3 = Utils.getFileFromAsset("10m.mp4.part.c")
        val parts =
            mutableListOf(Part(1, false, part1), Part(2, false, part2), Part(3, true, part3))

        val sessionId = UUID.randomUUID().toString()
        val mockUpstreamSessionStore =
            MockUpstreamDao(
                mutableListOf(
                    SingleUpstreamSessionEntityMockStore(
                        id = sessionId,
                        videoId = null,
                        token = uploadToken,
                        parts = parts
                    )
                )
            )

        val sessionCompleteLatch = CountDownLatch(1)
        val sessionPartCompleteLatch = CountDownLatch(3)
        var numberOfParts = 0

        val upstreamSession = UpstreamSession.loadExistingSession(
            context = context,
            sessionId = sessionId,
            sessionStore = mockUpstreamSessionStore,
            sessionListener = object : SessionListener {
                override fun onNumberOfPartsChanged(session: UpstreamSession, numOfParts: Int) {
                    numberOfParts = numOfParts
                }

                override fun onComplete(session: UpstreamSession) {
                    sessionCompleteLatch.countDown()
                }
            },
            sessionUploadPartListener = object : SessionUploadPartListener {
                override fun onComplete(session: UpstreamSession, partId: Int, video: Video) {
                    Log.i(TAG, "Part $partId complete")
                    sessionPartCompleteLatch.countDown()
                }
            }
        )

        sessionCompleteLatch.await(180, TimeUnit.SECONDS)

        assertEquals(3, upstreamSession.totalNumOfParts)
        assertEquals(3, upstreamSession.numOfPartsSent)
        assertEquals(3, upstreamSession.numOfPartsFinished)
        assertEquals(0, upstreamSession.numOfPartsFailed)
        assertEquals(0, upstreamSession.numOfPartsWaiting)

        assertEquals(3, numberOfParts)
        assertEquals(0, sessionPartCompleteLatch.count)
        assertEquals(0, sessionCompleteLatch.count)
    }

    companion object {
        private const val TAG = "UpstreamSessionUploadTokenTest"
    }
}
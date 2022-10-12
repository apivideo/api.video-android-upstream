package video.api.upstream

import android.Manifest
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import video.api.client.ApiVideoClient
import video.api.client.api.models.Environment
import video.api.client.api.models.VideoCreationPayload
import video.api.uploader.api.services.UploadService
import video.api.upstream.models.*
import video.api.upstream.service.UploadServiceWithoutNotifications
import video.api.upstream.utils.Utils
import video.api.upstream.views.ApiVideoView
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class ApiVideoUpstreamInstrumentedTest {
    @get:Rule
    val serviceRule: ServiceTestRule = ServiceTestRule()

    @get:Rule
    val runtimePermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private lateinit var upstream: ApiVideoUpstream
    private lateinit var apiClient: ApiVideoClient
    private val apiVideoView = ApiVideoView(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            UploadServiceWithoutNotifications::class.java
        )

        serviceIntent.putExtra(UploadService.API_KEY_KEY, Utils.apiKey)
        serviceIntent.putExtra(UploadService.BASE_PATH_KEY, Environment.SANDBOX.basePath)
        serviceIntent.putExtra(UploadService.APP_NAME_KEY, "upstream-integration-tests")
        serviceIntent.putExtra(UploadService.APP_VERSION_KEY, "0")

        val binder = serviceRule.bindService(serviceIntent)
        val uploadService = (binder as UploadService.UploadServiceBinder).service

        upstream = ApiVideoUpstream(
            ApplicationProvider.getApplicationContext(),
            uploadService,
            apiVideoView,
            initialAudioConfig = AudioConfig(),
            initialVideoConfig = VideoConfig()
        )

        // To create video ids
        apiClient = ApiVideoClient(Utils.apiKey, Environment.SANDBOX)
        apiClient.setApplicationName("upstream-integration-tests", "0")
    }

    @After
    fun tearDown() {
        upstream.releaseAndCancelAll()
    }

    @Test
    fun upstream1Session() {
        sleep(22220)
        val videoId = apiClient.videos()
            .create(VideoCreationPayload().title("[Android-upstream-tests] upstream 1 session")).videoId

        val completationCountDownLatch = CountDownLatch(1)
        val errorCountDownLatch = CountDownLatch(1)

        upstream.sessionListener = object : SessionListener {
            override fun onComplete(session: UpstreamSession) {
                completationCountDownLatch.countDown()
            }
        }

        upstream.sessionUploadPartListener = object : SessionUploadPartListener {
            override fun onError(session: UpstreamSession, partId: Int, error: Exception) {
                Log.e(TAG, "Failed to upload part $partId", error)
                errorCountDownLatch.countDown()
            }
        }
        upstream.startStreamingForVideoId(videoId)

        sleep(60000) // 1 min

        upstream.stopStreaming()
        completationCountDownLatch.await(30, TimeUnit.SECONDS)

        assertEquals(1, errorCountDownLatch) // No error

        // TODO check no file remaining in internal dir
    }


    fun upstreamWithError() {
        // TODO check all file are here
        // Check API returns correct lists
    }


    companion object {
        private const val TAG = "ApiVideoUpstreamInstrumentedTest"
    }
}
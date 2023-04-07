package video.api.upstream.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import video.api.client.ApiVideoClient
import video.api.client.api.models.Environment
import video.api.client.api.models.TokenCreationPayload
import video.api.uploader.VideosApi
import video.api.uploader.api.work.stores.VideosApiStore
import video.api.upstream.mocks.MockUpstreamDao

class UpstreamSessionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val apiClient: ApiVideoClient =
        ApiVideoClient("TaqUfiAO5ouqEPhqjQeK4wtKxajal1ScQy8LH1zBRPu", Environment.SANDBOX).apply {
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

    /**
     * Test that a session throws an exception if it is not in the store.
     */
    @Test
    fun loadNonExistingSession() {
        try {
            UpstreamSession.loadExistingSession(
                context,
                "non-existing-session-id",
                sessionStore = MockUpstreamDao()
            )
            fail("Should throw an exception")
        } catch (e: Exception) {

        }
    }

    companion object {
        private const val TAG = "UpstreamSessionTest"
    }
}
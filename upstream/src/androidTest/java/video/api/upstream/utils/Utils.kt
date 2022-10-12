package video.api.upstream.utils

import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException


object Utils {
    /**
     * Get API key from environment variables
     */
    @get:Throws(IOException::class)
    val apiKey: String
        get() {
            val env = InstrumentationRegistry.getArguments().getString("environmentVariables")
            val apiKey = env!!.replace("INTEGRATION_TESTS_API_KEY=".toRegex(), "")
            if (apiKey === "null") {
                throw IOException("API Key not found")
            }
            return apiKey
        }
}

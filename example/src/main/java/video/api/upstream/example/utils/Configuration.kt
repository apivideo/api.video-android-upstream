package video.api.upstream.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Size
import androidx.preference.PreferenceManager
import video.api.uploader.api.models.Environment
import video.api.upstream.example.R

class Configuration(context: Context) {
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
    private val resources = context.resources
    val video = Video(sharedPref, resources)
    val audio = Audio(sharedPref, resources)
    val apiEndpoint = ApiEndpoint(sharedPref, resources)
}

class Video(private val sharedPref: SharedPreferences, private val resources: Resources) {
    val fps: Int = 30
        get() = sharedPref.getString(
            resources.getString(R.string.video_fps_key),
            field.toString()
        )!!.toInt()

    val resolution: Size = Size(1280, 720)
        get() {
            val res = sharedPref.getString(
                resources.getString(R.string.video_resolution_key),
                field.toString()
            )!!
            val resArray = res.split("x")
            return Size(
                resArray[0].toInt(),
                resArray[1].toInt()
            )
        }

    val bitrate: Int = 2000
        get() = sharedPref.getInt(
            resources.getString(R.string.video_bitrate_key),
            field
        ) * 1024 // to bps
}

class Audio(private val sharedPref: SharedPreferences, private val resources: Resources) {
    val numberOfChannels: Int = 2
        get() = sharedPref.getString(
            resources.getString(R.string.audio_number_of_channels_key),
            field.toString()
        )!!.toInt()

    val bitrate: Int = 128000
        get() = sharedPref.getString(
            resources.getString(R.string.audio_bitrate_key),
            field.toString()
        )!!.toInt()

    val sampleRate: Int = 48000
        get() = sharedPref.getString(
            resources.getString(R.string.audio_sample_rate_key),
            field.toString()
        )!!.toInt()

    val enableEchoCanceler: Boolean = false
        get() = sharedPref.getBoolean(
            resources.getString(R.string.audio_enable_echo_canceler_key),
            field
        )

    val enableNoiseSuppressor: Boolean = false
        get() = sharedPref.getBoolean(
            resources.getString(R.string.audio_enable_noise_suppressor_key),
            field
        )
}

class ApiEndpoint(private val sharedPref: SharedPreferences, private val resources: Resources) {
    val apiKey: String = ""
        get() = sharedPref.getString(
            resources.getString(R.string.api_endpoint_api_key_key),
            field
        )!!

    val uploadToken: String = ""
        get() = sharedPref.getString(
            resources.getString(R.string.api_endpoint_upload_token_key),
            field
        )!!

    val environment: Environment
        get() = if (sharedPref.getBoolean(
                resources.getString(R.string.api_endpoint_type_key),
                true
            )
        ) {
            Environment.PRODUCTION
        } else {
            Environment.SANDBOX
        }

    val useApiKey: Boolean = true
        get() = sharedPref.getBoolean(
            resources.getString(R.string.api_endpoint_type_key),
            field
        )

}
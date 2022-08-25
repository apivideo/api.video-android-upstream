package video.api.upstream.example.preferences

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import video.api.upstream.ConfigurationHelper
import video.api.upstream.enums.Resolution
import video.api.upstream.example.R

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        inflatesPreferences()
    }

    private fun inflatesPreferences() {
        (findPreference(getString(R.string.api_endpoint_type_key)) as SwitchPreference?)!!.apply {
            (findPreference(getString(R.string.api_endpoint_api_key_key)) as EditTextPreference?)!!.isVisible =
                isChecked
            (findPreference(getString(R.string.api_endpoint_upload_token_key)) as EditTextPreference?)!!.isVisible =
                !isChecked

            setOnPreferenceChangeListener { _, newValue ->
                (findPreference(getString(R.string.api_endpoint_api_key_key)) as EditTextPreference?)!!.isVisible =
                    newValue as Boolean
                (findPreference(getString(R.string.api_endpoint_upload_token_key)) as EditTextPreference?)!!.isVisible =
                    !newValue
                true
            }
        }

        (findPreference(getString(R.string.video_resolution_key)) as ListPreference?)!!.apply {
            val resolutionsList = Resolution.values().map { it.toString() }.toTypedArray()
            entryValues = resolutionsList
            entries = resolutionsList
            if (value == null) {
                value = Resolution.RESOLUTION_720.toString()
            }
        }

        (findPreference(getString(R.string.video_fps_key)) as ListPreference?)!!.apply {
            val supportedFramerates = ConfigurationHelper.video.getSupportedFrameRates(
                requireContext(),
                "0"
            )
            entryValues.filter { fps ->
                supportedFramerates.any { it.contains(fps.toString().toInt()) }
            }.toTypedArray().run {
                this@apply.entries = this
                this@apply.entryValues = this
            }
        }

        (findPreference(getString(R.string.audio_sample_rate_key)) as ListPreference?)!!.apply {
            val supportedSampleRate =
                ConfigurationHelper.audio.getSupportedSampleRates()
            entries =
                supportedSampleRate.map { "${"%.1f".format(it.toString().toFloat() / 1000)} kHz" }
                    .toTypedArray()
            entryValues = supportedSampleRate.map { "$it" }.toTypedArray()
            if (entry == null) {
                value = "44100"
            }
        }
    }
}
package video.api.upstream.example.main

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import video.api.upstream.example.R
import video.api.upstream.example.databinding.FragmentPreviewBinding
import video.api.upstream.example.utils.DialogHelper
import video.api.upstream.example.utils.SessionAdapter

class PreviewFragment : Fragment() {
    private val viewModel by activityViewModels<PreviewViewModel>()
    private lateinit var binding: FragmentPreviewBinding
    private val sessionAdapter = SessionAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPreviewBinding.inflate(inflater, container, false)
        binding.sessionRecyclerView.adapter = sessionAdapter

        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        viewModel.buildUpstream(binding.apiVideoView)
        binding.liveButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                /**
                 * Lock orientation in live to avoid stream interruption if
                 * user turns the device.
                 */
                requireActivity().requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_LOCKED
                viewModel.startStream()
            } else {
                viewModel.stopStream()
                requireActivity().requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        binding.switchButton.setOnClickListener {
            viewModel.switchCamera()
        }

        binding.muteButton.setOnClickListener {
            viewModel.toggleMute()
        }

        viewModel.error.observe(viewLifecycleOwner) {
            binding.liveButton.isChecked = false
            manageError(getString(R.string.error), it)
        }

        viewModel.message.observe(viewLifecycleOwner) {
            showToast(it)
        }

        viewModel.sessionComplete.observe(viewLifecycleOwner) {
            if (it) {
                showSnackBar(getString(R.string.upload_success))
            } else {
                showSnackBar(
                    getString(R.string.upload_failed),
                    Snackbar.LENGTH_INDEFINITE
                ) { viewModel.retryAllSessions() }
            }
        }

        viewModel.newSession.observe(viewLifecycleOwner) {
            sessionAdapter.addSession(it)
        }

        viewModel.sessionEnded.observe(viewLifecycleOwner) {
            sessionAdapter.removeSession(it)
        }

        viewModel.numOfParts.observe(viewLifecycleOwner) {
            sessionAdapter.updateNumOfParts(it)
        }

        viewModel.progress.observe(viewLifecycleOwner) {
            sessionAdapter.updatePartProgress(it)
        }
    }

    private fun manageError(title: String, message: String) {
        requireActivity().requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        DialogHelper.showAlertDialog(requireContext(), title, message)
    }

    private fun showSnackBar(
        message: String,
        length: Int = Snackbar.LENGTH_SHORT,
        retryAction: (() -> Unit)? = null
    ) {
        Snackbar.make(binding.apiVideoView, message, length).apply {
            retryAction?.let { action ->
                setAction(R.string.retry) {
                    action()
                }
            }
        }.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
            .show()
    }
}
package video.api.upstream.example.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import video.api.upstream.example.R
import video.api.upstream.models.MultiFileUploader

class SessionAdapter : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {
    private val progressSessions = mutableListOf<ProgressSession>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val progressIndicator: CircularProgressIndicator = view.findViewById(R.id.progress)
        private val currentPartIdView: TextView = view.findViewById(R.id.currentPart)
        private val totalNumOfPartsView: TextView = view.findViewById(R.id.totalNumOfParts)

        fun bind(progressSession: ProgressSession) {
            progressIndicator.progress = progressSession.currentPartProgress?.progress ?: 0
            currentPartIdView.text = (progressSession.currentPartProgress?.part ?: 0).toString()
            totalNumOfPartsView.text = progressSession.numOfParts.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.session_row_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(progressSessions[position])
    }

    override fun getItemCount() = progressSessions.size

    private fun List<ProgressSession>.indexOfPart(sessionId: Int): Int {
        return indexOfFirst { it.sessionId == sessionId }
    }

    private fun List<ProgressSession>.indexOfPart(multiFileUploader: MultiFileUploader): Int {
        return indexOfFirst { it.sessionId == multiFileUploader.hashCode() }
    }

    fun addSession(multiFileUploader: MultiFileUploader) {
        synchronized(this) {
            progressSessions.add(ProgressSession(multiFileUploader))
            notifyItemInserted(progressSessions.size - 1)
        }
    }

    fun removeSession(multiFileUploader: MultiFileUploader) {
        synchronized(this) {
            val index = progressSessions.indexOfPart(multiFileUploader)
            if (index != RecyclerView.NO_POSITION) {
                progressSessions.removeAt(index)
                notifyItemRemoved(index)
            }
        }
    }

    fun updateNumOfParts(sessionParts: SessionParts) {
        val index = progressSessions.indexOfPart(sessionParts.sessionId)
        if (index != RecyclerView.NO_POSITION) {
            progressSessions[index].numOfParts = sessionParts.numOfParts
            notifyItemChanged(index, progressSessions[index])
        }
    }

    fun updatePartProgress(progressSessionPart: ProgressSessionPart) {
        val index = progressSessions.indexOfPart(progressSessionPart.sessionId)
        if (index != RecyclerView.NO_POSITION) {
            progressSessions[index].currentPartProgress =
                PartProgress(progressSessionPart.part, progressSessionPart.progress)
            notifyItemChanged(index, progressSessions[index])
        }
    }
}
package video.api.upstream.example.utils

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import video.api.upstream.example.R

class ProgressAdapter : RecyclerView.Adapter<ProgressAdapter.ViewHolder>() {
    private val partLists = mutableListOf<PartProgress>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val progressIndicator: LinearProgressIndicator = view.findViewById(R.id.progress)
        private val partId: TextView = view.findViewById(R.id.partId)

        fun bind(partProgress: PartProgress) {
            progressIndicator.progress = partProgress.progress
            partId.text = partProgress.partId.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.progress_row_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(partLists[position])
    }

    override fun getItemCount() = partLists.size

    private fun List<PartProgress>.indexOfPart(partId: Int): Int {
        return indexOfFirst { it.partId == partId }
    }

    fun addPart(partId: Int) {
        synchronized(this) {
            partLists.add(PartProgress(partId))
            notifyItemInserted(partLists.size - 1)
        }
    }

    fun removePart(partId: Int) {
        synchronized(this) {
            val index = partLists.indexOfPart(partId)
            if (index != RecyclerView.NO_POSITION) {
                partLists.removeAt(index)
                notifyItemRemoved(index)
            }
        }
    }

    fun updateProgress(partId: Int, progress: Int) {
        val index = partLists.indexOfPart(partId)
        if (index != RecyclerView.NO_POSITION) {
            partLists[index] = PartProgress(partId, progress)
            notifyItemChanged(index, progress)
        }
    }
}
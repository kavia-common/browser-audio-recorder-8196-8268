package org.example.app

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class RecordingsAdapter(
    private var items: List<Recording>,
    private val onSelected: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

    fun submit(newItems: List<Recording>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = RecordingRepository.formatTitle(item.createdAtMillis)
        holder.meta.text = RecordingRepository.formatMeta(item.createdAtMillis, item.sizeBytes)

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onSelected(item)
        }

        // Map long-press-like behavior to TV remote: DEL key or Menu key can delete.
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL || keyCode == KeyEvent.KEYCODE_MENU) {
                onDelete(item)
                true
            } else {
                false
            }
        }
    }

    internal class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.recTitle)
        val meta: TextView = itemView.findViewById(R.id.recMeta)
    }
}

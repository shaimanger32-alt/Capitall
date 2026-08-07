package com.shai.capitall.ui.spaces

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shai.capitall.data.model.Space
import com.shai.capitall.databinding.ItemSpaceBinding

class SpacesAdapter(
    private val onClick: (Space) -> Unit,
    private val onCodeClick: (Space) -> Unit
) : RecyclerView.Adapter<SpacesAdapter.SpaceViewHolder>() {

    private val items = mutableListOf<Space>()

    fun submitList(newItems: List<Space>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SpaceViewHolder(
        ItemSpaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: SpaceViewHolder, position: Int) =
        holder.bind(items[position], onClick, onCodeClick)

    override fun getItemCount() = items.size

    class SpaceViewHolder(private val binding: ItemSpaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(space: Space, onClick: (Space) -> Unit, onCodeClick: (Space) -> Unit) {
            binding.tvSpaceName.text = space.name
            binding.tvSpaceInitial.text = space.name.trim().take(1).ifBlank { "•" }
            binding.tvSpaceCode.text = space.inviteCode
            // שמות החברים ולא רק המספר — כך רואים מיד עם מי חולקים בלי להיכנס
            binding.tvSpaceMembers.text = space.memberIds.joinToString(" · ") { space.nameOf(it) }

            binding.root.setOnClickListener { onClick(space) }
            // לחיצה על הקוד פותחת העתקה/שיתוף בלי להיכנס לתיק
            binding.tvSpaceCode.setOnClickListener { onCodeClick(space) }
        }
    }
}

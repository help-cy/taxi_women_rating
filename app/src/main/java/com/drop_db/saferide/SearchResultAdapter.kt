package com.drop_db.saferide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drop_db.saferide.databinding.ItemSearchResultBinding
import com.drop_db.saferide.model.NominatimResult

class SearchResultAdapter(
    private val onClick: (NominatimResult) -> Unit
) : ListAdapter<NominatimResult, SearchResultAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NominatimResult) {
            binding.tvName.text = item.shortName
            binding.tvAddress.text = item.displayName
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NominatimResult>() {
            override fun areItemsTheSame(a: NominatimResult, b: NominatimResult) =
                a.displayName == b.displayName

            override fun areContentsTheSame(a: NominatimResult, b: NominatimResult) =
                a == b
        }
    }
}

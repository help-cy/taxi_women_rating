package com.drop_db.saferide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.drop_db.saferide.databinding.ItemTariffBinding
import com.drop_db.saferide.model.Tariff

class TariffAdapter(
    private val items: List<Tariff>,
    private val onSelect: (Tariff) -> Unit
) : RecyclerView.Adapter<TariffAdapter.VH>() {

    var selectedId: String = items.firstOrNull()?.id ?: ""
        private set

    inner class VH(val binding: ItemTariffBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTariffBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tariff = items[position]
        val ctx = holder.itemView.context
        with(holder.binding) {
            tvEmoji.text = tariff.emoji
            tvName.text = tariff.name
            tvTagline.text = tariff.tagline
            tvPrice.text = "€%.2f".format(tariff.price)
            tvEta.text = "~${tariff.etaMin} min"

            val selected = tariff.id == selectedId
            root.background = ContextCompat.getDrawable(
                ctx,
                if (selected) R.drawable.bg_tariff_selected else R.drawable.bg_tariff_default
            )
            tvName.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (selected) R.color.brand_primary else R.color.text_primary
                )
            )

            root.setOnClickListener {
                val prev = items.indexOfFirst { it.id == selectedId }
                selectedId = tariff.id
                notifyItemChanged(prev)
                notifyItemChanged(position)
                onSelect(tariff)
            }
        }
    }
}

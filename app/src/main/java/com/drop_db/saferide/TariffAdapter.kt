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

    fun selectTariff(id: String) {
        val prev = items.indexOfFirst { it.id == selectedId }
        selectedId = id
        if (prev >= 0) notifyItemChanged(prev)
        val next = items.indexOfFirst { it.id == id }
        if (next >= 0) notifyItemChanged(next)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tariff = items[position]
        val selected = tariff.id == selectedId
        with(holder.binding) {
            val context = root.context
            ivIcon.setImageResource(iconFor(tariff))
            tvName.text = labelFor(tariff)
            tvMeta.text = "${seatsFor(tariff)} • ${tariff.etaMin} min"
            tvTagline.text = taglineFor(tariff)
            tvPrice.text = if (selected) "€%.0f".format(tariff.price) else "~€%.0f".format(tariff.price)
            tariffContainer.background = ContextCompat.getDrawable(
                context,
                if (selected) R.drawable.bg_tariff_row_selected else R.drawable.bg_tariff_row_default
            )

            tariffContainer.setOnClickListener {
                selectTariff(tariff.id)
                onSelect(tariff)
            }
        }
    }

    private fun labelFor(tariff: Tariff): String = when (tariff.id) {
        "economy" -> "4-seater"
        "comfort" -> "6-seater"
        "premium" -> "Business"
        "safeplus" -> "SafeRide+"
        else -> tariff.name
    }

    private fun taglineFor(tariff: Tariff): String = when (tariff.id) {
        "economy" -> "Affordable fares"
        "comfort" -> "For large groups"
        "premium" -> "More comfort"
        "safeplus" -> "Extra safe trip"
        else -> tariff.tagline
    }

    private fun seatsFor(tariff: Tariff): String = when (tariff.id) {
        "comfort" -> "6"
        else -> "4"
    }

    private fun iconFor(tariff: Tariff): Int = when (tariff.id) {
        "economy" -> R.drawable.ic_transport_four_seater
        "comfort" -> R.drawable.ic_transport_six_seater
        "premium" -> R.drawable.ic_transport_city_to_city
        "safeplus" -> R.drawable.ic_transport_courier
        else -> R.drawable.ic_transport_four_seater
    }
}

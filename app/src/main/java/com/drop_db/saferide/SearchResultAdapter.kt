package com.drop_db.saferide

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drop_db.saferide.databinding.ItemSearchResultBinding
import com.drop_db.saferide.model.NominatimResult
import org.osmdroid.util.GeoPoint

class SearchResultAdapter(
    private val userLat: Double,
    private val userLon: Double,
    private val onClick: (NominatimResult) -> Unit
) : ListAdapter<NominatimResult, SearchResultAdapter.VH>(DIFF) {

    private var query: String = ""

    inner class VH(private val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NominatimResult) {
            binding.tvName.text = buildHighlightedName(item.shortName, query)
            binding.tvAddress.text = item.displayName.substringAfter(",", item.displayName).trim()
            binding.tvDistance.text = formatDistance(item.geoPoint)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    fun updateQuery(value: String) {
        query = value
    }

    private fun buildHighlightedName(name: String, query: String): CharSequence {
        if (query.isBlank()) return name
        val start = name.lowercase().indexOf(query.lowercase())
        if (start < 0) return name

        val end = start + query.length
        return SpannableString(name).apply {
            setSpan(ForegroundColorSpan(0xFF3D83E6.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun formatDistance(point: GeoPoint): String {
        if (userLat == 0.0 && userLon == 0.0) return ""
        val distanceMeters = GeoPoint(userLat, userLon).distanceToAsDouble(point)
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            "${"%.1f".format(distanceMeters / 1000.0)} km"
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

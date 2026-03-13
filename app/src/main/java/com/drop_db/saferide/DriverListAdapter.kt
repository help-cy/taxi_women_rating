package com.drop_db.saferide

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.drop_db.saferide.databinding.ItemDriverCardBinding
import com.drop_db.saferide.model.MockDriver

class DriverListAdapter(
    private val onBook: (MockDriver) -> Unit,
    private val onPass: (MockDriver) -> Unit,
    private val onReviews: (MockDriver, Int) -> Unit   // driver, templateIndex (0-based)
) : RecyclerView.Adapter<DriverListAdapter.VH>() {

    private val items = mutableListOf<MockDriver>()

    private val avatarColors = intArrayOf(
        Color.parseColor("#6C5CE7"),  // purple
        Color.parseColor("#00B09B"),  // teal
        Color.parseColor("#E84393"),  // pink
        Color.parseColor("#FF6B6B"),  // coral
        Color.parseColor("#45B7D1"),  // sky
        Color.parseColor("#6AB187"),  // sage
        Color.parseColor("#F0A500"),  // amber
    )

    fun submitList(drivers: List<MockDriver>) {
        items.clear()
        items.addAll(drivers)
        notifyDataSetChanged()
    }

    fun addDriver(driver: MockDriver) {
        items.add(driver)
        notifyItemInserted(items.size - 1)
    }

    fun remove(driver: MockDriver) {
        val idx = items.indexOfFirst { it.id == driver.id }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun isEmpty() = items.isEmpty()

    inner class VH(val b: ItemDriverCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDriverCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        with(holder.b) {
            // Avatar
            val initials = d.name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2).joinToString("")
            tvInitials.text = initials
            tvInitials.background.setTint(avatarColors[position % avatarColors.size])

            // Basic info
            tvDriverName.text = d.name
            tvDriverCar.text  = "${d.carModel}  •  ${d.plateNumber}"
            tvDriverEta.text  = "~${d.eta} min away"

            // Ratings
            tvOverallRating.text = String.format("%.1f", d.rating)
            tvReviewCount.text   = "${d.totalReviews} reviews"
            tvWomenRating.text = String.format("%.1f", d.womenRating)

            // See reviews link  (id is 1-based, templateIndex is 0-based)
            val templateIndex = d.id - 1
            tvSeeReviews.text = "See ${d.totalReviews} reviews"
            btnSeeReviews.setOnClickListener { onReviews(d, templateIndex) }
            root.setOnClickListener { onReviews(d, templateIndex) }

            // Buttons
            btnBook.setOnClickListener { onBook(d) }
            btnPass.setOnClickListener { onPass(d) }
        }
    }
}

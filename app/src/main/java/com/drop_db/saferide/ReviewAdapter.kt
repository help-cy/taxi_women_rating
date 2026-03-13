package com.drop_db.saferide

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.drop_db.saferide.databinding.ItemReviewBinding
import com.drop_db.saferide.model.Review

class ReviewAdapter : RecyclerView.Adapter<ReviewAdapter.VH>() {

    private val items = mutableListOf<Review>()

    private val avatarColors = intArrayOf(
        Color.parseColor("#6C5CE7"),
        Color.parseColor("#00B09B"),
        Color.parseColor("#E84393"),
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#45B7D1"),
        Color.parseColor("#6AB187"),
        Color.parseColor("#F0A500"),
    )

    fun submitList(reviews: List<Review>) {
        items.clear()
        items.addAll(reviews)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemReviewBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        with(holder.b) {
            // Avatar initials
            val initials = r.reviewerName
                .split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2).joinToString("")
            val photoRes = when {
                r.reviewerName.startsWith("Anna K.") -> R.drawable.anna_k
                r.reviewerName.startsWith("Sara T.") -> R.drawable.sara_t
                r.reviewerName.startsWith("Alex V.") -> R.drawable.alex_v
                r.reviewerName.startsWith("David S.") -> R.drawable.david_s
                r.reviewerName.startsWith("Maria P.") -> R.drawable.maria_p
                r.reviewerName.startsWith("John M.") -> R.drawable.john_m
                r.reviewerName.startsWith("Olivia R.") -> R.drawable.olivia_r
                else -> null
            }
            if (photoRes != null) {
                ivReviewerPhoto.setImageResource(photoRes)
                ivReviewerPhoto.visibility = View.VISIBLE
                tvReviewerInitials.visibility = View.GONE
            } else {
                ivReviewerPhoto.visibility = View.GONE
                tvReviewerInitials.visibility = View.VISIBLE
                tvReviewerInitials.text = initials
                tvReviewerInitials.background.setTint(
                    if (r.isFromWoman) Color.parseColor("#E84393")
                    else avatarColors[position % avatarColors.size]
                )
            }

            tvReviewerName.text =
                if (r.isFromWoman) {
                    val parts = r.reviewerName.trim().split(Regex("\\s+"))
                    val first = parts.getOrNull(0)?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                    val last = parts.getOrNull(1)?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                    when {
                        first.isNotEmpty() && last.isNotEmpty() -> "$first. $last."
                        first.isNotEmpty() -> "$first."
                        else -> ""
                    }
                } else {
                    r.reviewerName
                }
            tvReviewDate.text   = r.date
            tvWomanBadge.visibility = if (r.isFromWoman) View.VISIBLE else View.GONE

            // Stars: filled ★ colored, empty ☆ gray — each independently colored
            val filledColor = if (r.rating <= 2) Color.parseColor("#E84393") else Color.parseColor("#FFB300")
            val emptyColor  = Color.parseColor("#CCCCCC")
            val stars = SpannableStringBuilder()
            repeat(r.rating) {
                val start = stars.length
                stars.append('★')
                stars.setSpan(ForegroundColorSpan(filledColor), start, stars.length, 0)
            }
            repeat(5 - r.rating) {
                val start = stars.length
                stars.append('☆')
                stars.setSpan(ForegroundColorSpan(emptyColor), start, stars.length, 0)
            }
            tvReviewStars.text = stars

            tvReviewComment.text = r.comment
        }
    }
}

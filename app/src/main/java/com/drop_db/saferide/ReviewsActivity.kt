package com.drop_db.saferide

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import com.drop_db.saferide.databinding.ActivityReviewsBinding
import com.drop_db.saferide.model.MockReviews
import com.drop_db.saferide.model.Review

class ReviewsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DRIVER_TEMPLATE_INDEX = "driver_template_index"
        const val EXTRA_DRIVER_NAME           = "driver_name"
        const val EXTRA_OVERALL_RATING        = "overall_rating"
        const val EXTRA_WOMEN_RATING          = "women_rating"
        const val EXTRA_TOTAL_REVIEWS         = "total_reviews"
    }

    private lateinit var binding: ActivityReviewsBinding
    private val adapter = ReviewAdapter()
    private lateinit var allReviews: List<Review>

    private var showingWomenOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val templateIndex = intent.getIntExtra(EXTRA_DRIVER_TEMPLATE_INDEX, 0)
        val driverName    = intent.getStringExtra(EXTRA_DRIVER_NAME) ?: "Driver"
        val overallRating = intent.getFloatExtra(EXTRA_OVERALL_RATING, 0f)
        val womenRating   = intent.getFloatExtra(EXTRA_WOMEN_RATING, 0f)
        val totalReviews  = intent.getIntExtra(EXTRA_TOTAL_REVIEWS, 0)

        allReviews = MockReviews.forDriver(templateIndex)
        val womenReviews = allReviews.filter { it.isFromWoman }
        val allReviewsForUi = if (templateIndex == 4) allReviews.shuffled() else allReviews

        // Toolbar
        binding.tvDriverNameTitle.text = driverName
        val photoRes = when {
            driverName.startsWith("Anna K.") -> R.drawable.anna_k
            driverName.startsWith("Sara T.") -> R.drawable.sara_t
            driverName.startsWith("Alex V.") -> R.drawable.alex_v
            driverName.startsWith("David S.") -> R.drawable.david_s
            driverName.startsWith("Maria P.") -> R.drawable.maria_p
            driverName.startsWith("John M.") -> R.drawable.john_m
            driverName.startsWith("Olivia R.") -> R.drawable.olivia_r
            else -> null
        }
        if (photoRes != null) {
            binding.ivDriverPhoto.setImageResource(photoRes)
            binding.ivDriverPhoto.visibility = View.VISIBLE
            binding.tvDriverAvatar.visibility = View.GONE
        } else {
            binding.ivDriverPhoto.visibility = View.GONE
            binding.tvDriverAvatar.visibility = View.VISIBLE
            binding.tvDriverAvatar.text = driverName
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
            binding.tvDriverAvatar.background.setTint(0xFF90D800.toInt())
        }
        binding.tvDriverRatingLine.text = SpannableStringBuilder().apply {
            val overall = "★ %.1f overall".format(overallRating)
            val women = " ★ ♀ %.1f women".format(womenRating)
            append(overall)
            setSpan(ForegroundColorSpan(0xFFFFB300.toInt()), 0, 1, 0)
            val startWomen = length
            append(women)
            setSpan(ForegroundColorSpan(0xFFE84393.toInt()), startWomen, length, 0)
        }
        binding.btnBack.setOnClickListener { finish() }

        // Summary
        binding.tvSummaryOverall.text      = "%.1f".format(overallRating)
        binding.tvSummaryTotalReviews.text  = "$totalReviews reviews"
        binding.tvSummaryWomen.text         = "%.1f".format(womenRating)
        binding.tvSummaryWomenCount.text    = "${womenReviews.size} women reviews"

        // RecyclerView
        binding.rvReviews.layoutManager = LinearLayoutManager(this)
        binding.rvReviews.adapter = adapter

        // Tabs
        updateTabs()
        adapter.submitList(allReviewsForUi)

        binding.btnTabAll.setOnClickListener {
            showingWomenOnly = false
            updateTabs()
            adapter.submitList(allReviewsForUi)
        }

        binding.btnTabWomen.setOnClickListener {
            showingWomenOnly = true
            updateTabs()
            adapter.submitList(womenReviews)
        }
    }

    private fun updateTabs() {
        val colorActive   = 0xFF90D800.toInt()
        val colorInactive = getColor(R.color.bg_input)
        val textActive    = getColor(R.color.text_primary)
        val textInactive  = getColor(R.color.text_secondary)
        val womenPink     = 0xFFE84393.toInt()
        val womenBg       = 0xFFFFE3F1.toInt()

        with(binding) {
            btnTabAll.setBackgroundColor(if (!showingWomenOnly) colorActive else colorInactive)
            btnTabAll.setTextColor(if (!showingWomenOnly) textActive else textInactive)

            btnTabWomen.setBackgroundColor(if (showingWomenOnly) womenBg else colorInactive)
            btnTabWomen.setTextColor(womenPink)
        }
    }
}

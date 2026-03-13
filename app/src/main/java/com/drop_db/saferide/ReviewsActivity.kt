package com.drop_db.saferide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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

        // Toolbar
        binding.tvDriverNameTitle.text = driverName
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
        adapter.submitList(allReviews)

        binding.btnTabAll.setOnClickListener {
            showingWomenOnly = false
            updateTabs()
            adapter.submitList(allReviews)
        }

        binding.btnTabWomen.setOnClickListener {
            showingWomenOnly = true
            updateTabs()
            adapter.submitList(womenReviews)
        }
    }

    private fun updateTabs() {
        val colorActive   = getColor(R.color.brand_primary)
        val colorInactive = getColor(R.color.bg_input)
        val textActive    = getColor(R.color.white)
        val textInactive  = getColor(R.color.text_secondary)

        with(binding) {
            btnTabAll.setBackgroundColor(if (!showingWomenOnly) colorActive else colorInactive)
            btnTabAll.setTextColor(if (!showingWomenOnly) textActive else textInactive)

            btnTabWomen.setBackgroundColor(if (showingWomenOnly) colorActive else colorInactive)
            btnTabWomen.setTextColor(if (showingWomenOnly) textActive else textInactive)
        }
    }
}

package com.drop_db.saferide

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drop_db.saferide.databinding.ActivitySearchBinding
import com.drop_db.saferide.model.NominatimResult
import com.drop_db.saferide.util.NominatimApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter
    private var searchJob: Job? = null

    // User's current location for search biasing
    private var userLat: Double = 0.0
    private var userLon: Double = 0.0
    private var pickupAddress: String = "Current location"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userLat = intent.getDoubleExtra(EXTRA_USER_LAT, 0.0)
        userLon = intent.getDoubleExtra(EXTRA_USER_LON, 0.0)
        pickupAddress = intent.getStringExtra(EXTRA_PICKUP_ADDRESS) ?: "Current location"

        setupRecyclerView()
        setupSearch()
        binding.tvFromAddress.text = pickupAddress.substringBefore(",").trim().ifBlank { pickupAddress }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SearchResultAdapter(userLat, userLon) { result -> returnResult(result) }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.requestFocus()
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                searchJob?.cancel()
                if (query.length < 2) {
                    adapter.updateQuery("")
                    adapter.submitList(emptyList())
                    showEmptyState(true)
                    return
                }
                searchJob = lifecycleScope.launch {
                    delay(400)
                    performSearch(query)
                }
            }
        })
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private suspend fun performSearch(query: String) {
        showEmptyState(false)
        adapter.updateQuery(query)
        val results = NominatimApi.search(query, userLat, userLon)
        adapter.submitList(results)
        if (results.isEmpty()) {
            showEmptyState(true, "Nothing found for \"$query\"")
        }
    }

    private fun showEmptyState(show: Boolean, text: String = "Start typing to search…") {
        binding.tvEmptyState.text = text
        binding.tvEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvResults.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun returnResult(result: NominatimResult) {
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRA_LAT, result.geoPoint.latitude)
            putExtra(EXTRA_LON, result.geoPoint.longitude)
            putExtra(EXTRA_NAME, result.shortName)
            putExtra(EXTRA_ADDRESS, result.displayName)
        })
        finish()
    }

    companion object {
        const val EXTRA_USER_LAT = "extra_user_lat"
        const val EXTRA_USER_LON = "extra_user_lon"
        const val EXTRA_PICKUP_ADDRESS = "extra_pickup_address"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ADDRESS = "extra_address"
    }
}

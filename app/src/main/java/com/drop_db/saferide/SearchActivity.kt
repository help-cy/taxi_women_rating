package com.drop_db.saferide

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drop_db.saferide.databinding.ActivitySearchBinding
import com.drop_db.saferide.model.NominatimResult
import com.drop_db.saferide.util.NominatimApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController

class SearchActivity : AppCompatActivity() {

    private enum class ActiveField { FROM, TO }

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: SearchResultAdapter
    private var searchJob: Job? = null

    private var userLat: Double = 0.0
    private var userLon: Double = 0.0
    private var activeField: ActiveField = ActiveField.TO
    private var ignoreTextCallbacks = false

    private var initialPickupResult: NominatimResult? = null
    private var fromResult: NominatimResult? = null
    private var toResult: NominatimResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        Configuration.getInstance().load(
            this, android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userLat = intent.getDoubleExtra(EXTRA_USER_LAT, 0.0)
        userLon = intent.getDoubleExtra(EXTRA_USER_LON, 0.0)

        initialPickupResult = NominatimResult(
            displayName = intent.getStringExtra(EXTRA_PICKUP_ADDRESS) ?: "Current location",
            shortName = (intent.getStringExtra(EXTRA_PICKUP_ADDRESS) ?: "Current location")
                .substringBefore(",")
                .trim()
                .ifBlank { "Current location" },
            geoPoint = GeoPoint(
                intent.getDoubleExtra(EXTRA_PICKUP_LAT, userLat),
                intent.getDoubleExtra(EXTRA_PICKUP_LON, userLon)
            )
        )
        fromResult = initialPickupResult

        setupMap()
        setupRecyclerView()
        setupSearchFields()
        renderInitialValues()
        setActiveField(ActiveField.TO)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFromMap.setOnClickListener { restoreCurrentPickup() }
        binding.fieldFrom.setOnClickListener { setActiveField(ActiveField.FROM) }
        binding.fieldTo.setOnClickListener { setActiveField(ActiveField.TO) }
        binding.btnClearSearch.setOnClickListener { binding.etTo.text?.clear() }
        binding.btnClearFrom.setOnClickListener { binding.etFrom.text?.clear() }
    }

    override fun onResume() {
        super.onResume()
        binding.mapSearchView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapSearchView.onPause()
    }

    private fun setupMap() {
        with(binding.mapSearchView) {
            setTileSource(TileSourceFactory.MAPNIK)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMultiTouchControls(true)
            val center = GeoPoint(
                intent.getDoubleExtra(EXTRA_PICKUP_LAT, userLat).takeIf { it != 0.0 } ?: userLat,
                intent.getDoubleExtra(EXTRA_PICKUP_LON, userLon).takeIf { it != 0.0 } ?: userLon
            )
            if (center.latitude != 0.0 || center.longitude != 0.0) {
                controller.setZoom(15.0)
                controller.setCenter(center)
            }
        }

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                onMapTapped(p)
                return true
            }
            override fun longPressHelper(p: GeoPoint) = false
        }
        binding.mapSearchView.overlays.add(0, MapEventsOverlay(mapEventsReceiver))
    }

    private fun onMapTapped(point: GeoPoint) {
        hideKeyboard()
        binding.tvMapPickHint.text = "Loading address…"
        binding.tvMapPickHint.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = NominatimApi.reverse(point.latitude, point.longitude)
            if (result != null) {
                onSearchResultSelected(result)
            }
            binding.tvMapPickHint.visibility = View.GONE
        }
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun setupRecyclerView() {
        adapter = SearchResultAdapter(userLat, userLon) { result -> onSearchResultSelected(result) }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter
    }

    private fun setupSearchFields() {
        binding.etFrom.addTextChangedListener(searchWatcher(ActiveField.FROM))
        binding.etTo.addTextChangedListener(searchWatcher(ActiveField.TO))

        binding.etFrom.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setActiveField(ActiveField.FROM) }
        binding.etTo.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setActiveField(ActiveField.TO) }
    }

    private fun searchWatcher(field: ActiveField) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            if (ignoreTextCallbacks || activeField != field) return

            val query = s?.toString()?.trim().orEmpty()
            updateClearButtons()
            searchJob?.cancel()

            if (query.length < 2) {
                adapter.updateQuery("")
                adapter.submitList(emptyList())
                showEmptyState(true, if (query.isBlank()) "Start typing destination" else "Keep typing…")
                return
            }

            searchJob = lifecycleScope.launch {
                delay(220)
                performSearch(query)
            }
        }
    }

    private fun renderInitialValues() {
        ignoreTextCallbacks = true
        binding.etFrom.setText(fromResult?.shortName.orEmpty())
        binding.etTo.setText("")
        ignoreTextCallbacks = false
    }

    private fun restoreCurrentPickup() {
        val pickup = initialPickupResult ?: return
        fromResult = pickup
        ignoreTextCallbacks = true
        binding.etFrom.setText(pickup.shortName)
        ignoreTextCallbacks = false
        binding.etFrom.clearFocus()
        adapter.submitList(emptyList())
        showEmptyState(true, "Choose destination")
        setActiveField(ActiveField.TO)
    }

    private fun setActiveField(field: ActiveField) {
        activeField = field
        binding.fieldFrom.setBackgroundResource(
            if (field == ActiveField.FROM) R.drawable.bg_search_field_active else R.drawable.bg_search_field
        )
        binding.fieldTo.setBackgroundResource(
            if (field == ActiveField.TO) R.drawable.bg_search_field_active else R.drawable.bg_search_field
        )
        updateClearButtons()

        activeEditText().requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(activeEditText(), InputMethodManager.SHOW_IMPLICIT)
    }

    private fun activeEditText() = if (activeField == ActiveField.FROM) binding.etFrom else binding.etTo
    private fun updateClearButtons() {
        binding.btnClearFrom.visibility =
            if (activeField == ActiveField.FROM && !binding.etFrom.text.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.btnClearSearch.visibility =
            if (activeField == ActiveField.TO && !binding.etTo.text.isNullOrBlank()) View.VISIBLE else View.GONE
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

    private fun onSearchResultSelected(result: NominatimResult) {
        if (activeField == ActiveField.FROM) {
            fromResult = result
            ignoreTextCallbacks = true
            binding.etFrom.setText(result.shortName)
            ignoreTextCallbacks = false
            if (toResult != null) {
                returnResult()
            } else {
                adapter.submitList(emptyList())
                showEmptyState(true, "Choose destination")
                setActiveField(ActiveField.TO)
            }
        } else {
            toResult = result
            ignoreTextCallbacks = true
            binding.etTo.setText(result.shortName)
            ignoreTextCallbacks = false
            returnResult()
        }
    }

    private fun showEmptyState(show: Boolean, text: String = "Start typing destination") {
        binding.tvEmptyState.text = text
        binding.tvEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvResults.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun returnResult() {
        val from = fromResult ?: return
        val to = toResult ?: return
        hideKeyboard()
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRA_PICKUP_LAT, from.geoPoint.latitude)
            putExtra(EXTRA_PICKUP_LON, from.geoPoint.longitude)
            putExtra(EXTRA_PICKUP_ADDRESS, from.displayName)
            putExtra(EXTRA_PICKUP_NAME, from.shortName)
            putExtra(EXTRA_LAT, to.geoPoint.latitude)
            putExtra(EXTRA_LON, to.geoPoint.longitude)
            putExtra(EXTRA_NAME, to.shortName)
            putExtra(EXTRA_ADDRESS, to.displayName)
        })
        finish()
    }

    companion object {
        const val EXTRA_USER_LAT = "extra_user_lat"
        const val EXTRA_USER_LON = "extra_user_lon"
        const val EXTRA_PICKUP_LAT = "extra_pickup_lat"
        const val EXTRA_PICKUP_LON = "extra_pickup_lon"
        const val EXTRA_PICKUP_ADDRESS = "extra_pickup_address"
        const val EXTRA_PICKUP_NAME = "extra_pickup_name"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ADDRESS = "extra_address"
    }
}

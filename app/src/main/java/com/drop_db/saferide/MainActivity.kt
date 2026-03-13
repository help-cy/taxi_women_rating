package com.drop_db.saferide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drop_db.saferide.databinding.ActivityMainBinding
import com.drop_db.saferide.model.ALL_TARIFFS
import com.drop_db.saferide.model.MockData
import com.drop_db.saferide.model.MockDriver
import com.drop_db.saferide.model.Tariff
import com.drop_db.saferide.util.NominatimApi
import com.drop_db.saferide.util.OsrmApi
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ── Map ───────────────────────────────────────────────────────────────────
    private var routeOverlay: Polyline? = null
    private var arrivalRouteOverlay: Polyline? = null
    private var destinationMarker: Marker? = null
    private var userMarker: Marker? = null
    private var userLocation: GeoPoint? = null
    private var selectedPickupPoint: GeoPoint? = null
    private var selectedDestinationPoint: GeoPoint? = null
    private var selectedDestinationName: String = ""
    private var lastRoutePoints: List<GeoPoint> = emptyList()

    // ── Tariff ────────────────────────────────────────────────────────────────
    private lateinit var tariffAdapter: TariffAdapter
    private val tariffs = ALL_TARIFFS.map { it.copy() }.toMutableList()
    private var selectedTariff: Tariff = tariffs.first()

    // ── Driver list ───────────────────────────────────────────────────────────
    private lateinit var driverListAdapter: DriverListAdapter
    private var searchJob: Job? = null
    private var offersHeightLocked = false

    // ── Car animation ─────────────────────────────────────────────────────────
    private data class CarState(
        val driver: MockDriver,
        val marker: Marker,
        val spawn: GeoPoint,
        var pos: GeoPoint,
        var routePoints: List<GeoPoint>,
        var routeIndex: Int,
        var segmentProgress: Float
    )

    private val carStates = mutableListOf<CarState>()
    private var animJob: Job? = null
    private val roadDirs = listOf(
        0.002 to 0.000, -0.002 to 0.000,
        0.000 to 0.003, 0.000 to -0.003,
        0.0015 to 0.002, 0.0015 to -0.002,
        -0.0015 to 0.002, -0.0015 to -0.002
    )

    private var launchedSubActivity = false

    // ── Launchers ─────────────────────────────────────────────────────────────
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val ok = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                 perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) fetchLocation()
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        launchedSubActivity = false
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val pickupLat = data.getDoubleExtra(SearchActivity.EXTRA_PICKUP_LAT, Double.NaN)
            val pickupLon = data.getDoubleExtra(SearchActivity.EXTRA_PICKUP_LON, Double.NaN)
            val pickupAddress = data.getStringExtra(SearchActivity.EXTRA_PICKUP_ADDRESS).orEmpty()
            val lat  = data.getDoubleExtra(SearchActivity.EXTRA_LAT, 0.0)
            val lon  = data.getDoubleExtra(SearchActivity.EXTRA_LON, 0.0)
            val name = data.getStringExtra(SearchActivity.EXTRA_NAME) ?: ""
            if (!pickupLat.isNaN() && !pickupLon.isNaN()) {
                selectedPickupPoint = GeoPoint(pickupLat, pickupLon)
            }
            if (pickupAddress.isNotBlank()) updatePickupPoint(pickupAddress)
            drawRouteTo(GeoPoint(lat, lon), name)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        Configuration.getInstance().load(
            this, android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resetToHomeState()
        setupMap()
        setupTariffPanel()
        setupSearchingPanel()
        setupDriverListPanel()
        setupRateOverlay()
        setupListeners()
        requestLocationOrFetch()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (!launchedSubActivity && routeOverlay == null && destinationMarker == null && binding.loadingOverlay.visibility != View.VISIBLE) {
            resetToHomeState()
        }
        if (carStates.isNotEmpty()) startCarAnimation()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        animJob?.cancel()
    }

    // ── Map ───────────────────────────────────────────────────────────────────
    private fun setupMap() {
        with(binding.mapView) {
            setTileSource(TileSourceFactory.MAPNIK)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
        }
    }

    private fun setupListeners() {
        setupTransportSwitcher()
        binding.btnWhereTo.setOnClickListener {
            launchedSubActivity = true
            searchLauncher.launch(Intent(this, SearchActivity::class.java).apply {
                userLocation?.let {
                    putExtra(SearchActivity.EXTRA_USER_LAT, it.latitude)
                    putExtra(SearchActivity.EXTRA_USER_LON, it.longitude)
                }
                val pickup = selectedPickupPoint ?: userLocation
                pickup?.let {
                    putExtra(SearchActivity.EXTRA_PICKUP_LAT, it.latitude)
                    putExtra(SearchActivity.EXTRA_PICKUP_LON, it.longitude)
                }
                putExtra(SearchActivity.EXTRA_PICKUP_ADDRESS, binding.tvPickupAddress.text.toString())
            })
        }
        binding.tvSuggested1.setOnClickListener { quickRouteTo(binding.tvSuggested1.text.toString()) }
        binding.tvSuggested2.setOnClickListener { quickRouteTo(binding.tvSuggested2.text.toString()) }
        binding.tvSuggested3.setOnClickListener { quickRouteTo(binding.tvSuggested3.text.toString()) }
        binding.rowRouteTo.setOnClickListener {
            launchedSubActivity = true
            searchLauncher.launch(Intent(this, SearchActivity::class.java).apply {
                userLocation?.let {
                    putExtra(SearchActivity.EXTRA_USER_LAT, it.latitude)
                    putExtra(SearchActivity.EXTRA_USER_LON, it.longitude)
                }
                val pickup = selectedPickupPoint ?: userLocation
                pickup?.let {
                    putExtra(SearchActivity.EXTRA_PICKUP_LAT, it.latitude)
                    putExtra(SearchActivity.EXTRA_PICKUP_LON, it.longitude)
                }
                putExtra(SearchActivity.EXTRA_PICKUP_ADDRESS, binding.tvPickupAddress.text.toString())
            })
        }
        binding.fabMyLocation.setOnClickListener {
            centerOnUserLocation()
        }
        binding.btnCancelRequest.setOnClickListener { showCancelConfirm(true) }
    }

    private fun quickRouteTo(query: String) {
        val loc = userLocation ?: return
        lifecycleScope.launch {
            val results = NominatimApi.search(query, loc.latitude, loc.longitude)
            val first = results.firstOrNull()
            if (first != null) {
                drawRouteTo(first.geoPoint, first.shortName)
            } else {
                Snackbar.make(binding.root, "Couldn't find that place. Try search instead.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupTransportSwitcher() {
        val transportViews = listOf(
            binding.transportFourSeater,
            binding.transportSixSeater,
            binding.transportCouriers,
            binding.transportCityToCity
        )

        fun selectTransport(selected: LinearLayout) {
            transportViews.forEach { transport ->
                transport.setBackgroundResource(
                    if (transport === selected) R.drawable.bg_transport_selected
                    else R.drawable.bg_transport_item
                )
            }
        }

        transportViews.forEach { transport ->
            transport.setOnClickListener { selectTransport(transport) }
        }
    }

    private fun centerOnUserLocation() {
        userLocation?.let {
            binding.mapView.controller.animateTo(it)
            binding.mapView.controller.setZoom(18.5)
        }
    }

    private fun offsetMapForBottomPanel(
        panel: View,
        panelOffsetFraction: Float = 0.35f,
        center: GeoPoint? = null
    ) {
        val location = center ?: userLocation ?: return
        binding.mapView.post {
            val projection = binding.mapView.projection ?: return@post
            val baseOffset = if (panel.height > 0) panel.height else (binding.mapView.height * 0.25f).roundToInt()
            val offsetPx = (baseOffset * panelOffsetFraction).roundToInt()
            val point = projection.toPixels(location, Point())
            point.offset(0, offsetPx)
            val newCenter = projection.fromPixels(point.x, point.y) as GeoPoint
            binding.mapView.controller.animateTo(newCenter)
        }
    }

    private fun routeCenterPoint(): GeoPoint? {
        if (lastRoutePoints.isEmpty()) return selectedPickupPoint ?: userLocation
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        lastRoutePoints.forEach { p ->
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        return GeoPoint((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    }

    private fun driverPhotoRes(name: String): Int? = when {
        name.startsWith("Anna K.") -> R.drawable.anna_k
        name.startsWith("Sara T.") -> R.drawable.sara_t
        name.startsWith("Alex V.") -> R.drawable.alex_v
        name.startsWith("David S.") -> R.drawable.david_s
        name.startsWith("Maria P.") -> R.drawable.maria_p
        name.startsWith("John M.") -> R.drawable.john_m
        name.startsWith("Olivia R.") -> R.drawable.olivia_r
        else -> null
    }

    // ── PANEL 2: Tariff ───────────────────────────────────────────────────────
    private fun setupTariffPanel() {
        tariffAdapter = TariffAdapter(
            items = tariffs,
            onSelect = { t -> selectedTariff = t },
            onDecreasePrice = { t -> adjustTariffPrice(t, -1.0) },
            onIncreasePrice = { t -> adjustTariffPrice(t, 1.0) }
        )
        binding.rvTariffs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tariffAdapter
        }
        binding.btnCloseTariff.setOnClickListener {
            binding.cardTariff.slideDown()
            binding.cardRoutePreview.slideDown()
            clearRoute()
            setHomeChromeVisible(true)
            binding.cardWhereTo.slideUp()
            centerOnUserLocation()
        }
        binding.btnBook.setOnClickListener { startDriverSearch() }
    }

    private fun showTariffPanel(distanceKm: Double, nearestEta: Int) {
        setHomeChromeVisible(false)
        tariffs.forEachIndexed { i, t ->
            val base = 0.90 + distanceKm * 1.20
            tariffs[i] = t.copy(
                recommendedPrice = base * t.priceMultiplier,
                price  = base * t.priceMultiplier,
                etaMin = (nearestEta + t.etaOffsetMin).coerceAtLeast(1)
            )
        }
        selectedTariff = tariffs.first()
        tariffAdapter.selectTariff(selectedTariff.id)
        tariffAdapter.notifyDataSetChanged()

        val timeMin = (distanceKm / 30.0 * 60).roundToInt()
        binding.tvRouteSummary.text = "${"%.1f".format(distanceKm)} km  •  ~$timeMin min"
        binding.tvRouteFrom.text = binding.tvPickupAddress.text.toString().substringBefore(",").trim()
        binding.tvRouteTo.text = selectedDestinationName.ifBlank { "Destination" }
        binding.tvRouteEtaBadge.text = "~$timeMin min"

        binding.cardWhereTo.slideDown()
        binding.cardRoutePreview.slideUp()
        binding.cardTariff.slideUp()
        offsetMapForBottomPanel(binding.cardTariff, panelOffsetFraction = 0.55f, center = routeCenterPoint())
    }

    private fun adjustTariffPrice(tariff: Tariff, delta: Double) {
        if (tariff.id != selectedTariff.id) return
        tariff.price = (tariff.price + delta).coerceAtLeast(1.0)
        selectedTariff = tariff
        tariffAdapter.refreshSelected()
    }

    // ── PANEL 3: Searching ────────────────────────────────────────────────────
    private fun setupSearchingPanel() {
        binding.btnDecreaseSearchingPrice.setOnClickListener {
            adjustTariffPrice(selectedTariff, -1.0)
            syncSearchingFare()
        }
        binding.btnIncreaseSearchingPrice.setOnClickListener {
            adjustTariffPrice(selectedTariff, 1.0)
            syncSearchingFare()
        }
        binding.btnRaiseFare.setOnClickListener {
            adjustTariffPrice(selectedTariff, 1.0)
            syncSearchingFare()
        }
    }

    private fun startDriverSearch() {
        searchJob?.cancel()
        offersHeightLocked = false
        val center = userLocation ?: return
        val allDrivers = MockData.driversAround(center).shuffled()
        val filtered = if (selectedTariff.id == "safeplus")
            allDrivers.filter { it.womenSafe } else allDrivers

        binding.cardTariff.slideDown()
        binding.cardSearching.slideUp()
        hideImmediately(binding.panelDriverList)
        binding.btnCancelRequest.visibility = View.VISIBLE
        binding.fabMyLocation.visibility = View.GONE
        centerOnUserLocation()
        driverListAdapter.submitList(emptyList())
        syncSearchingFare()
        updateSearchingDriverState(0, filtered.size)
        binding.tvSearchingTimer.text = "1:00"
        binding.progressSearching.progress = 100

        searchJob = lifecycleScope.launch {
            val timerJob = launch {
                for (remaining in 60 downTo 0) {
                    val min = remaining / 60
                    val sec = remaining % 60
                    binding.tvSearchingTimer.text = "%d:%02d".format(min, sec)
                    binding.progressSearching.progress = ((remaining / 60.0) * 100.0).roundToInt()
                    if (remaining > 0) delay(1000L)
                }
            }

            val offersJob = launch {
                // First offer quickly, then steady stream.
                delay(3000L)
                if (!isActive) return@launch
                filtered.forEachIndexed { index, driver ->
                    if (index > 0) delay(2000L)
                    if (!isActive) return@launch

                    if (index == 0) {
                        driverListAdapter.submitList(listOf(driver))
                        // Once we have the first real offer, keep only the offers overlay on screen.
                        binding.cardSearching.slideDown()
                        binding.cardRoutePreview.slideDown()
                        showTopOffers()
                        lockOffersListHeightTo(3)
                    } else {
                        driverListAdapter.addDriver(driver)
                        lockOffersListHeightTo(3)
                    }
                    updateSearchingDriverState(driverListAdapter.itemCount, filtered.size)
                    // No auto-scroll on new offers; user can scroll manually.
                }
            }

            timerJob.join()
            offersJob.cancel()

            if (driverListAdapter.isEmpty()) {
                Snackbar.make(binding.root, "No offers received. Try again!", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun syncSearchingFare() {
        binding.tvSearchingPrice.text = "€%.0f".format(selectedTariff.price)
        binding.btnRaiseFare.text = "Raise fare to €%.0f".format(selectedTariff.price + 1.0)
    }

    // ── PANEL 4: Driver list ──────────────────────────────────────────────────
    private fun setupDriverListPanel() {
        driverListAdapter = DriverListAdapter(
            onBook = { driver -> onBookDriver(driver) },
            onPass = { driver ->
                driverListAdapter.remove(driver)
                updateSearchingDriverState(driverListAdapter.itemCount, driverListAdapter.itemCount)
                if (driverListAdapter.isEmpty()) {
                    hideImmediately(binding.panelDriverList)
                    Snackbar.make(binding.root, "No more drivers. Try again!", Snackbar.LENGTH_LONG).show()
                }
            },
            onReviews = { driver, templateIndex ->
                launchedSubActivity = true
                startActivity(Intent(this, ReviewsActivity::class.java).apply {
                    putExtra(ReviewsActivity.EXTRA_DRIVER_TEMPLATE_INDEX, templateIndex)
                    putExtra(ReviewsActivity.EXTRA_DRIVER_NAME,           driver.name)
                    putExtra(ReviewsActivity.EXTRA_OVERALL_RATING,        driver.rating)
                    putExtra(ReviewsActivity.EXTRA_WOMEN_RATING,          driver.womenRating)
                    putExtra(ReviewsActivity.EXTRA_TOTAL_REVIEWS,         driver.totalReviews)
                })
            }
        )
        binding.rvDrivers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = driverListAdapter
        }
    }

    private fun setHomeChromeVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnMenu.visibility = visibility
        binding.cardPickupPoint.visibility = visibility
    }

    private fun resetToHomeState() {
        hideImmediately(binding.loadingOverlay)
        hideImmediately(binding.offersDim)
        hideImmediately(binding.cardRoutePreview)
        hideImmediately(binding.cardTariff)
        hideImmediately(binding.cardSearching)
        hideImmediately(binding.panelDriverList)
        hideImmediately(binding.cancelConfirmOverlay)
        hideImmediately(binding.rateOverlay)
        hideImmediately(binding.cardDriverArriving)
        hideImmediately(binding.cardInRide)
        binding.btnCancelRequest.visibility = View.GONE
        binding.cardWhereTo.visibility = View.VISIBLE
        binding.cardWhereTo.translationY = 0f
        binding.fabMyLocation.visibility = View.VISIBLE
        launchedSubActivity = false
        setHomeChromeVisible(true)
    }

    private fun hideImmediately(view: View) {
        view.animate().cancel()
        view.clearAnimation()
        view.translationY = 0f
        view.visibility = View.GONE
    }

    private fun showDriverList(drivers: List<MockDriver>, center: GeoPoint) {
        driverListAdapter.submitList(emptyList())
        updateSearchingDriverState(0, drivers.size)

        searchJob = lifecycleScope.launch {
            drivers.forEachIndexed { index, driver ->
                delay(if (index == 0) 600L else 900L)
                driverListAdapter.addDriver(driver)
                if (binding.panelDriverList.visibility != View.VISIBLE) {
                    showTopOffers()
                }
                updateSearchingDriverState(driverListAdapter.itemCount, drivers.size)
                binding.rvDrivers.scrollToPosition(driverListAdapter.itemCount - 1)
            }
        }
    }

    private fun updateSearchingDriverState(foundCount: Int, totalCount: Int) {
        binding.tvSearchingStatus.text = when {
            foundCount <= 0 -> "Searching nearby drivers"
            else -> "Offers are coming in"
        }
    }

    private fun showTopOffers() {
        binding.panelDriverList.visibility = View.VISIBLE
        binding.tvDriverListSubtitle.text = "Choose a driver"
        binding.btnCancelRequest.bringToFront()
        binding.offersDim.visibility = View.VISIBLE
        binding.offersDim.alpha = 0f
        binding.offersDim.animate().alpha(1f).setDuration(160L).start()
        binding.panelDriverList.post {
            val offsetPx = resources.displayMetrics.density * 48f
            binding.panelDriverList.translationY = -offsetPx
            binding.panelDriverList.alpha = 0f
            binding.panelDriverList.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(260L)
                .start()
        }
    }

    // ── Rate overlay (post-trip) ─────────────────────────────────────────────
    private var rateStars = 5

    private fun setupRateOverlay() {
        val content = binding.rateContent
        val originalTop = content.paddingTop
        val compactTop = (6 * resources.displayMetrics.density).toInt()

        fun applyStars() {
            val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
            stars.forEachIndexed { idx, tv -> tv.alpha = if (idx < rateStars) 1f else 0.25f }
        }
        fun setStars(n: Int) {
            rateStars = n.coerceIn(1, 5)
            applyStars()
        }

        binding.star1.setOnClickListener { setStars(1) }
        binding.star2.setOnClickListener { setStars(2) }
        binding.star3.setOnClickListener { setStars(3) }
        binding.star4.setOnClickListener { setStars(4) }
        binding.star5.setOnClickListener { setStars(5) }
        applyStars()

        binding.btnCloseRate.setOnClickListener { finishRateOverlay() }
        binding.rateOverlay.setOnClickListener { finishRateOverlay() }
        binding.cardRate.setOnClickListener { /* consume */ }
        binding.btnSubmitRate.setOnClickListener {
            Snackbar.make(binding.root, "Thanks for your feedback!", Snackbar.LENGTH_LONG).show()
            finishRateOverlay()
        }
        binding.etRateComment.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                content.setPadding(content.paddingLeft, compactTop, content.paddingRight, content.paddingBottom)
            } else {
                content.setPadding(content.paddingLeft, originalTop, content.paddingRight, content.paddingBottom)
            }
        }
    }

    private fun showRateOverlay(driver: MockDriver) {
        rateStars = 5
        binding.etRateComment.setText("")
        binding.chipGroupWomen.clearCheck()
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        stars.forEachIndexed { idx, tv -> tv.alpha = if (idx < rateStars) 1f else 0.25f }

        binding.btnCancelRequest.visibility = View.GONE
        binding.offersDim.visibility = View.GONE
        binding.panelDriverList.visibility = View.GONE
        binding.cancelConfirmOverlay.visibility = View.GONE
        binding.fabMyLocation.visibility = View.GONE
        setHomeChromeVisible(false)

        binding.rateOverlay.visibility = View.VISIBLE
        binding.rateOverlay.bringToFront()
        binding.cardRate.bringToFront()
        binding.rateOverlay.alpha = 0f
        binding.rateOverlay.animate().alpha(1f).setDuration(160L).start()
        binding.cardRate.scaleX = 0.96f
        binding.cardRate.scaleY = 0.96f
        binding.cardRate.alpha = 0f
        binding.cardRate.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L).start()
    }

    private fun finishRateOverlay() {
        binding.cardRate.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(160L)
            .start()
        binding.rateOverlay.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction {
                hideImmediately(binding.rateOverlay)
                resetToHomeState()
                userLocation?.let { spawnCars(it) }
            }
            .start()
    }

    private fun showCancelConfirm(show: Boolean) {
        if (show) {
            binding.cancelConfirmOverlay.visibility = View.VISIBLE
            binding.cancelConfirmOverlay.alpha = 0f
            binding.cancelConfirmOverlay.animate().alpha(1f).setDuration(140L).start()

            binding.cardCancelConfirm.post {
                binding.cardCancelConfirm.translationY = binding.cardCancelConfirm.height.toFloat()
                binding.cardCancelConfirm.animate()
                    .translationY(0f)
                    .setDuration(260L)
                    .start()
            }

            binding.btnKeepSearching.setOnClickListener { showCancelConfirm(false) }
            binding.btnConfirmCancelRequest.setOnClickListener {
                searchJob?.cancel()
                searchJob = null
                showCancelConfirm(false)
                clearRoute()
                resetToHomeState()
            }
            binding.cancelConfirmOverlay.setOnClickListener { showCancelConfirm(false) }
        } else {
            binding.cardCancelConfirm.animate()
                .translationY(binding.cardCancelConfirm.height.toFloat())
                .setDuration(220L)
                .withEndAction {
                    binding.cancelConfirmOverlay.visibility = View.GONE
                    binding.cancelConfirmOverlay.alpha = 1f
                }
                .start()
        }
    }

    private fun lockOffersListHeightTo(maxVisibleItems: Int) {
        if (offersHeightLocked) return
        binding.rvDrivers.post {
            val firstChild = binding.rvDrivers.getChildAt(0) ?: return@post
            val lp = firstChild.layoutParams as? androidx.recyclerview.widget.RecyclerView.LayoutParams
            val perItem = firstChild.height + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
            if (perItem <= 0) return@post
            val visible = maxVisibleItems.coerceAtMost(driverListAdapter.itemCount.coerceAtLeast(1))
            val desired = perItem * visible + binding.rvDrivers.paddingTop + binding.rvDrivers.paddingBottom
            binding.rvDrivers.layoutParams = binding.rvDrivers.layoutParams.apply {
                height = desired
            }
            binding.rvDrivers.requestLayout()
            if (driverListAdapter.itemCount >= maxVisibleItems) {
                offersHeightLocked = true
            }
        }
    }

    private fun onBookDriver(driver: MockDriver) {
        searchJob?.cancel()
        hideImmediately(binding.panelDriverList)
        hideImmediately(binding.offersDim)
        hideImmediately(binding.cancelConfirmOverlay)
        binding.btnCancelRequest.visibility = View.GONE

        // Remove all car markers except the booked driver's
        animJob?.cancel()
        val bookedState = carStates.find { it.driver.id == driver.id }
        carStates.filter { it.driver.id != driver.id }.forEach { binding.mapView.overlays.remove(it.marker) }
        carStates.removeAll { it.driver.id != driver.id }

        // Hide trip route — will be redrawn when we start the ride
        routeOverlay?.let { binding.mapView.overlays.remove(it); routeOverlay = null }
        destinationMarker?.let { binding.mapView.overlays.remove(it) }
        binding.mapView.invalidate()

        // Fill arriving panel immediately
        binding.tvArrivingTitle.text = "Your driver will arrive in ~${driver.eta} min"
        binding.tvArrivingCar.text = driver.carModel
        binding.tvArrivingPlate.text = driver.plateNumber
        binding.tvArrivingName.text = driver.name
        binding.tvArrivingRating.text = "★ ${"%.1f".format(driver.rating)}"
        binding.tvArrivingWomenRating.text = "★ ♀ ${"%.1f".format(driver.womenRating)}"
        val arrivingPhoto = driverPhotoRes(driver.name)
        if (arrivingPhoto != null) {
            binding.ivArrivingPhoto.setImageResource(arrivingPhoto)
            binding.ivArrivingPhoto.visibility = View.VISIBLE
            binding.tvArrivingAvatar.visibility = View.GONE
        } else {
            binding.ivArrivingPhoto.visibility = View.GONE
            binding.tvArrivingAvatar.visibility = View.VISIBLE
            val avatarColors = listOf(0xFF6C5CE7, 0xFF00B894, 0xFF0984E3, 0xFFE17055, 0xFF00CEC9, 0xFFFDAB5D, 0xFF74B9FF)
            val circleColor = avatarColors[driver.id % avatarColors.size].toInt()
            binding.tvArrivingAvatar.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(circleColor)
            }
            val initials = driver.name.trim().split(Regex("\\s+"))
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2).joinToString("")
            binding.tvArrivingAvatar.text = initials
        }
        binding.btnCancelArriving.setOnClickListener {
            animJob?.cancel()
            hideImmediately(binding.cardDriverArriving)
            clearRoute()
            setHomeChromeVisible(true)
            binding.cardWhereTo.slideUp()
        }
        binding.cardDriverArriving.slideUp()

        val pickup = selectedPickupPoint ?: userLocation
        if (bookedState == null || pickup == null) return

        animJob = lifecycleScope.launch {
            val startPos = bookedState.pos

            // Fetch arrival route driver → pickup
            val route = OsrmApi.getRoute(startPos, pickup).takeIf { it.size >= 2 }
                ?: listOf(startPos, pickup)

            // Draw arrival route and zoom to fit it
            arrivalRouteOverlay?.let { binding.mapView.overlays.remove(it) }
            arrivalRouteOverlay = Polyline(binding.mapView).apply {
                setPoints(route)
                outlinePaint.color = ContextCompat.getColor(this@MainActivity, R.color.route_color)
                outlinePaint.strokeWidth = 11f
                outlinePaint.alpha = 200
            }
            binding.mapView.overlays.add(arrivalRouteOverlay)
            binding.mapView.post {
                // Pull back a bit more while the driver is arriving.
                binding.mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(route), true, 240)
            }
            binding.mapView.invalidate()

            val totalSteps = 80
            val stepMs = 5000L / totalSteps
            var lastEta = driver.eta
            for (step in 1..totalSteps) {
                val frac = step.toFloat() / totalSteps
                val newPos = interpolateRoute(route, frac)
                val prevPos = interpolateRoute(route, (step - 1f) / totalSteps)
                val dLat = newPos.latitude - prevPos.latitude
                val dLon = newPos.longitude - prevPos.longitude
                if (dLat * dLat + dLon * dLon > 1e-14)
                    bookedState.marker.rotation = Math.toDegrees(atan2(dLon, dLat)).toFloat()
                bookedState.marker.position = newPos
                bookedState.pos = newPos

                // Trim arrival route to show only remaining path
                val dropCount = (route.size * frac).toInt().coerceIn(0, route.size - 2)
                arrivalRouteOverlay?.setPoints(route.drop(dropCount))

                // Countdown ETA
                val newEta = (driver.eta * (1f - frac)).toInt().coerceAtLeast(0)
                if (newEta != lastEta) {
                    lastEta = newEta
                    binding.tvArrivingTitle.text =
                        if (newEta == 0) "Your driver is here!" else "Your driver will arrive in ~$newEta min"
                }
                binding.mapView.invalidate()
                delay(stepMs)
            }

            arrivalRouteOverlay?.let { binding.mapView.overlays.remove(it); arrivalRouteOverlay = null }
            binding.mapView.invalidate()
            delay(600L)
            startInRide(driver, bookedState)
        }
    }

    private fun startInRide(driver: MockDriver, carState: CarState) {
        hideImmediately(binding.cardDriverArriving)

        val destination = selectedDestinationPoint ?: return
        val pickup = selectedPickupPoint ?: userLocation ?: carState.pos

        // Calculate ETA
        val distKm = routeDistanceKmLast.coerceAtLeast(0.5)
        val etaMinutes = (distKm / 30.0 * 60).toLong().coerceAtLeast(1)
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, etaMinutes.toInt()) }
        val etaStr = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        binding.tvInRideEta.text = "You will arrive at ~$etaStr"
        binding.tvInRideCar.text = driver.carModel
        binding.tvInRidePlate.text = driver.plateNumber
        binding.tvInRideDriverName.text = driver.name
        binding.tvInRideRating.text = "★ ${"%.1f".format(driver.rating)}"
        binding.tvInRideWomenRating.text = "★ ♀ ${"%.1f".format(driver.womenRating)}"

        val inRidePhoto = driverPhotoRes(driver.name)
        if (inRidePhoto != null) {
            binding.ivInRidePhoto.setImageResource(inRidePhoto)
            binding.ivInRidePhoto.visibility = View.VISIBLE
            binding.tvInRideAvatar.visibility = View.GONE
        } else {
            binding.ivInRidePhoto.visibility = View.GONE
            binding.tvInRideAvatar.visibility = View.VISIBLE
            val avatarColors = listOf(0xFF6C5CE7, 0xFF00B894, 0xFF0984E3, 0xFFE17055, 0xFF00CEC9, 0xFFFDAB5D, 0xFF74B9FF)
            val circleColor = avatarColors[driver.id % avatarColors.size].toInt()
            binding.tvInRideAvatar.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(circleColor)
            }
            val initials = driver.name.trim().split(Regex("\\s+"))
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2).joinToString("")
            binding.tvInRideAvatar.text = initials
        }
        binding.cardInRide.slideUp()

        animJob = lifecycleScope.launch {
            // Use saved route or re-fetch
            val tripRoute = if (lastRoutePoints.size >= 2) lastRoutePoints
                            else OsrmApi.getRoute(pickup, destination).takeIf { it.size >= 2 }
                                ?: listOf(pickup, destination)

            // Draw trip route
            routeOverlay?.let { binding.mapView.overlays.remove(it) }
            routeOverlay = Polyline(binding.mapView).apply {
                setPoints(tripRoute)
                outlinePaint.color = ContextCompat.getColor(this@MainActivity, R.color.route_color)
                outlinePaint.strokeWidth = 13f
                outlinePaint.alpha = 210
            }
            binding.mapView.overlays.add(0, routeOverlay)

            // Re-add destination marker
            destinationMarker?.let { binding.mapView.overlays.remove(it) }
            destinationMarker = Marker(binding.mapView).apply {
                position = destination
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_pin)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            binding.mapView.overlays.add(destinationMarker)

            // Zoom to show full trip
            binding.mapView.post {
                binding.mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(tripRoute), true, 160)
            }
            binding.mapView.invalidate()
            delay(800L) // let zoom settle

            val totalSteps = 100
            val stepMs = 10_000L / totalSteps
            for (step in 1..totalSteps) {
                val frac = step.toFloat() / totalSteps
                val newPos = interpolateRoute(tripRoute, frac)
                val prevPos = interpolateRoute(tripRoute, (step - 1f) / totalSteps)
                val dLat = newPos.latitude - prevPos.latitude
                val dLon = newPos.longitude - prevPos.longitude
                if (dLat * dLat + dLon * dLon > 1e-14)
                    carState.marker.rotation = Math.toDegrees(atan2(dLon, dLat)).toFloat()
                carState.marker.position = newPos

                // Trim driven portion of route
                val dropCount = (tripRoute.size * frac).toInt().coerceIn(0, tripRoute.size - 2)
                routeOverlay?.setPoints(tripRoute.drop(dropCount))

                binding.mapView.controller.animateTo(newPos)
                binding.mapView.invalidate()
                delay(stepMs)
            }
            // Arrived at destination
            hideImmediately(binding.cardInRide)
            clearRoute()
            carStates.forEach { binding.mapView.overlays.remove(it.marker) }
            carStates.clear()
            delay(250L)
            showRateOverlay(driver)
        }
    }

    // ── Location ──────────────────────────────────────────────────────────────
    private fun requestLocationOrFetch() {
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun fetchLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            if (!lm.isProviderEnabled(p)) continue
            val loc = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
            if (loc != null && (best == null || loc.accuracy < best.accuracy)) best = loc
        }
        if (best != null) { onLocationReady(GeoPoint(best.latitude, best.longitude)); return }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                onLocationReady(GeoPoint(loc.latitude, loc.longitude))
                lm.removeUpdates(this)
            }
            @Deprecated("") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
        }
        for (p in providers) {
            if (!lm.isProviderEnabled(p)) continue
            try { lm.requestLocationUpdates(p, 0L, 0f, listener); break }
            catch (_: SecurityException) {}
        }
    }

    private fun onLocationReady(location: GeoPoint) {
        userLocation = location
        if (selectedPickupPoint == null) selectedPickupPoint = location
        binding.mapView.controller.animateTo(location)
        binding.mapView.controller.setZoom(18.5)
        placeUserMarker(location)
        spawnCars(location)
        loadPickupAddress(location)
    }

    private fun loadPickupAddress(location: GeoPoint) {
        lifecycleScope.launch {
            val result = NominatimApi.reverse(location.latitude, location.longitude)
            val address = result?.displayName
                ?.substringBefore(", Cyprus")
                ?.substringBefore(", Kipr")
                ?.trim()
                .orEmpty()

            if (address.isNotBlank()) {
                updatePickupPoint(address)
            } else {
                updatePickupPoint("${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}")
            }
        }
    }

    private fun updatePickupPoint(address: String) {
        binding.tvPickupAddress.text = address
    }

    // ── Overlays ──────────────────────────────────────────────────────────────
    private fun placeUserMarker(location: GeoPoint) {
        userMarker?.let { binding.mapView.overlays.remove(it) }
        userMarker = Marker(binding.mapView).apply {
            position = location
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_user_dot)
            title = "You are here"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(userMarker)
        binding.mapView.invalidate()
    }

    private fun spawnCars(center: GeoPoint) {
        animJob?.cancel()
        carStates.clear()
        val carIcon = ContextCompat.getDrawable(this, R.drawable.ic_car_marker)
        MockData.driversAround(center).forEach { driver ->
            val spawn = driver.spawnPoint
            val marker = Marker(binding.mapView).apply {
                position = spawn
                icon = carIcon
                title = "${driver.name}  ★${driver.rating}"
                snippet = "${driver.carModel}  •  ~${driver.eta} min"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            binding.mapView.overlays.add(marker)
            carStates.add(CarState(driver, marker, spawn, GeoPoint(spawn), emptyList(), 0, 0f))
        }
        binding.mapView.invalidate()
        startCarAnimation()
    }

    private fun startCarAnimation() {
        animJob?.cancel()
        animJob = lifecycleScope.launch {
            while (isActive) {
                carStates.forEach { car ->
                    if (car.routePoints.size < 2 || car.routeIndex >= car.routePoints.size - 1) {
                        val route = buildCarRoute(car.pos, car.spawn)
                        if (route.size < 2) return@forEach
                        car.routePoints = route
                        car.routeIndex = 0
                        car.segmentProgress = 0f
                    }

                    val from = car.routePoints[car.routeIndex]
                    val to = car.routePoints[car.routeIndex + 1]
                    val segLen = from.distanceToAsDouble(to).coerceAtLeast(1e-6)
                    val stepFrac = (9.0 / segLen).coerceAtMost(1.0)
                    car.segmentProgress = (car.segmentProgress + stepFrac).toFloat()

                    val newPos = if (car.segmentProgress >= 1f) {
                        car.routeIndex += 1
                        car.segmentProgress = 0f
                        to
                    } else {
                        GeoPoint(
                            from.latitude + (to.latitude - from.latitude) * car.segmentProgress,
                            from.longitude + (to.longitude - from.longitude) * car.segmentProgress
                        )
                    }

                    val dLat = newPos.latitude - car.pos.latitude
                    val dLon = newPos.longitude - car.pos.longitude
                    if (dLat * dLat + dLon * dLon > 1e-12)
                        car.marker.rotation = Math.toDegrees(atan2(dLon, dLat)).toFloat()
                    car.marker.position = newPos
                    car.pos = newPos
                }
                binding.mapView.invalidate()
                delay(120L)
            }
        }
    }

    private suspend fun buildCarRoute(current: GeoPoint, spawn: GeoPoint): List<GeoPoint> {
        repeat(4) {
            val dir = roadDirs.random()
            val candidate = GeoPoint(current.latitude + dir.first, current.longitude + dir.second)
            if (candidate.distanceToAsDouble(spawn) > 500.0) return@repeat
            val route = OsrmApi.getRoute(current, candidate)
            if (route.size >= 2) return route
        }
        val fallback = OsrmApi.getRoute(current, spawn)
        return if (fallback.size >= 2) fallback else emptyList()
    }

    // ── Route ─────────────────────────────────────────────────────────────────
    private var routeDistanceKmLast = 0.0

    private fun drawRouteTo(destination: GeoPoint, name: String) {
        val origin = selectedPickupPoint ?: userLocation ?: return
        selectedDestinationName = name
        selectedDestinationPoint = destination
        clearRoute()
        showRouteLoading(true)

        destinationMarker = Marker(binding.mapView).apply {
            position = destination
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_pin)
            title = name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(destinationMarker)
        binding.mapView.invalidate()

        lifecycleScope.launch {
            val nearestEta = MockData.driversAround(origin).minOf { it.eta }
            val quickDistanceKm = (origin.distanceToAsDouble(destination) / 1000.0).coerceAtLeast(0.3)
            val routeDeferred = async { OsrmApi.getRoute(origin, destination) }

            try {
                val initialRoute = withTimeoutOrNull(7000) { routeDeferred.await() }
                if (!initialRoute.isNullOrEmpty()) {
                    renderRoute(origin, destination, initialRoute, true)
                    routeDistanceKmLast = routeDistanceKm(initialRoute).coerceAtLeast(0.3)
                } else {
                    routeDistanceKmLast = quickDistanceKm
                    lifecycleScope.launch {
                        val finalRoute = routeDeferred.await()
                        if (finalRoute.isNotEmpty()) {
                            renderRoute(origin, destination, finalRoute, true)
                        } else {
                            renderRoute(origin, destination, listOf(origin, destination), false)
                            Snackbar.make(
                                binding.root,
                                "Couldn't load road route. Showing direct path instead.",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                showTariffPanel(routeDistanceKmLast, nearestEta)
            } finally {
                showRouteLoading(false)
            }
        }
    }

    private fun renderRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        points: List<GeoPoint>,
        roadRoute: Boolean
    ) {
        lastRoutePoints = points
        routeOverlay?.let { binding.mapView.overlays.remove(it) }
        routeOverlay = Polyline(binding.mapView).apply {
            setPoints(points)
            outlinePaint.color = ContextCompat.getColor(this@MainActivity, R.color.route_color)
            outlinePaint.strokeWidth = 13f
            outlinePaint.alpha = if (roadRoute) 210 else 150
        }
        binding.mapView.overlays.add(0, routeOverlay)
        binding.mapView.invalidate()

        val box = BoundingBox.fromGeoPoints(listOf(origin, destination))
        binding.mapView.post { binding.mapView.zoomToBoundingBox(box, true, 160) }
    }

    private fun showRouteLoading(loading: Boolean) {
        binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.cardWhereTo.visibility = View.GONE
            binding.cardPickupPoint.visibility = View.GONE
            binding.btnMenu.visibility = View.GONE
            binding.fabMyLocation.visibility = View.GONE
            binding.cardRoutePreview.visibility = View.GONE
            binding.cardTariff.visibility = View.GONE
        } else {
            binding.fabMyLocation.visibility = View.VISIBLE
        }
    }

    private fun clearRoute() {
        routeOverlay?.let { binding.mapView.overlays.remove(it); routeOverlay = null }
        arrivalRouteOverlay?.let { binding.mapView.overlays.remove(it); arrivalRouteOverlay = null }
        destinationMarker?.let { binding.mapView.overlays.remove(it); destinationMarker = null }
        binding.mapView.invalidate()
    }

    private fun interpolateRoute(points: List<GeoPoint>, fraction: Float): GeoPoint {
        if (points.size < 2) return points.first()
        var total = 0.0
        for (i in 1 until points.size) total += points[i - 1].distanceToAsDouble(points[i])
        val target = total * fraction.coerceIn(0f, 1f)
        var covered = 0.0
        for (i in 1 until points.size) {
            val seg = points[i - 1].distanceToAsDouble(points[i])
            if (covered + seg >= target) {
                val t = if (seg > 0) (target - covered) / seg else 0.0
                return GeoPoint(
                    points[i - 1].latitude  + (points[i].latitude  - points[i - 1].latitude)  * t,
                    points[i - 1].longitude + (points[i].longitude - points[i - 1].longitude) * t
                )
            }
            covered += seg
        }
        return points.last()
    }

    private fun routeDistanceKm(points: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += points[i - 1].distanceToAsDouble(points[i])
        return total / 1000.0
    }
}

// ── Animations ────────────────────────────────────────────────────────────────

private fun View.slideUp() {
    visibility = View.VISIBLE
    post {
        translationY = height.toFloat()
        animate().translationY(0f).setDuration(340).start()
    }
}

private fun View.slideDown() {
    animate().translationY(height.toFloat()).setDuration(280)
        .withEndAction { visibility = View.GONE }.start()
}

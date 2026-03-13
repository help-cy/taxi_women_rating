package com.drop_db.saferide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
    private var destinationMarker: Marker? = null
    private var userMarker: Marker? = null
    private var userLocation: GeoPoint? = null
    private var selectedPickupPoint: GeoPoint? = null
    private var selectedDestinationName: String = ""

    // ── Tariff ────────────────────────────────────────────────────────────────
    private lateinit var tariffAdapter: TariffAdapter
    private val tariffs = ALL_TARIFFS.map { it.copy() }.toMutableList()
    private var selectedTariff: Tariff = tariffs.first()

    // ── Driver list ───────────────────────────────────────────────────────────
    private lateinit var driverListAdapter: DriverListAdapter
    private var searchJob: Job? = null

    // ── Car animation ─────────────────────────────────────────────────────────
    private data class CarState(
        val marker: Marker,
        val spawn: GeoPoint,
        var pos: GeoPoint,
        var target: GeoPoint
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
        binding.fabMyLocation.setOnClickListener {
            centerOnUserLocation()
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
        }
        binding.btnBook.setOnClickListener { startDriverSearch() }
    }

    private fun showTariffPanel(distanceKm: Double, nearestEta: Int) {
        setHomeChromeVisible(false)
        tariffs.forEachIndexed { i, t ->
            val base = 0.90 + distanceKm * 1.20
            tariffs[i] = t.copy(
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
        val center = userLocation ?: return
        val allDrivers = MockData.driversAround(center).shuffled()
        val filtered = if (selectedTariff.id == "safeplus")
            allDrivers.filter { it.womenSafe } else allDrivers

        binding.cardTariff.slideDown()
        binding.cardSearching.slideUp()
        hideImmediately(binding.panelDriverList)
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
                    } else {
                        driverListAdapter.addDriver(driver)
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
        hideImmediately(binding.cardRoutePreview)
        hideImmediately(binding.cardTariff)
        hideImmediately(binding.cardSearching)
        hideImmediately(binding.panelDriverList)
        binding.cardWhereTo.visibility = View.VISIBLE
        binding.cardWhereTo.translationY = 0f
        binding.fabMyLocation.visibility = View.VISIBLE
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
        binding.tvDriverListSubtitle.text =
            if (foundCount <= 0) "Waiting for offers" else "Driver offers"
    }

    private fun showTopOffers() {
        binding.panelDriverList.visibility = View.VISIBLE
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

    private fun onBookDriver(driver: MockDriver) {
        Snackbar.make(
            binding.root,
            "✅  ${driver.name} is on the way! ETA ~${driver.eta} min",
            Snackbar.LENGTH_LONG
        ).setBackgroundTint(ContextCompat.getColor(this, R.color.brand_primary))
         .setTextColor(ContextCompat.getColor(this, R.color.white))
         .show()
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
            carStates.add(CarState(marker, spawn, GeoPoint(spawn), pickTarget(spawn, spawn)))
        }
        binding.mapView.invalidate()
        startCarAnimation()
    }

    private fun startCarAnimation() {
        animJob?.cancel()
        animJob = lifecycleScope.launch {
            while (isActive) {
                carStates.forEach { car ->
                    val tLat = car.target.latitude; val tLon = car.target.longitude
                    val cLat = car.pos.latitude;    val cLon = car.pos.longitude
                    val newLat = cLat + (tLat - cLat) * 0.018
                    val newLon = cLon + (tLon - cLon) * 0.018
                    val newPos = GeoPoint(newLat, newLon)
                    val dLat = tLat - cLat; val dLon = tLon - cLon
                    if (dLat * dLat + dLon * dLon > 1e-12)
                        car.marker.rotation = Math.toDegrees(atan2(dLon, dLat)).toFloat()
                    car.marker.position = newPos
                    car.pos = newPos
                    if (newPos.distanceToAsDouble(car.target) < 15.0)
                        car.target = pickTarget(newPos, car.spawn)
                }
                binding.mapView.invalidate()
                delay(120L)
            }
        }
    }

    private fun pickTarget(current: GeoPoint, spawn: GeoPoint): GeoPoint {
        repeat(10) {
            val dir = roadDirs.random()
            val c = GeoPoint(current.latitude + dir.first, current.longitude + dir.second)
            if (c.distanceToAsDouble(spawn) < 500.0) return c
        }
        return spawn
    }

    // ── Route ─────────────────────────────────────────────────────────────────
    private var routeDistanceKmLast = 0.0

    private fun drawRouteTo(destination: GeoPoint, name: String) {
        val origin = selectedPickupPoint ?: userLocation ?: return
        selectedDestinationName = name
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
                val initialRoute = withTimeoutOrNull(2000) { routeDeferred.await() }
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
        destinationMarker?.let { binding.mapView.overlays.remove(it); destinationMarker = null }
        binding.mapView.invalidate()
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

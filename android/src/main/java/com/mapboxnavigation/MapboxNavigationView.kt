package com.mapboxnavigation

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsWaypoint
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.RouteLayerConstants.TOP_LEVEL_ROUTE_LINE_LAYER_ID
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mapbox.navigation.voice.model.SpeechVolume
import com.mapboxnavigation.databinding.NavigationViewBinding
import java.util.Locale
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.AnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.view.KeyEventDispatcher
import com.mapbox.api.directions.v5.models.BannerComponents
import com.mapbox.navigation.tripdata.maneuver.model.Component
import com.mapbox.navigation.tripdata.maneuver.model.PrimaryManeuver
import com.mapbox.navigation.ui.components.maneuver.view.MapboxPrimaryManeuver
import com.mapbox.navigation.tripdata.maneuver.model.PrimaryManeuverFactory
import com.mapbox.navigation.tripdata.maneuver.model.TextComponentNode
import java.time.Instant
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


// Keep the same data class already used
data class CustomWaypoint(val point: Point, val isWaypoint: Boolean)

@SuppressLint("ViewConstructor")
class MapboxNavigationView(private val context: ThemedReactContext): FrameLayout(context.baseContext) {
  private companion object {
    private const val BUTTON_ANIMATION_DURATION = 1500L
    private const val REROUTE_DELAY_MS = 500L
  }

  private var isRoutingToOrigin = false
  private var hasArrivedAtOrigin = false
  private val START_THRESHOLD_METERS = 1.0

  // Rate limiting and off-route thresholds
  private var lastRerouteTimestamp: Long = 0L
  private val MIN_REROUTE_INTERVAL_MS = 5_000L        // 5 seconds between reroute requests
  private val OFF_ROUTE_THRESHOLD_METERS = 30.0       // if farther than 30m from the next target, reroute

  private var origin: Point? = null
  private var destination: Point? = null
  private var customerLocation: Point? = null
  private var destinationTitle: String = "Destination"
  private var waypoints: List<CustomWaypoint> = listOf()
  private var waypointLegs: List<WaypointLegs> = listOf()
  private var distanceUnit: String = DirectionsCriteria.IMPERIAL
  private var locale = Locale.getDefault()
  private var travelMode: String = DirectionsCriteria.PROFILE_DRIVING
  private var customerAnnotationManager: PointAnnotationManager? = null
  private var customerAnnotation: com.mapbox.maps.plugin.annotation.generated.PointAnnotation? = null
  private var reroutePending = false
  private var isNavigating = false
  private var navigationInitialized = false
  private var mapStyle: String = NavigationStyles.NAVIGATION_DAY_STYLE
  private var isWaypointArrived = false;
  private var isDestinationArrived = false;
  private var lastArrivedLegIndex: Int? = null
  private var waypointTitle: String = "";
  private var waypointAnnotationManager: PointAnnotationManager? = null
  private var waypointAnnotations: MutableList<PointAnnotation> = mutableListOf()

  private fun resetArrivalFlags() {
    isWaypointArrived = false
    isDestinationArrived = false
  }

  private val rerouteRunnable = Runnable {
    reroutePending = false
    updateRoute()
  }

  private fun checkInitNavigation() {
    if (!navigationInitialized && origin != null && destination != null) {
      navigationInitialized = true
      initNavigation()
    }
  }

  /**
   * Bindings to the example layout.
   */
  private var binding: NavigationViewBinding = NavigationViewBinding.inflate(LayoutInflater.from(context), this, true)

  /**
   * Produces the camera frames based on the location and routing data for the [navigationCamera] to execute.
   */
  private var viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.mapboxMap)

  /**
   * Used to execute camera transitions based on the data generated by the [viewportDataSource].
   * This includes transitions from route overview to route following and continuously updating the camera as the location changes.
   */
  private var navigationCamera = NavigationCamera(
    binding.mapView.mapboxMap,
    binding.mapView.camera,
    viewportDataSource
  )

  /**
   * Mapbox Navigation entry point. There should only be one instance of this object for the app.
   * You can use [MapboxNavigationProvider] to help create and obtain that instance.
   */
  private var mapboxNavigation: MapboxNavigation? = null

  /*
   * Below are generated camera padding values to ensure that the route fits well on screen while
   * other elements are overlaid on top of the map (including instruction view, buttons, etc.)
   */
  private val pixelDensity = Resources.getSystem().displayMetrics.density
  private val overviewPadding: EdgeInsets by lazy {
    EdgeInsets(
      140.0 * pixelDensity,
      40.0 * pixelDensity,
      120.0 * pixelDensity,
      40.0 * pixelDensity
    )
  }
  private val landscapeOverviewPadding: EdgeInsets by lazy {
    EdgeInsets(
      30.0 * pixelDensity,
      380.0 * pixelDensity,
      110.0 * pixelDensity,
      20.0 * pixelDensity
    )
  }
  private val followingPadding: EdgeInsets by lazy {
    EdgeInsets(
      180.0 * pixelDensity,
      40.0 * pixelDensity,
      150.0 * pixelDensity,
      40.0 * pixelDensity
    )
  }
  private val landscapeFollowingPadding: EdgeInsets by lazy {
    EdgeInsets(
      30.0 * pixelDensity,
      380.0 * pixelDensity,
      110.0 * pixelDensity,
      40.0 * pixelDensity
    )
  }

  /**
   * Generates updates for the [MapboxManeuverView] to display the upcoming maneuver instructions
   * and remaining distance to the maneuver point.
   */
  private lateinit var maneuverApi: MapboxManeuverApi

  /**
   * Generates updates for the [MapboxTripProgressView] that include remaining time and distance to the destination.
   */
  private lateinit var tripProgressApi: MapboxTripProgressApi

  /**
   * Stores and updates the state of whether the voice instructions should be played as they come or muted.
   */
  private var isVoiceInstructionsMuted = false
    set(value) {
      field = value
      if (value) {
        binding.soundButton.muteAndExtend(BUTTON_ANIMATION_DURATION)
        voiceInstructionsPlayer?.volume(SpeechVolume(0f))
      } else {
        binding.soundButton.unmuteAndExtend(BUTTON_ANIMATION_DURATION)
        voiceInstructionsPlayer?.volume(SpeechVolume(1f))
      }
    }

  /**
   * Extracts message that should be communicated to the driver about the upcoming maneuver.
   * When possible, downloads a synthesized audio file that can be played back to the driver.
   */
  private lateinit var speechApi: MapboxSpeechApi

  /**
   * Plays the synthesized audio files with upcoming maneuver instructions
   * or uses an on-device Text-To-Speech engine to communicate the message to the driver.
   * NOTE: do not use lazy initialization for this class since it takes some time to initialize
   * the system services required for on-device speech synthesis. With lazy initialization
   * there is a high risk that said services will not be available when the first instruction
   * has to be played. [MapboxVoiceInstructionsPlayer] should be instantiated in
   * Activity#onCreate.
   */
  private var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer? = null

  /**
   * Observes when a new voice instruction should be played.
   */
  private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
    speechApi.generate(voiceInstructions, speechCallback)
  }

  /**
   * Based on whether the synthesized audio file is available, the callback plays the file
   * or uses the fall back which is played back using the on-device Text-To-Speech engine.
   */
  private val speechCallback =
    MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
      expected.fold(
        { error ->
          // play the instruction via fallback text-to-speech engine
          voiceInstructionsPlayer?.play(
            error.fallback,
            voiceInstructionsPlayerCallback
          )
        },
        { value ->
          // play the sound file from the external generator
          voiceInstructionsPlayer?.play(
            value.announcement,
            voiceInstructionsPlayerCallback
          )
        }
      )
    }

  /**
   * When a synthesized audio file was downloaded, this callback cleans up the disk after it was played.
   */
  private val voiceInstructionsPlayerCallback =
    MapboxNavigationConsumer<SpeechAnnouncement> { value ->
      // remove already consumed file to free-up space
      speechApi.clean(value)
    }

  /**
   * [NavigationLocationProvider] is a utility class that helps to provide location updates generated by the Navigation SDK
   * to the Maps SDK in order to update the user location indicator on the map.
   */
  private val navigationLocationProvider = NavigationLocationProvider()

  /**
   * RouteLine: Additional route line options are available through the
   * [MapboxRouteLineViewOptions]. If using the default colors the [RouteLineColorResources] does not need to be set.
   */
  private val routeLineViewOptions: MapboxRouteLineViewOptions by lazy {
    MapboxRouteLineViewOptions.Builder(context)
        .routeLineColorResources(
            RouteLineColorResources.Builder()
                .routeDefaultColor(android.graphics.Color.parseColor("#3887BE")) // Blue for untraveled
                .routeLineTraveledColor(android.graphics.Color.parseColor("#9E9E9E"))   // Grey for traveled
                .routeLineTraveledCasingColor(android.graphics.Color.parseColor("#757575")) // Darker grey for traveled casing
                .build()
        )
        .routeLineBelowLayerId("road-label")
        .build()
}

  private val routeLineApiOptions: MapboxRouteLineApiOptions by lazy {
    MapboxRouteLineApiOptions.Builder()
      .build()
  }

  private val routeLineView by lazy {
    MapboxRouteLineView(routeLineViewOptions)
  }

  private val routeLineApi: MapboxRouteLineApi by lazy {
    MapboxRouteLineApi(routeLineApiOptions)
  }

  private val routeArrowApi: MapboxRouteArrowApi by lazy {
    MapboxRouteArrowApi()
  }

  private val routeArrowOptions by lazy {
    RouteArrowOptions.Builder(context)
      .withAboveLayerId(TOP_LEVEL_ROUTE_LINE_LAYER_ID)
      .build()
  }

  private val routeArrowView: MapboxRouteArrowView by lazy {
    MapboxRouteArrowView(routeArrowOptions)
  }

// REPLACE the body of your locationObserver with this implementation
private val locationObserver = object : LocationObserver {
  var firstLocationUpdateReceived = false

  override fun onNewRawLocation(rawLocation: Location) {
    // not handled
  }

  @SuppressLint("MissingPermission")
  override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val enhancedLocation = locationMatcherResult.enhancedLocation
  
      // 1) Update puck & camera
      navigationLocationProvider.changePosition(location = enhancedLocation, keyPoints = locationMatcherResult.keyPoints)
      viewportDataSource.onLocationChanged(enhancedLocation)
      viewportDataSource.evaluate()
  
      // 2) First fix camera to user on initial update
      if (!firstLocationUpdateReceived) {
          firstLocationUpdateReceived = true
          navigationCamera.requestNavigationCameraToOverview(
              stateTransitionOptions = NavigationCameraTransitionOptions.Builder().maxDuration(0).build()
          )
      }
  
      // 3) Emit location to React Native
      Arguments.createMap().also { event ->
          event.putDouble("longitude", enhancedLocation.longitude)
          event.putDouble("latitude", enhancedLocation.latitude)
          event.putDouble("heading", enhancedLocation.bearing ?: 0.0)
          event.putDouble("accuracy", enhancedLocation.horizontalAccuracy ?: 0.0)
          context.getJSModule(RCTEventEmitter::class.java).receiveEvent(id, "onLocationChange", event)
      }
  
      // 4) Update stored customerLocation (so manual reroute uses latest start)
      val userPoint = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)
      customerLocation = userPoint
      updateCustomerAnnotation()
  
      // 5) If user is essentially already at the origin, mark hasArrivedAtOrigin true
      //    but DO NOT trigger a reroute or arrival event here. We just treat origin as reached.
      origin?.let { startOrigin ->
          val dist = TurfMeasurement.distance(userPoint, startOrigin, TurfConstants.UNIT_METERS)
          if (!hasArrivedAtOrigin && dist <= START_THRESHOLD_METERS) {
              // mark arrived at origin so updateRoute() won't add a duplicate start point
              hasArrivedAtOrigin = true
              isRoutingToOrigin = false
              // Do NOT call scheduleReroute() or emit an arrival here — avoid confusing the SDK.
          }
      }
  
      // NOTE: we intentionally DO NOT call requestRoutes() from location updates anymore.
      // The initial route is created once (startRoute -> updateRoute). The route line will be
      // visually trimmed by routeProgressObserver as the user moves.
  }
  
}


// REPLACE your routeProgressObserver with this updated version
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(com.mapbox.navigation.base.ExperimentalMapboxNavigationAPI::class)
private val routeProgressObserver = RouteProgressObserver { routeProgress ->
    // Update the camera position to account for the progressed fragment of the route
    viewportDataSource.onRouteProgressChanged(routeProgress)
    viewportDataSource.evaluate()

    // CRITICAL: Update route line to show traveled vs remaining portions
    // This should grey out the traveled portion
    routeLineApi.updateWithRouteProgress(routeProgress) { result ->
        binding.mapView.mapboxMap.style?.let { style ->
            routeLineView.renderRouteLineUpdate(style, result)
        }
    }

    // Draw the upcoming maneuver arrow on the map
    val style = binding.mapView.mapboxMap.style
    if (style != null) {
        val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
        routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
    }

    // Rest of your maneuver and progress code remains the same...
    if (::maneuverApi.isInitialized) {
        val maneuvers = maneuverApi.getManeuvers(routeProgress)
        maneuvers.fold(
            { error ->
                Log.w("Maneuvers error:", error.throwable)
            },
            {
                val maneuverViewOptions = ManeuverViewOptions.Builder()
                    .primaryManeuverOptions(
                        ManeuverPrimaryOptions.Builder()
                            .textAppearance(R.style.PrimaryManeuverTextAppearance)
                            .build()
                    )
                    .secondaryManeuverOptions(
                        ManeuverSecondaryOptions.Builder()
                            .textAppearance(R.style.ManeuverTextAppearance)
                            .build()
                    )
                    .subManeuverOptions(
                        ManeuverSubOptions.Builder()
                            .textAppearance(R.style.ManeuverTextAppearance)
                            .build()
                    )
                    .stepDistanceTextAppearance(R.style.StepDistanceRemainingAppearance)
                    .build()

                if(isWaypointArrived){
                    val currentTime = Instant.now()
                    val textNode = TextComponentNode.Builder()
                        .text("You have arrived at $waypointTitle")
                        .build()
                    val textComp = Component(
                        BannerComponents.TEXT,
                        textNode
                    )

                    val primaryManeuver = PrimaryManeuverFactory.buildPrimaryManeuver(
                        id = "arrival_$currentTime",
                        text = "You have arrived at $waypointTitle",
                        type = null,
                        degrees = null,
                        modifier = null,
                        drivingSide = null,
                        componentList = listOf(textComp)
                    )

                    binding.maneuverView.visibility = View.VISIBLE
                    binding.maneuverView.updateManeuverViewOptions(maneuverViewOptions)
                    binding.maneuverView.renderPrimary(primaryManeuver,null)
                }
                else if (isDestinationArrived){
                    val currentTime = Instant.now()
                    val textNode = TextComponentNode.Builder()
                        .text("You have arrived at $destinationTitle")
                        .build()
                    val textComp = Component(
                        BannerComponents.TEXT,
                        textNode
                    )

                    val primaryManeuver = PrimaryManeuverFactory.buildPrimaryManeuver(
                        id = "arrival_$currentTime",
                        text = "You have arrived at $destinationTitle",
                        type = null,
                        degrees = null,
                        modifier = null,
                        drivingSide = null,
                        componentList = listOf(textComp)
                    )

                    binding.maneuverView.visibility = View.VISIBLE
                    binding.maneuverView.updateManeuverViewOptions(maneuverViewOptions)
                    binding.maneuverView.renderPrimary(primaryManeuver,null)
                }
                else {
                    binding.maneuverView.visibility = View.VISIBLE
                    binding.maneuverView.updateManeuverViewOptions(maneuverViewOptions)
                    binding.maneuverView.renderManeuvers(maneuvers)
                }
            }
        )
    } else {
        Log.w("MapboxNavigationView", "maneuverApi not initialized yet")
    }

    val update = tripProgressApi.getTripProgress(routeProgress)
    binding.tripProgressCard.visibility = View.VISIBLE

    val durationSeconds = routeProgress.durationRemaining
    val distanceMeters = routeProgress.distanceRemaining

    binding.timeText.text = formatDuration(durationSeconds)
    binding.distanceText.text = formatDistance(distanceMeters)
    val etaMillis = System.currentTimeMillis() + (durationSeconds * 1000).toLong()
    val etaFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(etaMillis))
    binding.arrivalText.text = etaFormatted

    val event = Arguments.createMap()
    event.putDouble("distanceTraveled", routeProgress.distanceTraveled.toDouble())
    event.putDouble("durationRemaining", routeProgress.durationRemaining)
    event.putDouble("fractionTraveled", routeProgress.fractionTraveled.toDouble())
    event.putDouble("distanceRemaining", routeProgress.distanceRemaining.toDouble())
    context
        .getJSModule(RCTEventEmitter::class.java)
        .receiveEvent(id, "onRouteProgressChange", event)
}

  private val routesObserver = RoutesObserver { routeUpdateResult ->
    if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
      routeLineApi.setNavigationRoutes(
        routeUpdateResult.navigationRoutes
      ) { value ->
        binding.mapView.mapboxMap.style?.apply {
          routeLineView.renderRouteDrawData(this, value)
        }
      }
  
      viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
      viewportDataSource.evaluate()
    } else {
      val style = binding.mapView.mapboxMap.style
      if (style != null) {
        routeLineApi.clearRouteLine { value ->
          routeLineView.renderClearRouteLineValue(
            style,
            value
          )
        }
        routeArrowView.render(style, routeArrowApi.clearArrows())
      }
  
      viewportDataSource.clearRouteData()
      viewportDataSource.evaluate()
    }
  }

  init {
    onCreate()
    val unitType = if (distanceUnit == "imperial") UnitType.IMPERIAL else UnitType.METRIC
    val distanceFormatterOptions = DistanceFormatterOptions.Builder(context)
        .unitType(unitType)
        .build()
    maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))
    tripProgressApi = MapboxTripProgressApi(
        TripProgressUpdateFormatter.Builder(context)
            .distanceRemainingFormatter(DistanceRemainingFormatter(distanceFormatterOptions))
            .timeRemainingFormatter(TimeRemainingFormatter(context))
            .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
            .estimatedTimeToArrivalFormatter(EstimatedTimeToArrivalFormatter(context, TimeFormat.NONE_SPECIFIED))
            .build()
    )
  }

  private fun onCreate() {
    mapboxNavigation = if (MapboxNavigationProvider.isCreated()) {
      MapboxNavigationProvider.retrieve()
    } else {
      MapboxNavigationProvider.create(
        NavigationOptions.Builder(context)
          .build()
      )
    }
  }

  @SuppressLint("MissingPermission")
  private fun initNavigation() {
    if (origin == null || destination == null) {
      sendErrorToReact("origin and destination are required")
      return
    }

    val initialCameraOptions = CameraOptions.Builder()
      .zoom(14.0)
      .center(origin)
      .build()
    binding.mapView.mapboxMap.setCamera(initialCameraOptions)

    startNavigation()

    binding.mapView.camera.addCameraAnimationsLifecycleListener(
      NavigationBasicGesturesHandler(navigationCamera)
    )
    navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
      when (navigationCameraState) {
        NavigationCameraState.TRANSITION_TO_FOLLOWING,
        NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE

        NavigationCameraState.TRANSITION_TO_OVERVIEW,
        NavigationCameraState.OVERVIEW,
        NavigationCameraState.IDLE -> binding.recenter.visibility = View.VISIBLE
      }
    }

    if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      viewportDataSource.overviewPadding = landscapeOverviewPadding
    } else {
      viewportDataSource.overviewPadding = overviewPadding
    }
    if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      viewportDataSource.followingPadding = landscapeFollowingPadding
    } else {
      viewportDataSource.followingPadding = followingPadding
    }

    val unitType = if (distanceUnit == "imperial") UnitType.IMPERIAL else UnitType.METRIC
    val distanceFormatterOptions = DistanceFormatterOptions.Builder(context)
        .unitType(unitType)
        .build()

    maneuverApi = MapboxManeuverApi(
        MapboxDistanceFormatter(distanceFormatterOptions)
    )

    tripProgressApi = MapboxTripProgressApi(
        TripProgressUpdateFormatter.Builder(context)
            .distanceRemainingFormatter(
                DistanceRemainingFormatter(distanceFormatterOptions)
            )
            .timeRemainingFormatter(
                TimeRemainingFormatter(context)
            )
            .percentRouteTraveledFormatter(
                PercentDistanceTraveledFormatter()
            )
            .estimatedTimeToArrivalFormatter(
                EstimatedTimeToArrivalFormatter(context, TimeFormat.NONE_SPECIFIED)
            )
            .build()
    )

    speechApi = MapboxSpeechApi(
      context,
      locale.language
    )
    voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
      context,
      locale.language
    )

   binding.mapView.mapboxMap.loadStyle(mapStyle) {
    val dotBitmap = BitmapFactory.decodeResource(context.resources,R.drawable.red_dot)
    it.addImage(
        "customer_icon",
        dotBitmap
    )

    // Initialize route line layers FIRST
    routeLineView.initializeLayers(it)
    
    // Clear any existing route line
    routeLineApi.clearRouteLine { value ->
        routeLineView.renderClearRouteLineValue(it, value)
    }



        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = LocationPuck2D(
                bearingImage = ImageHolder.Companion.from(
                    com.mapbox.navigation.ui.maps.R.drawable.mapbox_navigation_puck_icon
                )
            )
            puckBearingEnabled = true
            enabled = true
        }

        updateCustomerAnnotation()
        updateWaypointAnnotations()
    }

    binding.stop.setOnClickListener {
      val event = Arguments.createMap()
      event.putString("message", "Navigation Cancel")
      context
        .getJSModule(RCTEventEmitter::class.java)
        .receiveEvent(id, "onCancelNavigation", event)
      isNavigating = false
      removeCallbacks(rerouteRunnable)
      mapboxNavigation?.stopTripSession()
      navigationInitialized = false

    }


    binding.recenter.setOnClickListener {
      navigationCamera.requestNavigationCameraToFollowing()
      binding.routeOverview.showTextAndExtend(BUTTON_ANIMATION_DURATION)
    }
    binding.routeOverview.setOnClickListener {
      navigationCamera.requestNavigationCameraToOverview()
      binding.recenter.showTextAndExtend(BUTTON_ANIMATION_DURATION)
    }
    binding.soundButton.setOnClickListener {
      isVoiceInstructionsMuted = !isVoiceInstructionsMuted
    }

    if (this.isVoiceInstructionsMuted) {
      binding.soundButton.mute()
      voiceInstructionsPlayer?.volume(SpeechVolume(0f))
    } else {
      binding.soundButton.unmute()
      voiceInstructionsPlayer?.volume(SpeechVolume(1f))
    }
  }

  private fun formatDuration(seconds: Double): String {
      val minutes = (seconds / 60).toInt()
      val hours = minutes / 60
      val remainingMinutes = minutes % 60

      return if (hours > 0) {
          "$hours h $remainingMinutes min"
      } else {
          "$remainingMinutes min"
      }
  }

  private fun formatDistance(meters: Float): String {
      return if (distanceUnit == "imperial") {
          if (meters >= 1609.34) {
              String.format("%.1f mi", meters / 1609.34)
          } else {
              String.format("%.0f m", meters)
          }
      } else {
          if (meters >= 1000) {
              String.format("%.1f km", meters / 1000)
          } else {
              String.format("%.0f m", meters)
          }
      }
  }

  private fun onDestroy() {
    resetArrivalFlags()
    maneuverApi.cancel()
    routeLineApi.cancel()
    routeLineView.cancel()
    speechApi.cancel()
    voiceInstructionsPlayer?.shutdown()
    isNavigating = false
    removeCallbacks(rerouteRunnable)
    mapboxNavigation?.stopTripSession()
    navigationInitialized = false

  }

  private fun startNavigation() {
    startRoute()
  }

  private fun startRoute() {
    isNavigating = true
    mapboxNavigation?.registerRoutesObserver(routesObserver)
    mapboxNavigation?.registerArrivalObserver(arrivalObserver)
    mapboxNavigation?.registerRouteProgressObserver(routeProgressObserver)
    mapboxNavigation?.registerLocationObserver(locationObserver)
    mapboxNavigation?.registerVoiceInstructionsObserver(voiceInstructionsObserver)
    mapboxNavigation?.startTripSession(withForegroundService = true)
    // Reset arrival flags so arrivalObserver handles origin → checkpoint correctly
    // DO NOT set hasArrivedAtOrigin = true here. It should be set only when user is near origin.
    hasArrivedAtOrigin = false
    isRoutingToOrigin = false
    updateRoute()
  }

  private fun scheduleReroute() {
    if (!isNavigating) return
    removeCallbacks(rerouteRunnable)
    reroutePending = true
    postDelayed(rerouteRunnable, REROUTE_DELAY_MS)
  }

// REPLACE current updateRoute() with this
private fun updateRoute() {
  if (destination == null) return

  val coords = mutableListOf<Point>()

  // If we haven't arrived at origin yet, prefer starting from customerLocation -> origin
  if (!hasArrivedAtOrigin) {
      if (customerLocation != null && origin != null) {
          // If customerLocation is essentially the same as origin, mark arrived and start at origin
          val distToOrigin = TurfMeasurement.distance(customerLocation!!, origin!!, TurfConstants.UNIT_METERS)
          if (distToOrigin <= START_THRESHOLD_METERS) {
              hasArrivedAtOrigin = true
              coords.add(origin!!)
          } else {
              coords.add(customerLocation!!)
              coords.add(origin!!)
          }
      } else if (customerLocation != null) {
          coords.add(customerLocation!!)
      } else {
          origin?.let { coords.add(it) }
      }
  } else {
      // Already at origin — start from origin
      origin?.let { coords.add(it) }
  }

  // Add waypoints/checkpoints and final destination
  coords.addAll(waypoints.map { it.point })
  destination?.let { coords.add(it) }

  // Remove consecutive duplicate points (same lat/lng)
  val unique = mutableListOf<Point>()
  coords.forEach { p ->
      if (unique.isEmpty() ||
          p.latitude() != unique.last().latitude() ||
          p.longitude() != unique.last().longitude()
      ) {
          unique.add(p)
      }
  }

  // Directions API needs at least two coordinates
  if (unique.size < 2) return

  findRoute(unique)
}





  private val arrivalObserver = object : ArrivalObserver {

    override fun onWaypointArrival(routeProgress: RouteProgress) {
      lastArrivedLegIndex = routeProgress.currentLegProgress?.legIndex
      waypointTitle = waypointLegs
        .firstOrNull { it.index == lastArrivedLegIndex }
        ?.name ?: "Waypoint"

      isWaypointArrived = true
      onArrival(routeProgress)
    }

    override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
      resetArrivalFlags()
    }

    override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
      lastArrivedLegIndex = routeProgress.currentLegProgress?.legIndex
      isDestinationArrived = true
      onArrival(routeProgress)
    }
  }

  private fun onArrival(routeProgress: RouteProgress) {
    val leg = routeProgress.currentLegProgress
    if (leg != null) {
      val event = Arguments.createMap()
      event.putInt("index", leg.legIndex)
      event.putDouble("latitude", leg.legDestination?.location?.latitude() ?: 0.0)
      event.putDouble("longitude", leg.legDestination?.location?.longitude() ?: 0.0)
      context
        .getJSModule(RCTEventEmitter::class.java)
        .receiveEvent(id, "onArrive", event)
    }
  }

  override fun requestLayout() {
    super.requestLayout()
    post(measureAndLayout)
  }

  private val measureAndLayout = Runnable {
    measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
    )
    layout(left, top, right, bottom)
  }

  private fun findRoute(coordinates: List<Point>) {
    if (coordinates.isEmpty()) return

    // Update last reroute timestamp (guard to prevent frequent requests)
    lastRerouteTimestamp = System.currentTimeMillis()

    mapboxNavigation?.requestRoutes(
      RouteOptions.builder()
        .applyDefaultNavigationOptions()
        .applyLanguageAndVoiceUnitOptions(context)
        .coordinatesList(coordinates)
        .language(locale.language)
        .steps(true)
        .voiceInstructions(true)
        .voiceUnits(distanceUnit)
        .profile(travelMode)
        .build(),
      object : NavigationRouterCallback {
        override fun onCanceled(routeOptions: RouteOptions, @RouterOrigin routerOrigin: String) {
          // no implementation
        }

        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
          sendErrorToReact("Error finding route $reasons")
        }

        override fun onRoutesReady(
          routes: List<NavigationRoute>,
          @RouterOrigin routerOrigin: String
        ) {
          val route = routes.firstOrNull()?.directionsRoute ?: return

          val polyline = route.geometry()
          val distance = route.distance()
          val duration = route.duration()

          sendRouteDetailsToReact(polyline, distance, duration)
          setRouteAndStartNavigation(routes)
        }
      }
    )
  }

  private fun sendRouteDetailsToReact(polyline: String?, distance: Double, duration: Double) {
    val event = Arguments.createMap().apply {
      putString("polyline", polyline)
      putDouble("distance", distance)
      putDouble("duration", duration)
    }

    context
      .getJSModule(RCTEventEmitter::class.java)
      .receiveEvent(id, "onRouteReady", event)
  }

  @SuppressLint("MissingPermission")
  private fun setRouteAndStartNavigation(routes: List<NavigationRoute>) {
    mapboxNavigation?.setNavigationRoutes(routes)

    binding.soundButton.visibility = View.VISIBLE
    binding.routeOverview.visibility = View.VISIBLE
    binding.tripProgressCard.visibility = View.VISIBLE

  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    mapboxNavigation?.unregisterRoutesObserver(routesObserver)
    mapboxNavigation?.unregisterArrivalObserver(arrivalObserver)
    mapboxNavigation?.unregisterLocationObserver(locationObserver)
    mapboxNavigation?.unregisterRouteProgressObserver(routeProgressObserver)
    mapboxNavigation?.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
    isNavigating = false
    reroutePending = false
    navigationInitialized = false
    removeCallbacks(rerouteRunnable)

    mapboxNavigation?.setNavigationRoutes(listOf())

    binding.soundButton.visibility = View.INVISIBLE
    binding.maneuverView.visibility = View.INVISIBLE
    binding.routeOverview.visibility = View.INVISIBLE
    binding.tripProgressCard.visibility = View.INVISIBLE
  }

  private fun sendErrorToReact(error: String?) {
    val event = Arguments.createMap()
    event.putString("error", error)
    context
      .getJSModule(RCTEventEmitter::class.java)
      .receiveEvent(id, "onError", event)
  }

  fun onDropViewInstance() {
    this.onDestroy()
  }

  fun setStartOrigin(origin: Point?) {
    this.origin = origin
    checkInitNavigation()
    if (isNavigating) scheduleReroute()
  }

  fun setDestination(destination: Point?) {
    this.destination = destination
    checkInitNavigation()
    if (isNavigating) scheduleReroute()
  }

  fun setCustomerLocation(location: Point?) {
    this.customerLocation = location
    updateCustomerAnnotation()
  }

  fun setDestinationTitle(title: String) {
    this.destinationTitle = title
    if (isNavigating) scheduleReroute()
  }

  fun setWaypointLegs(legs: List<WaypointLegs>) {
    this.waypointLegs = legs
    if (isNavigating) scheduleReroute()
  }

  fun setWaypoints(waypoints: List<CustomWaypoint>) {
    this.waypoints = waypoints
    updateWaypointAnnotations()
    if (isNavigating) scheduleReroute()
  }

  fun setDirectionUnit(unit: String) {
    this.distanceUnit = unit
    if (isNavigating) scheduleReroute()
  }

  fun setLocal(language: String) {
    val locals = language.split("-")
    when (locals.size) {
      1 -> locale = Locale(locals.first())
      2 -> locale = Locale(locals.first(), locals.last())
    }
    if (isNavigating) scheduleReroute()
  }

  fun setMute(mute: Boolean) {
    this.isVoiceInstructionsMuted = mute
  }

  fun setShowCancelButton(show: Boolean) {
    binding.stop.visibility = if (show) View.VISIBLE else View.INVISIBLE
  }

  fun setMapStyle(style: String){
    this.mapStyle = style
    Log.d("MapboxNavigationView", "Setting map style: $style")
    binding.mapView.mapboxMap.style?.let {
      binding.mapView.mapboxMap.loadStyle(style)
    }
  }

  fun setTravelMode(mode: String) {
    travelMode = when (mode.lowercase()) {
      "walking" -> DirectionsCriteria.PROFILE_WALKING
      "cycling" -> DirectionsCriteria.PROFILE_CYCLING
      "driving" -> DirectionsCriteria.PROFILE_DRIVING
      "driving-traffic" -> DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
      else -> DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    }
    if (isNavigating) scheduleReroute()
  }

  private fun updateCustomerAnnotation() {
    val mapView = binding.mapView
    if (customerLocation == null) {
      customerAnnotationManager?.deleteAll()
      customerAnnotation = null
      return
    }
    if (customerAnnotationManager == null) {
      customerAnnotationManager = mapView.annotations.createPointAnnotationManager()
    }
    val point = customerLocation!!
    val manager = customerAnnotationManager!!
    val opts = PointAnnotationOptions()
      .withPoint(customerLocation!!)
      .withIconImage("customer_icon")
      .withIconSize(0.1)
    if (customerAnnotation == null) {
      customerAnnotation = manager.create(opts)
    } else {
      customerAnnotation!!.point = point
      manager.update(listOf(customerAnnotation!!))
    }
  }

  private fun updateWaypointAnnotations() {
    val mapView = binding.mapView
    waypointAnnotationManager?.deleteAll()
    waypointAnnotations.clear()
    if (waypoints.isEmpty() && origin == null && destination == null) return

    mapView.mapboxMap.getStyle { style ->
        if (waypointAnnotationManager == null) {
            waypointAnnotationManager = mapView.annotations.createPointAnnotationManager()
        }
        val manager = waypointAnnotationManager!!

        origin?.let { startPoint ->
            val startFlagIconId = "start_checkered_flag_icon"
            if (style.getStyleImage(startFlagIconId) == null) {
                val flagBitmap = createCheckeredFlagBitmap()
                style.addImage(startFlagIconId, flagBitmap)
            }
            val opts = PointAnnotationOptions()
                .withPoint(startPoint)
                .withIconImage(startFlagIconId)
                .withIconSize(1.0)
            val annotation = manager.create(opts)
            waypointAnnotations.add(annotation)
        }

        waypoints.forEachIndexed { idx, customWaypoint ->
            val point = customWaypoint.point
            val isWaypoint = customWaypoint.isWaypoint
            val iconId = if (isWaypoint) "waypoint_pin_icon_$idx" else "waypoint_flag_icon_$idx"
            val bitmap = if (isWaypoint) createMapPinBitmap() else createDestinationFlagBitmap()
            style.addImage(iconId, bitmap)
            val opts = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(iconId)
                .withIconSize(1.0)
            val annotation = manager.create(opts)
            waypointAnnotations.add(annotation)
        }

        destination?.let { destPoint ->
            val flagIconId = "destination_checkered_flag_icon"
            if (style.getStyleImage(flagIconId) == null) {
                val flagBitmap = createCheckeredFlagBitmap()
                style.addImage(flagIconId, flagBitmap)
            }
            val opts = PointAnnotationOptions()
                .withPoint(destPoint)
                .withIconImage(flagIconId)
                .withIconSize(1.0)
            val annotation = manager.create(opts)
            waypointAnnotations.add(annotation)
        }
    }
  }

  private fun createDestinationFlagBitmap(): android.graphics.Bitmap { /* same as before */
    val width = 48
    val height = 64
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.isAntiAlias = true
    paint.color = android.graphics.Color.parseColor("#8B4513")
    paint.strokeWidth = 4f
    canvas.drawLine(width * 0.15f, height * 0.2f, width * 0.15f, height * 0.95f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#FF3B30")
    val flagPath = android.graphics.Path()
    flagPath.moveTo(width * 0.2f, height * 0.2f)
    flagPath.lineTo(width * 0.85f, height * 0.35f)
    flagPath.lineTo(width * 0.2f, height * 0.5f)
    flagPath.close()
    canvas.drawPath(flagPath, paint)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.parseColor("#D70015")
    paint.strokeWidth = 2f
    canvas.drawPath(flagPath, paint)
    return bitmap
  }

  private fun createNumberedWaypointBitmap(number: Int): android.graphics.Bitmap { /* same as before */
    val size = 64
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.isAntiAlias = true
    paint.color = android.graphics.Color.parseColor("#007AFF")
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 6f
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f - 3f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    paint.textSize = size / 2f + 4f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.isFakeBoldText = true
    val textY = size / 2f - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(number.toString(), size / 2f, textY, paint)
    return bitmap
  }

  private fun createMapPinBitmap(): android.graphics.Bitmap { /* same as before */
    val width = 48
    val height = 64
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.isAntiAlias = true
    paint.color = android.graphics.Color.parseColor("#007AFF")
    val ovalRect = android.graphics.RectF(0f, 0f, width.toFloat(), height * 0.75f)
    canvas.drawOval(ovalRect, paint)
    paint.color = android.graphics.Color.parseColor("#007AFF")
    val path = android.graphics.Path()
    path.moveTo(width / 2f, height.toFloat())
    path.lineTo(width * 0.15f, height * 0.75f)
    path.lineTo(width * 0.85f, height * 0.75f)
    path.close()
    canvas.drawPath(path, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(width / 2f, height * 0.35f, width * 0.15f, paint)
    return bitmap
  }

  private fun createCheckeredFlagBitmap(): android.graphics.Bitmap { /* same as before */
    val width = 48
    val height = 64
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.isAntiAlias = true
    paint.color = android.graphics.Color.parseColor("#8B4513")
    paint.strokeWidth = 4f
    canvas.drawLine(width * 0.15f, height * 0.2f, width * 0.15f, height * 0.95f, paint)
    val flagLeft = width * 0.2f
    val flagTop = height * 0.2f
    val flagRight = width * 0.85f
    val flagBottom = height * 0.5f
    val flagWidth = flagRight - flagLeft
    val flagHeight = flagBottom - flagTop
    val rows = 4
    val cols = 6
    val cellW = flagWidth / cols
    val cellH = flagHeight / rows
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = if ((row + col) % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val left = flagLeft + col * cellW
            val top = flagTop + row * cellH
            val right = left + cellW
            val bottom = top + cellH
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.BLACK
    paint.strokeWidth = 2f
    canvas.drawRect(flagLeft, flagTop, flagRight, flagBottom, paint)
    return bitmap
  }
}

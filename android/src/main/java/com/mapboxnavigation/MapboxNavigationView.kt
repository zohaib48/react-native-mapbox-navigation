//MapboxNavigationView.kt
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
// In your imports:
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfConstants

@SuppressLint("ViewConstructor")
class MapboxNavigationView(private val context: ThemedReactContext): FrameLayout(context.baseContext) {
  private companion object {
    private const val BUTTON_ANIMATION_DURATION = 1500L
    private const val REROUTE_DELAY_MS = 500L
  }

private var isRoutingToOrigin = false
private var hasArrivedAtOrigin = false
private val START_THRESHOLD_METERS = 1.0



  private var origin: Point? = null
  private var destination: Point? = null
  private var customerLocation: Point? = null
  private var destinationTitle: String = "Destination"
  private var waypoints: List<Point> = listOf()
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
   * `Activity#onCreate`.
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
   * [MapboxRouteLineViewOptions] and [MapboxRouteLineApiOptions].
   * Notice here the [MapboxRouteLineViewOptions.routeLineBelowLayerId] option. The map is made up of layers. In this
   * case the route line will be placed below the "road-label" layer which is a good default
   * for the most common Mapbox navigation related maps. You should consider if this should be
   * changed for your use case especially if you are using a custom map style.
   */
  private val routeLineViewOptions: MapboxRouteLineViewOptions by lazy {
    MapboxRouteLineViewOptions.Builder(context)
      /**
       * Route line related colors can be customized via the [RouteLineColorResources]. If using the
       * default colors the [RouteLineColorResources] does not need to be set as seen here, the
       * defaults will be used internally by the builder.
       */
      .routeLineColorResources(RouteLineColorResources.Builder().build())
      .routeLineBelowLayerId("road-label-navigation")
      .build()
  }

  private val routeLineApiOptions: MapboxRouteLineApiOptions by lazy {
    MapboxRouteLineApiOptions.Builder()
      .build()
  }

  /**
   * RouteLine: This class is responsible for rendering route line related mutations generated
   * by the [routeLineApi]
   */
  private val routeLineView by lazy {
    MapboxRouteLineView(routeLineViewOptions)
  }


  /**
   * RouteLine: This class is responsible for generating route line related data which must be
   * rendered by the [routeLineView] in order to visualize the route line on the map.
   */
  private val routeLineApi: MapboxRouteLineApi by lazy {
    MapboxRouteLineApi(routeLineApiOptions)
  }

  /**
   * RouteArrow: This class is responsible for generating data related to maneuver arrows. The
   * data generated must be rendered by the [routeArrowView] in order to apply mutations to
   * the map.
   */
  private val routeArrowApi: MapboxRouteArrowApi by lazy {
    MapboxRouteArrowApi()
  }

  /**
   * RouteArrow: Customization of the maneuver arrow(s) can be done using the
   * [RouteArrowOptions]. Here the above layer ID is used to determine where in the map layer
   * stack the arrows appear. Above the layer of the route traffic line is being used here. Your
   * use case may necessitate adjusting this to a different layer position.
   */
  private val routeArrowOptions by lazy {
    RouteArrowOptions.Builder(context)
      .withAboveLayerId(TOP_LEVEL_ROUTE_LINE_LAYER_ID)
      .build()
  }

  /**
   * RouteArrow: This class is responsible for rendering the arrow related mutations generated
   * by the [routeArrowApi]
   */
  private val routeArrowView: MapboxRouteArrowView by lazy {
    MapboxRouteArrowView(routeArrowOptions)
  }

  /**
   * Gets notified with location updates.
   *
   * Exposes raw updates coming directly from the location services
   * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
   */
  private val locationObserver = object : LocationObserver {
    var firstLocationUpdateReceived = false

    override fun onNewRawLocation(rawLocation: Location) {
      // not handled
    }

  @SuppressLint("MissingPermission")
override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
    val enhancedLocation = locationMatcherResult.enhancedLocation

    // 1. Update the puck and camera
    navigationLocationProvider.changePosition(
        location = enhancedLocation,
        keyPoints = locationMatcherResult.keyPoints
    )
    viewportDataSource.onLocationChanged(enhancedLocation)
    viewportDataSource.evaluate()

    // 2. First‐fix camera to user on initial update
    if (!firstLocationUpdateReceived) {
        firstLocationUpdateReceived = true
        navigationCamera.requestNavigationCameraToOverview(
            stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                .maxDuration(0) // instant
                .build()
        )
    }

    // 3. Emit location to React Native
    Arguments.createMap().also { event ->
        event.putDouble("longitude", enhancedLocation.longitude)
        event.putDouble("latitude", enhancedLocation.latitude)
        event.putDouble("heading", enhancedLocation.bearing ?: 0.0)
        event.putDouble("accuracy", enhancedLocation.horizontalAccuracy ?: 0.0)
        context.getJSModule(RCTEventEmitter::class.java)
            .receiveEvent(id, "onLocationChange", event)
    }

    // 4. Always build a route from current location to startOrigin, waypoints, destination
    origin?.let { startOrigin ->
        val userPoint = Point.fromLngLat(
            enhancedLocation.longitude,
            enhancedLocation.latitude
        )
        val coordinatesList = mutableListOf<Point>()
        coordinatesList.add(userPoint)
        coordinatesList.add(startOrigin)
        coordinatesList.addAll(waypoints)
        destination?.let { coordinatesList.add(it) }
        mapboxNavigation?.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(context)
                .coordinatesList(coordinatesList)
                .profile(travelMode)
                .steps(true)
                .build(),
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    @RouterOrigin routerOrigin: String
                ) {
                    mapboxNavigation?.setNavigationRoutes(routes)
                    mapboxNavigation?.startTripSession(withForegroundService = true)
                }
                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) { Log.e("Routing", "Failed to build route: $reasons") }
                override fun onCanceled(
                    routeOptions: RouteOptions,
                    @RouterOrigin routerOrigin: String
                ) { /* no‐op */ }
            }
        )
        return
    }

    // If no origin, do nothing
}

  }

  /**
   * Gets notified with progress along the currently active route.
   */
  @RequiresApi(Build.VERSION_CODES.O)
  @OptIn(com.mapbox.navigation.base.ExperimentalMapboxNavigationAPI::class)
  private val routeProgressObserver = RouteProgressObserver { routeProgress ->
    // update the camera position to account for the progressed fragment of the route
    if (routeProgress.fractionTraveled.toDouble() != 0.0) {
      viewportDataSource.onRouteProgressChanged(routeProgress)
    }
    viewportDataSource.evaluate()

    // draw the upcoming maneuver arrow on the map
    val style = binding.mapView.mapboxMap.style
    if (style != null) {
      val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
      routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
    }

    // update top banner with maneuver instructions
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

    // update bottom trip progress summary
    binding.tripProgressView.render(
      tripProgressApi.getTripProgress(routeProgress)
    )

    val event = Arguments.createMap()
    event.putDouble("distanceTraveled", routeProgress.distanceTraveled.toDouble())
    event.putDouble("durationRemaining", routeProgress.durationRemaining)
    event.putDouble("fractionTraveled", routeProgress.fractionTraveled.toDouble())
    event.putDouble("distanceRemaining", routeProgress.distanceRemaining.toDouble())
    context
      .getJSModule(RCTEventEmitter::class.java)
      .receiveEvent(id, "onRouteProgressChange", event)
  }

  /**
   * Gets notified whenever the tracked routes change.
   *
   * A change can mean:
   * - routes get changed with [MapboxNavigation.setNavigationRoutes]
   * - routes annotations get refreshed (for example, congestion annotation that indicate the live traffic along the route)
   * - driver got off route and a reroute was executed
   */
  private val routesObserver = RoutesObserver { routeUpdateResult ->
    if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
      // generate route geometries asynchronously and render them
      routeLineApi.setNavigationRoutes(
        routeUpdateResult.navigationRoutes
      ) { value ->
        binding.mapView.mapboxMap.style?.apply {
          routeLineView.renderRouteDrawData(this, value)
        }
      }

      // update the camera position to account for the new route
      viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
      viewportDataSource.evaluate()
    } else {
      // remove the route line and route arrow from the map
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

      // remove the route reference from camera position evaluations
      viewportDataSource.clearRouteData()
      viewportDataSource.evaluate()
    }
  }

  init {
    onCreate()
  }

  private fun onCreate() {
    // initialize Mapbox Navigation
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

    // Recenter Camera
    val initialCameraOptions = CameraOptions.Builder()
      .zoom(14.0)
      .center(origin)
      .build()
    binding.mapView.mapboxMap.setCamera(initialCameraOptions)

    // Start Navigation
    startNavigation()

    // set the animations lifecycle listener to ensure the NavigationCamera stops
    // automatically following the user location when the map is interacted with
    binding.mapView.camera.addCameraAnimationsLifecycleListener(
      NavigationBasicGesturesHandler(navigationCamera)
    )
    navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
      // shows/hide the recenter button depending on the camera state
      when (navigationCameraState) {
        NavigationCameraState.TRANSITION_TO_FOLLOWING,
        NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE

        NavigationCameraState.TRANSITION_TO_OVERVIEW,
        NavigationCameraState.OVERVIEW,
        NavigationCameraState.IDLE -> binding.recenter.visibility = View.VISIBLE
      }
    }
    // set the padding values depending on screen orientation and visible view layout
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

    // make sure to use the same DistanceFormatterOptions across different features
    val unitType = if (distanceUnit == "imperial") UnitType.IMPERIAL else UnitType.METRIC
    val distanceFormatterOptions = DistanceFormatterOptions.Builder(context)
      .unitType(unitType)
      .build()

    // initialize maneuver api that feeds the data to the top banner maneuver view
    maneuverApi = MapboxManeuverApi(
      MapboxDistanceFormatter(distanceFormatterOptions)
    )

    // initialize bottom progress view
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
    // initialize voice instructions api and the voice instruction player
    speechApi = MapboxSpeechApi(
      context,
      locale.language
    )
    voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
      context,
      locale.language
    )

    // load map style
 // load map style
binding.mapView.mapboxMap.loadStyle(mapStyle) {
    // Ensure that the route line related layers are present before the route arrow
    val dotBitmap = BitmapFactory.decodeResource(context.resources,R.drawable.red_dot)
    it.addImage(
        "customer_icon",
        dotBitmap
    )
    routeLineView.initializeLayers(it)
    updateCustomerAnnotation()
    updateWaypointAnnotations()
}

    // initialize view interactions
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
      // mute/unmute voice instructions
      isVoiceInstructionsMuted = !isVoiceInstructionsMuted
    }

    // Check initial muted or not
    if (this.isVoiceInstructionsMuted) {
      binding.soundButton.mute()
      voiceInstructionsPlayer?.volume(SpeechVolume(0f))
    } else {
      binding.soundButton.unmute()
      voiceInstructionsPlayer?.volume(SpeechVolume(1f))
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
    // initialize location puck
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
    hasArrivedAtOrigin = true  // prevent re-routing back to origin
    isRoutingToOrigin = false
    updateRoute()
}

  private fun scheduleReroute() {
    if (!isNavigating) return
    removeCallbacks(rerouteRunnable)
    reroutePending = true
    postDelayed(rerouteRunnable, REROUTE_DELAY_MS)
  }

  private fun updateRoute() {
    if (origin == null || destination == null) return

    val coordinatesList = mutableListOf<Point>()
    origin?.let { coordinatesList.add(it) }
    coordinatesList.addAll(waypoints)
    destination?.let { coordinatesList.add(it) }

    findRoute(coordinatesList)
  }



  private val arrivalObserver = object : ArrivalObserver {

    override fun onWaypointArrival(routeProgress: RouteProgress) {
      lastArrivedLegIndex = routeProgress.currentLegProgress?.legIndex
      waypointTitle = waypointLegs
        .firstOrNull { it.index == lastArrivedLegIndex }  // comes from the names you passed in RouteOptions
        ?.name ?: "Waypoint"

      isWaypointArrived = true
      onArrival(routeProgress)
    }

    override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
      // do something when the user starts a new leg
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
    // Separate legs work
    val indices = mutableListOf<Int>()
    val names = mutableListOf<String>()
    indices.add(0)
    names.add("origin")
    indices.addAll(waypointLegs.map { it.index })
    names.addAll(waypointLegs.map { it.name })
    indices.add(coordinates.count() - 1)
    names.add(destinationTitle)

    mapboxNavigation?.requestRoutes(
      RouteOptions.builder()
        .applyDefaultNavigationOptions()
        .applyLanguageAndVoiceUnitOptions(context)
        .coordinatesList(coordinates)
        .waypointIndicesList(indices)
        .waypointNamesList(names)
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
    // set routes, where the first route in the list is the primary route that
    // will be used for active guidance
    mapboxNavigation?.setNavigationRoutes(routes)

    // show UI elements
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

    // Clear routs and end
    mapboxNavigation?.setNavigationRoutes(listOf())

    // hide UI elements
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

  fun setWaypoints(waypoints: List<Point>) {
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
    // Remove old annotations
    waypointAnnotationManager?.deleteAll()
    waypointAnnotations.clear()
    if (waypoints.isEmpty() && destination == null) return
    
    // Only proceed if style is loaded
    mapView.mapboxMap.getStyle { style ->
        if (waypointAnnotationManager == null) {
            waypointAnnotationManager = mapView.annotations.createPointAnnotationManager()
        }
        val manager = waypointAnnotationManager!!
        
        // Add waypoint annotations with numbers (do NOT number startOrigin)
        waypoints.forEachIndexed { idx, point ->
            val number = idx + 1 // Numbering starts at 1 for waypoints only
            val iconId = "waypoint_icon_$number"
            // Create and add the numbered bitmap to the style
            val bitmap = createNumberedWaypointBitmap(number)
            style.addImage(iconId, bitmap)
            val opts = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(iconId)
                .withIconSize(1.0)
            val annotation = manager.create(opts)
            waypointAnnotations.add(annotation)
        }
        // Add destination annotation with flag icon
        destination?.let { destPoint ->
            val flagIconId = "destination_flag_icon"
            if (style.getStyleImage(flagIconId) == null) {
                val flagBitmap = createDestinationFlagBitmap()
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

private fun createDestinationFlagBitmap(): android.graphics.Bitmap {
    val width = 48
    val height = 64
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.isAntiAlias = true
    
    // Draw flag pole
    paint.color = android.graphics.Color.parseColor("#8B4513") // Brown
    paint.strokeWidth = 4f
    canvas.drawLine(width * 0.15f, height * 0.2f, width * 0.15f, height * 0.95f, paint)
    
    // Draw flag
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#FF3B30") // Red flag
    val flagPath = android.graphics.Path()
    flagPath.moveTo(width * 0.2f, height * 0.2f)
    flagPath.lineTo(width * 0.85f, height * 0.35f)
    flagPath.lineTo(width * 0.2f, height * 0.5f)
    flagPath.close()
    canvas.drawPath(flagPath, paint)
    
    // Draw flag border
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.parseColor("#D70015") // Darker red
    paint.strokeWidth = 2f
    canvas.drawPath(flagPath, paint)
    
    return bitmap
}

private fun createNumberedWaypointBitmap(number: Int): android.graphics.Bitmap {
    val size = 64
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.isAntiAlias = true
    // Draw blue circle (iOS style)
    paint.color = android.graphics.Color.parseColor("#007AFF") // iOS blue
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
    // Draw white border
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 6f
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f - 3f, paint)
    // Draw number
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    paint.textSize = size / 2f + 4f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.isFakeBoldText = true
    val textY = size / 2f - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(number.toString(), size / 2f, textY, paint)
    return bitmap
}
}

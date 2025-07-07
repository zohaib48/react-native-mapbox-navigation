import MapboxCoreNavigation
import MapboxNavigation
import MapboxDirections
import MapboxMaps
import Polyline
import CoreLocation

extension UIView {
    var parentViewController: UIViewController? {
        var parentResponder: UIResponder? = self
        while parentResponder != nil {
            parentResponder = parentResponder!.next
            if let viewController = parentResponder as? UIViewController {
                return viewController
            }
        }
        return nil
    }
}

public protocol MapboxCarPlayDelegate {
    func connect(with navigationView: MapboxNavigationView)
    func disconnect()
}

public protocol MapboxCarPlayNavigationDelegate {
    func startNavigation(with navigationView: MapboxNavigationView)
    func endNavigation()
}

public class MapboxNavigationView: UIView, NavigationViewControllerDelegate, CLLocationManagerDelegate {
    public weak var navViewController: NavigationViewController?
    public var indexedRouteResponse: IndexedRouteResponse?
    private var navigationInitialized = false
    private var rerouteDelay: TimeInterval = 0.5
    private var rerouteWorkItem: DispatchWorkItem?
    private var isNavigating = false
    
    var embedded: Bool
    var embedding: Bool

    @objc public var startOrigin: NSArray = []

    @objc public var customerLocation: NSArray = [] {
        didSet {
            DispatchQueue.main.async { [weak self] in
                self?.updateCustomerAnnotation()
            }
        }
    }
    
    var waypoints: [Waypoint] = [] {
        didSet { propDidChange() }
    }
    
    func setWaypoints(waypoints: [MapboxWaypoint]) {
        self.waypoints = waypoints.enumerated().map { (index, waypointData) in
            let name = waypointData.name as? String ?? "\(index)"
            let waypoint = Waypoint(coordinate: waypointData.coordinate, name: name)
            waypoint.separatesLegs = waypointData.separatesLegs
            return waypoint
        }
    }
    
    @objc var destination: NSArray = [] {
        didSet { propDidChange() }
    }
    
    @objc var shouldSimulateRoute: Bool = false
    @objc var showsEndOfRouteFeedback: Bool = false
    @objc var showCancelButton: Bool = false
    @objc var hideStatusView: Bool = false
    @objc var mute: Bool = false
    @objc var distanceUnit: NSString = "imperial" {
        didSet { propDidChange() }
    }
    @objc var language: NSString = "us"
    @objc var destinationTitle: NSString = "Destination" { didSet { propDidChange() } }
    @objc var travelMode: NSString = "driving-traffic"
    @objc var mapStyle: NSString?

    // MARK: – Customer annotation manager
    private var customerAnnotationManager: PointAnnotationManager?
    private let customerAnnotationManagerId = "customer-location-manager"
    private var customerAnnotation: PointAnnotation?

    @objc var onLocationChange: RCTDirectEventBlock?
    @objc var onRouteProgressChange: RCTDirectEventBlock?
    @objc var onError: RCTDirectEventBlock?
    @objc var onCancelNavigation: RCTDirectEventBlock?
    @objc var onArrive: RCTDirectEventBlock?
    @objc var vehicleMaxHeight: NSNumber?
    @objc var vehicleMaxWidth: NSNumber?
    @objc var onRouteReady: RCTDirectEventBlock?

    // CLLocationManager for user location
    private let locationManager = CLLocationManager()
    private var userLocation: CLLocationCoordinate2D?
    private var pendingNavigation = false
    private var pendingReroute = false

    override init(frame: CGRect) {
        self.embedded = false
        self.embedding = false
        super.init(frame: frame)
        locationManager.delegate = self
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public override func layoutSubviews() {
        super.layoutSubviews()

        if (navViewController == nil && !embedding && !embedded) {
            embed()
        } else {
            navViewController?.view.frame = bounds
        }
    }

    public override func removeFromSuperview() {
        super.removeFromSuperview()
        self.navViewController?.removeFromParent()
        isNavigating = false
        navigationInitialized = false
        rerouteWorkItem?.cancel()
        navViewController?.navigationService?.stop()
        
        if let carPlayNavigation = UIApplication.shared.delegate as? MapboxCarPlayNavigationDelegate {
            carPlayNavigation.endNavigation()
        }
        NotificationCenter.default.removeObserver(self, name: .navigationSettingsDidChange, object: nil)
    }

    private func embed() {
        guard startOrigin.count == 2 && destination.count == 2 else { return }

        // Wait for user location before building the route
        if userLocation == nil {
            pendingNavigation = true
            return
        }
        pendingNavigation = false
        embedding = true
        self.isNavigating = true

        // Always force: [userLocation, startOrigin, ...waypoints, destination]
        var waypointsArray: [Waypoint] = []
        if let userLoc = userLocation {
            waypointsArray.append(Waypoint(coordinate: userLoc))
        } else {
            waypointsArray.append(Waypoint(coordinate: CLLocationCoordinate2D(latitude: startOrigin[1] as! CLLocationDegrees, longitude: startOrigin[0] as! CLLocationDegrees)))
        }
        // Start origin: do NOT assign a name
        waypointsArray.append(Waypoint(coordinate: CLLocationCoordinate2D(latitude: startOrigin[1] as! CLLocationDegrees, longitude: startOrigin[0] as! CLLocationDegrees)))
        waypointsArray.append(contentsOf: waypoints)
        let destinationWaypoint = Waypoint(coordinate: CLLocationCoordinate2D(latitude: destination[1] as! CLLocationDegrees, longitude: destination[0] as! CLLocationDegrees), name: destinationTitle as String)
        waypointsArray.append(destinationWaypoint)

        let profile: MBDirectionsProfileIdentifier
        switch travelMode {
            case "cycling":
                profile = .cycling
            case "walking":
                profile = .walking
            case "driving-traffic":
                profile = .automobileAvoidingTraffic
            default:
                profile = .automobile
        }

        let options = NavigationRouteOptions(waypoints: waypointsArray, profileIdentifier: profile)
        let locale = self.language.replacingOccurrences(of: "-", with: "_")
        options.locale = Locale(identifier: (language as String).replacingOccurrences(of: "-", with: "_"))
        options.distanceMeasurementSystem =  distanceUnit == "imperial" ? .imperial : .metric

        Directions.shared.calculateRoutes(options: options) { [weak self] result in
            guard let strongSelf = self, let parentVC = strongSelf.parentViewController else {
                return
            }

            switch result {
            case .failure(let error):
                strongSelf.onError!(["message": error.localizedDescription])
            case .success(let response):
                strongSelf.indexedRouteResponse = response
              guard let route = response.routeResponse.routes?.first else { return }
              let coord = route.shape?.coordinates

              let polyline = Polyline(coordinates: coord!, precision: 1e6).encodedPolyline
              let distance = route.distance
              let duration = route.expectedTravelTime

              self?.onRouteReady?(["polyline": polyline,
                                         "distance": distance,
                                         "duration": duration])


                let navigationOptions = NavigationOptions(simulationMode: strongSelf.shouldSimulateRoute ? .always : .never)
                let vc = NavigationViewController(for: response, navigationOptions: navigationOptions)

                vc.showsEndOfRouteFeedback = strongSelf.showsEndOfRouteFeedback
                StatusView.appearance().isHidden = strongSelf.hideStatusView

                NavigationSettings.shared.voiceMuted = strongSelf.mute
                NavigationSettings.shared.distanceUnit = strongSelf.distanceUnit == "imperial" ? .mile : .kilometer

                let styleString = (strongSelf.mapStyle as String?) ?? StyleURI.navigationDay.rawValue
                guard let styleURI = StyleURI(rawValue: styleString) else { return }
                
                vc.navigationMapView?.mapView.mapboxMap.loadStyleURI(styleURI){ _ in }

                vc.delegate = strongSelf

                parentVC.addChild(vc)
                strongSelf.addSubview(vc.view)
                vc.view.frame = strongSelf.bounds
                vc.didMove(toParent: parentVC)
                strongSelf.navViewController = vc
                
                // Initialize annotation manager
                strongSelf.setupAnnotationManager()
                strongSelf.updateCustomerAnnotation()
            }

            strongSelf.embedding = false
            strongSelf.embedded = true
            
            if let carPlayNavigation = UIApplication.shared.delegate as? MapboxCarPlayNavigationDelegate {
                carPlayNavigation.startNavigation(with: strongSelf)
            }
        }
    }

    private func setupAnnotationManager() {
        guard
            let navVC = navViewController,
            let mapView = navVC.navigationMapView?.mapView,
            customerAnnotationManager == nil
        else { return }
        
        customerAnnotationManager = mapView.annotations
            .makePointAnnotationManager(
                id: customerAnnotationManagerId,
                layerPosition: nil
            )
        
        // Create initial annotation
        var annotation = PointAnnotation(point: Point(CLLocationCoordinate2D(latitude: 0, longitude: 0)))  // Updated initialization
        if let dotImage = UIImage(named: "red_dot") {
            let scale: CGFloat = 0.10
            let newSize = CGSize(width: dotImage.size.width * scale,
                                height: dotImage.size.height * scale)
            UIGraphicsBeginImageContextWithOptions(newSize, false, 0.0)
            dotImage.draw(in: CGRect(origin: .zero, size: newSize))
            let resized = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            if let smallImage = resized {
                annotation.image = .init(image: smallImage, name: "red_dot_small")
            }
        }
        customerAnnotation = annotation
        customerAnnotationManager?.annotations = [annotation]
    }

    private func updateCustomerAnnotation() {
        guard
            customerLocation.count == 2,
            let lon = customerLocation[0] as? CLLocationDegrees,
            let lat = customerLocation[1] as? CLLocationDegrees,
            let annotationManager = customerAnnotationManager,
            var annotation = customerAnnotation
        else {
            return
        }
        
        let newCoordinate = CLLocationCoordinate2D(latitude: lat, longitude: lon)
        annotation.point = Point(newCoordinate)  // Changed from .coordinate to .point
        customerAnnotation = annotation
        annotationManager.annotations = [annotation]
    }

    private func propDidChange() {
        checkInitNavigation()
        guard navigationInitialized, isNavigating, !embedding else { return }
        if isNavigating {
            scheduleReroute()
        }
    }

    private func checkInitNavigation(){
        guard !navigationInitialized, startOrigin.count == 2, destination.count == 2 else {
            return
        }
        navigationInitialized = true
        embed()
    }

    private func scheduleReroute() {
        guard navigationInitialized, isNavigating else { return }
        rerouteWorkItem?.cancel()
        let work = DispatchWorkItem { [weak self] in
        self?.performReroute()
        }
        rerouteWorkItem = work
        DispatchQueue.main.asyncAfter(deadline: .now() + rerouteDelay, execute: work)
    }

    private func performReroute() {
        guard
            let startLat = startOrigin[1] as? NSNumber,
            let startLon = startOrigin[0] as? NSNumber,
            let destLat  = destination[1] as? NSNumber,
            let destLon  = destination[0] as? NSNumber
        else { return }

        // Wait for user location before rerouting
        if userLocation == nil {
            pendingReroute = true
            return
        }
        pendingReroute = false
        // Always force: [userLocation, startOrigin, ...waypoints, destination]
        var coords: [Waypoint] = []
        if let userLoc = userLocation {
            coords.append(.init(coordinate: userLoc))
        } else {
            coords.append(.init(coordinate: CLLocationCoordinate2D(latitude: startLat.doubleValue, longitude: startLon.doubleValue)))
        }
        // Start origin: do NOT assign a name
        coords.append(.init(coordinate: CLLocationCoordinate2D(latitude: startLat.doubleValue, longitude: startLon.doubleValue)))
        coords += waypoints
        coords.append(.init(coordinate: CLLocationCoordinate2D(latitude: destLat.doubleValue, longitude: destLon.doubleValue), name: destinationTitle as String))

        let profile: MBDirectionsProfileIdentifier = {
            switch travelMode {
            case "cycling":          return .cycling
            case "walking":          return .walking
            case "driving-traffic":  return .automobileAvoidingTraffic
            default:                 return .automobile
            }
        }()

        let options = NavigationRouteOptions(waypoints: coords,
                                            profileIdentifier: profile)
        options.distanceMeasurementSystem =
            distanceUnit == "imperial" ? .imperial : .metric
        options.locale = Locale(identifier:
            language.replacingOccurrences(of: "-", with: "_"))

        // re-calculate the route
        Directions.shared.calculateRoutes(options: options) { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .failure(let err):
                self.onError?(["message": err.localizedDescription])
            case .success(let indexedRouteResponse):
                // this is the new, updated RouteResponse + index
                // hand it off to the Router so NavigationViewController updates
                if let router = self.navViewController?
                                    .navigationService
                                    .router {
                    router.updateRoute(
                    with: indexedRouteResponse,
                    routeOptions: options,
                    completion: nil
                    )
                }
            }
        }
    }



    public func navigationViewController(_ navigationViewController: NavigationViewController, didUpdate progress: RouteProgress, with location: CLLocation, rawLocation: CLLocation) {
        onLocationChange?([
            "longitude": location.coordinate.longitude,
            "latitude": location.coordinate.latitude,
            "heading": location.course,
            "accuracy": location.horizontalAccuracy.magnitude
        ])
        onRouteProgressChange?([
            "distanceTraveled": progress.distanceTraveled,
            "durationRemaining": progress.durationRemaining,
            "fractionTraveled": progress.fractionTraveled,
            "distanceRemaining": progress.distanceRemaining
        ])
    }

    public func navigationViewControllerDidDismiss(_ navigationViewController: NavigationViewController, byCanceling canceled: Bool) {
        guard canceled else { return }
        isNavigating = false
        navigationInitialized = false
        rerouteWorkItem?.cancel()

        onCancelNavigation?(["message": "Navigation Cancel"])
    }

    public func navigationViewController(_ navigationViewController: NavigationViewController, didArriveAt waypoint: Waypoint) -> Bool {
        onArrive?([
            "name": waypoint.name ?? waypoint.description,
            "longitude": waypoint.coordinate.longitude,
            "latitude": waypoint.coordinate.latitude,
        ])

        
        return true
    }

    // CLLocationManagerDelegate
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        userLocation = locations.last?.coordinate
        if pendingNavigation {
            pendingNavigation = false
            embed()
        }
        if pendingReroute {
            pendingReroute = false
            performReroute()
        }
    }
}

@preconcurrency import CoreLocation
import SwiftUI

@MainActor
final class LocationService: NSObject, ObservableObject, @preconcurrency CLLocationManagerDelegate {
    @Published private(set) var location: CLLocation?
    @Published private(set) var heading: Double?
    @Published private(set) var accuracy: Double = -1
    @Published private(set) var error: String?
    @Published private(set) var isLocating = false
    let hasCompass = CLLocationManager.headingAvailable()
    private let manager = CLLocationManager()
    private var followsHeading = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
        manager.headingFilter = 1
    }

    func startCompass() {
        #if DEBUG
        if ProcessInfo.processInfo.environment["AYNAMA_SCREENSHOT_FIXTURES"] == "1" { return }
        #endif
        followsHeading = true
        locate()
        if hasCompass { manager.startUpdatingHeading() }
    }

    func locate() {
        error = nil
        isLocating = true
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse: manager.requestLocation()
        case .denied, .restricted:
            isLocating = false
            error = "Location access is off. Choose a city or enable access in Settings."
        @unknown default: isLocating = false
        }
    }

    func stop() {
        manager.stopUpdatingHeading()
        manager.stopUpdatingLocation()
        followsHeading = false
        isLocating = false
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if isLocating, manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
            if followsHeading, hasCompass { manager.startUpdatingHeading() }
        } else if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted {
            isLocating = false
            error = "Location access is off. Choose a city or enable access in Settings."
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        isLocating = false
        location = locations.last.flatMap {
            $0.horizontalAccuracy >= 0 && abs($0.timestamp.timeIntervalSinceNow) < 3600 ? $0 : nil
        }
        if location == nil { error = "Couldn't find your location. Try again or choose a city." }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        accuracy = newHeading.headingAccuracy
        // trueHeading is unavailable without a fix. Do not quietly label magnetic north as true.
        guard newHeading.trueHeading >= 0, accuracy >= 0 else { heading = nil; return }
        let raw = newHeading.trueHeading
        if let previous = heading {
            let delta = ((raw - previous).truncatingRemainder(dividingBy: 360) + 540)
                .truncatingRemainder(dividingBy: 360) - 180
            heading = previous + delta
        } else { heading = raw }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        isLocating = false
        location = nil
        self.error = "Couldn't find your location. Try again or choose a city."
    }
}

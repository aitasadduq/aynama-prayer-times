import Foundation

/// Bearing and distance to the Kaaba.
///
/// Deliberately not Adhan's own `Qibla` type: Android hand-rolls the same great-circle formula in
/// `QiblaCalculator.kt` and the two must agree to the degree, so both platforms compute it the
/// same way from the same constants rather than one deferring to a library the other does not use.
public enum QiblaCalculator {

    public static let kaabaLatitude = 21.4225
    public static let kaabaLongitude = 39.8262
    static let earthRadiusKm = 6371.0

    /// Initial great-circle bearing from the user to the Kaaba, in degrees clockwise from true
    /// north, normalised to [0, 360).
    public static func bearing(latitude: Double, longitude: Double) -> Double {
        let lat1 = latitude * .pi / 180
        let lat2 = kaabaLatitude * .pi / 180
        let dLng = (kaabaLongitude - longitude) * .pi / 180
        let y = sin(dLng) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (atan2(y, x) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
    }

    /// Great-circle distance to the Kaaba in kilometres (haversine, spherical Earth).
    public static func distanceKm(latitude: Double, longitude: Double) -> Double {
        let lat1 = latitude * .pi / 180
        let lat2 = kaabaLatitude * .pi / 180
        let dLat = (kaabaLatitude - latitude) * .pi / 180
        let dLng = (kaabaLongitude - longitude) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

import Foundation

/// The app's URL scheme: `aynama://profile/<id>`.
///
/// **A widget tap opens the app on that widget's own profile, not the default one** — the Phase 1
/// requirement Android meets with an intent extra. iOS widgets have no intent extras; a tap
/// carries a `widgetURL`, so the profile id travels as a URL and the app resolves it on
/// `onOpenURL`. The Live Activity and the prayer notifications use the same shape.
///
/// In `SharedLogic` rather than in the app because the widget extension *writes* these URLs and
/// the app *reads* them. Two definitions of the same format in two targets is how a widget ends
/// up opening the wrong profile, which is precisely the bug Phase 4A tests for.
public enum AynamaURL {

    public static let scheme = "aynama"

    static let profileHost = "profile"

    public static func profile(_ id: Int64) -> URL {
        // Force-unwrapped safely: the scheme is a literal and `id` is an integer, so there is no
        // input that can make this fail to parse.
        URL(string: "\(scheme)://\(profileHost)/\(id)")!
    }

    /// The profile id in `url`, or nil if it does not name one.
    ///
    /// Deliberately strict. A malformed, foreign or partial URL resolves to nil and the app opens
    /// on the user's own selection — which is a good outcome — rather than on profile 0, or on a
    /// number scraped out of whatever happened to be in the path.
    public static func profileID(from url: URL) -> Int64? {
        guard url.scheme == scheme, url.host() == profileHost else { return nil }
        let segments = url.pathComponents.filter { $0 != "/" }
        guard segments.count == 1 else { return nil }
        return Int64(segments[0])
    }
}

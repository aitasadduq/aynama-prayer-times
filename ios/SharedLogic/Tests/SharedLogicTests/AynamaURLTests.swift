import Foundation
import Testing

@testable import SharedLogic

/// Per-widget profile navigation, at the format level.
///
/// The Android equivalent is `WidgetProfileBindingTest`, which needs a device because an intent
/// extra only exists inside an `Intent`. The iOS carrier is a URL, so the contract between the
/// widget that writes it and the app that reads it can be pinned here, on every commit, with no
/// simulator: a round trip must survive, and nothing else may be mistaken for one.
@Suite("Deep links")
struct AynamaURLTests {

    @Test("a profile URL round-trips", arguments: [Int64(0), 1, 7, 42, 9_999, Int64.max])
    func roundTrip(id: Int64) {
        #expect(AynamaURL.profileID(from: AynamaURL.profile(id)) == id)
    }

    @Test("the URL has the shape widgets and notifications agree on")
    func shape() {
        #expect(AynamaURL.profile(3).absoluteString == "aynama://profile/3")
    }

    @Test(
        "anything that is not a profile URL resolves to nil",
        arguments: [
            // Another app's scheme. A widget from a different app must not steer this one.
            "https://profile/3",
            "aynamax://profile/3",
            // Right scheme, wrong host.
            "aynama://settings/3",
            "aynama://profile",
            // Not an id.
            "aynama://profile/abc",
            "aynama://profile/3.5",
            "aynama://profile/-",
            // Extra path segments: an id scraped out of a longer path is a guess, not a link.
            "aynama://profile/3/edit",
        ]
    )
    func rejected(text: String) {
        let url = URL(string: text)
        #expect(url != nil, "test fixture is not a URL: \(text)")
        #expect(AynamaURL.profileID(from: url!) == nil, "\(text) should not name a profile")
    }

    @Test("an empty path segment normalises away rather than being rejected")
    func doubledSlash() {
        // `URL.pathComponents` collapses `//3` to a single "3", so this is one segment, not two.
        // Accepted deliberately: the path is unambiguous, nothing this app writes can produce it,
        // and re-parsing the raw string to reject it would be code guarding against nothing.
        #expect(AynamaURL.profileID(from: URL(string: "aynama://profile//3")!) == 3)
    }

    @Test("a negative id parses, and is rejected later by the profile that does not exist")
    func negativeID() {
        // The parser's job is the format, not the range. Ids are assigned from 1 upward, so -3
        // names no profile — and `SelectedProfile.resolve` already falls back to the first real
        // profile for any id that is not in the store, which is the same path a link to a
        // deleted profile takes. Range-checking here would add a second, weaker guard.
        #expect(AynamaURL.profileID(from: URL(string: "aynama://profile/-3")!) == -3)
    }
}

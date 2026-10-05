import Foundation
import Testing
@testable import SharedLogic

struct ProfileLocationNameTests {
    @Test("Location names survive encoding, and older profiles without the field still decode")
    func persistedAndLegacyNames() throws {
        let profile = Profile(name: "Home", latitude: 21.3871, longitude: 39.8688,
                              calculationMethod: .ummAlQura, asrMadhab: .shafii,
                              locationName: "Makkah, Saudi Arabia")
        let data = try JSONEncoder().encode(profile)
        #expect(try JSONDecoder().decode(Profile.self, from: data) == profile)
        var legacy = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        legacy.removeValue(forKey: "locationName")
        let decoded = try JSONDecoder().decode(Profile.self, from: JSONSerialization.data(withJSONObject: legacy))
        #expect(decoded.locationName == nil)
        #expect(decoded.name == "Home")
        #expect(decoded.latitude == 21.3871)
    }
}

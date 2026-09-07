import Foundation
import Testing

@testable import SharedLogic

/// Adhan-Swift must agree with Adhan-Kotlin on every committed test vector, within ±1 minute.
///
/// This is the pre-v3 gate from TODOS.md ("Adhan-Swift version pin + parity check"). The vectors
/// under `test-vectors/prayer-times/` are what Adhan-Kotlin 1.2.1 — the release the Android app
/// ships — actually produces; regenerate them with `scripts/adhan-parity/generate.py`. Holding
/// iOS to Android's numbers rather than to a third opinion is what Phase 3A asks for: the tested
/// Android behaviour is the product specification.
///
/// Tolerance is the ±1 minute architecture-design.md specifies, so a case failing here means a
/// disagreement of two minutes or more — a real divergence, not rounding.
@Suite("Adhan cross-port parity")
struct VectorParityTests {

    // MARK: - Vector loading

    struct VectorFile: Decodable {
        let method: String
        let toleranceMinutes: Int
        let reference: String
        let cases: [Case]

        enum CodingKeys: String, CodingKey {
            case method
            case toleranceMinutes = "tolerance_minutes"
            case reference
            case cases
        }
    }

    struct Case: Decodable {
        let description: String?
        let input: Input
        let expected: [String: String]
    }

    struct Input: Decodable {
        let latitude: Double
        let longitude: Double
        let date: String
        let timezone: String
        let elevationMeters: Double?

        enum CodingKeys: String, CodingKey {
            case latitude, longitude, date, timezone
            case elevationMeters = "elevation_meters"
        }
    }

    /// `test-vectors/` sits at the repo root, four levels above this file. Resolved from
    /// `#filePath` rather than a bundle so the same test runs under `swift test` and under
    /// `xcodebuild test` without a resource-copy step, and so a missing directory is a loud
    /// failure rather than an empty, silently-passing argument list.
    static let vectorsDirectory: URL = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()  // SharedLogicTests
        .deletingLastPathComponent()  // Tests
        .deletingLastPathComponent()  // SharedLogic
        .deletingLastPathComponent()  // ios
        .deletingLastPathComponent()  // <repo root>
        .appendingPathComponent("test-vectors/prayer-times")

    static let vectorFiles: [VectorFile] = {
        let urls = (try? FileManager.default.contentsOfDirectory(
            at: vectorsDirectory, includingPropertiesForKeys: nil
        )) ?? []
        return urls
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .compactMap { url in
                guard let data = try? Data(contentsOf: url) else { return nil }
                return try? JSONDecoder().decode(VectorFile.self, from: data)
            }
    }()

    /// One argument per case, flattened so a failure names the city rather than the file.
    static let allCases: [(method: String, tolerance: Int, testCase: Case)] =
        vectorFiles.flatMap { file in
            file.cases.map { (method: file.method, tolerance: file.toleranceMinutes, testCase: $0) }
        }

    // MARK: - The gate

    @Test("the vector directory is present and non-empty")
    func vectorsExist() {
        #expect(
            !Self.vectorFiles.isEmpty,
            "no vector files under \(Self.vectorsDirectory.path) — run scripts/adhan-parity/generate.py"
        )
        #expect(Self.allCases.count == 12, "the parity set is twelve cities")
        // Every method the app offers must be exercised, or a mapping bug in AdhanWrapper's
        // CalculationMethodKey → CalculationMethod switch could ship unnoticed.
        let covered = Set(Self.vectorFiles.map { $0.method.lowercased() })
        let offered = Set(CalculationMethodKey.allCases.map { normalise($0) })
        #expect(covered == offered, "uncovered methods: \(offered.subtracting(covered).sorted())")
    }

    @Test("Adhan-Swift agrees with Adhan-Kotlin on every vector", arguments: allCases)
    func parity(vector: (method: String, tolerance: Int, testCase: Case)) throws {
        let input = vector.testCase.input
        let method = try #require(
            CalculationMethodKey.allCases.first { normalise($0) == vector.method.lowercased() },
            "unmapped method \(vector.method)"
        )
        let zone = try #require(TimeZone(identifier: input.timezone))
        let parts = input.date.split(separator: "-").compactMap { Int($0) }
        try #require(parts.count == 3)

        let actual = try AdhanWrapper().prayerTimes(
            latitude: input.latitude,
            longitude: input.longitude,
            date: CalendarDate(year: parts[0], month: parts[1], day: parts[2]),
            timeZone: zone,
            method: method
        )

        let produced: [String: ClockTime] = [
            "fajr": actual.fajr,
            "sunrise": actual.sunrise,
            "dhuhr": actual.dhuhr,
            "asr_shafii": actual.asrShafii,
            "asr_hanafi": actual.asrHanafi,
            "maghrib": actual.maghrib,
            "isha": actual.isha,
        ]

        let label = vector.testCase.description ?? "\(input.latitude),\(input.longitude)"
        for (key, expectedText) in vector.testCase.expected.sorted(by: { $0.key < $1.key }) {
            let expected = try #require(parseClockTime(expectedText), "unparsable \(key): \(expectedText)")
            let produced = try #require(produced[key], "vector names an unknown time: \(key)")
            let delta = minuteDelta(expected, produced)
            #expect(
                delta <= vector.tolerance,
                """
                \(label) — \(key): Adhan-Kotlin says \(expectedText), \
                Adhan-Swift says \(String(format: "%02d:%02d", produced.hour, produced.minute)) \
                (\(delta) min apart, tolerance \(vector.tolerance))
                """
            )
        }
    }

    // MARK: - Helpers

    /// Vector method names are the schema's SCREAMING_SNAKE; the Swift enum is camelCase.
    func normalise(_ key: CalculationMethodKey) -> String {
        switch key {
        case .mwl: "mwl"
        case .isna: "isna"
        case .ummAlQura: "umm_al_qura"
        case .egyptian: "egyptian"
        case .karachi: "karachi"
        case .dubai: "dubai"
        case .moonSightingCommittee: "moon_sighting_committee"
        case .kuwait: "kuwait"
        case .qatar: "qatar"
        case .singapore: "singapore"
        }
    }

    func parseClockTime(_ text: String) -> ClockTime? {
        let parts = text.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return ClockTime(hour: parts[0], minute: parts[1])
    }

    /// Minutes apart, taking the short way round midnight — a 23:58 vs 00:01 pair is 3 minutes,
    /// not 1437. Late Isha times sit on both sides of midnight depending on the port's rounding.
    func minuteDelta(_ lhs: ClockTime, _ rhs: ClockTime) -> Int {
        let raw = abs(lhs.secondOfDay - rhs.secondOfDay) / 60
        return min(raw, 24 * 60 - raw)
    }
}

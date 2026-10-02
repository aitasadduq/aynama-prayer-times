// swift-tools-version:6.0

import PackageDescription

// Adhan-Swift is pinned to an exact release, not a range. The prayer times this package
// produces are validated against committed test vectors generated from Adhan-Kotlin, so an
// unattended minor bump would silently move the numbers the vectors are meant to pin.
// The pin is mirrored in scripts/reference-versions.json.
let package = Package(
    name: "SharedLogic",
    platforms: [
        .iOS(.v17),
        .watchOS(.v10),
        .macOS(.v14),
    ],
    products: [
        .library(name: "SharedLogic", targets: ["SharedLogic"]),
    ],
    dependencies: [
        .package(url: "https://github.com/batoulapps/adhan-swift.git", exact: "1.5.0"),
    ],
    targets: [
        .target(
            name: "SharedLogic",
            dependencies: [.product(name: "Adhan", package: "adhan-swift")]
        ),
        .testTarget(
            name: "SharedLogicTests",
            dependencies: ["SharedLogic"]
        ),
    ]
)

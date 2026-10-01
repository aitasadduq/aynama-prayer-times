import XCTest

final class DesignParityUITests: XCTestCase {
    @MainActor
    private func launch(dark: Bool = false, store: String = UUID().uuidString, largeText: Bool = false) -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchEnvironment["AYNAMA_UI_TEST_STORE"] = store
        app.launchEnvironment["AYNAMA_SCREENSHOT_FIXTURES"] = "1"
        app.launchEnvironment["AYNAMA_TEST_APPEARANCE"] = dark ? "dark" : "light"
        app.launchEnvironment["AYNAMA_TEST_NOW"] = "2026-09-30T12:15:00Z"
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-AppleInterfaceStyle", dark ? "Dark" : "Light"]
        if largeText { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launch()
        XCTAssertTrue(app.staticTexts["London · MWL"].waitForExistence(timeout: 10))
        return app
    }
    @MainActor
    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
    @MainActor
    private func assertPrayerListFits(_ app: XCUIApplication) {
        for name in ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"] {
            let row = app.descendants(matching: .any)["home-row-\(name)"]
            XCTAssertTrue(row.waitForExistence(timeout: 5), "Missing \(name) row")
            XCTAssertTrue(row.isHittable, "\(name) should be visible above the tab bar")
        }
    }

    @MainActor
    func testScreenshotsAndNativeNavigation() {
        let app = launch()
        for title in ["Prayers", "Qibla", "Tracker", "Settings"] { XCTAssertTrue(app.tabBars.buttons[title].exists) }
        XCTAssertTrue(app.buttons["New profile"].isHittable)
        assertPrayerListFits(app)
        capture(app, "01-prayers-light")
        app.tabBars.buttons["Qibla"].tap()
        XCTAssertTrue(app.staticTexts["Qibla"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Bearing from true north"].exists)
        capture(app, "02-qibla-light")
        app.tabBars.buttons["Tracker"].tap()
        XCTAssertTrue(app.staticTexts["Today"].waitForExistence(timeout: 5))
        capture(app, "03-tracker-light")
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Profiles"].waitForExistence(timeout: 5))
        capture(app, "04-settings-light")
        app.buttons["London"].tap()
        XCTAssertTrue(app.textFields["profile-name"].waitForExistence(timeout: 5))
        capture(app, "05-profile-sheet-light")
        app.buttons["Cancel"].tap()
        app.buttons["Notifications"].tap()
        XCTAssertTrue(app.staticTexts["PRAYERS"].waitForExistence(timeout: 5))
        capture(app, "06-notifications-light")
        app.buttons["Fajr notification settings"].tap()
        XCTAssertTrue(app.buttons["alert-offset"].waitForExistence(timeout: 5))
        capture(app, "07-prayer-alert-sheet-light")
    }

    @MainActor
    func testDarkScreenshots() {
        let app = launch(dark: true)
        capture(app, "08-prayers-dark")
        app.tabBars.buttons["Tracker"].tap()
        XCTAssertTrue(app.staticTexts["Today"].waitForExistence(timeout: 5))
        capture(app, "09-tracker-dark")
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Profiles"].waitForExistence(timeout: 5))
        capture(app, "10-settings-dark")
        app.buttons["Notifications"].tap()
        XCTAssertTrue(app.staticTexts["PRAYERS"].waitForExistence(timeout: 5))
        capture(app, "11-notifications-dark")
    }

    @MainActor
    func testLargeTextScreenshotsKeepProfileControlsReachable() {
        let app = launch(largeText: true)
        XCTAssertTrue(app.buttons["New profile"].isHittable)
        assertPrayerListFits(app)
        capture(app, "12-prayers-large-text")
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Profiles"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["New profile"].isHittable)
        capture(app, "13-settings-large-text")
    }

    @MainActor
    func testPrayerMarkPersistsAndPastPrayersCannotBeMarkedOnTime() {
        let store = UUID().uuidString
        let app = launch(store: store)
        app.tabBars.buttons["Tracker"].tap()
        let fajr = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Fajr,")).firstMatch
        XCTAssertTrue(fajr.waitForExistence(timeout: 5))
        fajr.tap()
        capture(app, "14-prayer-mark-sheet")
        XCTAssertTrue(app.buttons["I prayed this"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons.matching(identifier: "I prayed this").count, 1)
        XCTAssertFalse(app.buttons["I prayed this"].isEnabled)
        app.buttons["I didn't pray this"].tap()
        XCTAssertTrue(app.staticTexts["2 prayers outstanding"].waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        app.tabBars.buttons["Tracker"].tap()
        XCTAssertTrue(app.staticTexts["2 prayers outstanding"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testFixedTimeCancelAndProfileEditing() {
        let app = launch()
        app.tabBars.buttons["Settings"].tap()
        app.buttons["London"].tap()
        let name = app.textFields["profile-name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText(" office")
        app.buttons["Save"].tap()
        XCTAssertTrue(app.buttons["London office"].waitForExistence(timeout: 5))
        app.buttons["Notifications"].tap()
        app.buttons["Fajr notification settings"].tap()
        app.buttons["alert-fixed-time"].tap()
        XCTAssertTrue(app.datePickers.firstMatch.waitForExistence(timeout: 5))
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.buttons["alert-fixed-time"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Not set"].exists)
    }
}

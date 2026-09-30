import XCTest

final class ProfileFlowUITests: XCTestCase {
    @MainActor
    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchEnvironment["AYNAMA_UI_TEST_STORE"] = UUID().uuidString
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(app.buttons["Create profile"].waitForExistence(timeout: 10))
        return app
    }

    @MainActor
    private func fillProfile(_ app: XCUIApplication, name: String) {
        let nameField = app.textFields["profile-name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        nameField.tap()
        nameField.typeText(name)
        app.textFields["profile-latitude"].tap()
        app.textFields["profile-latitude"].typeText("21.4225")
        app.textFields["profile-longitude"].tap()
        app.textFields["profile-longitude"].typeText("39.8262")
    }

    @MainActor
    private func assertProfileVisible(_ app: XCUIApplication, name: String,
                                      file: StaticString = #filePath, line: UInt = #line) {
        // The profile name follows the prayer ribbon and may need scrolling on smaller phones.
        let label = app.staticTexts[name]
        _ = label.waitForExistence(timeout: 5)
        if !label.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(label.waitForExistence(timeout: 5), file: file, line: line)
        XCTAssertTrue(label.isHittable, "The selected profile must be on screen", file: file, line: line)
    }

    @MainActor
    func testCancelFromEmptyStateDoesNotCreateAProfile() {
        let app = launchApp()
        app.buttons["Create profile"].tap()
        fillProfile(app, name: "Cancelled profile")
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.buttons["Create profile"].waitForExistence(timeout: 5))

        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["Create profile"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Cancelled profile"].exists)
    }

    @MainActor
    func testSaveRequiresANameAndValidCoordinates() {
        let app = launchApp()
        app.buttons["Create profile"].tap()
        let save = app.buttons["Save"]
        XCTAssertTrue(save.waitForExistence(timeout: 5))
        XCTAssertFalse(save.isEnabled)
        app.textFields["profile-name"].tap()
        app.textFields["profile-name"].typeText("Home")
        XCTAssertFalse(save.isEnabled)
        app.textFields["profile-latitude"].tap()
        app.textFields["profile-latitude"].typeText("91")
        app.textFields["profile-longitude"].tap()
        app.textFields["profile-longitude"].typeText("39.8262")
        XCTAssertFalse(save.isEnabled)
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.buttons["Create profile"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testSaveFromBothEntryPointsSelectsAndPersistsTheNewProfile() {
        let app = launchApp()
        app.buttons["Create profile"].tap()
        fillProfile(app, name: "Home")
        XCTAssertTrue(app.buttons["Save"].isEnabled)
        app.buttons["Save"].tap()
        assertProfileVisible(app, name: "Home")
        app.buttons["New profile"].tap()
        fillProfile(app, name: "Office")
        app.buttons["Save"].tap()
        assertProfileVisible(app, name: "Office")

        app.terminate()
        app.launch()
        assertProfileVisible(app, name: "Office")
        XCTAssertFalse(app.buttons["Create profile"].exists)
    }

    @MainActor
    func testCancelFromFABKeepsTheExistingProfileSelected() {
        let app = launchApp()
        app.buttons["Create profile"].tap()
        fillProfile(app, name: "Home")
        app.buttons["Save"].tap()
        assertProfileVisible(app, name: "Home")
        app.buttons["New profile"].tap()
        fillProfile(app, name: "Cancelled profile")
        app.buttons["Cancel"].tap()
        assertProfileVisible(app, name: "Home")
        XCTAssertFalse(app.staticTexts["Cancelled profile"].exists)
        XCTAssertFalse(app.buttons["Create profile"].exists)
    }
}

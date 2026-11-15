package com.openfocushq.openfocus.screenshots

import org.robolectric.annotation.Config

/** Same screens as [ScreenshotTest], rendered in dark theme to docs/screenshots/dark/. */
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
class DarkScreenshotTest : ScreenshotTest() {
    override val variant = "dark"
}

# Borescope Direct

A local-first Android UVC endoscope app built with Jetpack Compose, the GoodUse Jetpack shell, and the maintained AUSBC engine.

## Product promise

- Test compatibility before paying: the unlock prompt appears only after a real video frame arrives.
- 90-second first-use compatibility preview, one test photo, and a 10-second test recording.
- One-time Play purchase; no subscription, ads, account, analytics, or cloud upload.
- One physical USB camera is active at a time.
- Lens switching appears only when Android exposes multiple USB camera devices. Cable-button and proprietary switching are not promised.
- Local app-specific photo/video storage with deliberate Android share actions.
- Sanitized compatibility report containing phone model/API, USB VID/PID, attempted profiles, and no serial number or media.
- Native Play review flow only after three successful camera sessions and a successful capture; there is no custom positive/negative sentiment gate.
- Complete Big 7 localization: English, Spanish, French, German, Brazilian Portuguese, Japanese, and Simplified Chinese.

## Compatibility strategy

The controller asks Android for permission and waits for an actual frame before declaring success. It tries:

1. 1280×720 MJPEG
2. 640×480 MJPEG
3. 640×480 YUYV

Each attempt has an eight-second stable-frame timeout. The app targets standard UVC endoscopes connected over USB OTG. Wi-Fi scopes and proprietary protocols are outside the v1 promise.

## Open in Android Studio

1. Install a current Android Studio with JDK 17 and Android SDK 37.
2. Open this folder and let Gradle sync.
3. Connect an Android 6.0+ USB-host phone. The first target device is a Galaxy S21 Ultra on Android 15.
4. Run the `app` debug configuration.

This export intentionally omits a generated Gradle wrapper JAR. Android Studio can use its bundled Gradle tooling or generate a wrapper with `gradle wrapper`.

## Browser-openable full app

Open `html-preview/index.html` directly in Chrome. The strip above the device is a test harness for permission, one-camera, two-camera, incompatibility, trial-expiry, and Play-offline states; it is outside the app UI. Captures, trial use, unlock state, and language selection are simulated locally in browser storage. The seven-language selector changes the full preview in place without discarding its current state.

See `LOCALIZATION.md` for the locale matrix, terminology decisions, coverage boundary, and automated checks.

## Play Console setup

Create a one-time in-app product with ID:

`borescope_direct_unlock`

Set the intended base price in Play Console (the product plan discussed was USD 5.99). The UI always displays Google Play's localized returned price and never hardcodes a currency amount.

For release builds, use Play license testers and an Internal Testing track to exercise purchase, cancellation, pending purchase, restore, and acknowledgement. The included entitlement implementation is client-only. That keeps the app account-free and inexpensive, but server-side purchase verification is stronger against tampering and should be considered if fraud becomes material.

## Permissions and data

The manifest asks only for `CAMERA`, because Android's USB video stack requires it on modern targets. The app does not open built-in phone cameras. It declares no internet, microphone, storage, location, advertising ID, or notification permission. Google Play Billing communicates through the Play Store app.

## Modules

- `app`: product flow, UVC controller, media, entitlement, and Compose UI.
- `gooduse-shell`: pinned GoodUse Jetpack shell source used as the app's UI foundation.

## Before publishing

- Review `PRODUCT_DESIGN_SHIPPING_REVIEW.md` and its before/after visual evidence.
- Review `CUSTOMER_LONGEVITY_AUDIT.md`; every remaining item is an explicit physical or Play release gate.
- Complete the two-camera device matrix in `DEVICE_TEST_CHECKLIST.md`.
- Run an external closed test on at least 20 device/camera combinations.
- Verify a signed release AAB, 16 KB page-size compatibility, startup, ANRs, and native crashes in Play pre-launch reports.
- Configure the one-time product and test restore on another phone using the same Play account.
- Replace the example application ID if needed before creating the permanent Play listing.
- Review every dependency and bundled native license before distribution.

## Not medical equipment

Borescope Direct is for general inspection work. It is not a medical device and must not be used on or inside people or animals.

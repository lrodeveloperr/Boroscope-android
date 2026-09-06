# Verification record

## Completed in this workspace

- Project structure, package declarations, manifest XML, and resource references checked statically.
- No `INTERNET`, microphone, storage, location, advertising ID, or notification permission declared.
- UVC safe-profile order covered by a unit test.
- Stable-frame acceptance, size changes, stalls, and clock reversal covered by unit tests.
- 90-second live-only timer arithmetic, clamping, and underflow covered by unit tests.
- Partial-video naming, playable-video validation, JPEG validation, and the full longevity audit are checked by the static verifier.
- Debug-only unlock button is guarded by `BuildConfig.DEBUG`.
- Store UI uses Play's localized `formattedPrice`.
- Compatibility report excludes USB serial number and media paths.
- `scripts/verify_project.sh` completes successfully, including XML parsing and sensitive-permission rejection.
- `scripts/adversarial_review.mjs` checks concurrency gates, driver boundaries, media publication, lifecycle cleanup, and HTML state/persistence invariants.
- Product Design captured and inspected the first-launch, live-camera, gallery, unlock, and incompatible-camera states; purchase UI is now gated to confirmed compatibility.
- Big 7 localization coverage is exact across 162 Android resources and 43 preview-only strings per locale; positional placeholders and resource types are verified automatically.
- The localized HTML runtime was exercised in all seven locales, including an in-place Japanese-to-Chinese switch during a live simulated session with the live phase and controls preserved.

## Not completed here

This environment does not contain Android SDK, ADB, or a Gradle installation capable of resolving and compiling the Android project. Therefore no APK/AAB was produced, no Gradle test was executed, and USB/native behavior has not been claimed as verified.

Use Android Studio/JDK 17 to run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Then complete `DEVICE_TEST_CHECKLIST.md` on the Galaxy S21 Ultra and both sample endoscopes before release.

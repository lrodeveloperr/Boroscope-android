# Big 7 localization record

## Shipping locale set

The app ships in the following seven languages. “Big 7” is defined here as a practical global-revenue set for this product:

| Language | Android resource | HTML locale | Style |
|---|---|---|---|
| English | `values` | `en` | Plain international English |
| Spanish | `values-es` | `es` | Neutral international Spanish; clear `tú`-compatible UI without regional slang |
| French | `values-fr` | `fr` | Standard contemporary French with formal, concise instructions |
| German | `values-de` | `de` | Direct, compound-aware technical German |
| Portuguese (Brazil) | `values-pt-rBR` | `pt-BR` | Native Brazilian terminology and tone |
| Japanese | `values-ja` | `ja` | Concise, polite-neutral Japanese UI |
| Chinese (Simplified) | `values-zh-rCN` | `zh-CN` | Mainland Simplified Chinese terminology |

## Coverage

- 162 Android string/plural resources exist in every shipping locale.
- 43 additional browser-preview strings exist in every locale.
- Visible labels, connection guidance, trial/paywall copy, billing status, capture and storage errors, gallery text, help/safety/privacy copy, share text, compatibility reports, dialogs, toasts, test-harness labels, and accessibility descriptions are covered.
- Runtime values retain positional placeholders, so word order can change naturally by language.
- Dates, numbers, byte sizes, and the preview’s demonstration price follow the selected locale. The Android purchase button uses Google Play’s already localized `formattedPrice`.
- Android follows the device/app language. The browser preview exposes an in-place language selector and preserves the current simulated camera state while switching.

## Cultural and technical terminology

- `UVC`, `OTG`, `MJPEG`, `YUYV`, `VID`, `PID`, and `AUSBC` remain standard technical identifiers.
- “Endoscope” is translated as an inspection-camera term, while the safety copy explicitly states that the product is not a medical device and must not be used on people or animals.
- The one-time purchase message is adapted naturally in every locale and preserves the promises of no ads, account, or subscription.
- Japanese and Chinese use native counting/spacing conventions rather than forced English plural grammar.
- Language names in the selector are intentionally shown in their own language so users can recover from an unfamiliar selection.

## Automated safeguards

Run:

```bash
bash scripts/verify_project.sh
```

The localization verifier rebuilds the browser catalog, requires exact locale-key coverage, compares resource types and positional placeholder signatures, checks all seven selector options, and rejects common hard-coded user-facing English patterns in the Kotlin UI/controller/billing surfaces.

## External text boundaries

Android permission sheets, Google Play purchase sheets, the system share chooser, and other operating-system surfaces are owned and localized by Android or Google Play. Camera product names and filenames are user/device data and are intentionally not translated.


# Product Design shipping review

## Scope

Final combined UX and visible-accessibility review of the approved first-launch, live-camera, gallery, unlock, and incompatibility states. Navigation, product rules, monetization, layout structure, and the core camera workflow were frozen.

## Verdict

The app is visually coherent and appropriately restrained for a single-purpose endoscope utility. The camera feed remains dominant, capture controls are immediately recognizable, touch targets are generous, and the paid conversion screen appears only after compatibility is proven.

## Review steps

1. **First launch — healthy.** One centered instruction card communicates the three required physical actions without technical format choices.
2. **Live camera — healthy.** Status, remaining compatibility time, and the three primary controls remain readable without obscuring the inspection feed.
3. **Saved locally — healthy.** Capture metadata, thumbnail, share, and delete affordances are clear; the empty space is intentional because the review state contains one capture.
4. **Unlock — healthy.** The one-time price and no-account/no-subscription reassurance are visually dominant; restore remains available without competing with the primary action.
5. **Incompatible camera — corrected.** The initial review exposed an impossible overlay: expired-preview purchase controls could cover the incompatibility card. Purchase controls are now restricted to a confirmed compatibility state, and the failure card uses a warning icon.

## Small shipping changes applied

- Replaced the generic camera symbol with a warning symbol for incompatibility.
- Prevented success/purchase UI from appearing over connection and compatibility failures.
- Added stronger keyboard focus rings for primary, secondary, link, and camera controls in the HTML preview.
- Aligned the report-sharing icon and label.
- Reduced countdown repainting to once per visible second.
- Isolated billing-error preview state from unrelated test presets.

## Evidence limits

The screenshots verify visual hierarchy, visible copy, spacing, target size, and state separation in Chrome. They do not prove TalkBack reading order, physical-device contrast under sunlight, maximum Android font scaling, or USB-system-dialog appearance. Those remain physical Android release checks.

## Evidence

- `design-review/01-first-launch.jpg`
- `design-review/02-live-camera.jpg`
- `design-review/03-gallery.jpg`
- `design-review/04-unlock.jpg`
- `design-review/05-incompatible-before.jpg`
- `design-review/06-incompatible-after.jpg`

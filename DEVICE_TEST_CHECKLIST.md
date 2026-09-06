# Device test checklist

Use this for the Galaxy S21 Ultra (Android 15) and each sample endoscope. Record the exact cable/adapter and power arrangement.

## Camera identity

- [ ] Camera A brand/model, USB VID:PID, connector, claimed resolution
- [ ] Camera B brand/model, USB VID:PID, connector, claimed resolution
- [ ] Direct USB-C connection tested
- [ ] Known-good OTG adapter tested
- [ ] Powered hub tested only if direct power is unstable

## Idiot test: clean install

- [ ] Install from Android Studio/Internal Testing and launch with no camera attached
- [ ] The screen says exactly what to connect; no paywall appears
- [ ] Plug in the endoscope; Android permission prompts are understandable
- [ ] Deny Camera permission once; recovery explains that phone cameras are not opened
- [ ] Deny USB permission once; Try again returns to the Android prompt
- [ ] Allow both; the app tests profiles without asking the user to choose MJPEG/YUYV
- [ ] Live view is declared successful only after a real frame appears
- [ ] Preview timer starts at first stable frame, not app launch
- [ ] Background the app for two minutes during the preview; no trial time is consumed while backgrounded
- [ ] A single or frozen frame never counts as compatible
- [ ] No purchase prompt appears for incompatible hardware

## Live inspection

- [ ] Feed remains stable for 15 minutes at the chosen profile
- [ ] Rotate cycles 0°, 90°, 180°, 270° without distortion
- [ ] Photo saves, opens, shares, and deletes
- [ ] Free test photo is limited to one
- [ ] Free recording stops at 10 seconds and plays with no audio track
- [ ] Unlocked recording starts/stops repeatedly and plays correctly
- [ ] Backgrounding stops the camera; foregrounding reconnects cleanly
- [ ] Screen remains awake only while the feed is live
- [ ] Unplugging while live produces a clear reconnect message
- [ ] Unplugging while recording closes the file without crashing
- [ ] Fill storage below 150 MB; recording is blocked with a useful recovery message
- [ ] Force-stop during a recording; no partial file appears in Gallery after relaunch
- [ ] Rapid unplug/replug five times produces no crash, duplicate preview, or stuck permission state
- [ ] Trigger capture, unplug at the same instant as its timeout, and confirm exactly one completion/result
- [ ] Start a second recording immediately after stopping the first; a late first callback cannot stop or orphan the second
- [ ] Tap retry repeatedly while profiles are opening; only the newest connection attempt can become live
- [ ] Repeat unplug/replug 100 times and record/stop 100 times for the release candidate

## Multiple cameras / dual-lens reality

- [ ] With one Android USB device, no Lens button is shown
- [ ] If two USB video devices are exposed, Lens switches one active device at a time
- [ ] If the scope uses a cable button, Help tells the user to use it
- [ ] App copy never promises control of proprietary LEDs, focus, or lens switching

## Compatibility failure

- [ ] A non-UVC USB accessory is ignored
- [ ] A deliberately unsupported camera ends in the three-profile failure message
- [ ] Compatibility report lists attempts and VID/PID, but no USB serial number
- [ ] Report shares through the Android chooser
- [ ] Remove every compatible share/viewer app; open/share failures remain recoverable
- [ ] Low-power failure advice recommends direct OTG first, then a powered hub

## Billing

- [ ] Debug unlock works only in debug builds
- [ ] Localized price comes from Play, not app text
- [ ] Successful purchase unlocks immediately and survives restart/offline use
- [ ] Cancel leaves the app locked without a scary error
- [ ] Pending purchase does not unlock until purchased
- [ ] Restore works after reinstall and on a second device using the same Play account
- [ ] Purchased item is acknowledged within three days
- [ ] Disconnect Play during purchase and restore; neither control remains stuck
- [ ] Alternate purchase, restore, and Play reconnect rapidly; an older query never replaces newer entitlement state
- [ ] Complete several eligible sessions rapidly; at most one native review flow is requested at a time

## Accessibility and visual QA

- [ ] Capture every customer-visible state as a screenshot on the target phone
- [ ] Repeat at maximum supported font and display scaling in portrait and landscape
- [ ] No text clips, overlaps, or hides an action; bottom controls remain at least 48 dp
- [ ] TalkBack announces live status changes, buttons, thumbnails, and destructive confirmation clearly
- [ ] Error, pending, restored, disabled, and recording states are distinguishable without color alone

## Rating protection release gate

Ship only when both physical cameras pass the clean-install flow, at least 95% of closed-test sessions reach a correct success/failure result, crash-free users exceed 99.5%, and every incompatible device can send a useful report without paying.

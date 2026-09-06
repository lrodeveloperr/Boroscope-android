# Customer longevity audit

This is an adversarial source-and-state audit of Borescope Direct: repeated use, denial, interruption, unplugging, bad storage, driver errors, purchase edge cases, clock changes, configuration changes, and a very large local gallery. It is not a substitute for the physical-camera and screenshot checks in `DEVICE_TEST_CHECKLIST.md`.

## Outcome

All reproducible flaws found in the code/state pass were patched. The remaining release gates require a real Android phone, one or two UVC endoscopes, Google Play test products, and visual inspection at multiple font/display sizes.

| Area | Flaw found | Customer failure | Patch |
|---|---|---|---|
| Trial | Wall-clock trial could expire while the app was backgrounded | User pays for time they did not use | Persist and consume only monotonic time while a stable live feed is visible |
| Trial | A negative or oversized elapsed value could corrupt entitlement time | Trial jumps or underflows | Clamp both stored time and consumption |
| Compatibility | One accidental frame counted as success | False promise followed by a paywall | Require six same-size frames over at least 400 ms |
| Compatibility | A stalled or changing stream could still pass | Frozen/unstable hardware looks compatible | Reset the stability probe on frame-size changes, long gaps, and clock reversal |
| USB | Manual enumeration accepted unrelated USB accessories | Wrong device prompts and confusing failures | Filter by USB video class at device or interface level |
| USB | A null USB control block left the user waiting | Permanent spinner/dead end | Show a recoverable connection error |
| USB | Attach/detach callbacks could mutate the device list during reporting | Rare concurrent-modification crash | Use a concurrent device map and deterministic device ordering |
| Permissions | Camera permission could be requested before an explanation | Low trust and denial | Show rationale first; prompt only after the user taps Allow access |
| Permissions | Denied USB access had no clear path back | User must force-close | Keep the device pending and expose Allow USB again |
| Permissions | Permanently denied Android permission looped | Repeated useless prompt | Route the next action to app settings |
| Driver | Synchronous AUSBC exceptions could crash the app | Crash during attach/open/capture/rotate/stop | Guard monitor, camera, preview, capture, rotation, and shutdown calls |
| Driver | A delayed retry could reopen a detached camera | Ghost preview or crash | Generation-token every camera lifecycle and cancel stale retries |
| Driver | A queued stable-frame result could survive navigation | False LIVE state after leaving the camera | Invalidate frame callbacks when preview detaches or unlock pause begins |
| Driver | Requested resolution remained visible after profile change | Misleading compatibility report | Clear observed size for each attempt and publish only frame-observed dimensions |
| Retry | Retry after USB-monitor startup failure did nothing | Error button leads to another dead end | Restart monitoring when Retry is pressed from a stopped state |
| Rotation | Rotation reset after camera reopen | Repeated manual correction | Apply current rotation to every new camera request |
| Capture | Rapid taps could overlap photo/video operations | Duplicates, corruption, stuck controls | One capture operation at a time with operation IDs and busy states |
| Capture | Camera callback could never arrive | Controls stuck forever | Add photo, recording-start, and recording-finalization timeouts |
| Capture | Capture APIs could throw before callbacks | App crash | Catch synchronous failures and complete the operation as failed |
| Photo | Empty or corrupt JPEG could be reported as saved | Broken item in Gallery | Validate JPEG dimensions before success |
| Video | Partial MP4 could appear in Gallery | Unplayable recording | Write to `.recording.mp4`, then expose only after finalization |
| Video | File-size alone treated video as valid | Corrupt MP4 reported as successful | Require readable positive duration metadata before publishing |
| Video | Failed copy could leave a visible partial final file | Gallery contains a broken video | Delete failed destination files |
| Video | Abandoned partials accumulated forever | Storage leak | Remove hidden partial recordings older than 24 hours, including large clock shifts |
| Storage | No preflight space check | Capture fails late and opaquely | Require headroom before photo/video and give a recovery message |
| Storage | External-storage fallback mixed picture/video paths | Duplicated gallery rows | Use distinct internal fallback folders and de-duplicate absolute paths |
| Gallery | File scanning blocked the UI | Jank with years of captures | Run enumeration and thumbnail decoding on an I/O dispatcher |
| Gallery | Empty/in-progress media was listed | Broken rows | Filter zero-byte and partial files |
| Gallery | Rows had no visual preview or open action | Hard to find a capture | Add bounded photo/video thumbnails and tap-to-open |
| Gallery | Machine filenames dominated the list | Poor long-term browsing | Show localized capture date/time, type, and size |
| Gallery | Missing files crashed or silently failed on open/share | Broken external handoff | Verify the file and catch FileProvider/intent failures with feedback |
| Gallery | Delete failure looked successful | User thinks storage was freed | Make delete idempotent and show a failure message |
| Lifecycle | System Back from Help/Gallery could exit the app | Lost context | Return to the camera first |
| Lifecycle | Rotation reset page/session state | Duplicate session counts and navigation loss | Save current page and session marker across configuration changes |
| Lifecycle | Camera remained active behind the paywall | Heat, power drain, USB instability | Pause the camera only after active capture finishes; resume after unlock |
| Lifecycle | Screen stayed awake beyond useful preview | Battery drain | Keep screen on only for a live, non-expired preview |
| Billing | Restore launched a new purchase flow | Trust-breaking purchase behavior | Separate query-only Restore purchase action |
| Billing | Multiple connection/purchase actions overlapped | Confusing Play dialogs and state races | Guard connection and mutually disable purchase/restore operations |
| Billing | Pending purchase could unlock prematurely | Incorrect entitlement | Unlock only `PURCHASED`; show pending status |
| Billing | Play disconnect left a permanent spinner | Paywall dead end | Clear in-progress state and provide retryable status |
| Billing | Product query could erase a restore result | User cannot tell restore worked | Preserve transaction status across concurrent product lookup |
| Billing | Success/pending text used error styling | Purchase looked broken | Use neutral status styling; reserve error color for failures |
| Reviews | Review prompt was eligible after a trivial connection | Annoying prompt and low ratings | Count a session only after 30 seconds of live use plus a successful capture |
| Reviews | Review request was marked before Play completed it | Lost future opportunity | Mark requested after the review flow finishes |
| Privacy | Diagnostic report used a hard-coded app version | Misleading support data | Read build version dynamically |
| Privacy | Report could imply collection of sensitive device data | Low trust | Include only model/API, VID/PID, profiles, and explicit exclusions |
| Safety | No misuse warning | Unsafe medical interpretation | Add clear general-inspection-only warning |
| Help | Raw report overwhelmed the normal help flow | Important guidance buried | Hide technical report behind an explicit toggle |
| Accessibility | Controls could crowd or clip with larger text | Unusable bottom bar | Give each control equal width, keep 52 dp targets, and provide descriptions |
| Layout | Unlock content could overflow a small landscape screen | Price/restore action becomes unreachable | Constrain the card and make its contents scrollable |

## Remaining physical release gates

1. Capture screenshots for every state on the target phone: no device, permission rationale, USB denial, each profile attempt, live, recording, expired/unlock, billing offline, empty/full gallery, Help, and every error.
2. Repeat those screens at 1.0× and maximum supported font/display scaling, portrait and landscape, checking clipping, contrast, focus order, and TalkBack announcements.
3. Run both sample cameras for 15 minutes, then repeat 100 unplug/replug cycles and 100 record/stop cycles.
4. Verify every saved JPEG and MP4 in at least two external Android viewers and share targets.
5. Exercise Play license-test cases: success, cancel, pending, already owned, offline, service disconnect, restore after reinstall, and refund/revocation.
6. Confirm the governing license for the bundled GoodUse Jetpack shell source and retain every required notice; no license file was available in this workspace.
7. Ship only after the quantitative gate in `DEVICE_TEST_CHECKLIST.md` is met. A static audit cannot establish camera-driver stability, device power behavior, codec output, Play behavior, or visual correctness.

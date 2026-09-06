# Adversarial code-integrity review

Scope: the complete Android application, its GoodUse shell boundary, persistence, UVC state machine, capture lifecycle, billing lifecycle, review flow, media repository, static configuration, and the browser-openable HTML counterpart. The approved UI and product rules were frozen.

## Defects found and patched

| Severity | Integrity defect | Failure mode | Patch |
|---|---|---|---|
| Critical | Preview callback was registered during camera construction and again on every profile open | Duplicate frames, false stability, callback leaks | Register at one controlled site and remove before adding |
| Critical | Photo/video completion used an unsynchronized Boolean | Timeout and driver callback could both finalize one operation | Atomic single-completion gate |
| Critical | A stale video callback could clear the finish handler belonging to a newer recording | New recording becomes impossible to finish cleanly | Bind cleanup to the matching operation ID |
| High | Older Play product and ownership callbacks could overwrite newer restore/purchase state | Wrong entitlement or misleading paywall status | Independent generation tokens for product and ownership queries |
| High | USB monitor registration could partially succeed and then fail during enumeration | Registered callback survives a failed start | Unregister during failed startup and make shutdown unconditional |
| High | Camera request construction was outside the driver exception boundary | Malformed/unsupported profile could crash | Guard request creation and continue the safe-profile fallback |
| High | Photo/video completion could race across threads | Duplicate state changes, duplicate review trigger, file race | Idempotent atomic completion and generation checks |
| High | Review requests had no thread-safe in-flight guard | Two native review flows could be requested | Atomic single-flight flag plus RESUMED lifecycle requirement |
| Medium | Review manager/task construction could throw | Capture succeeds, then app crashes while asking for a review | Guard both boundaries and silently retain future eligibility |
| Medium | Same-millisecond or clock-adjusted captures could reuse a filename | Existing media overwritten or capture fails | Synchronized unique-name allocation with suffix fallback |
| Medium | JPEG bounds decoding was not exception-safe | Malformed file crashes completion callback | Fail-closed decoder validation |
| Medium | Thumbnail target accepted zero/negative values | Non-terminating sampling or division failure | Clamp to at least one pixel |
| Medium | Storage availability calls were not exception-safe | Mounted-storage edge case crashes capture | Guard directory and space queries; fail closed |
| Medium | Stable-frame probe ignored a midstream data-format change | Mixed stream could pass compatibility | Require consistent dimensions and data format |
| Medium | Stability-probe constructor accepted impossible thresholds | Tests or future configuration silently misbehave | Fail-fast parameter invariants |
| Low | Session counter accepted corrupt negative state and could overflow | Review eligibility becomes nonsensical after damaged preferences | Clamp reads to zero and saturate increments |
| Low | Settings/permission intents were not guarded | Vendor ROM without a handler crashes recovery | Catch activity-result and Settings failures |
| Low | Gallery thumbnail used a force unwrap | Unnecessary crash surface during recomposition | Snapshot nullable bitmap without force unwrap |
| High | Expired entitlement UI was keyed only to elapsed time | An incompatible or disconnected camera could be covered by the success/unlock card | Require the confirmed `PAUSED_FOR_UNLOCK` camera state before showing purchase controls |

## Invariants now enforced

1. Only a current camera generation can publish LIVE or mutate current capture UI state.
2. Each capture completes at most once, regardless of callback order.
3. A recording callback can clear only its own finish handler.
4. Only the newest Play query may update product or ownership state.
5. A file is visible in Gallery only after structural validation.
6. The free timer consumes only bounded foreground LIVE time.
7. Restart, retry, detach, background, and destroy paths clear scheduled work and camera resources.
8. The HTML preview treats browser storage as untrusted and restores only validated fields.

## Verification boundary

`scripts/verify_project.sh` and `scripts/adversarial_review.mjs` pass in this workspace. The Android SDK, Gradle runtime, ADB, Google Play test environment, and physical USB cameras are unavailable here, so compilation, native codec output, USB power behavior, and device screenshots remain release gates rather than claimed results.

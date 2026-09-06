# Product workflow

```mermaid
flowchart TD
    A[Open app] --> B{USB video device found?}
    B -- No --> C[Show three-step connection help]
    B -- Yes --> D[Explain and request Android Camera permission]
    D --> E[Request USB-device permission]
    E --> F[Try 720p MJPEG]
    F --> G{Stable frames within 8s?}
    G -- No --> H[Try 480p MJPEG, then YUYV]
    H --> I{Stable frames?}
    I -- No --> J[Honest incompatible result and report]
    G -- Yes --> K[Live inspection]
    I -- Yes --> K
    K --> L{Already unlocked?}
    L -- Yes --> M[Unlimited local photo and video]
    L -- No --> N[90s preview, one photo, 10s recording]
    N --> O[One-time Play unlock]
```

## State-to-screen contract

| State | What the user sees | Primary action | Never do |
|---|---|---|---|
| No device | Cable-first connection instructions | Connect camera | Show payment |
| Camera permission denied | Why Android requires it; phone cameras are not opened | Allow / open Settings | Claim USB works without permission |
| USB permission denied | Android owns this prompt | Try again | Invent a technical failure |
| Testing | Current safe profile and attempt number | Wait | Make the user pick codecs |
| Stable frame | Full-screen feed and core controls | Inspect/capture | Interrupt before proving compatibility |
| Trial expired | “It works on this phone” and localized one-time price | Unlock / restore | Subscription, countdown pressure, fake discount |
| Incompatible | Profiles tried, power/OTG advice, sanitized report | Retry / share report | Charge the user |
| Unplugged | Direct reconnect message | Reconnect | Crash or leave a frozen “live” frame |

## Supported camera count

The engine can enumerate multiple Android USB video devices, but v1 opens exactly one at a time for stability, power, and thermal control. The UI offers Lens only when more than one device is genuinely exposed. A dual-lens endoscope that switches internally through a proprietary cable command still appears as one device and must use its physical cable button.

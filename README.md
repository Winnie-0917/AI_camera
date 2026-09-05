# AI_camera

A professional manual camera app for Android, built directly on **Camera2** (not CameraX — full
manual control needs raw `CaptureRequest` access). Includes a Gemini-powered AI photography
assistant that can see your current camera settings.

## Features

**Manual controls** — each one is hidden automatically if the camera does not actually support it:

| Control | Notes |
|---|---|
| ISO | Requires `MANUAL_SENSOR`. Linked with shutter: Camera2 needs both once AE is off |
| Shutter speed | Full sensor range, e.g. 1/10000 – 1.0" |
| Exposure compensation | Auto-exposure mode only |
| White balance | 7 presets + manual Kelvin (2000–10000K) |
| Focus | Autofocus, tap-to-focus, or manual distance (∞ ↔ macro) |
| Zoom | Digital zoom via sensor crop region |
| JPEG quality | 50–100 |
| Flash | Off / auto / on / torch |
| Aspect ratio | Full / 4:3 / 16:9 / 1:1 |

**Other:** RAW (DNG) + JPEG in one capture, live luminance histogram, rule-of-thirds grid,
level indicator, self-timer, EXIF metadata, front/back camera.

**Languages:** English, 繁體中文, 日本語 (Settings → Language, or follow the system).

## Setup

### 1. API key (`.env`)

The AI assistant needs a Gemini API key. Get one free at
**https://aistudio.google.com/apikey**.

Copy the template and fill in your key:

```bash
cp .env.example .env
```

Then edit `.env` in the **project root** (same folder as `settings.gradle.kts`):

```properties
GEMINI_API_KEY=AIza...your_key_here
GEMINI_MODEL=gemini-2.5-flash
```

**Rebuild after changing `.env`** — the key is read at build time and compiled into
`BuildConfig`, so a running build will not pick up edits.

> ⚠️ Use `clean` when you change the key. `BuildConfig.GEMINI_API_KEY` is a `static final String`,
> i.e. a compile-time constant that the compiler **inlines into the calling code**. An incremental
> build regenerates `BuildConfig` but does not necessarily recompile the classes that read it, so
> the app keeps using the old value and still reports "no API key configured".

```bash
./gradlew clean assembleDebug
```

Without a key the app still builds and runs — every other feature works, and the assistant simply
tells you the key is missing.

`GEMINI_MODEL` is optional (defaults to `gemini-2.5-flash`). Any model your key can access works,
e.g. `gemini-2.5-pro` for stronger reasoning or `gemini-2.0-flash`.

`.env` is **git-ignored** and must never be committed. `.env.example` is the committed template.
A `GEMINI_API_KEY` environment variable is used as a fallback if `.env` is absent, which is
convenient for CI.

> **Security note.** Anything compiled into an APK can be extracted by decompiling it, so this
> setup is fine for development and personal builds but is *not* safe for a public release. For a
> shipped app, put the key on a backend you control and have the app call that instead.

### 2. Build

Requires **JDK 21** — the JBR bundled with Android Studio. Gradle 8.10.2 cannot parse Java 25 and
will fail with `IllegalArgumentException: 25` during Kotlin DSL compilation.

In Android Studio: *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK →
jbr-21*.

From the command line:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew installDebug
```

## Using the AI assistant

Tap the yellow ✨ button at the top right of the viewfinder.

Every question is sent with a snapshot of the current camera state — exposure mode, ISO, shutter,
white balance, focus, zoom, flash, and what the lens is capable of — so you can ask things like:

- 「現在這個光線該用什麼設定？」
- "Why is my shot blurry at these settings?"
- "What shutter speed freezes a running dog?"

The assistant is **Mochi**, the puppy from the app icon — a shooting buddy rather than a manual.
The persona is deliberately confined to tone: the system prompt states that technique outweighs
friendliness, and carries explicit exposure trade-offs (blur comes from a slow shutter, so raising
exposure compensation must never be offered as a cure for it). Suggestion-card labels stay
descriptive, since those are controls the user taps.

It replies in whatever language you ask in.

### One-tap styles

Ask for a *look* rather than a fact — "make it moody and cinematic", 「我想要暖色調」, "make this
look better" — and the reply comes with a **suggestion card** listing concrete parameters and an
**Apply** button that sets them on the camera.

The parameters arrive as structured JSON (Gemini `responseSchema`), not parsed out of prose, so
Apply never depends on the model's formatting. Before anything reaches the camera it is checked
against that lens's real capabilities and clamped to its ranges — asking for manual white balance
on a camera without `MANUAL_POST_PROCESSING` drops that one field and says so, rather than sending
an unsupported request.

### Live angle guide

**Long-press** the ✨ button to toggle it. The app samples the viewfinder, sends the frame to the
model, and shows one physical correction — `→ move right`, `↓ tilt down`, `✓ perfect` — updating
on a timer until you switch it off.

The model is asked only to *describe* the image — "subject sits left of centre", "horizon is
high" — and the app derives the instruction from that. Asking the model for the correction
directly produced reversed advice, because with a subject on the left it reasons about pushing the
subject rightwards rather than about moving the camera. Two rules live in `AngleGuidance`:

- You centre a subject by turning the camera **towards** it. Panning left brings content at the
  left edge in towards the middle.
- The front lens faces the photographer, so its left is their right: left/right and the sense of
  rotation are mirrored for the front camera, while up/down and closer/farther are not.

Each call carries the previous two checks (a 3-scan sliding window, model-only — the viewfinder
shows just the latest). Judging every frame independently made it oscillate: a subject near the
middle reads as slightly left on one check and slightly right on the next, so it sent the user
back and forth. The prompt also judges generously — near the middle third counts as centred, a few
degrees off counts as level — because a bar that is never reached means the guide never stops
correcting.

Cadence: **5s** while the framing needs work, **8s** once it reports perfect, since there is
nothing to correct while the shot is already good. Failures back off exponentially (15s, 30s, …
capped at 2 min) so a rate-limited or offline API is not polled on the normal cadence.

> ⚠️ This is by far the most quota-hungry feature: a 5s cadence is ~12 requests per minute, which
> exhausts a free-tier key quickly. Expect `You exceeded your current quota` (HTTP 429) if you
> leave it running. Frames are downscaled to 640px and sent at JPEG quality 80 to keep each call
> small.

`thinkingBudget` is set to 0 for `flash` models: 2.5 models think before answering by default,
which together with JSON mode pushed responses past a 60s timeout. Pro models require a budget of
at least 128, so they are left alone.

## Project layout

```
app/src/main/java/com/example/ai_camera/
├── camera/         Camera2 layer
│   ├── CameraController.kt   Session, capture requests, JPEG/DNG capture
│   ├── CameraSpecs.kt        Per-camera capabilities + adjustability checks
│   ├── CaptureSettings.kt    All adjustable parameters (immutable state)
│   ├── WhiteBalance.kt       Kelvin → RGGB gains
│   ├── ImageSaver.kt         MediaStore + EXIF
│   └── ImageProcessing.kt    Aspect-ratio cropping
├── ai/             Gemini assistant (client + chat UI)
├── settings/       Per-app language switching
└── ui/             Compose viewfinder, controls, overlays
```

### A note on manual white balance

`COLOR_CORRECTION_GAINS` operates in raw sensor space, where green dominates a Bayer array, so
gains derived purely from a black-body model render with a heavy green cast. Instead the Kelvin
slider shifts *relative to the gains the device's own AWB produced*, which are already calibrated
for that sensor. At 5500K the result matches auto white balance; the slider warms or cools from
there. The trade-off: the number tracks true colour temperature closely in daylight but drifts
under strongly non-daylight illuminants.

## Requirements

- Android 7.0 (API 24) or newer
- Manual controls need a camera reporting `MANUAL_SENSOR`; RAW needs the `RAW` capability. The app
  detects both and hides what is unavailable.

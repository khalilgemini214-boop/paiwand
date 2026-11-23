# Paiwand

A small, quiet translator. Floating bubble reads what is on your screen, sends the text to a
translation model, and shows the translation in a card. Plus a normal type-and-translate screen.

Built with Kotlin + Jetpack Compose (Material 3). No ads, no accounts, no tracking.

---

## What is in the box

| Piece | Where |
|---|---|
| Design system (colour, type, shape, motion) | `ui/theme/Theme.kt` |
| Translate screen | `ui/screens/TranslateScreen.kt` |
| Language picker | `ui/screens/LanguageSheet.kt` |
| Settings (API key, bubble, updates) | `ui/screens/SettingsScreen.kt` |
| Update sheet | `ui/screens/UpdateSheet.kt` |
| DeepSeek-compatible client | `translate/TranslationEngine.kt` |
| Self-updater (check → download → install) | `update/UpdateManager.kt` |
| Screen capture (MediaProjection) | `overlay/ScreenCapture.kt` |
| Floating bubble + foreground service | `overlay/BubbleService.kt` |
| Translation card over other apps | `overlay/TranslatePopupActivity.kt` |
| CI: build APK + release + `update.json` | `.github/workflows/build.yml` |

---

## 1. Put it on GitHub

```bash
git init
git add .
git commit -m "first build"
git remote add origin https://github.com/YOUR_NAME/paiwand.git
git push -u origin main
```

The first push triggers **Actions → Build APK**. It takes about 8 minutes the first time.

When it finishes:

- **Actions run → Artifacts → `paiwand-apk`** — that is your APK (GitHub zips it; unzip it).
- **Releases** — every build also publishes a release with `paiwand.apk` and `update.json`.

Install the APK on your phone. Enable "install unknown apps" for your browser or file manager once.

---

## 2. Signing (do this once, or updates break)

Android will only update an app in place if the new APK is signed with the **same key**. The CI
runner's debug key is thrown away after every build, so without a stable key the app cannot
update itself and Paiwand will fall back to "Download APK".

Fix it once:

1. **Actions → Make signing key → Run workflow**
2. Download the `paiwand-keystore` artifact, open `keystore.b64`, copy the whole text.
3. Repo **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | `paiwand-dev-2026` |
| `KEY_ALIAS` | `paiwand` |
| `KEY_PASSWORD` | `paiwand-dev-2026` |

Change those passwords if you like — just keep them consistent.

---

## 3. Give the app an API key

Open Paiwand → Settings → Translation engine:

**Option A — Groq (fast, free tier, reliable):**

- **API key** — `gsk_…` from console.groq.com
- **API base URL** — `https://api.groq.com/openai/v1`
- **Model** — `openai/gpt-oss-120b`

**Option B — Google Gemini (free tier):**

- **API base URL** — `https://generativelanguage.googleapis.com/v1beta/openai`
- **Model** — `gemini-3.5-flash-lite` (note: `gemini-3.5-flash` frequently returns
  503 "currently experiencing high demand"; `-lite` has been serving reliably)

**Option C — DeepSeek (paid, best Urdu quality):**

- **API base URL** — `https://api.deepseek.com/v1`
- **Model** — `deepseek-chat`

Then type something and hit Translate. **Test connection** verifies key + URL + model with a single
word and shows the exact error if something is wrong.

> **Not interchangeable:** an OpenCode / OpenCode Go plan key is a credential for the OpenCode
> gateway (`opencode-go/deepseek-v4.1-flash`). It is not a DeepSeek platform key and will be
> rejected by `api.deepseek.com`. Use a key from platform.deepseek.com, or point the base URL at a
> gateway you are allowed to use from an app.

> **Before sharing the app publicly** move the key to a small proxy (Cloudflare Worker, Supabase
> edge function). A key inside an APK can be extracted in seconds. The client already supports
> any base URL, so swapping it later is a one-line change.

---

## 4. How the in-app update works

On start the app fetches:

```
https://github.com/OWNER/REPO/releases/latest/download/update.json
```

```json
{
  "versionCode": 137,
  "versionName": "1.0.37",
  "apkUrl": "https://github.com/OWNER/REPO/releases/latest/download/paiwand.apk",
  "releasePage": "https://github.com/OWNER/REPO/releases/latest",
  "sizeBytes": 8421376,
  "sha256": "…",
  "mandatory": false,
  "requiresReinstall": false,
  "changelog": ["Fixed the bubble on Android 14"]
}
```

If `versionCode` is higher than the installed build, a sheet appears:

1. **Update now** → downloads the APK with a progress bar → hands it to Android's installer.
2. The system shows one confirmation. Tap **Update**. The app updates in place; history, settings
   and the API key are kept.
3. If the signature does not match (you changed the keystore), Android refuses and Paiwand switches
   to **Download APK**, which opens the release page in the browser.
4. Set `"requiresReinstall": true` in `update.json` to skip step 1 and always show the download button.

**Honest limit:** no app can silently install itself on stock Android. One tap on the system dialog
is the floor. Everything else — version check, download, install flow, fallback — is automatic.

---

## 5. The floating bubble

Settings → Magic translate → Floating bubble. Android asks twice:

1. **Display over other apps** — needed to draw the bubble.
2. **Start recording or casting** — this is MediaProjection. It is how the app sees the screen.

Then a green bubble sits on the edge. Drag it anywhere. Tap it:

- frame is captured, ML Kit reads the text on-device,
- text goes to the model,
- card slides up with the translation.

**Known limitation:** ML Kit's on-device OCR has no Urdu/Arabic-script model. It reads Latin,
Devanagari, Chinese, Japanese and Korean well; Urdu and Pashto come out empty or garbled. The card
says so and points you to the main screen. Options later: a cloud OCR call, or a vision-capable
model.

---

## 6. Build locally (optional)

You do not need this — CI builds for you. If you want it anyway:

```bash
# Gradle 8.9, JDK 17, Android SDK 35
gradle assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

---

## Roadmap

- [ ] Proxy server so the key never ships in the APK
- [ ] Streaming translations (first words in <1s)
- [ ] Urdu/Arabic OCR
- [ ] Read clipboard button in the bubble card
- [ ] Offline phrasebook for the 50 most common sentences
- [ ] Talk mode (speech in, speech out)

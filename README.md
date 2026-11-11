# Smart Organizer

Android app that watches one inbox folder, uses **ML Kit OCR** plus a selected on-device model to decide what a file *means*, then files it.

A resume PDF goes to `Resume/`. A boarding-pass PDF or a ticket photo both go to `Ticket/`. Folders are created under the inbox if they are missing. Sorted files are stored in a local Room database.

Pick **Gemma 4 E2B** (2.6 GB, can look at images), **Gemma 3 1B** (~557 MB, text after OCR), or **Qwen 3 0.6B** (~585 MB, text after OCR). Download the selected model once; after that, sorting stays offline.

## What it does

1. Choose a model in the app and **Download once**, or import a `.litertlm` file you already have.
2. Pick an inbox folder with the system folder picker.
3. Start watching. A foreground service scans that folder every few seconds.
4. ML Kit reads text from images and PDFs. The selected model returns a category. Photos with no text fall back to `Photo` / `Screenshot` when using a text-only model.
5. The app creates that folder inside the inbox and moves the file.
6. History is kept on-device.

Categories: `Resume`, `Ticket`, `Bill`, `ID`, `Notes`, `Screenshot`, `Photo`, `Other`, plus `Review` when confidence is low.

## Requirements

- Android 8 (API 26) or later. About 6 GB RAM is realistic for Gemma 4 E2B; the 1B / 0.6B models fit mid-range phones.
- First-time internet only, for the selected model download (and the first ML Kit OCR pack if needed).
- If Hugging Face returns 401/403, accept the model license and paste an access token in the app.

## Open in Android Studio

Open this folder as a Gradle project. The Android SDK path is in `local.properties`.

You can also build from the command line:

```bash
./gradlew :app:assembleDebug
```

Install the debug APK and grant notification permission when asked, so the watch service can stay alive.

## Using the app

1. Select a model, then tap **Download once** (or **Import file**).
2. Tap **Choose folder** and pick the parent inbox.
3. Tap **Start watching**.
4. Drop files into that inbox (not into the category subfolders). Wait a few seconds.

Do not pack model weights inside the APK. The app stores them under its private `files/models/` directory and reuses them on later launches. Stop watching before switching models.

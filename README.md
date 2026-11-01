# Smart Organizer

Android app that watches one inbox folder, uses **Gemma 4 E2B** on the phone to decide what a file *means*, then files it.

A resume PDF goes to `Resume/`. A boarding-pass PDF or a ticket photo both go to `Ticket/`. Folders are created under the inbox if they are missing. Sorted files are stored in a local Room database.

After Gemma 4 is downloaded once, classification is offline.

## What it does

1. Download Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, 2.6 GB) into app storage, or import a copy you already have.
2. Pick an inbox folder with the system folder picker.
3. Start watching. A foreground service scans that folder every few seconds.
4. New files (images, PDFs, text) are shown to Gemma 4. It returns one category.
5. The app creates that folder inside the inbox and moves the file.
6. History is kept on-device.

Categories: `Resume`, `Ticket`, `Bill`, `ID`, `Notes`, `Screenshot`, `Photo`, `Other`, plus `Review` when confidence is low.

## Requirements

- Android 8 (API 26) or later. A phone with about 6 GB RAM is realistic for Gemma 4 E2B.
- First-time internet only, for the model download.
- If Hugging Face returns 401/403, accept the Gemma license on [the model page](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) and paste an access token in the app.

## Open in Android Studio

Open this folder as a Gradle project. The Android SDK path is in `local.properties`.

You can also build from the command line:

```bash
./gradlew :app:assembleDebug
```

Install the debug APK and grant notification permission when asked, so the watch service can stay alive.

## Using the app

1. Tap **Download once** (or **Import file** if you already pushed `gemma-4-E2B-it.litertlm` to the phone).
2. Tap **Choose folder** and pick the parent inbox.
3. Tap **Start watching**.
4. Drop files into that inbox (not into the category subfolders). Wait a few seconds.

Do not put the 2.6 GB model inside the APK. The app stores it under its private `files/models/` directory and reuses it on later launches.

# Quake Alert Android app

This is a self-contained native Kotlin client for the Python earthquake alert
server. It polls `GET /api/state` once per second while the app is open. Set
the server address in **Server settings**, for example:

`https://192.168.1.20` (the Python server defaults to HTTPS port 443)

The address must include the scheme and (when non-standard) port. The device
must be able to reach the computer running the server. For HTTPS deployments,
use a certificate trusted by Android (or use the server's HTTP listener on a
trusted local network); the app does not disable TLS certificate validation.

## Build and install

The repository workflow (`.github/workflows/android.yml`) installs Java 17 and
Gradle on GitHub Actions and produces `app-debug.apk` as an artifact. No
Gradle wrapper is included. Locally, with Android SDK and Gradle installed:

```text
cd android
gradle assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The app requests notification and camera permissions on first launch. From
**Server settings**, use **Update warning sound from server** to download
`/warning.wav` into private app storage without reinstalling the APK. A major
alert loops the downloaded sound (falling back to `res/raw/warning.wav`),
vibrates until dismissed, enables the Camera2 torch when permitted, and posts a
high-priority notification. The alert displays quake and user latitude/longitude using native
Android views (no map SDK or external dependency). The main screen displays
the server location and scheduled-test banner only when the server's
`schedule.warn` flag is true. Dark/light preference is saved in Settings and
takes effect on the next app creation.

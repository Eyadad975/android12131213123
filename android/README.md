# Quake Alert Android app

This is a self-contained native Kotlin client for the Python earthquake alert
server. A foreground service polls `GET /api/state` once per second in the
background. Set
the server address in **Server settings**, for example:

`https://192.168.1.20:443` (the Python server defaults to HTTPS port 443)

The address must include the scheme and (when non-standard) port. The device
must be able to reach the computer running the server. For a configured private/local HTTPS server, the Android client uses an
app-scoped TLS socket factory that accepts that server's self-signed
certificate. This does not change Android's global trust store or affect any
other app traffic, but it means the configured local endpoint must be kept
private and the URL/host should be verified. Public/non-local HTTPS endpoints
continue to use normal Android certificate validation.

## Build and install

The repository workflow installs Java 17 and
Gradle on GitHub Actions and produces a debug APK artifact. No
Gradle wrapper is included. Locally, with Android SDK and Gradle installed:

```text
cd android
gradle assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The app requests notification and camera permissions on first launch. The main
screen uses the web monitor's dark HUD styling and map/radar presentation.
Minor earthquakes are delivered as ordinary notifications without opening the
warning screen. Minor earthquakes use a non-clickable notification and play the
currently downloaded warning sound once. Major earthquakes use a high-priority notification with a
full-screen intent, so the red warning can appear over the lock screen or
another app (subject to the device's notification and full-screen policies).
From
**Server settings**, enter the server URL and use **Update warning sound from server** to download
`/warning.wav` into private app storage without reinstalling the APK. Minor
alerts also use that downloaded file. A major
alert loops the downloaded sound (falling back to `res/raw/warning.wav`),
vibrates until dismissed, repeatedly flashes the Camera2 torch when permitted,
and posts a high-priority notification. The alert displays a full-screen
countdown, quake and user latitude/longitude
using native Android views (no map SDK or external dependency). Scheduling
controls are intentionally available only in the web control panel; Android
only monitors the server and displays live alerts.

# GoWithLine

Route simulation for Android — draw a route, set a speed, and let the mock location follow it. No root, no account, no ads, open source. [中文](README.md)

[![release](https://img.shields.io/badge/release-1.0-blue)](https://github.com/99galaxy/GoWithLine/releases/latest)
[![license](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84)

![GoWithLine demo](docs/screenshots/demo.gif)

**[Download the latest APK](https://github.com/99galaxy/GoWithLine/releases/latest)** · ~5 MB · Android 8.0+ · SHA-256 on the release page

| Good for | Not for |
|---|---|
| Development, teaching demos, and testing map/tracking/location-aware apps | Attendance fraud, campus running/PE tests, game location spoofing, or bypassing detection |

The app injects locations through Android's debugging API. Some apps detect mock locations (`isFromMockProvider`, FusedLocation, vendor SDKs); there is no way around that without root, and bypassing detection is not a goal of this project.

<img src="docs/screenshots/main.png" width="240" alt="Freehand route"> <img src="docs/screenshots/joystick.png" width="240" alt="Joystick mode"> <img src="docs/screenshots/selfcheck.png" width="240" alt="Self-check">

## Features

- **Freehand routes:** press and drag with one finger; multiple segments connect automatically; undo and clear are supported. **Two-finger zoom/pan still works while drawing**; release and continue with one finger.
- **Constant speed:** enter km/h directly; built-in walk/run/bike/drive presets; change speed while running.
- **Smooth movement:** distance advances by real time, with interpolated latitude/longitude and bearing along the polyline; injects into GPS, NETWORK and FUSED test providers at once (the working combination varies by device).
- **Pause / resume / stop:** the last position keeps being published while paused or finished to avoid location snap-back; stopping removes the test providers and restores the real location.
- **Loop, map follow, foreground notification (pause/resume/stop), WakeLock.**
- **Floating speed window:** shows the current mock speed over other apps; draggable, `−/+` fine-tuning, 5/10/20/40 km/h presets, and a close button to hide it.
- **Joystick mode:** a joystick appears in the floating window; it controls direction only, at the configured speed. Drag to move, release to stop. Useful when drawing a route in advance is inconvenient.
- **Joystick lock:** tap the joystick head to toggle lock/unlock; when locked, releasing your finger keeps moving at the set speed, and tapping the head again stops it.
- **The joystick window has no panel:** just a semi-transparent joystick and `−/+` speed buttons; speed, unit and lock state are drawn on the joystick head.
- **Settings page:** simulation mode, floating window, map layer, self-check and about in one place.
- **About:** current version, developer GitHub, project URL, and update check (GitHub Releases).
- **Automatic state saving:** route, speed, loop, layer, simulation mode and map center.
- **Map layers:**
  - OpenStreetMap (WGS-84, built in; HOT tile mirror served by OpenStreetMap France)
  - Custom XYZ tile template (WGS-84 / GCJ-02; use a service you are authorized to use)

> Only OpenStreetMap is built in (HOT tile mirror; data © OpenStreetMap contributors, ODbL). For other basemaps, use a custom XYZ source you are authorized to use. Built-in tiles may fail due to network or service policy.

## Usage

1. Install the APK, open the app, tick and accept the usage statement (not for attendance, campus running/PE tests, or game location), and grant location and notification permissions.
2. Enable Developer options:
   - Settings → About phone → tap “Build number” 7 times;
   - Settings → Developer options → **Select mock location app** → choose “GoWithLine”.
3. Route mode: tap “Draw route” and press-drag with one finger. You can pinch to zoom or pan while drawing; release and continue with one finger. Draw multiple segments, then tap “Finish drawing” to leave drawing mode.
4. Enter a speed (km/h), optionally enable loop, and tap “Start”.
5. During simulation you can pause, resume or stop; the notification also controls it. Stopping restores the real location.
6. Joystick mode: gear icon → Settings → Simulation mode → **Joystick mode** (requires overlay permission). Back on the main screen, tap “Start”; the floating window shows a semi-transparent joystick and `−/+` speed buttons. Drag to steer at the configured speed, release to stop. No route is needed.
   - The joystick head shows the current speed (km/h) and a faint “lock” hint; tap the head to toggle lock/unlock.
   - When locked, releasing keeps moving in the current direction and the joystick stays deflected; tap the head again to stop.
   - While locked, drag the joystick to change direction: it follows your finger smoothly from its current position instead of jumping to the touch point; release and it settles on the new direction and keeps moving.
   - `−/+` adjusts by 1 km/h; the small bar at the top of the floating window drags the whole control.
7. Floating speed window: Settings → Floating speed window (the first time opens the system “Display over other apps” page). In route mode it shows the speed and presets while running; in joystick mode it is forced on and shows the joystick.
   - Reinstalling may reset the overlay permission; the toggle will prompt you to grant it again.
8. Map layer: Settings → Map layer → OpenStreetMap / Custom XYZ; returning to the map applies it and converts the existing route.
9. Self-check: Settings → Self-check: developer options, mock location app, location service, provider injection and system read-back.
10. About: Settings → About: current version (1.0), developer GitHub, project URL and update check.
    - The update check calls GitHub Releases; network differences may make it fail, and the release page is offered on failure.

If the app says permission is missing on first launch, follow the in-app dialog to open Developer settings.

## How it works

### 1. No-root mock location

`MockLocationService` is a foreground service (`foregroundServiceType="location"`). On start it:

1. registers test providers with `LocationManager.addTestProvider(GPS / NETWORK / FUSED, ...)`;
2. calls `setTestProviderLocation()` every 200 ms with the current mock position;
3. fills `Location` with latitude/longitude, altitude, accuracy, bearing, speed, time and `elapsedRealtimeNanos`; GPS also gets `satellites` extras;
4. on stop, calls `setTestProviderEnabled(false)` + `removeTestProvider()`.

This is the same approach as GoGoGo's `ServiceGo`; API 31+ uses `ProviderProperties` power/accuracy constants, older versions use `Criteria`.

### 2. Coordinate systems

- Drawn points, the route, markers and the map center all use the current layer's coordinate system.
- The built-in OpenStreetMap layer is WGS-84; a custom GCJ-02 layer needs conversion.
- Conversion happens only at the system boundary: a GCJ-02 route is converted to WGS-84 when starting the simulation, and the WGS-84 mock position is converted back to GCJ-02 when displayed.
- Switching between layers with different coordinate systems converts the existing route and map center to avoid an offset.
- This is the same class of problem as GoGoGo converting Baidu BD-09 coordinates to WGS-84 before feeding them to the location API.

### 3. Route progression

The geometry layer is plain Java and does not depend on Android:

- `GeoMath`: Haversine distance and bearing;
- `RoutePath`: cumulative distance, interpolation by traveled distance, and bearing from a few metres ahead/behind;
- `RouteSimulator`: `traveled += speed × Δt`, with pause, finish and loop modulo;
- `RouteSimplifier`: Douglas-Peucker simplification of freehand points.

The service uses `SystemClock.elapsedRealtime()` for the real Δt, so Handler delay does not accumulate into a speed error.

### 4. Joystick mode

- `simulation_mode` is stored in preferences and passed to the service Intent by the main screen.
- Joystick mode does not build a route; the service starts at the map center (WGS-84) and advances by `speed × Δt` along the joystick direction using `GeoMath.destinationPoint()`.
- The joystick reports only direction and active state; speed is fixed at the UI/floating-window km/h. Releasing or pausing stops movement.
- The joystick is drawn inside the existing `TYPE_APPLICATION_OVERLAY` window, so it keeps working over other apps.
- The lock state is maintained by the joystick view: tap the head to toggle; unlocking resets to center and stops; when locked, releasing keeps the deflection and continues reporting active, so the service keeps moving at the configured speed.

## FAQ

### GoWithLine is missing from the mock location app list

The “Select mock location app” list only includes apps that declare `android.permission.ACCESS_MOCK_LOCATION`. This project declares it (it is not granted; it only makes the app appear in the list). Confirm you installed the latest build:

- Download the latest `GoWithLine-1.0.apk` from [Releases](https://github.com/99galaxy/GoWithLine/releases/latest);
- Some ROMs cache the list; reopen Settings or reboot;
- If it still does not appear, grant it via ADB:
  `adb shell appops set com.gowithline.mock android:mock_location allow`
  (install the app and grant location permission first).

### Simulation runs, but the map or another app does not move

Open “Help → Self-check” in the app and tap “Copy”. The report shows:

- developer options and whether the mock location app is selected;
- the system location service, Wi-Fi and precise-location permission;
- whether the GPS / NETWORK / FUSED test providers were added;
- the injection count;
- **system read-back:** the service registers a location listener; if the system really accepted our test position, this shows “success N times”. If read-back stays 0, the system did not apply the injection — the problem is permissions/ROM, not the UI.

Common causes:

1. **“GoWithLine” is not selected in Developer options:** self-check shows “not selected”. Select it again.
2. **System location is off:** self-check shows “off”. Turn location on and retry.
3. **Approximate location only:** self-check shows “precise location not granted”. Change it to precise in app settings.
4. **Wi-Fi is on:** some devices are overridden by Wi-Fi/fused location and snap back. Turn Wi-Fi off (or disable Wi-Fi scanning) and retry.
5. **Provider add failed:** self-check includes the exception. Common on vendor ROMs that restrict `addTestProvider`.
6. **Read-back succeeds but a specific app still does not move:** the app may use Google Play Services' FusedLocationProvider, its own location SDK, or actively check `isFromMockProvider()`. Such apps generally cannot be mocked without root.

Further ADB checks:

```bash
adb shell appops get com.gowithline.mock android:mock_location
adb logcat -d -s GoWithLineService | tail -100
adb shell dumpsys location | grep -A4 -i "last location"
```

If GPS/NETWORK succeed and the injection count grows but system read-back stays 0, share the self-check text and `adb logcat -s GoWithLineService` output; that distinguishes a ROM permission block from rejected provider parameters.

### How do I zoom or pan while drawing?

In drawing mode:

- **One finger:** draws;
- **Two fingers:** zoom and pan. When a two-finger gesture starts, the in-progress stroke is discarded to avoid accidental lines; release both fingers and draw again with one finger.

### The map is blank / tiles fail to load

Only OpenStreetMap is built in, and tiles may be affected by network, region or service policy. If they fail, open Settings → Map layer and enter your own XYZ tile template (a service you are authorized to use). The project does not bundle offline maps or redistribute tile data.

## Changelog

### 1.0

First stable release.

- Freehand multi-segment routes, with two-finger zoom/pan while drawing; constant speed in km/h along the polyline.
- Injection into GPS / NETWORK / FUSED test providers; pause/resume/stop/loop.
- Joystick mode with direction lock; floating speed window; OpenStreetMap + custom XYZ layers.
- Built-in self-check; test providers are removed and the real location restored on stop.
- GPL-3.0, no account, no ads, no self-hosted backend, Android 8.0+.

## A personal note

Dragging a joystick by hand gets old, doesn't it? .jpg

I used to do mock movement with Yingsuo, but hand-dragging the joystick is awkward at times, and reproducing a fixed route and speed is even more of a pain. So I built this little custom-route tool with an AI.

Besides, Yingsuo has gone closed source~

The test-provider idea comes from Yingsuo; the map and route progression are independent implementations.

Issues and PRs are welcome~

## Build

Requirements: JDK 17+, Android SDK Platform 36, Build-Tools 36.0.0.

```bash
cd GoWithLine
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (debug signing, local testing only).

Release signing:

1. Generate and keep a keystore outside the repository (if it is lost, you cannot publish an update that installs over the existing app).
2. Configure it in the Gradle user home's `gradle.properties` (default `~/.gradle`, or `GRADLE_USER_HOME` if set; do not put secrets in the repository):

```properties
GOWITHLINE_STORE_FILE=<path>/gowithline-release.jks
GOWITHLINE_STORE_PASSWORD=...
GOWITHLINE_KEY_ALIAS=gowithline
GOWITHLINE_KEY_PASSWORD=...
```

3. Build:

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Project layout

```
app/src/main/java/com/gowithline/mock/
├─ MainActivity.java               map, drawing, speed, state and permissions
├─ geo/                            pure Java geometry, coordinate conversion, path, simulator
├─ map/                            osmdroid layers and XYZ tile sources
├─ service/MockLocationService.java foreground service, test providers, notification
├─ ui/DrawOverlayView.java         freehand drawing overlay
└─ util/                           mock-location detection, formatting
```

## Privacy

- No account, no self-hosted backend, no advertising or analytics SDK; route, speed, layer and map center are stored only in local SharedPreferences.
- Built-in OpenStreetMap tiles are served by the HOT service of OpenStreetMap France; with a custom XYZ template, requests go to the server you enter. Both expose your IP and the requested coordinates to that service.

See [PRIVACY.md](PRIVACY.md).

## Disclaimer

The app mocks location through Android's debugging API and is intended for development, teaching demos and location debugging only. Do not use it for game cheating, attendance/campus running or other prohibited purposes. Some apps can detect mock locations, and vendor ROMs may impose restrictions; by using the app you accept the relevant responsibility.

## License

Copyright (C) 2026 99galaxy

This project is released under the [GNU General Public License v3.0](LICENSE) (GPL-3.0). SPDX identifier: `GPL-3.0-only`.

```
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

The “GoWithLine” and “路线模拟” names and the icon are not licensed with the code; this is a trademark/identifier reservation permitted by GPLv3 section 7(e), not an additional restriction on the license. Third-party libraries, map data and tile terms are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [NOTICE](NOTICE).

## Acknowledgements

- [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo): no-root mock location idea and coordinate handling reference.
- [osmdroid](https://github.com/osmdroid/osmdroid): map rendering (Apache-2.0).
- Map data: © OpenStreetMap contributors (ODbL). Built-in tiles are the HOT style, served by OpenStreetMap France.

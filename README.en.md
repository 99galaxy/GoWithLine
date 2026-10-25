# GoWithLine

Route simulation for Android — draw a route, set a speed, and let your mock location glide along it smoothly. A lightweight, root-free, account-free, and ad-free open-source location testing tool. [中文](README.md)

[![release](https://img.shields.io/badge/release-1.1-blue)](https://github.com/99galaxy/GoWithLine/releases/latest)
[![license](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84)

![GoWithLine demo](docs/screenshots/demo.gif)

**[Download Latest APK](https://github.com/99galaxy/GoWithLine/releases/latest)** · ~5 MB · Android 8.0+ · SHA-256 available on the Release page

| Intended Use Cases | Prohibited / Out-of-Scope Uses |
|---|---|
| Development, debugging, educational demos, and testing map/tracking/location-aware apps | Attendance fraud, campus running/PE test spoofing, game cheating, or bypassing detection |

This application injects mock locations via Android developer debugging APIs. Certain applications can detect mock locations (e.g., via `isFromMockProvider`, FusedLocationProvider, or vendor-specific security SDKs); bypassing such detection without root privileges is technically impossible and is strictly out of scope for this project.

<img src="docs/screenshots/main.png" width="240" alt="Freehand route"> <img src="docs/screenshots/joystick.png" width="240" alt="Joystick mode"> <img src="docs/screenshots/selfcheck.png" width="240" alt="Self-check">

## Features

- **Freehand Route Drawing**: Draw multi-segment routes with a single finger touch-and-drag; segments connect automatically with full support for undo and clear. **Two-finger pan and zoom gestures remain active while drawing**—simply release your fingers to seamlessly resume drawing with one finger.
- **Constant Speed Control**: Set speeds in km/h with convenient presets for walking, running, cycling, and driving. Speed can be adjusted on the fly while simulation is running.
- **Smooth Trajectory Movement**: Distance advances according to real physical time, smoothly interpolating coordinates and bearings along the polyline. Injects into GPS, NETWORK, and FUSED test providers simultaneously (effective combinations depend on device hardware and ROM).
- **Comprehensive Lifecycle Control**: Seamlessly pause, resume, and stop. During pause or upon reaching the destination, the final location continues to be broadcast to prevent snap-back to the real location. Stopping cleanly unregisters test providers and restores real GPS.
- **Essential Utilities**: Route looping, camera auto-follow, foreground notification with playback controls (pause/resume/stop), and WakeLock to prevent the screen from sleeping.
- **Floating Speed Overlay**: Displays the current mock speed over other applications. Features drag-to-reposition, `−/+` fine-tuning, quick preset chips (5/10/20/40 km/h), and an instant close button.
- **Virtual Joystick Mode**: No pre-planned route required. Switch to joystick mode to display a virtual joystick in the floating overlay. The joystick controls heading direction while moving at the set speed—drag to move, release to stop. Ideal for spontaneous exploration or scenarios where drawing a route in advance is impractical.
- **Direction Lock (Cruise Control)**: Tap the center knob (joystick head) to toggle between locked and unlocked heading. When locked, releasing your finger keeps cruising in the current direction; tap the knob again to stop.
- **Minimalist Joystick Overlay**: In joystick mode, the floating window dispenses with bulky panels, keeping only a semi-transparent joystick and `−/+` speed buttons. Speed, unit, and lock status are rendered directly inside the joystick knob.
- **Centralized Settings**: Manage simulation modes, floating window toggles, map tile layers, environment diagnostics, and about info in one place.
- **About & Updates**: Inspect the current version, visit the developer's GitHub profile, access the source repository, and check for new releases via GitHub Releases.
- **State Persistence**: Automatically persists routes, speed settings, loop toggle, layer selection, simulation mode, and map center across app restarts.
- **Custom Map Tile Layers**:
  - **OpenStreetMap** (WGS-84, built-in): Powered by the HOT tile mirror hosted by OpenStreetMap France.
  - **Custom XYZ Tile Templates** (Supports WGS-84 / GCJ-02): Allows connecting to any third-party tile service you are authorized to use.
  - Go to "Settings → Map layer", choose "Custom XYZ…", enter a tile URL template containing `{z}`, `{x}`, and `{y}` placeholders (e.g. `https://tiles.example.com/{z}/{x}/{y}.png`), and specify the corresponding coordinate system.

> Only OpenStreetMap is built-in (HOT tile style, map data © OpenStreetMap contributors, licensed under ODbL). For other base maps, please configure a custom XYZ tile source you have permission to access. Built-in tiles may experience intermittent connectivity issues due to network environments or upstream service policies; custom tile services must also be accessible from your device's network.

## Usage

1. **Installation & Permissions**: Install the APK and launch the app. Read and accept the usage agreement (confirming not to use it for attendance check-ins, campus fitness tests, or game spoofing), then grant basic location and notification permissions.
2. **Enable Developer Options & Mock Location**:
   - Go to system "Settings → About phone", tap "Build number" 7 times to activate Developer Options;
   - Navigate to system "Settings → Developer options → **Select mock location app**", and choose "GoWithLine".
3. **Route Mode**: Tap "Draw route" on the main screen, then press and drag with one finger to outline your route. You can pinch to zoom or pan the map with two fingers at any time; lift both fingers to continue drawing with one finger. Multi-segment drawing is supported; tap "Finish drawing" when done.
4. **Start Simulation**: Enter your target speed (km/h), toggle "Loop" if desired, and tap "Start".
5. **Simulation Controls**: Pause, resume, or stop the simulation at any time from the app or directly via the system notification shade. Stopping the simulation immediately restores your device's real location.
6. **Joystick Mode**:
   - Tap the gear icon in the toolbar → "Simulation mode" → select **Joystick mode** (requires the "Display over other apps" overlay permission on first use);
   - Return to the main screen and tap "Start". A semi-transparent joystick and `−/+` speed buttons will appear on screen;
   - Drag the joystick to steer at your configured speed; release to stop instantly—no route drawing needed;
   - The joystick knob displays the current speed (km/h) and a lock icon; tap the knob to toggle direction lock;
   - When locked, releasing your finger retains the deflection angle and continues moving forward; tap the knob again to stop;
   - While locked, dragging the joystick smoothly adjusts your heading: it tracks your finger smoothly from its current position rather than snapping abruptly to the touch point; releasing fixes the new direction and resumes cruise;
   - Use the drag handle at the top of the overlay to reposition the control on screen; `−/+` buttons adjust speed by 1 km/h per tap.
7. **Floating Speed Overlay**: Go to "Settings → Floating speed window" (first use will redirect to the system overlay permission page). In route mode, it shows real-time speed and preset chips during simulation; in joystick mode, it is automatically enabled to host the virtual joystick.
   - If the app is reinstalled, the operating system may revoke this permission; the switch will prompt you to re-grant it when needed.
8. **Custom Map Tile Layers**: Go to "Settings → Map layer → Custom XYZ…" → Confirm → enter the tile URL template and select the coordinate system (WGS-84 or GCJ-02) → Confirm. Returning to the map applies the new layer and automatically reprojects any existing route coordinates. The template must include `{z}`, `{x}`, and `{y}` placeholders, such as `https://tiles.example.com/{z}/{x}/{y}.png`.
9. **Environment Self-Check**: Open "Settings → Self-check" to run automated diagnostics on developer options, mock location app selection, system location toggles, provider injection status, and system read-back counts.
10. **About & Update Checks**: Open "Settings → About" to view the installed version (1.1), developer GitHub profile, source code repository, and check for new updates.
    - Update checks query the GitHub Releases API. If connectivity issues cause the check to fail, a direct link to the Releases page will be provided.

> If permission prompts appear on first launch, simply follow the in-app dialog instructions to navigate to system settings and grant the required permissions.

## How It Works

### 1. No-Root Mock Location Mechanism

`MockLocationService` runs as an Android Foreground Service (declaring `foregroundServiceType="location"`) to ensure process persistence and reliable injection:

1. Upon startup, it registers GPS, NETWORK, and FUSED test providers via `LocationManager.addTestProvider(...)`;
2. An internal timer triggers every 200 ms to write the latest mock position using `setTestProviderLocation()`;
3. The generated `Location` object is populated with latitude, longitude, altitude, accuracy, bearing, speed, timestamp, and `elapsedRealtimeNanos`; GPS provider injections include additional satellite count extras;
4. Upon stopping, it cleanly invokes `setTestProviderEnabled(false)` followed by `removeTestProvider()` to unregister providers completely.

This architecture adopts proven techniques from GoGoGo's `ServiceGo`. On Android 12+ (API 31+), power and accuracy constants are defined using `ProviderProperties`, while older versions gracefully fall back to `Criteria` constants.

### 2. Coordinate Systems & Transformations

- All hand-drawn points, polylines, map markers, and map center coordinates strictly adhere to the coordinate system of the currently active layer.
- The built-in OpenStreetMap layer uses WGS-84; custom tile layers using China's GCJ-02 coordinate system require transformation.
- Coordinate transformation strictly occurs at the system boundary: GCJ-02 coordinates are converted to WGS-84 when starting the simulation and sending data to the system location API; conversely, injected WGS-84 coordinates are transformed back to GCJ-02 when rendering the current mock position on the map.
- Switching between map layers with different coordinate systems automatically reprojects existing routes and map center points, preventing visual displacement.
- This design mirrors the approach in GoGoGo, which transforms Baidu BD-09 coordinates into WGS-84 before submitting them to the Android location provider.

### 3. Route Progression Algorithms

The core geometric computation layer is implemented in pure Java without Android SDK dependencies, ensuring decoupled architecture and comprehensive unit testability:

- `GeoMath`: Implements Haversine great-circle distance calculations and initial bearing estimation;
- `RoutePath`: Computes cumulative route distances, performs linear distance interpolation along polylines, and calculates heading angles via forward/backward differential sampling;
- `RouteSimulator`: Core state machine advancing progress via `traveled += speed × Δt`, with complete support for pausing, completion detection, and modular loop wrap-around;
- `RouteSimplifier`: Uses the Douglas-Peucker algorithm to decimate and smooth raw freehand touch points.

The service uses `SystemClock.elapsedRealtime()` to determine high-precision wall-clock time intervals (Δt), eliminating speed drift caused by cumulative Handler dispatch latency.

### 4. Joystick Mode Architecture

- The `simulation_mode` preference is persisted in storage and passed to the background service via Intent extras on startup.
- Joystick mode bypasses route generation: the service starts from the current map center (WGS-84) and calculates subsequent waypoints along the joystick heading using `GeoMath.destinationPoint()`, advancing by `speed × Δt` on each tick.
- The virtual joystick outputs only the heading angle and active state; travel speed remains strictly locked to the configured km/h value from the UI/overlay. Releasing touch or pausing halts movement immediately.
- The joystick control is rendered inside a system-level overlay window (`TYPE_APPLICATION_OVERLAY`), allowing full steering control when switching to background apps or other tasks.
- Direction lock is maintained internally by the joystick view: tapping the center knob toggles lock status; when unlocked, releasing the knob snaps back to center and halts movement; when locked, releasing preserves the deflection angle and continues reporting active state, keeping the service cruising forward at constant speed.

## FAQ

### "GoWithLine" does not appear in the mock location app list

The Android "Select mock location app" menu only lists applications that declare `android.permission.ACCESS_MOCK_LOCATION` in their `AndroidManifest.xml`. This app includes that permission declaration (it is never granted at runtime; it merely serves as a declaration to appear in the developer options list). If it does not show up:

- Verify you installed the official build from the [Releases](https://github.com/99galaxy/GoWithLine/releases/latest) page;
- Certain vendor ROMs cache the application list. Try closing and reopening Settings, or reboot your device;
- If it still does not appear, grant mock location permissions directly via ADB:
  ```bash
  adb shell appops set com.gowithline.mock android:mock_location allow
  ```
  *(Ensure the app is already installed and basic location permissions have been granted).*

### The simulation has started, but location does not update on the map or in other apps

Open "Settings → Self-check" from the top-right menu and tap "Copy" to export diagnostics. The self-check panel inspects:

- **Developer Environment**: Whether Developer Options are enabled and GoWithLine is selected as the mock location app;
- **System Service Status**: System location toggle, Wi-Fi status, and Precise Location permission status;
- **Provider Status**: Whether GPS, NETWORK, and FUSED test providers were successfully registered;
- **Injection Count**: Total number of location updates dispatched;
- **System Read-back (Crucial Metric)**: The service registers a system location listener to verify whether injected locations are actually accepted by the Android framework. If accepted, this shows "Success N times". If read-back remains 0, the injection is being rejected or blocked at the OS/ROM level, indicating a permission or system policy issue rather than a UI problem.

**Common Causes & Solutions**:

1. **GoWithLine is not selected in Developer Options**: Self-check reports "Not selected". Re-select it in system Developer Options.
2. **System location is turned off**: Self-check reports "Disabled". Turn on location services in the system status bar or settings.
3. **Only Approximate Location permission granted**: Self-check reports "Precise location not granted". Go to app settings and switch location permission to "Precise location".
4. **Wi-Fi / Fused Location interference**: Some devices force location updates from Wi-Fi scanning or fused positioning, causing location snap-back. Try temporarily disabling Wi-Fi or turning off "Wi-Fi scanning" in system settings.
5. **Provider registration failure**: Self-check displays the thrown exception. This commonly occurs on heavily customized vendor ROMs that restrict `addTestProvider`.
6. **Read-back count is normal, but a specific app does not update**: That application may rely on Google Play Services FusedLocationProvider, a proprietary location SDK, or explicitly inspect `location.isFromMockProvider()`. Apps with active mock detection cannot be overridden in a non-root environment.

**Further ADB Diagnostics**:

```bash
adb shell appops get com.gowithline.mock android:mock_location
adb logcat -d -s GoWithLineService | tail -100
adb shell dumpsys location | grep -A4 -i "last location"
```

> If GPS/NETWORK injection succeeds and injection count keeps increasing while "System read-back" remains 0, please open an Issue with the exported self-check text and `adb logcat -s GoWithLineService` output so we can help determine whether it is a ROM permission restriction or provider parameter rejection.

### How do I zoom or pan the map while drawing a route?

In route drawing mode:

- **Single finger**: Touch and drag to draw route segments;
- **Two fingers**: Pinch to zoom or drag to pan the map. The moment two fingers are detected, the stroke currently in progress is automatically discarded to prevent accidental lines; lift both fingers and resume drawing with a single finger.

### The map is blank or tiles fail to load

By default, the application only includes OpenStreetMap tiles (the HOT style mirror hosted by OpenStreetMap France). Tile loading may occasionally fail due to international network latency, regional restrictions, or upstream server policies. If tiles fail to load, navigate to "Settings → Map layer", choose "Custom XYZ…", confirm, and enter a tile URL template and coordinate system for an XYZ tile service you are authorized to use. The template must include `{z}`, `{x}`, and `{y}` placeholders (e.g. `https://tiles.example.com/{z}/{x}/{y}.png`). The project does not bundle offline map packages or redistribute proprietary tile datasets.

## Changelog

### 1.1

- UI polish: reworked visual hierarchy on the main and settings screens, added a simulation-mode badge, and enlarged touch targets to at least 48dp.
- Fixed the Settings → Map layer picker showing no options, which made custom XYZ layers unreachable.
- The self-check "system read-back" no longer counts a nearby real fix as a mock echo.
- Documented the custom XYZ tile setup steps.

### 1.0

Initial official release.

- Freehand multi-segment route drawing with two-finger pan/zoom support; constant speed simulation in km/h along polylines.
- Concurrent injection into GPS, NETWORK, and FUSED test providers; pause, resume, stop, and route loop modes.
- Virtual joystick mode with directional cruise lock; floating speed overlay; OpenStreetMap and custom XYZ tile layers.
- Built-in environment self-check; automatic provider unregistration and real location restoration upon stopping.
- GPL-3.0 open source, zero accounts, zero ads, zero custom backends, compatible with Android 8.0+.

## A Personal Note

老实摇杆，很枯燥吧？.jpg

之前一直习惯用影梭模拟位移，但很多时候自己手动推着摇杆实在不太方便，想要稳定复现一条固定路线和设定速度就更费劲了。于是让大肥鱼折腾了这个支持自定义画路线的小工具。

况且影梭也已经转为闭源了~

测试 Provider 的底层注入思路借鉴了影梭，地图与路线推进算法则是完全独立实现的。

欢迎Issue和PR~

## Build

**Requirements**: JDK 17+, Android SDK Platform 36, Build-Tools 36.0.0.

```bash
cd GoWithLine
./gradlew assembleDebug
```

Debug artifact: `app/build/outputs/apk/debug/app-debug.apk` (debug signed, for local testing and debugging).

### Release Signing

1. Generate and securely store a release keystore outside the project repository (if lost, you will not be able to publish update packages over existing installations);
2. Configure signing credentials in `gradle.properties` located in your local Gradle user home directory (defaults to `~/.gradle/gradle.properties`, or the path specified by `GRADLE_USER_HOME`; **never commit credentials to Git**):

```properties
GOWITHLINE_STORE_FILE=<path>/gowithline-release.jks
GOWITHLINE_STORE_PASSWORD=...
GOWITHLINE_KEY_ALIAS=gowithline
GOWITHLINE_KEY_PASSWORD=...
```

3. Run the release build:

```bash
./gradlew assembleRelease
```

Release artifact: `app/build/outputs/apk/release/app-release.apk`.

### Unit Tests

```bash
./gradlew :app:testDebugUnitTest
```

## Project Structure

```
app/src/main/java/com/gowithline/mock/
├─ MainActivity.java                Main UI controller: map interaction, drawing, speed control, and permissions
├─ geo/                             Pure Java geometry: coordinate conversions, pathing, simulator, and simplification
├─ map/                             Map integration: osmdroid layer configurations and custom XYZ tile sources
├─ service/MockLocationService.java Core foreground service: test provider injection, broadcasts, and persistent notifications
├─ ui/DrawOverlayView.java          Top-level freehand drawing overlay view
└─ util/                            Utilities: mock location self-check diagnostics, formatting, and helpers
```

## Privacy

- **Zero Data Collection**: No accounts, no self-hosted backends, and no embedded third-party advertising or analytics SDKs. Drawn routes, speed settings, layer preferences, and map center coordinates are stored exclusively on your local device via SharedPreferences.
- **Tile Network Requests**: Built-in OpenStreetMap tiles are served by OpenStreetMap France's HOT infrastructure; when using custom XYZ tiles, requests are sent directly to the server you configure. During tile retrieval, the respective server will receive your IP address and the requested tile coordinates in accordance with standard HTTP protocols.

For further details, refer to [PRIVACY.md](PRIVACY.md).

## Disclaimer

This application utilizes official Android developer debugging APIs for mock location injection and is intended solely for software development, educational demonstrations, and debugging location-aware features. Do not use this application for attendance fraud, campus fitness test manipulation, game location spoofing, or any purpose that violates applicable laws or third-party platform terms of service. Certain applications have the capability to detect mock locations, and device manufacturers may impose specific firmware-level restrictions; by using this application, you acknowledge these conditions and assume all associated risks and responsibilities.

## License

Copyright (C) 2026 99galaxy

This project is licensed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0). SPDX Identifier: `GPL-3.0-only`.

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

The names "GoWithLine" and "路线模拟", as well as the application icon, are reserved and are not licensed alongside the source code; this represents a trademark and identifier reservation permitted under Section 7(e) of the GPLv3 license and does not constitute an additional restriction on the GPL terms. Third-party library licenses, map data attributions, and tile terms are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [NOTICE](NOTICE).

## Acknowledgements

- [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo): Inspired the no-root mock location injection approach and cross-coordinate system handling.
- [osmdroid](https://github.com/osmdroid/osmdroid): Excellent open-source map rendering library for Android (licensed under Apache-2.0).
- Map data: © OpenStreetMap contributors (licensed under ODbL). Built-in map tiles use the HOT style, graciously provided by OpenStreetMap France.

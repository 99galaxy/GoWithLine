# Joystick map start selection

Goal: In joystick mode, tap the map before starting to choose the simulation's start location.

Behavior:
- Show the selected start with a marker and coordinates in the existing status area.
- Store the selection in WGS-84 so map panning, recreation, and WGS-84/GCJ-02 layer changes preserve the same location.
- Use the existing map-center fallback when no start has been selected.
- Reject changes while starting, running, or paused; keep route drawing and map gestures unchanged.
- Keep the published 1.2 release unchanged; this feature is developed on codex/joystick-map-start.

Implementation and verification:
- [x] Reproduce the missing map selection on the current device build.
- [x] Add map-tap handling, start marker, selection persistence, and startup coordinate resolution.
- [x] Update user-facing hints and the Chinese/English usage documentation.
- [x] Run unit tests and build the debug APK (41 tests passed).
- [x] Verify selection, pan independence, restored selection, startup readback, active-state protection, and route-mode isolation through ADB.
- [x] Restore the device's original preferences and stop simulation after testing.

Device verification:
- A selected point survived pan and app relaunch; reselection updated the stored coordinates.
- WGS-84 point 31.20328, 121.49814 was echoed by the system at startup instead of the map center.
- Running and paused map taps did not change the stored start.
- On a map configured as GCJ-02, tapping the camera center stored WGS-84 31.2014775, 121.4973986, within 1 meter of the expected WGS-84 camera center.
- Route-mode taps did not change either the route or the joystick selection.
- With no saved selection, system readback matched the original map center.
- Independent read-only code review found no actionable issues.

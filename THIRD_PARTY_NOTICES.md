# Third-party notices

GoWithLine's own source code is licensed under the GNU General Public License
v3.0 (GPL-3.0, SPDX: GPL-3.0-only). The following components and data are used
by the app and remain under their own licenses/terms.

## Libraries

| Component | License | Link |
|---|---|---|
| osmdroid | Apache License 2.0 | https://github.com/osmdroid/osmdroid |
| AndroidX / AppCompat | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| Material Components for Android | Apache License 2.0 | https://github.com/material-components/material-components-android |
| Material Icons | Apache License 2.0 | https://github.com/google/material-design-icons |

## Map data and tiles

- **OpenStreetMap** — map data © OpenStreetMap contributors, available under
  the Open Database License (ODbL). https://www.openstreetmap.org/copyright
  Built-in tiles use the HOT style served by OpenStreetMap France
  (https://tile.openstreetmap.fr/hot/). The app shows the required
  attribution; the service is intended for reasonable, non-bulk use.
- No other map provider is bundled. A custom XYZ template is supplied by the
  user and remains subject to the terms of the service the user chooses.

The app only requests tiles at runtime; no map data is redistributed in this
repository.

## Inspiration

The no-root mock-location approach was studied from
[ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo) (GPL-3.0). GoWithLine is an
independent implementation: no GoGoGo source code is copied into this
repository. The map, route drawing, interpolation, diagnostics, floating
window, and coordinate handling are original code.

GoWithLine is distributed under GPL-3.0-only, the same license family as GoGoGo,
so the distribution remains license-compatible even if a reviewer considers a
small portion derived.

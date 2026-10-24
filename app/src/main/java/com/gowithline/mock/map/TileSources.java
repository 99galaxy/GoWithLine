package com.gowithline.mock.map;

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;

/**
 * Built-in raster tile layers.
 *
 * <p>Only OpenStreetMap is built in. Tiles are requested directly from the
 * HOT style tile service operated by OpenStreetMap France; the app sets a
 * descriptive User-Agent and shows the required attribution. For any other
 * provider, configure a custom XYZ template that you are authorized to use.
 * This project does not bundle, proxy or redistribute map data.</p>
 */
public final class TileSources {
    private static final String OSM_URL =
            "https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png";

    private TileSources() {
    }

    public static MapLayer osm() {
        return new MapLayer(
                MapLayer.ID_OSM,
                "OpenStreetMap",
                "© OpenStreetMap contributors · HOT",
                MapLayer.CoordSystem.WGS84,
                new TemplateTileSource("gowithline-osm", 3, 19, OSM_URL, null));
    }

    public static MapLayer custom(String url, MapLayer.CoordSystem coordSystem) {
        if (!isValidTemplate(url)) {
            throw new IllegalArgumentException("custom tile URL must contain {z}, {x} and {y}");
        }
        String name = "gowithline-custom-" + Integer.toHexString(url.hashCode());
        return new MapLayer(
                MapLayer.ID_CUSTOM,
                "自定义图层",
                "自定义图层",
                coordSystem,
                new TemplateTileSource(name, 0, 19, url, null));
    }

    public static boolean isValidTemplate(String url) {
        return url != null
                && url.contains("{z}")
                && url.contains("{x}")
                && url.contains("{y}");
    }

    /**
     * Older builds stored "gaode"/"esri" layer ids. Those built-in sources have
     * been removed; unknown ids fall back to the only bundled layer.
     */
    public static MapLayer builtIn(String id) {
        return osm();
    }

    public static final class TemplateTileSource extends OnlineTileSourceBase {
        private final String template;
        private final String[] servers;

        public TemplateTileSource(String name, int minZoom, int maxZoom,
                                  String template, String[] servers) {
            super(name, minZoom, maxZoom, 256, ".png", new String[]{template});
            this.template = template;
            this.servers = servers;
        }

        @Override
        public String getTileURLString(long tileIndex) {
            int zoom = MapTileIndex.getZoom(tileIndex);
            int x = MapTileIndex.getX(tileIndex);
            int y = MapTileIndex.getY(tileIndex);

            String url = template
                    .replace("{z}", Integer.toString(zoom))
                    .replace("{x}", Integer.toString(x))
                    .replace("{y}", Integer.toString(y));

            if (servers != null && servers.length > 0) {
                int index = (int) (tileIndex % servers.length);
                url = url.replace("{s}", servers[index]);
            } else {
                url = url.replace("{s}", "a");
            }
            return url;
        }
    }
}

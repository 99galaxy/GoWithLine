package com.gowithline.mock.map;

import org.osmdroid.tileprovider.tilesource.ITileSource;

public final class MapLayer {
    public enum CoordSystem {
        WGS84,
        GCJ02
    }

    public static final String ID_OSM = "osm";
    public static final String ID_CUSTOM = "custom";

    public final String id;
    public final String label;
    public final String attribution;
    public final CoordSystem coordSystem;
    public final ITileSource tileSource;

    public MapLayer(String id, String label, String attribution,
                    CoordSystem coordSystem, ITileSource tileSource) {
        this.id = id;
        this.label = label;
        this.attribution = attribution;
        this.coordSystem = coordSystem;
        this.tileSource = tileSource;
    }

    public boolean usesGcj02() {
        return coordSystem == CoordSystem.GCJ02;
    }
}

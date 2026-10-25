package com.gowithline.mock.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.util.MapTileIndex;

/**
 * Regression coverage for the custom XYZ tile pathway: template validation and
 * the per-tile URL that osmdroid actually requests.
 */
public class TileSourcesTest {

    @Test
    public void isValidTemplate_acceptsAllThreePlaceholdersInAnyOrder() {
        assertTrue(TileSources.isValidTemplate(
                "https://tiles.example.com/{z}/{x}/{y}.png"));
        assertTrue(TileSources.isValidTemplate(
                "https://tiles.example.com/{y}/{x}/{z}.png?token=abc"));
        assertTrue(TileSources.isValidTemplate(
                "http://127.0.0.1:8765/tiles/{z}/{x}/{y}.png"));
    }

    @Test
    public void isValidTemplate_rejectsMissingPlaceholderOrNull() {
        assertFalse(TileSources.isValidTemplate(null));
        assertFalse(TileSources.isValidTemplate(""));
        assertFalse(TileSources.isValidTemplate(
                "https://tiles.example.com/{z}/{x}.png"));
        assertFalse(TileSources.isValidTemplate(
                "https://tiles.example.com/{z}/{y}.png"));
        assertFalse(TileSources.isValidTemplate(
                "https://tiles.example.com/{x}/{y}.png"));
        assertFalse(TileSources.isValidTemplate(
                "https://tiles.example.com/{Z}/{X}/{Y}.png"));
    }

    @Test
    public void custom_rejectsInvalidTemplate() {
        try {
            TileSources.custom("https://tiles.example.com/{z}/{x}.png",
                    MapLayer.CoordSystem.WGS84);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void templateTileSource_substitutesZxyForRequestedTile() {
        ITileSource source = TileSources.custom(
                "https://tiles.example.com/{z}/{x}/{y}.png",
                MapLayer.CoordSystem.WGS84).tileSource;

        long tile = MapTileIndex.getTileIndex(16, 54881, 26778);
        String url = ((TileSources.TemplateTileSource) source)
                .getTileURLString(tile);

        assertEquals("https://tiles.example.com/16/54881/26778.png", url);
        assertFalse(url.contains("{z}"));
        assertFalse(url.contains("{x}"));
        assertFalse(url.contains("{y}"));
    }

    @Test
    public void templateTileSource_preservesQueryAndDefaultsSubdomain() {
        ITileSource source = TileSources.custom(
                "https://{s}.tiles.example.com/{z}/{x}/{y}.png?key=abc",
                MapLayer.CoordSystem.GCJ02).tileSource;

        long tile = MapTileIndex.getTileIndex(5, 26, 13);
        String url = ((TileSources.TemplateTileSource) source)
                .getTileURLString(tile);

        assertEquals("https://a.tiles.example.com/5/26/13.png?key=abc", url);
        assertEquals(MapLayer.ID_CUSTOM,
                TileSources.custom("https://t/{z}/{x}/{y}.png",
                        MapLayer.CoordSystem.WGS84).id);
    }
}

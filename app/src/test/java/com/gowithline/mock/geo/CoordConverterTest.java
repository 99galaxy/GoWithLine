package com.gowithline.mock.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoordConverterTest {
    @Test
    public void beijingOffsetAndRoundTrip() {
        PointD wgs = new PointD(39.9087, 116.3975);
        PointD gcj = CoordConverter.wgs84ToGcj02(wgs);

        assertTrue("GCJ-02 must shift Beijing coordinates",
                GeoMath.distanceMeters(wgs, gcj) > 100.0);

        PointD back = CoordConverter.gcj02ToWgs84(gcj);
        assertTrue("inverse must return to WGS-84",
                GeoMath.distanceMeters(wgs, back) < 0.5);
    }

    @Test
    public void coordinatesOutsideChinaAreUnchanged() {
        PointD paris = new PointD(48.8566, 2.3522);
        PointD converted = CoordConverter.wgs84ToGcj02(paris);
        assertEquals(paris.lat, converted.lat, 1e-12);
        assertEquals(paris.lng, converted.lng, 1e-12);

        PointD back = CoordConverter.gcj02ToWgs84(paris);
        assertEquals(paris.lat, back.lat, 1e-12);
        assertEquals(paris.lng, back.lng, 1e-12);
    }

    @Test
    public void shenzhenRoundTrip() {
        PointD wgs = new PointD(22.5431, 114.0579);
        PointD gcj = CoordConverter.wgs84ToGcj02(wgs);
        PointD back = CoordConverter.gcj02ToWgs84(gcj);
        assertTrue(GeoMath.distanceMeters(wgs, back) < 0.5);
        assertEquals(wgs.lat, back.lat, 1e-6);
        assertEquals(wgs.lng, back.lng, 1e-6);
    }
}

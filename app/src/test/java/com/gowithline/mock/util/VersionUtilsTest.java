package com.gowithline.mock.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionUtilsTest {
    @Test
    public void comparesNumericSegments() {
        assertTrue(VersionUtils.compare("1.1", "1.0") > 0);
        assertTrue(VersionUtils.compare("1.10", "1.9") > 0);
        assertEquals(0, VersionUtils.compare("v1.1", "1.1.0"));
        assertEquals(0, VersionUtils.compare("1.0.0-beta", "1.0.0"));
    }

    @Test
    public void isNewer() {
        assertTrue(VersionUtils.isNewer("v1.0.3", "1.0.2"));
        assertTrue(VersionUtils.isNewer("1.10.0", "1.9.9"));
        assertFalse(VersionUtils.isNewer("v1.0", "1.1"));
        assertFalse(VersionUtils.isNewer("1.1", "1.1"));
    }

    @Test
    public void parsesWordPrefixedTags() {
        assertTrue(VersionUtils.isNewer("release-1.0", "0.9"));
        assertTrue(VersionUtils.isNewer("GoWithLine v1.0", "0.9"));
        assertTrue(VersionUtils.isNewer("Release-2.0", "1.9.9"));
        assertFalse(VersionUtils.isNewer("release-1.0", "1.1"));
        assertEquals(0, VersionUtils.compare("release-1.0.0", "v1.0"));
    }

    @Test
    public void handlesTagsWithoutDigits() {
        assertFalse(VersionUtils.isNewer("beta", "1.1"));
        assertFalse(VersionUtils.isNewer(null, "1.1"));
        assertFalse(VersionUtils.isNewer("", "1.1"));
    }
}

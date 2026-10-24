package com.gowithline.mock.service;

/**
 * Snapshot of the mock-location plumbing, exposed to the in-app self-check.
 * Values are updated by the service worker thread.
 */
public final class ServiceDiagnostics {
    public final boolean running;
    public final boolean gpsReady;
    public final boolean networkReady;
    public final boolean fusedReady;
    public final String gpsError;
    public final String networkError;
    public final String fusedError;
    public final int injectedCount;
    public final String lastInjectionError;
    public final int echoCount;
    public final double echoLat;
    public final double echoLng;
    public final double injectedLat;
    public final double injectedLng;
    public final long echoElapsedMs;

    public ServiceDiagnostics(boolean running,
                              boolean gpsReady, boolean networkReady, boolean fusedReady,
                              String gpsError, String networkError, String fusedError,
                              int injectedCount, String lastInjectionError,
                              int echoCount, double echoLat, double echoLng,
                              double injectedLat, double injectedLng,
                              long echoElapsedMs) {
        this.running = running;
        this.gpsReady = gpsReady;
        this.networkReady = networkReady;
        this.fusedReady = fusedReady;
        this.gpsError = gpsError;
        this.networkError = networkError;
        this.fusedError = fusedError;
        this.injectedCount = injectedCount;
        this.lastInjectionError = lastInjectionError;
        this.echoCount = echoCount;
        this.echoLat = echoLat;
        this.echoLng = echoLng;
        this.injectedLat = injectedLat;
        this.injectedLng = injectedLng;
        this.echoElapsedMs = echoElapsedMs;
    }

    public static ServiceDiagnostics idle() {
        return new ServiceDiagnostics(false,
                false, false, false,
                null, null, null,
                0, null,
                0, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,
                0L);
    }
}

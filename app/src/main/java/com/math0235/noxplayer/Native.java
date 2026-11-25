package com.math0235.noxplayer;

import android.content.Context;

/** Pont vers le cœur C++ (libnox.so). */
public final class Native {
    private static boolean loaded;
    private static Boolean official;

    static {
        try {
            System.loadLibrary("nox");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
    }

    private Native() {}

    static native boolean verify(Context c);
    static native void fft(byte[] fft, float[] levels);
    static native int[] palette(int[] pixels);
    static native String lrc(String text);
    static native float[] wave(long seed, int n);

    /** true seulement si l'APK est signé avec la clé officielle de math_0235 (vérifié en C++). */
    public static boolean ok(Context c) {
        if (official != null) return official;
        boolean r = false;
        try { r = loaded && verify(c.getApplicationContext()); } catch (Throwable ignored) {}
        official = r;
        return r;
    }

    public static boolean loaded() { return loaded; }
}

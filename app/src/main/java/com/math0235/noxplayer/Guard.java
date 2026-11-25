package com.math0235.noxplayer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * Anti-modification : NOX Player vérifie qu'il est bien signé avec la clé de math_0235.
 * Si quelqu'un modifie l'APK (virus, hack…), il doit le re-signer avec une autre clé
 * -> l'empreinte ne correspond plus et l'appli refuse de démarrer.
 */
public final class Guard {
    /** Empreinte SHA-256 du certificat officiel de NOX Player. */
    private static final String OFFICIAL = "FA62989101FA1DCC7ABF1700A7E610A3764B1F6E3A2F5F32B356AC74F6CADDD8";
    private static final String PACKAGE = "com.math0235.noxplayer";
    private static Boolean cached;

    private Guard() {}

    @SuppressWarnings("deprecation")
    public static boolean isOfficial(Context c) {
        if (cached != null) return cached;
        boolean ok = false;
        try {
            if (!PACKAGE.equals(c.getPackageName())) { cached = false; return false; }
            PackageManager pm = c.getPackageManager();
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi.signingInfo == null || pi.signingInfo.hasMultipleSigners()) { cached = false; return false; }
                sigs = pi.signingInfo.getApkContentsSigners();
            } else {
                sigs = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNATURES).signatures;
            }
            if (sigs != null && sigs.length == 1) {
                byte[] d = MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray());
                StringBuilder sb = new StringBuilder();
                for (byte b : d) sb.append(String.format("%02X", b));
                ok = OFFICIAL.equals(sb.toString());
            }
        } catch (Exception e) {
            ok = false;
        }
        cached = ok;
        return ok;
    }
}

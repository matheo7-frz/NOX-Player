# L'interface JavaScript appelle ces méthodes par leur nom : on les garde.
-keepclassmembers class com.math0235.noxplayer.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
# Le C++ retrouve ces méthodes par leur nom exact (JNI).
-keep class com.math0235.noxplayer.Native {
    native <methods>;
}
# Tout le reste est renommé / obfusqué.
-allowaccessmodification
-repackageclasses 'n'

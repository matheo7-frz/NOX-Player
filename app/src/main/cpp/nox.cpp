// NOX Player — cœur natif en C++ (fait par math_0235)
// - verify()  : anti-modification, vérifie la signature de l'APK (SHA-256) directement via JNI
// - fft()     : traitement du son pour le visualiseur
// - palette() : couleurs tirées de la pochette
// - lrc()     : lecture des paroles synchronisées (.lrc)
// - wave()    : forme de l'onde décorative du lecteur
#include <jni.h>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>

static const unsigned char K_MASK[32] = {0x79, 0xfd, 0x2b, 0x2d, 0x3a, 0x30, 0x66, 0x48, 0xab, 0xc0, 0x9c, 0xbe, 0xee, 0xba, 0xd7, 0x69, 0x7b, 0x8b, 0xb2, 0xf5, 0xc9, 0x4f, 0x20, 0x3a, 0x96, 0x4c, 0xf9, 0x3f, 0x74, 0xb8, 0xcf, 0x55};
static const unsigned char K_ENC[32] = {0x83, 0x9f, 0xb3, 0xbc, 0x3b, 0xca, 0x7b, 0x84, 0xd1, 0x7f, 0x8b, 0xbe, 0x49, 0x5c, 0xc7, 0xca, 0x0d, 0xc0, 0xad, 0x9b, 0xf3, 0x60, 0x7f, 0x08, 0x25, 0x1a, 0x55, 0x4b, 0x82, 0x72, 0x12, 0x8d};

// ============================================================ SHA-256
namespace {
struct Sha256 {
    uint32_t h[8];
    uint64_t len = 0;
    uint8_t buf[64];
    size_t n = 0;
    static uint32_t rotr(uint32_t x, int r) { return (x >> r) | (x << (32 - r)); }
    Sha256() {
        static const uint32_t init[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
                                         0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
        memcpy(h, init, sizeof h);
    }
    void block(const uint8_t* p) {
        static const uint32_t k[64] = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};
        uint32_t w[64];
        for (int i = 0; i < 16; i++)
            w[i] = (uint32_t)p[i * 4] << 24 | (uint32_t)p[i * 4 + 1] << 16 | (uint32_t)p[i * 4 + 2] << 8 | p[i * 4 + 3];
        for (int i = 16; i < 64; i++) {
            uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
            uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
        for (int i = 0; i < 64; i++) {
            uint32_t S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
            uint32_t ch = (e & f) ^ (~e & g);
            uint32_t t1 = hh + S1 + ch + k[i] + w[i];
            uint32_t S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
            uint32_t mj = (a & b) ^ (a & c) ^ (b & c);
            uint32_t t2 = S0 + mj;
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
    }
    void update(const uint8_t* p, size_t l) {
        len += l;
        while (l--) {
            buf[n++] = *p++;
            if (n == 64) { block(buf); n = 0; }
        }
    }
    void final(uint8_t out[32]) {
        uint64_t bits = len * 8;
        uint8_t one = 0x80, zero = 0;
        update(&one, 1);
        while (n != 56) update(&zero, 1);
        for (int i = 7; i >= 0; i--) { uint8_t b = (uint8_t)(bits >> (i * 8)); update(&b, 1); }
        for (int i = 0; i < 8; i++) {
            out[i * 4] = h[i] >> 24; out[i * 4 + 1] = h[i] >> 16; out[i * 4 + 2] = h[i] >> 8; out[i * 4 + 3] = h[i];
        }
    }
};

bool clearEx(JNIEnv* env) {
    if (env->ExceptionCheck()) { env->ExceptionClear(); return true; }
    return false;
}

int sdkInt(JNIEnv* env) {
    jclass v = env->FindClass("android/os/Build$VERSION");
    if (!v || clearEx(env)) return 0;
    jfieldID f = env->GetStaticFieldID(v, "SDK_INT", "I");
    if (!f || clearEx(env)) return 0;
    return env->GetStaticIntField(v, f);
}

// ============================================================ Couleurs
void rgb2hsv(float r, float g, float b, float& h, float& s, float& v) {
    float mx = std::max(r, std::max(g, b)), mn = std::min(r, std::min(g, b)), d = mx - mn;
    v = mx;
    s = mx <= 0 ? 0 : d / mx;
    if (d <= 0) { h = 0; return; }
    if (mx == r) h = 60 * std::fmod((g - b) / d, 6.0f);
    else if (mx == g) h = 60 * ((b - r) / d + 2);
    else h = 60 * ((r - g) / d + 4);
    if (h < 0) h += 360;
}

int hsv2argb(float h, float s, float v) {
    float c = v * s, x = c * (1 - std::fabs(std::fmod(h / 60.0f, 2.0f) - 1)), m = v - c;
    float r = 0, g = 0, b = 0;
    if (h < 60) { r = c; g = x; } else if (h < 120) { r = x; g = c; } else if (h < 180) { g = c; b = x; }
    else if (h < 240) { g = x; b = c; } else if (h < 300) { r = x; b = c; } else { r = c; b = x; }
    auto to = [&](float f) { return (int)std::lround(std::min(1.0f, std::max(0.0f, f + m)) * 255); };
    return (int)(0xFF000000u | (uint32_t)to(r) << 16 | (uint32_t)to(g) << 8 | (uint32_t)to(b));
}

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return "";
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string r(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

void jsonEscape(std::string& out, const std::string& s) {
    for (unsigned char ch : s) {
        switch (ch) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': break;
            case '\t': out += ' '; break;
            default:
                if (ch < 0x20) break;
                out += (char)ch;
        }
    }
}
}  // namespace

extern "C" {

// ============================================================ Anti-modification
JNIEXPORT jboolean JNICALL
Java_com_math0235_noxplayer_Native_verify(JNIEnv* env, jclass, jobject ctx) {
    if (!ctx) return JNI_FALSE;
    jclass ctxCls = env->GetObjectClass(ctx);
    jmethodID getName = env->GetMethodID(ctxCls, "getPackageName", "()Ljava/lang/String;");
    jmethodID getPm = env->GetMethodID(ctxCls, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    if (clearEx(env) || !getName || !getPm) return JNI_FALSE;
    jstring pkg = (jstring)env->CallObjectMethod(ctx, getName);
    if (clearEx(env) || jstr(env, pkg) != "com.math0235.noxplayer") return JNI_FALSE;
    jobject pm = env->CallObjectMethod(ctx, getPm);
    if (clearEx(env) || !pm) return JNI_FALSE;
    jclass pmCls = env->GetObjectClass(pm);
    jmethodID getInfo = env->GetMethodID(pmCls, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    if (clearEx(env) || !getInfo) return JNI_FALSE;

    jobjectArray sigs = nullptr;
    if (sdkInt(env) >= 28) {
        jobject info = env->CallObjectMethod(pm, getInfo, pkg, (jint)0x08000000);  // GET_SIGNING_CERTIFICATES
        if (clearEx(env) || !info) return JNI_FALSE;
        jclass piCls = env->GetObjectClass(info);
        jfieldID fSi = env->GetFieldID(piCls, "signingInfo", "Landroid/content/pm/SigningInfo;");
        if (clearEx(env) || !fSi) return JNI_FALSE;
        jobject si = env->GetObjectField(info, fSi);
        if (!si) return JNI_FALSE;
        jclass siCls = env->GetObjectClass(si);
        jmethodID multi = env->GetMethodID(siCls, "hasMultipleSigners", "()Z");
        jmethodID signers = env->GetMethodID(siCls, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
        if (clearEx(env) || !multi || !signers) return JNI_FALSE;
        if (env->CallBooleanMethod(si, multi)) return JNI_FALSE;
        sigs = (jobjectArray)env->CallObjectMethod(si, signers);
    } else {
        jobject info = env->CallObjectMethod(pm, getInfo, pkg, (jint)0x40);  // GET_SIGNATURES
        if (clearEx(env) || !info) return JNI_FALSE;
        jclass piCls = env->GetObjectClass(info);
        jfieldID fS = env->GetFieldID(piCls, "signatures", "[Landroid/content/pm/Signature;");
        if (clearEx(env) || !fS) return JNI_FALSE;
        sigs = (jobjectArray)env->GetObjectField(info, fS);
    }
    if (clearEx(env) || !sigs || env->GetArrayLength(sigs) != 1) return JNI_FALSE;
    jobject sig = env->GetObjectArrayElement(sigs, 0);
    jclass sigCls = env->GetObjectClass(sig);
    jmethodID toBytes = env->GetMethodID(sigCls, "toByteArray", "()[B");
    if (clearEx(env) || !toBytes) return JNI_FALSE;
    jbyteArray der = (jbyteArray)env->CallObjectMethod(sig, toBytes);
    if (clearEx(env) || !der) return JNI_FALSE;

    jsize n = env->GetArrayLength(der);
    std::vector<uint8_t> data((size_t)n);
    env->GetByteArrayRegion(der, 0, n, reinterpret_cast<jbyte*>(data.data()));
    uint8_t digest[32];
    Sha256 s;
    s.update(data.data(), data.size());
    s.final(digest);
    unsigned diff = 0;  // comparaison en temps constant
    for (int i = 0; i < 32; i++) diff |= (unsigned)(digest[i] ^ (K_ENC[i] ^ K_MASK[i]));
    return diff == 0 ? JNI_TRUE : JNI_FALSE;
}

// ============================================================ Visualiseur
// fft : données brutes du Visualizer Android ; levels : barres lissées (in/out)
JNIEXPORT void JNICALL
Java_com_math0235_noxplayer_Native_fft(JNIEnv* env, jclass, jbyteArray fftArr, jfloatArray levelsArr) {
    jsize fl = env->GetArrayLength(fftArr), nb = env->GetArrayLength(levelsArr);
    if (fl < 4 || nb <= 0) return;
    std::vector<jbyte> fft((size_t)fl);
    env->GetByteArrayRegion(fftArr, 0, fl, fft.data());
    std::vector<float> lv((size_t)nb);
    env->GetFloatArrayRegion(levelsArr, 0, nb, lv.data());
    int bins = fl / 2;
    for (int i = 0; i < nb; i++) {
        // échelle logarithmique : plus de détail dans les basses
        float a = std::pow((float)i / nb, 2.0f), b = std::pow((float)(i + 1) / nb, 2.0f);
        int k0 = 1 + (int)(a * (bins - 2)), k1 = std::max(k0 + 1, 1 + (int)(b * (bins - 2)));
        float peak = 0;
        for (int k = k0; k < k1 && 2 * k + 1 < fl; k++) {
            float re = fft[2 * k], im = fft[2 * k + 1];
            peak = std::max(peak, std::sqrt(re * re + im * im));
        }
        float mag = std::log10(1.0f + peak) / 2.1f;
        mag = std::min(1.0f, mag * (1.0f + 0.6f * (float)i / nb));  // compense les aigus
        lv[(size_t)i] = mag > lv[(size_t)i] ? mag : lv[(size_t)i] * 0.82f + mag * 0.18f;
    }
    env->SetFloatArrayRegion(levelsArr, 0, nb, lv.data());
}

// ============================================================ Couleurs de la pochette
// pixels ARGB -> {fond sombre, couleur claire d'accent}
JNIEXPORT jintArray JNICALL
Java_com_math0235_noxplayer_Native_palette(JNIEnv* env, jclass, jintArray px) {
    jsize n = env->GetArrayLength(px);
    std::vector<jint> p((size_t)n);
    env->GetIntArrayRegion(px, 0, n, p.data());
    // histogramme de teintes pondéré par la saturation
    float hist[36] = {0};
    double sr = 0, sg = 0, sb = 0;
    for (jint c : p) {
        float r = ((c >> 16) & 255) / 255.0f, g = ((c >> 8) & 255) / 255.0f, b = (c & 255) / 255.0f;
        sr += r; sg += g; sb += b;
        float h, s, v;
        rgb2hsv(r, g, b, h, s, v);
        if (v > 0.12f && v < 0.97f) hist[(int)(h / 10) % 36] += s * s * v;
    }
    int best = 0;
    for (int i = 1; i < 36; i++) if (hist[i] > hist[best]) best = i;
    float total = 0;
    for (float x : hist) total += x;
    float h, s, v;
    rgb2hsv((float)(sr / std::max<jsize>(1, n)), (float)(sg / std::max<jsize>(1, n)), (float)(sb / std::max<jsize>(1, n)), h, s, v);
    float hue = total > 0.5f ? best * 10.0f + 5 : h;
    float sat = total > 0.5f ? std::min(0.45f, std::max(s, 0.18f)) : std::min(s, 0.12f);
    jint out[2] = {hsv2argb(hue, std::min(sat, 0.35f), 0.19f), hsv2argb(hue, std::min(sat, 0.28f) + 0.04f, 0.95f)};
    jintArray res = env->NewIntArray(2);
    env->SetIntArrayRegion(res, 0, 2, out);
    return res;
}

// ============================================================ Paroles .lrc
// -> JSON [{"t":ms,"l":"ligne"}...] ; t = -1 si pas de minutage
JNIEXPORT jstring JNICALL
Java_com_math0235_noxplayer_Native_lrc(JNIEnv* env, jclass, jstring text) {
    std::string src = jstr(env, text), out = "[", line;
    bool first = true;
    size_t i = 0;
    while (i <= src.size()) {
        size_t e = src.find('\n', i);
        if (e == std::string::npos) e = src.size();
        line = src.substr(i, e - i);
        i = e + 1;
        std::vector<long> times;
        size_t p = 0;
        bool meta = false;
        while (p < line.size() && line[p] == '[') {
            size_t q = line.find(']', p);
            if (q == std::string::npos) break;
            std::string tag = line.substr(p + 1, q - p - 1);
            int mm = 0, ss = 0, cs = 0;
            char dot = 0;
            if (sscanf(tag.c_str(), "%d:%d%c%d", &mm, &ss, &dot, &cs) >= 2 && tag.size() && isdigit((unsigned char)tag[0])) {
                size_t dp = tag.find_first_of(".:", tag.find(':') + 1);
                long frac = 0;
                if (dp != std::string::npos) {
                    std::string f = tag.substr(dp + 1);
                    frac = std::atol(f.c_str());
                    if (f.size() == 1) frac *= 100; else if (f.size() == 2) frac *= 10;
                }
                times.push_back((mm * 60L + ss) * 1000L + frac);
            } else {
                meta = true;  // [ar:], [ti:] ...
            }
            p = q + 1;
        }
        std::string txt = line.substr(std::min(p, line.size()));
        while (!txt.empty() && (txt.back() == '\r' || txt.back() == ' ')) txt.pop_back();
        if (meta && times.empty()) continue;
        if (times.empty()) times.push_back(-1);
        for (long t : times) {
            if (!first) out += ",";
            first = false;
            out += "{\"t\":" + std::to_string(t) + ",\"l\":\"";
            jsonEscape(out, txt);
            out += "\"}";
        }
        if (e >= src.size()) break;
    }
    out += "]";
    return env->NewStringUTF(out.c_str());
}

// ============================================================ Onde du lecteur
JNIEXPORT jfloatArray JNICALL
Java_com_math0235_noxplayer_Native_wave(JNIEnv* env, jclass, jlong seed, jint n) {
    if (n < 2) n = 2;
    uint64_t st = (uint64_t)seed * 6364136223846793005ULL + 1442695040888963407ULL;
    auto rnd = [&]() {
        st ^= st >> 33; st *= 0xff51afd7ed558ccdULL; st ^= st >> 33;
        return (float)((st >> 11) & 0xFFFFFF) / 16777216.0f;
    };
    int bumps = 3 + (int)(rnd() * 3);
    std::vector<float> cx, hy, wd, amp((size_t)n);
    for (int b = 0; b < bumps; b++) {
        cx.push_back(0.2f + rnd() * 0.65f);
        hy.push_back(0.35f + rnd() * 0.65f);
        wd.push_back(0.06f + rnd() * 0.1f);
    }
    for (int i = 0; i < n; i++) {
        float x = (float)i / (n - 1), v = 0.05f;
        for (int b = 0; b < bumps; b++) {
            float d = (x - cx[b]) / wd[b];
            v += hy[b] * std::exp(-d * d / 2);
        }
        float edge = std::min(1.0f, std::min(x, 1 - x) * 6.0f);
        amp[(size_t)i] = std::min(1.0f, v) * edge;
    }
    jfloatArray r = env->NewFloatArray(n);
    env->SetFloatArrayRegion(r, 0, n, amp.data());
    return r;
}

}  // extern "C"

import com.android.apksig.ApkSigner;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signs an APK with APK Signature Scheme v2 using Google's apksig library. */
public class Sign {
    public static void main(String[] a) throws Exception {
        if (a.length != 5) {
            System.err.println("usage: Sign <keystore.p12> <password> <alias> <in.apk> <out.apk>");
            System.exit(2);
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[0])) { ks.load(in, a[1].toCharArray()); }
        PrivateKey key = (PrivateKey) ks.getKey(a[2], a[1].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(a[2]);
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder(
                "tvhome", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(new File(a[3]))
                .setOutputApk(new File(a[4]))
                .setMinSdkVersion(26)
                .setV1SigningEnabled(false) // v2 is enough for minSdk 26; old apksig v1 needs removed JDK internals
                .setV2SigningEnabled(true)
                .build()
                .sign();
        System.out.println("signed " + a[4]);
    }
}

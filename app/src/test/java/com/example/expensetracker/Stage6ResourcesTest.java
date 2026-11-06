package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 6: static checks (JVM, no emulator) that the Firebase wiring is complete and that no resource is referenced
 * but missing - the usual cause of "cannot find symbol R.string.xxx" build errors.
 */
public class Stage6ResourcesTest {
    private static File file(String path) {
        File f = new File(path);
        return f.exists() ? f : new File("app/" + path); // when run from the project root
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(file(path).toPath()), StandardCharsets.UTF_8);
    }

    private static String readRoot(String name) throws Exception {
        File f = new File("../" + name);
        if (!f.exists()) f = new File(name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void gradleDeclaresFirebaseAuthFirestoreAndTheGoogleServicesPlugin() throws Exception {
        String app = read("build.gradle");
        assertTrue(app.contains("platform('com.google.firebase:firebase-bom:"));
        assertTrue(app.contains("'com.google.firebase:firebase-auth'"));
        assertTrue(app.contains("'com.google.firebase:firebase-firestore'"));
        // applied only when the file exists, so the project still builds before the user adds it
        assertTrue(app.contains("file('google-services.json').exists()"));
        assertTrue(app.contains("apply plugin: 'com.google.gms.google-services'"));
        assertTrue(readRoot("build.gradle").contains("com.google.gms.google-services' version"));
    }

    @Test
    public void manifestHasInternetAndTheCloudScreenIsPrivate() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android.permission.INTERNET"));
        int at = manifest.indexOf(".CloudAccountActivity");
        assertTrue(at > 0);
        String tag = manifest.substring(manifest.lastIndexOf("<activity", at), manifest.indexOf("/>", at));
        assertTrue(tag.contains("android:exported=\"false\""));
    }

    @Test
    public void firestoreRulesOnlyAllowTheOwner() throws Exception {
        String rules = readRoot("firestore.rules");
        assertTrue(rules.contains("request.auth.uid == userId"));
        assertTrue(rules.contains("match /users/{userId}"));
        assertFalse("rules must never be wide open", rules.contains("allow read, write: if true"));
    }

    @Test
    public void everyStringAndIdUsedInJavaOrXmlIsDefined() throws Exception {
        String strings = read("src/main/res/values/strings.xml");
        Set<String> defined = new HashSet<>();
        Matcher m = Pattern.compile("<string name=\"(\\w+)\"").matcher(strings);
        while (m.find()) defined.add(m.group(1));
        m = Pattern.compile("<(?:string-array|plurals) name=\"(\\w+)\"").matcher(strings);
        while (m.find()) defined.add(m.group(1));

        Set<String> ids = new HashSet<>();
        File layoutDir = file("src/main/res/layout");
        File menuDir = file("src/main/res/menu");
        StringBuilder xml = new StringBuilder();
        for (File dir : new File[]{layoutDir, menuDir}) {
            for (File f : dir.listFiles()) xml.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        }
        xml.append(read("src/main/AndroidManifest.xml"));
        Matcher idm = Pattern.compile("@\\+id/(\\w+)").matcher(xml);
        while (idm.find()) ids.add(idm.group(1));

        Matcher sx = Pattern.compile("@string/(\\w+)").matcher(xml);
        while (sx.find()) {
            if (sx.group(1).equals("appbar_scrolling_view_behavior")) continue; // provided by the Material library
            assertTrue("missing string " + sx.group(1), defined.contains(sx.group(1)));
        }

        for (File f : file("src/main/java/com/example/expensetracker").listFiles()) {
            if (!f.getName().endsWith(".java")) continue;
            String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Matcher rs = Pattern.compile("(?<![\\w.])R\\.string\\.(\\w+)").matcher(src);
            while (rs.find()) assertTrue(f.getName() + " uses missing string " + rs.group(1), defined.contains(rs.group(1)));
            Matcher ri = Pattern.compile("(?<![\\w.])R\\.id\\.(\\w+)").matcher(src);
            while (ri.find()) assertTrue(f.getName() + " uses missing id " + ri.group(1), ids.contains(ri.group(1)));
        }
    }

    @Test
    public void cloudScreenIsRegisteredOnceAndReachableFromSettingsAndTheMenu() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        assertEquals(1, manifest.split("\\.CloudAccountActivity", -1).length - 1);
        assertTrue(read("src/main/java/com/example/expensetracker/SettingsActivity.java").contains("CloudAccountActivity.class"));
        assertTrue(read("src/main/java/com/example/expensetracker/MainActivity.java").contains("CloudAccountActivity.class"));
    }
}

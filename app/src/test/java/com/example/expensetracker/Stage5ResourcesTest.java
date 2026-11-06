package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static checks of the XML resources (run on the JVM, no emulator): the About text, the four themes and the
 * launcher/welcome-screen wiring. Paths are relative to the app module (Gradle's working directory for unit tests).
 */
public class Stage5ResourcesTest {
    private static String read(String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) f = new File("app/" + path); // when run from the project root
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String stringRes(String name) throws Exception {
        Matcher m = Pattern.compile("<string name=\"" + name + "\"[^>]*>(.*?)</string>", Pattern.DOTALL)
                .matcher(read("src/main/res/values/strings.xml"));
        assertTrue("missing string " + name, m.find());
        return m.group(1).replace("\\n", "\n").replace("\\u2019", "\u2019").replace("\\u2013", "\u2013");
    }

    @Test
    public void aboutTextIsExactlyAsSpecified() throws Exception {
        String expected = "Personal_Expense_tracker_and_Budget_planning_ver_2026.0.0\n"
                + "\n"
                + "This app is made for academic purposes.\n"
                + "\n"
                + "Made by\n"
                + "Krishno Mondol\n"
                + "Department of CSE,KUET\n"
                + "Khulna, Bangladesh";
        assertEquals(expected, stringRes("about_text"));
    }

    @Test
    public void welcomeMessageIsExactlyAsSpecified() throws Exception {
        assertEquals("Welcome to the Personal Expense Tracker and Budget Planning Application",
                stringRes("welcome_message"));
    }

    @Test
    public void splashNeverExceedsTwoSeconds() {
        assertTrue(SplashConfig.DISPLAY_MS <= SplashConfig.MAX_DISPLAY_MS);
        assertEquals(2000L, SplashConfig.MAX_DISPLAY_MS);
        assertTrue(SplashConfig.FADE_OUT_MS < SplashConfig.DISPLAY_MS);
        assertTrue(SplashConfig.TEXT_DELAY_MS + SplashConfig.TEXT_MS <= SplashConfig.DISPLAY_MS);
    }

    @Test
    public void splashIsTheOnlyLauncherActivity() throws Exception {
        String manifest = read("src/main/AndroidManifest.xml");
        assertEquals(1, count(manifest, "android.intent.category.LAUNCHER"));
        int launcher = manifest.indexOf("android.intent.category.LAUNCHER");
        int activityStart = manifest.lastIndexOf("<activity", launcher);
        assertTrue(manifest.substring(activityStart, launcher).contains(".SplashActivity"));
    }

    @Test
    public void everyThemeDefinesEverySemanticColourAndLayoutsUseNoHardCodedHex() throws Exception {
        String attrs = read("src/main/res/values/attrs.xml");
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("<attr name=\"(et\\w+)\"").matcher(attrs);
        while (m.find()) names.add(m.group(1));
        assertTrue(names.size() >= 20);

        String themes = read("src/main/res/values/themes.xml");
        for (String theme : new String[]{"White", "Blue", "Red", "Dark"}) {
            int start = themes.indexOf("<style name=\"Theme.ExpenseTracker." + theme + "\"");
            assertTrue("theme missing: " + theme, start >= 0);
            String body = themes.substring(start, themes.indexOf("</style>", start));
            for (String n : names) assertTrue(theme + " lacks " + n, body.contains("name=\"" + n + "\""));
        }
        File layoutDir = new File("src/main/res/layout");
        if (!layoutDir.exists()) layoutDir = new File("app/src/main/res/layout");
        for (File f : layoutDir.listFiles()) {
            String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            assertTrue(f.getName() + " has a hard-coded colour",
                    !Pattern.compile("android:(textColor|background)=\"#[0-9A-Fa-f]{6,8}\"").matcher(xml).find());
        }
    }

    /** Regression: aapt2 failed with "Theme.MaterialComponents.DarkActionBar not found". Only real parents allowed. */
    @Test
    public void themeParentsAreExistingMaterialThemes() throws Exception {
        String themes = read("src/main/res/values/themes.xml");
        Matcher m = Pattern.compile("<style name=\"(Theme\\.ExpenseTracker\\.\\w+)\" parent=\"([^\"]+)\"").matcher(themes);
        int n = 0;
        while (m.find()) {
            n++;
            String parent = m.group(2);
            boolean ok = parent.equals("Theme.MaterialComponents")
                    || parent.equals("Theme.MaterialComponents.Light.DarkActionBar")
                    || parent.startsWith("Theme.ExpenseTracker.");
            assertTrue(m.group(1) + " has unknown parent " + parent, ok);
        }
        assertTrue(n >= 5); // White, Blue, Red, Dark, Splash
    }

    private static int count(String s, String part) {
        int n = 0, i = 0;
        while ((i = s.indexOf(part, i)) >= 0) {
            n++;
            i += part.length();
        }
        return n;
    }
}

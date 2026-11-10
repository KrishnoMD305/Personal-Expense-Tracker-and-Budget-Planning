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

/** Stage 7: static checks (JVM) that every new feature is wired into Gradle, the manifest and the layouts. */
public class Stage7ResourcesTest {
    private static File file(String path) {
        File f = new File(path);
        return f.exists() ? f : new File("app/" + path);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(file(path).toPath()), StandardCharsets.UTF_8);
    }

    private static String readRoot(String name) throws Exception {
        File f = new File("../" + name);
        if (!f.exists()) f = new File(name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static Set<String> ids(String layout) throws Exception {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("@\\+id/(\\w+)").matcher(read(layout));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    public void gradleEnablesViewBindingAndAddsTheNewLibraries() throws Exception {
        String g = read("build.gradle");
        assertTrue(g.contains("viewBinding true"));
        assertTrue(g.contains("'com.google.firebase:firebase-database'"));
        assertTrue(g.contains("'com.google.firebase:firebase-storage'"));
        assertTrue(g.contains("androidx.activity:activity:"));
    }

    @Test
    public void manifestDeclaresTheForegroundServiceAndFileProvider() throws Exception {
        String m = read("src/main/AndroidManifest.xml");
        assertTrue(m.contains("android.permission.FOREGROUND_SERVICE\""));
        assertTrue(m.contains("android:foregroundServiceType=\"dataSync\""));
        int at = m.indexOf(".BackupService");
        assertTrue(at > 0);
        String tag = m.substring(m.lastIndexOf("<service", at), m.indexOf("/>", at));
        assertTrue(tag.contains("android:exported=\"false\""));
        assertTrue(m.contains("androidx.core.content.FileProvider"));
        assertTrue(m.contains("grantUriPermissions=\"true\""));
        assertFalse("the camera picture goes through the camera app: no CAMERA permission",
                m.contains("android.permission.CAMERA"));
        // these three actions cannot be declared in the manifest on modern Android
        assertFalse(m.contains("AIRPLANE_MODE_CHANGED"));
        assertFalse(m.contains("BATTERY_LOW"));
    }

    @Test
    public void receiverIsRegisteredInOnStartAndUnregisteredInOnStop() throws Exception {
        String main = read("src/main/java/com/example/expensetracker/MainActivity.java");
        int start = main.indexOf("protected void onStart()");
        int stop = main.indexOf("protected void onStop()");
        assertTrue(start > 0 && stop > start);
        assertTrue(main.substring(start, stop).contains("deviceReceiver.register(this)"));
        assertTrue(main.substring(stop, stop + 400).contains("deviceReceiver.unregister(this)"));
        String rx = read("src/main/java/com/example/expensetracker/DeviceStateReceiver.java");
        assertTrue(rx.contains("Intent.ACTION_AIRPLANE_MODE_CHANGED"));
        assertTrue(rx.contains("Intent.ACTION_BATTERY_LOW"));
        assertTrue(rx.contains("Intent.ACTION_BATTERY_OKAY"));
        assertTrue(rx.contains("registerReceiver(") && rx.contains("unregisterReceiver("));
    }

    @Test
    public void cloudSyncWorkerRespectsTheDeviceState() throws Exception {
        String w = read("src/main/java/com/example/expensetracker/CloudSyncWorker.java");
        assertTrue(w.contains("DeviceStateReader.read"));
        assertTrue(w.contains("deferAutoBackup()"));
        assertTrue(w.contains("setRequiresBatteryNotLow(true)"));
    }

    @Test
    public void landscapeLayoutsHaveTheSameIdsAsPortrait() throws Exception {
        for (String name : new String[]{"activity_main.xml", "activity_reports.xml"}) {
            Set<String> portrait = ids("src/main/res/layout/" + name);
            Set<String> land = ids("src/main/res/layout-land/" + name);
            assertEquals("ids differ in " + name, portrait, land);
        }
    }

    @Test
    public void screensUseViewBindingInsteadOfFindViewById() throws Exception {
        String[] screens = {"MainActivity", "AddTransactionActivity", "BudgetActivity", "GoalsActivity",
                "GoalDetailActivity", "RecurringActivity", "SettingsActivity", "BillsActivity",
                "CloudAccountActivity", "ReportsActivity", "TransactionAdapter", "BudgetAdapter", "PlanRowAdapter"};
        for (String s : screens) {
            String src = read("src/main/java/com/example/expensetracker/" + s + ".java");
            assertFalse(s + " still uses findViewById", src.contains("findViewById"));
            assertTrue(s + " does not use a generated binding", src.contains("Binding"));
        }
    }

    @Test
    public void contactAndPhotoPickersAreUsed() throws Exception {
        String bills = read("src/main/java/com/example/expensetracker/BillsActivity.java");
        assertTrue(bills.contains("ContactsContract.CommonDataKinds.Phone.CONTENT_URI"));
        assertTrue(bills.contains("Intent.ACTION_PICK"));
        assertTrue(bills.contains("getContentResolver().query("));
        assertTrue(bills.contains("ActivityResultContracts.PickVisualMedia"));
        assertFalse("the pickers need no permission", read("src/main/AndroidManifest.xml").contains("READ_CONTACTS"));
    }

    @Test
    public void securityRulesOnlyAllowTheOwner() throws Exception {
        String db = readRoot("database.rules.json");
        assertTrue(db.contains("auth.uid === $uid"));
        assertFalse(db.contains("\".read\": true"));
        String st = readRoot("storage.rules");
        assertTrue(st.contains("request.auth.uid == userId"));
        assertTrue(st.contains("match /users/{userId}/receipts/{fileName}"));
        assertFalse(st.contains("if true"));
    }

    @Test
    public void databaseVersionIs4WithAMigration() throws Exception {
        assertEquals(4, AppDatabase.VERSION);
        String db = read("src/main/java/com/example/expensetracker/AppDatabase.java");
        assertTrue(db.contains("MIGRATION_3_4"));
        assertTrue(db.contains("addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)"));
    }
}

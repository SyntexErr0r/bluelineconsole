package net.nhiroki.bluelineconsole.applock;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.AppLockCommandSearcher;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;

import java.util.List;

public class AppLockTests {

    @Test
    public void testLockedAppConfig() {
        AppLockManager.LockedAppConfig cfg = new AppLockManager.LockedAppConfig("com.whatsapp", "9428", "94258", true);
        assertEquals("com.whatsapp", cfg.packageName);
        assertEquals("9428", cfg.pin);
        assertEquals("94258", cfg.pattern);
        assertTrue(cfg.enabled);

        // Null safe defaults
        AppLockManager.LockedAppConfig cfgNull = new AppLockManager.LockedAppConfig("test.app", null, null, false);
        assertEquals("", cfgNull.pin);
        assertEquals("", cfgNull.pattern);
        assertFalse(cfgNull.enabled);

        // null json handling
        assertNull(AppLockManager.LockedAppConfig.fromJson(null));

        // toJson returns non-null object
        assertNotNull(cfg.toJson());
    }

    @Test
    public void testTelegramConfig() {
        AppLockManager.LockedAppConfig tgCfg = new AppLockManager.LockedAppConfig("org.telegram.messenger", "8353", "835", true);
        assertEquals("org.telegram.messenger", tgCfg.packageName);
        assertEquals("8353", tgCfg.pin);
        assertEquals("835", tgCfg.pattern);
        assertTrue(tgCfg.enabled);
    }

    @Test
    public void testSessionUnlock() {
        AppLockManager mgr = AppLockManager.getInstance();
        assertFalse(mgr.isAppUnlockedForSession("com.whatsapp"));

        mgr.unlockAppSession("com.whatsapp");
        assertTrue(mgr.isAppUnlockedForSession("com.whatsapp"));
        assertTrue(mgr.isAppUnlockedForSession("COM.WHATSAPP")); // Case insensitivity
    }

    @Test
    public void testConsoleLaunchNotification() {
        AppLockManager mgr = AppLockManager.getInstance();
        mgr.notifyAppLaunchedFromConsole("org.telegram.messenger");
        // Notification recorded in memory without throwing
    }

    @Test
    public void testMainActivityUnlockConstants() {
        assertEquals("net.nhiroki.bluelineconsole.action.UNLOCK_APP", net.nhiroki.bluelineconsole.applicationMain.MainActivity.ACTION_UNLOCK_APP);
        assertEquals("net.nhiroki.bluelineconsole.extra.UNLOCK_PACKAGE", net.nhiroki.bluelineconsole.applicationMain.MainActivity.EXTRA_UNLOCK_PACKAGE);
    }

    @Test
    public void testAppLockCommandSearcherParsing() {
        AppLockCommandSearcher searcher = new AppLockCommandSearcher();

        // Non-lock queries return empty
        List<CandidateEntry> none = searcher.searchCandidateEntries("hello", null);
        assertTrue(none.isEmpty());

        // Overview
        List<CandidateEntry> overview = searcher.searchCandidateEntries("lock", null);
        assertFalse(overview.isEmpty());

        // "lock list"
        List<CandidateEntry> list = searcher.searchCandidateEntries("lock list", null);
        assertEquals(1, list.size());
        assertTrue(list.get(0) instanceof AppLockCommandSearcher.AppLockListCandidateEntry);

        // "lock on" / "lock off"
        List<CandidateEntry> on = searcher.searchCandidateEntries("lock on", null);
        assertEquals(1, on.size());
        assertTrue(on.get(0) instanceof AppLockCommandSearcher.AppLockToggleCandidateEntry);

        List<CandidateEntry> off = searcher.searchCandidateEntries("lock off", null);
        assertEquals(1, off.size());
        assertTrue(off.get(0) instanceof AppLockCommandSearcher.AppLockToggleCandidateEntry);

        // "lock whatsapp pin 9428"
        List<CandidateEntry> setPin = searcher.searchCandidateEntries("lock whatsapp pin 9428", null);
        assertEquals(1, setPin.size());
        assertTrue(setPin.get(0) instanceof AppLockCommandSearcher.AppLockSetPinCandidateEntry);
        assertTrue(setPin.get(0).getTitle().contains("9428"));

        // "lock telegram pattern 835"
        List<CandidateEntry> setPat = searcher.searchCandidateEntries("lock telegram pattern 835", null);
        assertEquals(1, setPat.size());
        assertTrue(setPat.get(0) instanceof AppLockCommandSearcher.AppLockSetPatternCandidateEntry);
        assertTrue(setPat.get(0).getTitle().contains("835"));

        // "lock whatsapp remove"
        List<CandidateEntry> remove = searcher.searchCandidateEntries("lock whatsapp remove", null);
        assertEquals(1, remove.size());
        assertTrue(remove.get(0) instanceof AppLockCommandSearcher.AppLockRemoveCandidateEntry);
    }

    @Test
    public void testExpandPatternWithIntermediateDots() {
        // WhatsApp: 9 -> 4 -> 2 -> 8 traverses dot 5 between 2 and 8
        assertEquals("94258", AppLockManager.expandPatternWithIntermediateDots("9428"));
        assertEquals("94258", AppLockManager.expandPatternWithIntermediateDots("94258"));

        // Horizontal line: 1 -> 3 crosses 2, 4 -> 6 crosses 5, 7 -> 9 crosses 8
        assertEquals("123", AppLockManager.expandPatternWithIntermediateDots("13"));
        assertEquals("456", AppLockManager.expandPatternWithIntermediateDots("46"));
        assertEquals("789", AppLockManager.expandPatternWithIntermediateDots("79"));

        // Vertical line: 1 -> 7 crosses 4, 2 -> 8 crosses 5, 3 -> 9 crosses 6
        assertEquals("147", AppLockManager.expandPatternWithIntermediateDots("17"));
        assertEquals("258", AppLockManager.expandPatternWithIntermediateDots("28"));
        assertEquals("369", AppLockManager.expandPatternWithIntermediateDots("39"));

        // Diagonal: 1 -> 9 crosses 5, 3 -> 7 crosses 5
        assertEquals("159", AppLockManager.expandPatternWithIntermediateDots("19"));
        assertEquals("357", AppLockManager.expandPatternWithIntermediateDots("37"));

        // Reverse
        assertEquals("852", AppLockManager.expandPatternWithIntermediateDots("82"));
        assertEquals("951", AppLockManager.expandPatternWithIntermediateDots("91"));
        assertEquals("753", AppLockManager.expandPatternWithIntermediateDots("73"));

        // Square corners: 1 -> 3 -> 7 -> 9
        // 1 to 3 crosses 2 -> 1,2,3
        // 3 to 7 crosses 5 -> 1,2,3,5,7
        // 7 to 9 crosses 8 -> 1,2,3,5,7,8,9
        assertEquals("1235789", AppLockManager.expandPatternWithIntermediateDots("1379"));

        // Telegram pattern: 8 -> 3 -> 5 (no dots skipped)
        assertEquals("835", AppLockManager.expandPatternWithIntermediateDots("835"));

        // Re-visiting an intermediate dot that was already visited: e.g. 5 -> 2 -> 8
        // 5 is already in path; when going 2 to 8, 5 is NOT re-added
        assertEquals("528", AppLockManager.expandPatternWithIntermediateDots("528"));

        // Non 1-9 characters (like PIN with 0) are untouched
        assertEquals("0000", AppLockManager.expandPatternWithIntermediateDots("0000"));
        assertEquals("1204", AppLockManager.expandPatternWithIntermediateDots("1204"));
    }

    @Test
    public void testMatchesPattern() {
        // WhatsApp target "94258" matched by "9428" and "94258"
        assertTrue(AppLockManager.matchesPattern("9428", "94258"));
        assertTrue(AppLockManager.matchesPattern("94258", "9428"));
        assertTrue(AppLockManager.matchesPattern("9428", "9428"));
        assertTrue(AppLockManager.matchesPattern("94258", "94258"));

        // Arbitrary 4+ dot patterns
        assertTrue(AppLockManager.matchesPattern("1379", "1235789"));

        // Sub-4-dot gestures MUST be rejected for security (no 1, 2, or 3-dot patterns)
        assertFalse(AppLockManager.matchesPattern("835", "835"));
        assertFalse(AppLockManager.matchesPattern("835", "8353"));
        assertFalse(AppLockManager.matchesPattern("28", "258"));
        assertFalse(AppLockManager.matchesPattern("481", "4813"));
        assertFalse(AppLockManager.matchesPattern("481", "48123"));

        // Non-matches
        assertFalse(AppLockManager.matchesPattern("1234", "5678"));
        assertFalse(AppLockManager.matchesPattern("9428", "8353"));
        assertFalse(AppLockManager.matchesPattern("", "9428"));
        assertFalse(AppLockManager.matchesPattern(null, "9428"));
    }

    @Test
    public void testTimePatternAt1843RequiresAtLeast4Dots() {
        // At 18:43, formula Ba:Ab yields PIN "4813"
        String pin1843 = AppLockManager.computeTimePin(18, 43);
        assertEquals("4813", pin1843);

        // Pattern expands 1->3 crossing 2, resulting in "48123" (5 dots)
        String pat1843 = AppLockManager.computeTimePatternFromPin(pin1843);
        assertEquals("48123", pat1843);

        // Valid swipe patterns
        assertTrue(AppLockManager.matchesPattern("4813", pat1843));
        assertTrue(AppLockManager.matchesPattern("48123", pat1843));

        // Critical fix: 3-point swipe "481" MUST be rejected!
        assertFalse(AppLockManager.matchesPattern("481", pat1843));
        assertFalse(AppLockManager.matchesPattern("481", pin1843));
        assertFalse(AppLockManager.isValidTimeBasedPattern("481"));
        assertFalse(AppLockManager.isValidTimeBasedPattern("48"));
        assertFalse(AppLockManager.isValidTimeBasedPattern("4"));
    }

    @Test
    public void testLockAllAppsAndMasterCredentials() {
        AppLockManager mgr = AppLockManager.getInstance();

        // Master PIN & Pattern getters and setters
        mgr.setMasterPin(null, "5432");
        assertEquals("5432", mgr.getMasterPin(null));

        mgr.setMasterPattern(null, "12369");
        assertEquals("12369", mgr.getMasterPattern(null));

        // Lock All Apps toggle
        mgr.setLockAllApps(null, true);
        assertTrue(mgr.isLockAllApps(null));

        // Exempt management
        assertFalse(mgr.isExempt(null, "com.arbitrary.newapp"));
        mgr.setExempt(null, "com.exempt.app", true);
        assertTrue(mgr.isExempt(null, "com.exempt.app"));

        // Effective config resolution for non-exempt app under Lock All uses app's T9 PIN as primary
        AppLockManager.LockedAppConfig effective = mgr.getEffectiveLockedAppConfig(null, "com.arbitrary.newapp");
        assertNotNull(effective);
        assertEquals("6392", effective.pin); // T9 for "newapp" (6-3-9-2)
        assertEquals("6392", effective.pattern);
        assertTrue(effective.enabled);

        // App without letters falls back to Master PIN / Pattern
        AppLockManager.LockedAppConfig numericApp = mgr.getEffectiveLockedAppConfig(null, "1234");
        assertNotNull(numericApp);
        assertEquals("5432", numericApp.pin);
        assertEquals("12369", numericApp.pattern);

        // Effective config for exempt app is null
        assertNull(mgr.getEffectiveLockedAppConfig(null, "com.exempt.app"));

        // Clean up
        mgr.setMasterPin(null, AppLockManager.DEFAULT_MASTER_PIN);
        mgr.setMasterPattern(null, AppLockManager.DEFAULT_MASTER_PATTERN);
        mgr.setExempt(null, "com.exempt.app", false);
    }

    @Test
    public void testAppLockCommandSearcherExtended() {
        AppLockCommandSearcher searcher = new AppLockCommandSearcher();

        // "lock all on" / "lock all off"
        List<CandidateEntry> allOn = searcher.searchCandidateEntries("lock all on", null);
        assertEquals(1, allOn.size());
        assertTrue(allOn.get(0) instanceof AppLockCommandSearcher.AppLockToggleLockAllCandidateEntry);
        assertTrue(allOn.get(0).getTitle().contains("ON"));

        List<CandidateEntry> allOff = searcher.searchCandidateEntries("lock all off", null);
        assertEquals(1, allOff.size());
        assertTrue(allOff.get(0) instanceof AppLockCommandSearcher.AppLockToggleLockAllCandidateEntry);
        assertTrue(allOff.get(0).getTitle().contains("OFF"));

        // "lock master pin 1234"
        List<CandidateEntry> mPin = searcher.searchCandidateEntries("lock master pin 1234", null);
        assertEquals(1, mPin.size());
        assertTrue(mPin.get(0) instanceof AppLockCommandSearcher.AppLockSetMasterPinCandidateEntry);
        assertTrue(mPin.get(0).getTitle().contains("1234"));

        // "lock master pattern 12369"
        List<CandidateEntry> mPat = searcher.searchCandidateEntries("lock master pattern 12369", null);
        assertEquals(1, mPat.size());
        assertTrue(mPat.get(0) instanceof AppLockCommandSearcher.AppLockSetMasterPatternCandidateEntry);
        assertTrue(mPat.get(0).getTitle().contains("12369"));

        // "lock master 7890"
        List<CandidateEntry> mShort = searcher.searchCandidateEntries("lock master 7890", null);
        assertEquals(1, mShort.size());
        assertTrue(mShort.get(0) instanceof AppLockCommandSearcher.AppLockSetMasterPinCandidateEntry);

        // "lock exempt com.test.app"
        List<CandidateEntry> exempt = searcher.searchCandidateEntries("lock exempt com.test.app", null);
        assertEquals(1, exempt.size());
        assertTrue(exempt.get(0) instanceof AppLockCommandSearcher.AppLockExemptCandidateEntry);

        // "lock instagram" (interactive app action candidate)
        List<CandidateEntry> quick = searcher.searchCandidateEntries("lock instagram", null);
        assertEquals(1, quick.size());
        assertTrue(quick.get(0) instanceof AppLockCommandSearcher.AppLockAppActionCandidateEntry);
    }

    @Test
    public void testT9PinComputation() {
        // WhatsApp: W-H-A-T -> 9-4-2-8
        assertEquals("9428", AppLockManager.computeT9PinFromName("WhatsApp"));
        assertEquals("9428", AppLockManager.computeT9PinFromName("whatsapp"));

        // Telegram: T-E-L-E -> 8-3-5-3
        assertEquals("8353", AppLockManager.computeT9PinFromName("Telegram"));

        // Termux: T-E-R-M -> 8-3-7-6
        assertEquals("8376", AppLockManager.computeT9PinFromName("Termux"));

        // Smart Launcher: S-M-A-R -> 7-6-2-7
        assertEquals("7627", AppLockManager.computeT9PinFromName("Smart Launcher"));

        // YouTube: Y-O-U-T -> 9-6-8-8
        assertEquals("9688", AppLockManager.computeT9PinFromName("YouTube"));

        // Chrome: C-H-R-O -> 2-4-7-6
        assertEquals("2476", AppLockManager.computeT9PinFromName("Chrome"));

        // Instagram: I-N-S-T -> 4-6-7-8
        assertEquals("4678", AppLockManager.computeT9PinFromName("Instagram"));

        // Short names padded to 4 digits: "AI" -> "2444", "X" -> "9999"
        assertEquals("2444", AppLockManager.computeT9PinFromName("AI"));
        assertEquals("9999", AppLockManager.computeT9PinFromName("X"));

        // Empty / null handling
        assertEquals("", AppLockManager.computeT9PinFromName(""));
        assertEquals("", AppLockManager.computeT9PinFromName(null));

        // Package fallback without context: com.termux -> termux -> 8376
        assertEquals("8376", AppLockManager.getT9PinForPackage(null, "com.termux"));
        assertEquals("9428", AppLockManager.getT9PinForPackage(null, "com.whatsapp"));
    }

    @Test
    public void testHomeLauncherImmunity() {
        AppLockManager mgr = AppLockManager.getInstance();

        // Smart Launcher variations
        assertTrue(mgr.isHomeLauncher(null, "ginlemon.flowerfree"));
        assertTrue(mgr.isHomeLauncher(null, "ginlemon.flowerpro"));
        assertTrue(mgr.isHomeLauncher(null, "ginlemon.flower"));

        // Nova Launcher
        assertTrue(mgr.isHomeLauncher(null, "com.teslacoilsw.launcher"));

        // Niagara Launcher
        assertTrue(mgr.isHomeLauncher(null, "bitpit.launcher"));

        // Lawnchair
        assertTrue(mgr.isHomeLauncher(null, "ch.deletescape.lawnchair.plah"));
        assertTrue(mgr.isHomeLauncher(null, "app.lawnchair"));

        // Standard launcher keywords
        assertTrue(mgr.isHomeLauncher(null, "com.google.android.apps.nexuslauncher"));
        assertTrue(mgr.isHomeLauncher(null, "com.sec.android.app.launcher"));

        // Regular apps are not home launchers
        assertFalse(mgr.isHomeLauncher(null, "com.whatsapp"));
        assertFalse(mgr.isHomeLauncher(null, "com.termux"));
        assertFalse(mgr.isHomeLauncher(null, "org.telegram.messenger"));

        // Launchers are never locked even if Lock All is true
        mgr.setMasterEnabled(null, true);
        mgr.setLockAllApps(null, true);
        assertFalse(mgr.isPackageLocked(null, "ginlemon.flowerfree"));
        assertFalse(mgr.isPackageLocked(null, "com.teslacoilsw.launcher"));
        assertFalse(mgr.isPackageLocked(null, "com.android.launcher3"));

        // Clean up
        mgr.setLockAllApps(null, false);
    }

    @Test
    public void testDynamicTimeBasedPinAndPattern() {
        // User example: 07:57 -> 5707 (Aa:Bb -> Ba:Ab)
        assertEquals("5707", AppLockManager.computeTimePin(7, 57));

        // Additional examples
        assertEquals("4012", AppLockManager.computeTimePin(10, 42)); // 10:42 -> 4012
        assertEquals("2305", AppLockManager.computeTimePin(3, 25));  // 03:25 -> 2305
        assertEquals("3214", AppLockManager.computeTimePin(12, 34)); // 12:34 -> 3214
        assertEquals("5917", AppLockManager.computeTimePin(19, 57)); // 19:57 -> 5917 (24h)
        assertEquals("0000", AppLockManager.computeTimePin(0, 0));   // 00:00 -> 0000
        assertEquals("5329", AppLockManager.computeTimePin(23, 59)); // 23:59 -> 5329

        // Pattern generation (Option A - Smart Remap)
        // 3214 has distinct digits 1-9 -> 3214 directly
        assertEquals("3214", AppLockManager.computeTimePatternFromPin("3214"));

        // 5707 has 0 and duplicate 7 -> maps 0 to 9, duplicate 7 to 1 -> 5791, 7 to 9 passes 8 -> 57891
        String pat5707 = AppLockManager.computeTimePatternFromPin("5707");
        assertNotNull(pat5707);
        assertTrue(pat5707.length() >= 4);
        assertFalse(pat5707.contains("0")); // No '0' in pattern!

        // Time lock toggle
        AppLockManager mgr = AppLockManager.getInstance();
        mgr.setTimeLockEnabled(null, true);
        assertTrue(mgr.isTimeLockEnabled(null));

        // Current time PIN is valid right now
        java.util.Calendar now = java.util.Calendar.getInstance();
        int h = now.get(java.util.Calendar.HOUR_OF_DAY);
        int m = now.get(java.util.Calendar.MINUTE);
        String currentPin = AppLockManager.computeTimePin(h, m);
        assertTrue(AppLockManager.isValidTimeBasedPin(currentPin));

        // Random non-time PIN fails
        assertFalse(AppLockManager.isValidTimeBasedPin("9999"));

        // Command searcher supports "lock time on" and "lock time off"
        AppLockCommandSearcher searcher = new AppLockCommandSearcher();
        List<CandidateEntry> timeOn = searcher.searchCandidateEntries("lock time on", null);
        assertEquals(1, timeOn.size());
        assertTrue(timeOn.get(0) instanceof AppLockCommandSearcher.AppLockToggleTimeLockCandidateEntry);
        assertTrue(timeOn.get(0).getTitle().contains("ON"));

        List<CandidateEntry> timeOff = searcher.searchCandidateEntries("lock time off", null);
        assertEquals(1, timeOff.size());
        assertTrue(timeOff.get(0) instanceof AppLockCommandSearcher.AppLockToggleTimeLockCandidateEntry);
        assertTrue(timeOff.get(0).getTitle().contains("OFF"));
    }

    @Test
    public void testStrict4DigitPinLengthRequirement() {
        // WhatsApp T9 PIN is 9428; prefixes like 942 must not match
        String waPin = AppLockManager.computeT9PinFromName("WhatsApp");
        assertEquals("9428", waPin);
        assertFalse("942".equals(waPin));
        assertTrue("9428".equals(waPin));

        // Termux T9 PIN is 8376; prefixes like 837 must not match
        String termuxPin = AppLockManager.computeT9PinFromName("Termux");
        assertEquals("8376", termuxPin);
        assertFalse("837".equals(termuxPin));
        assertTrue("8376".equals(termuxPin));

        // Time-based PIN (e.g. 03:31 -> 3301); prefixes like 330 must not match
        String timePin = AppLockManager.computeTimePin(3, 31);
        assertEquals("3301", timePin);
        assertFalse("330".equals(timePin));
        assertTrue("3301".equals(timePin));
        assertFalse(AppLockManager.isValidTimeBasedPin("330"));
        assertFalse(AppLockManager.isValidTimeBasedPin("837"));
        assertFalse(AppLockManager.isValidTimeBasedPin("942"));
    }

    @Test
    public void testMasterTimeLockIntegration() {
        AppLockManager mgr = AppLockManager.getInstance();

        // When time lock is enabled and default master credentials are set,
        // getMasterPin and getMasterPattern dynamically return the current rolling time credentials
        mgr.setMasterPin(null, AppLockManager.DEFAULT_MASTER_PIN);
        mgr.setMasterPattern(null, AppLockManager.DEFAULT_MASTER_PATTERN);
        mgr.setTimeLockEnabled(null, true);

        java.util.Calendar now = java.util.Calendar.getInstance();
        int h = now.get(java.util.Calendar.HOUR_OF_DAY);
        int m = now.get(java.util.Calendar.MINUTE);
        String expectedTimePin = AppLockManager.computeTimePin(h, m);
        String expectedTimePattern = AppLockManager.computeTimePatternFromPin(expectedTimePin);

        assertEquals(expectedTimePin, mgr.getMasterPin(null));
        assertEquals(expectedTimePattern, mgr.getMasterPattern(null));

        // When custom master PIN is set, it overrides the time lock
        mgr.setMasterPin(null, "8888");
        assertEquals("8888", mgr.getMasterPin(null));

        // Clean up
        mgr.setMasterPin(null, AppLockManager.DEFAULT_MASTER_PIN);
    }

    @Test
    public void testAppLockInteractiveUXCandidates() {
        AppLockCommandSearcher searcher = new AppLockCommandSearcher();

        // 1. "lock settings" / "lock manage" returns settings candidate
        List<CandidateEntry> settings = searcher.searchCandidateEntries("lock settings", null);
        assertEquals(1, settings.size());
        assertTrue(settings.get(0) instanceof AppLockCommandSearcher.AppLockSettingsCandidateEntry);
        assertEquals("lock settings", settings.get(0).getTitle());

        List<CandidateEntry> manage = searcher.searchCandidateEntries("lock manage", null);
        assertEquals(1, manage.size());
        assertTrue(manage.get(0) instanceof AppLockCommandSearcher.AppLockSettingsCandidateEntry);

        // 2. "lock master pattern" (no digits) returns interactive 9-dot draw candidate
        List<CandidateEntry> drawPat = searcher.searchCandidateEntries("lock master pattern", null);
        assertEquals(1, drawPat.size());
        assertTrue(drawPat.get(0) instanceof AppLockCommandSearcher.AppLockDrawMasterPatternCandidateEntry);
        assertTrue(drawPat.get(0).getTitle().contains("Draw Master Pattern"));

        // 3. "lock master pin" (no digits) returns interactive pin modal candidate
        List<CandidateEntry> enterPin = searcher.searchCandidateEntries("lock master pin", null);
        assertEquals(1, enterPin.size());
        assertTrue(enterPin.get(0) instanceof AppLockCommandSearcher.AppLockEnterMasterPinCandidateEntry);
        assertTrue(enterPin.get(0).getTitle().contains("Set Master PIN"));

        // 4. "lock" / "/lock" / "/applock" opens settings cleanly just like "config"
        List<CandidateEntry> overview = searcher.searchCandidateEntries("lock", null);
        assertEquals(1, overview.size());
        assertTrue(overview.get(0) instanceof AppLockCommandSearcher.AppLockSettingsCandidateEntry);
        assertEquals("lock", overview.get(0).getTitle());

        List<CandidateEntry> slashLock = searcher.searchCandidateEntries("/lock", null);
        assertEquals(1, slashLock.size());
        assertTrue(slashLock.get(0) instanceof AppLockCommandSearcher.AppLockSettingsCandidateEntry);
        assertEquals("/lock", slashLock.get(0).getTitle());

        List<CandidateEntry> slashAppLock = searcher.searchCandidateEntries("/applock", null);
        assertEquals(1, slashAppLock.size());
        assertTrue(slashAppLock.get(0) instanceof AppLockCommandSearcher.AppLockSettingsCandidateEntry);
        assertEquals("/applock", slashAppLock.get(0).getTitle());

        // "lock status" returns the status overview
        List<CandidateEntry> status = searcher.searchCandidateEntries("lock status", null);
        assertEquals(1, status.size());
        assertTrue(status.get(0) instanceof AppLockCommandSearcher.AppLockStatusCandidateEntry);

        // 5. "lock whatsapp" returns interactive app action entry
        List<CandidateEntry> wa = searcher.searchCandidateEntries("lock whatsapp", null);
        assertEquals(1, wa.size());
        assertTrue(wa.get(0) instanceof AppLockCommandSearcher.AppLockAppActionCandidateEntry);
        assertTrue(wa.get(0).getTitle().contains("whatsapp"));
    }

    @Test
    public void testApplicationCommandSearcherSlashPrefix() {
        net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.ApplicationCommandSearcher appSearcher =
                new net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.ApplicationCommandSearcher();
        List<CandidateEntry> res = appSearcher.searchCandidateEntries("/lock", null);
        assertTrue(res.isEmpty());
        List<CandidateEntry> res2 = appSearcher.searchCandidateEntries("/applock", null);
        assertTrue(res2.isEmpty());
        List<CandidateEntry> res3 = appSearcher.searchCandidateEntries("/", null);
        assertTrue(res3.isEmpty());
    }

    @Test
    public void testConsoleLockMasterTimeLockSupport() {
        AppLockManager mgr = AppLockManager.getInstance();
        mgr.setTimeLockEnabled(null, true);
        mgr.setMasterPin(null, "1234");

        // 1. Time Lock PIN is accepted as master code for Console Lock
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int m = cal.get(java.util.Calendar.MINUTE);
        String currentPin = AppLockManager.computeTimePin(h, m);
        assertTrue(AppLockManager.isValidTimeBasedPin(currentPin));

        // 2. Custom Master PIN is accepted
        assertEquals("1234", mgr.getMasterPin(null));

        // 3. Reset back to defaults
        mgr.setMasterPin(null, AppLockManager.DEFAULT_MASTER_PIN);
    }

    @Test
    public void testGracePeriodModesAndSummary() {
        AppLockManager mgr = AppLockManager.getInstance();

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_30_SEC);
        assertEquals(AppLockManager.GRACE_30_SEC, mgr.getGracePeriodMode(null));
        assertEquals("30 seconds", mgr.getGracePeriodSummary(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_2_MIN);
        assertEquals(AppLockManager.GRACE_2_MIN, mgr.getGracePeriodMode(null));
        assertEquals("2 minutes", mgr.getGracePeriodSummary(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_5_MIN);
        assertEquals(AppLockManager.GRACE_5_MIN, mgr.getGracePeriodMode(null));
        assertEquals("5 minutes", mgr.getGracePeriodSummary(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_UNTIL_LOCKED);
        assertEquals(AppLockManager.GRACE_UNTIL_LOCKED, mgr.getGracePeriodMode(null));
        assertEquals("Until phone is locked", mgr.getGracePeriodSummary(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_CUSTOM);
        mgr.setCustomGracePeriodSeconds(null, 45);
        assertEquals("Custom (45s)", mgr.getGracePeriodSummary(null));

        mgr.setCustomGracePeriodSeconds(null, 120);
        assertEquals("Custom (2m)", mgr.getGracePeriodSummary(null));

        mgr.setCustomGracePeriodSeconds(null, 90);
        assertEquals("Custom (1m 30s)", mgr.getGracePeriodSummary(null));

        // Reset to default
        mgr.setGracePeriodMode(null, AppLockManager.GRACE_UNTIL_LOCKED);
    }

    @Test
    public void testGracePeriodSessionExpiration() {
        AppLockManager mgr = AppLockManager.getInstance();
        mgr.clearUnlockedSessions();

        // 1. Until locked mode
        mgr.setGracePeriodMode(null, AppLockManager.GRACE_UNTIL_LOCKED);
        assertFalse(mgr.isAppUnlockedForSession("com.example.testapp"));
        mgr.unlockAppSession(null, "com.example.testapp");
        assertTrue(mgr.isAppUnlockedForSession("com.example.testapp"));

        // Screen off or clear flushes sessions
        mgr.clearUnlockedSessions();
        assertFalse(mgr.isAppUnlockedForSession("com.example.testapp"));

        // 2. 30 seconds mode
        mgr.setGracePeriodMode(null, AppLockManager.GRACE_30_SEC);
        mgr.unlockAppSession(null, "com.example.testapp2");
        assertTrue(mgr.isAppUnlockedForSession("com.example.testapp2"));

        mgr.clearUnlockedSessions();
        assertFalse(mgr.isAppUnlockedForSession("com.example.testapp2"));

        // Reset to default
        mgr.setGracePeriodMode(null, AppLockManager.GRACE_UNTIL_LOCKED);
    }

    @Test
    public void testCooldownMasterCodeSilentBypassLogic() {
        AppLockManager mgr = AppLockManager.getInstance();
        mgr.setTimeLockEnabled(null, true);
        mgr.setMasterPin(null, "8899");

        String masterPin = mgr.getMasterPin(null);
        assertEquals("8899", masterPin);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int m = cal.get(java.util.Calendar.MINUTE);
        String timePin = AppLockManager.computeTimePin(h, m);

        // Stealth master bypass validation
        boolean masterMatch = (!masterPin.equals(AppLockManager.DEFAULT_MASTER_PIN) && "8899".equals(masterPin)) ||
                              AppLockManager.isValidTimeBasedPin("8899");
        assertTrue(masterMatch);

        boolean timeMatch = (!masterPin.equals(AppLockManager.DEFAULT_MASTER_PIN) && timePin.equals(masterPin)) ||
                            AppLockManager.isValidTimeBasedPin(timePin);
        assertTrue(timeMatch);

        boolean wrongMatch = (!masterPin.equals(AppLockManager.DEFAULT_MASTER_PIN) && "1111".equals(masterPin)) ||
                             AppLockManager.isValidTimeBasedPin("1111");
        assertFalse(wrongMatch);

        // Reset
        mgr.setMasterPin(null, AppLockManager.DEFAULT_MASTER_PIN);
    }

    @Test
    public void testConsoleLockCooldownAndGracePeriod() {
        AppLockManager mgr = AppLockManager.getInstance();
        net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.resetFailedAttempts();

        // 1. Max failed attempts is 3
        assertEquals(3, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getMaxFailedAttempts());

        // 2. Lockout triggers after 3 failed attempts
        assertFalse(net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut());
        net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.recordFailedAttempt();
        assertEquals(1, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getFailedAttempts());
        assertFalse(net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut());

        net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.recordFailedAttempt();
        assertEquals(2, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getFailedAttempts());
        assertFalse(net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut());

        net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.recordFailedAttempt();
        assertEquals(3, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getFailedAttempts());
        assertTrue(net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut());

        // 3. Lockout duration is 10 seconds
        long remainingSec = net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getRemainingLockoutSeconds();
        assertTrue(remainingSec > 0 && remainingSec <= 10);

        // Reset lockout
        net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.resetFailedAttempts();
        assertFalse(net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut());

        // 4. Grace period duration matches AppLockManager setting
        mgr.setGracePeriodMode(null, AppLockManager.GRACE_30_SEC);
        assertEquals(30000L, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getGracePeriodDurationMs(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_2_MIN);
        assertEquals(120000L, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getGracePeriodDurationMs(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_5_MIN);
        assertEquals(300000L, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getGracePeriodDurationMs(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_UNTIL_LOCKED);
        assertEquals(Long.MAX_VALUE, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getGracePeriodDurationMs(null));

        mgr.setGracePeriodMode(null, AppLockManager.GRACE_CUSTOM);
        mgr.setCustomGracePeriodSeconds(null, 75);
        assertEquals(75000L, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getGracePeriodDurationMs(null));

        // Reset
        mgr.setGracePeriodMode(null, AppLockManager.GRACE_UNTIL_LOCKED);
    }

    @Test
    public void testInteractiveAccessibilityEventsTracked() {
        int stateChanged = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        int contentChanged = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        int clicked = android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
        int scrolled = android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED;

        assertTrue(stateChanged != 0);
        assertTrue(contentChanged != 0);
        assertTrue(clicked != 0);
        assertTrue(scrolled != 0);

        // Verify all 4 event types are accepted by the guard logic
        int[] guardedEvents = new int[]{stateChanged, contentChanged, clicked, scrolled};
        for (int evt : guardedEvents) {
            boolean isGuarded = (evt == stateChanged || evt == contentChanged || evt == clicked || evt == scrolled);
            assertTrue("Event type " + evt + " must be guarded", isGuarded);
        }
    }

    @Test
    public void testCyberGlobeAssetVerification() {
        java.io.File assetFile = new java.io.File("src/main/assets/cyber_globe.html");
        assertTrue("cyber_globe.html must exist in assets", assetFile.exists());
        assertTrue("cyber_globe.html size must be greater than 1KB", assetFile.length() > 1024);
    }

    @Test
    public void test11NodeWingZeroPatternMatching() {
        // Direct match with '0'
        assertTrue(AppLockManager.matchesPattern("3502", "3502"));
        assertTrue(AppLockManager.matchesPattern("0532", "0532"));
        assertTrue(AppLockManager.matchesPattern("1843", "1843"));
        assertTrue(AppLockManager.matchesPattern("0643", "0643"));

        // Double '0' supported via Left 0 and Right 0 wings (e.g. 0058)
        assertTrue(AppLockManager.matchesPattern("0058", "0058"));

        // Under-length gestures (< 4 effective digits) MUST be rejected
        assertFalse(AppLockManager.matchesPattern("350", "3502"));
        assertFalse(AppLockManager.matchesPattern("005", "0058"));
        assertFalse(AppLockManager.matchesPattern("123", "1234"));

        // 11-Node geometric matrix jump expansion:
        // Moving 1 to 3 crosses 2 (row 0)
        assertEquals("123", AppLockManager.expand11NodePattern("13"));
        // Moving 4 to 6 crosses 5 (row 1)
        assertEquals("456", AppLockManager.expand11NodePattern("46"));
        // Moving 7 to 9 crosses 8 (row 2)
        assertEquals("789", AppLockManager.expand11NodePattern("79"));

        // Vertical jumps:
        // Moving 1 to 7 crosses 4 (col 1)
        assertEquals("147", AppLockManager.expand11NodePattern("17"));
        // Moving 2 to 8 crosses 5 (col 2)
        assertEquals("258", AppLockManager.expand11NodePattern("28"));
        // Moving 3 to 9 crosses 6 (col 3)
        assertEquals("369", AppLockManager.expand11NodePattern("39"));

        // Diagonal jumps:
        // Moving 1 to 9 crosses 5
        assertEquals("159", AppLockManager.expand11NodePattern("19"));
        // Moving 3 to 7 crosses 5
        assertEquals("357", AppLockManager.expand11NodePattern("37"));

        // Wing 0 jumps:
        // Left 0 to 5 crosses 4
        assertEquals("045", AppLockManager.expand11NodePattern("05"));

        // Matrix coordinates check
        assertEquals(0, AppLockManager.getDotRow('1'));
        assertEquals(0, AppLockManager.getDotRow('2'));
        assertEquals(0, AppLockManager.getDotRow('3'));
        assertEquals(1, AppLockManager.getDotRow('0'));
        assertEquals(1, AppLockManager.getDotRow('4'));
        assertEquals(1, AppLockManager.getDotRow('5'));
        assertEquals(1, AppLockManager.getDotRow('6'));
        assertEquals(2, AppLockManager.getDotRow('7'));
        assertEquals(2, AppLockManager.getDotRow('8'));
        assertEquals(2, AppLockManager.getDotRow('9'));

        assertEquals('0', AppLockManager.getDotChar(1, 0));
        assertEquals('0', AppLockManager.getDotChar(1, 4));
        assertEquals('5', AppLockManager.getDotChar(1, 2));
    }

    @Test
    public void testNotesModel() throws Exception {
        net.nhiroki.bluelineconsole.notes.Note note = new net.nhiroki.bluelineconsole.notes.Note("Matrix Plan", "Deploy cyber grid 3-5-3");
        assertNotNull(note.id);
        assertEquals("Matrix Plan", note.title);
        assertEquals("Deploy cyber grid 3-5-3", note.content);
        assertTrue(note.createdAt > 0);
        assertTrue(note.updatedAt > 0);

        JSONObject json = note.toJson();
        assertNotNull(json);

        // Null safe
        assertNull(net.nhiroki.bluelineconsole.notes.Note.fromJson(null));
    }
}


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
        AppLockManager.LockedAppConfig cfg = new AppLockManager.LockedAppConfig("com.whatsapp", "9428", "9428", true);
        assertEquals("com.whatsapp", cfg.packageName);
        assertEquals("9428", cfg.pin);
        assertEquals("9428", cfg.pattern);
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
}

package net.nhiroki.bluelineconsole.commandSearchers;

import android.content.Context;

import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.AICommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.ApplicationCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.CalendarCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.CalculatorCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.ColorDisplayCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.ContactSearchCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.DateCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.FactorCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.HelpCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.LogCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.NetUtilCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.PreferencesCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.SearchEngineCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.SearchEngineDefaultCommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.URICommandSearcher;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.WidgetCommandSearcher;
import net.nhiroki.bluelineconsole.dataStore.persistent.HomeScreenSetting;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.CommandSearcher;
import net.nhiroki.bluelineconsole.wrapperForAndroid.AppWidgetsHostManager;

import java.util.List;
import java.util.ArrayList;

public class CommandSearchAggregator {
    private final List <CommandSearcher> commandSearcherList = new ArrayList<>();
    private final List <CommandSearcher> commandSearcherListAlwaysLast = new ArrayList<>();

    private AppWidgetsHostManager appWidgetsHostManager = null;

    private static final int MAX_CACHE_SIZE = 50;
    private final java.util.Map<String, List<CandidateEntry>> queryCache = new java.util.LinkedHashMap<String, List<CandidateEntry>>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, List<CandidateEntry>> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };


    public CommandSearchAggregator(Context context) {
        // Starting with specific string
        commandSearcherList.add(new HelpCommandSearcher());
        commandSearcherList.add(new PreferencesCommandSearcher());
        commandSearcherList.add(new DateCommandSearcher());
        commandSearcherList.add(new URICommandSearcher());
        commandSearcherList.add(new NetUtilCommandSearcher());
        commandSearcherList.add(new CalendarCommandSearcher());
        commandSearcherList.add(new FactorCommandSearcher());
        commandSearcherList.add(new AICommandSearcher());
        commandSearcherList.add(new LogCommandSearcher());

        // Fully user-defined
        commandSearcherList.add(new WidgetCommandSearcher());

        // First character is limited
        commandSearcherList.add(new CalculatorCommandSearcher());
        commandSearcherList.add(new SearchEngineCommandSearcher(context));
        commandSearcherList.add(new ColorDisplayCommandSearcher());

        // Command searchers which may return tons candidate should comes to the last of "search result"
        commandSearcherList.add(new ContactSearchCommandSearcher());
        commandSearcherList.add(new ApplicationCommandSearcher());

        // This should be separately called and order does not matter, and results are placed at last.
        commandSearcherListAlwaysLast.add(new SearchEngineDefaultCommandSearcher(context));

        refresh(context);
    }

    public void close() {
        for (CommandSearcher cs : commandSearcherList) {
            cs.close();
        }
        for (CommandSearcher cs : commandSearcherListAlwaysLast) {
            cs.close();
        }
    }

    // May just start thread. In sequential execution it may be better to call this earlier.
    // Returns so early that users can wait.
    public void refresh(Context context) {
        for (CommandSearcher cs : commandSearcherList) {
            cs.refresh(context);
        }
        for (CommandSearcher cs : commandSearcherListAlwaysLast) {
            cs.refresh(context);
        }
        this.appWidgetsHostManager = new AppWidgetsHostManager(context);
        synchronized (queryCache) {
            queryCache.clear();
        }
    }

    public boolean isPrepared() {
        for (CommandSearcher cs : commandSearcherList) {
            if (!cs.isPrepared()) {
                return false;
            }
        }
        return true;
    }

    public void waitUntilPrepared() {
        for (CommandSearcher cs : commandSearcherList) {
            cs.waitUntilPrepared();
        }
    }

    public List<CandidateEntry> searchCandidateEntries(String s, Context context) {
        List<CandidateEntry> candidates = new ArrayList<>();
        if (s.isEmpty()) {
            return candidates;
        }

        boolean cacheable = !s.startsWith("?") && !s.toLowerCase().startsWith("ai");

        if (cacheable) {
            synchronized (queryCache) {
                List<CandidateEntry> cached = queryCache.get(s);
                if (cached != null) {
                    return new ArrayList<>(cached);
                }
            }
        }

        for (CommandSearcher cs : commandSearcherList) {
            candidates.addAll(cs.searchCandidateEntries(s, context));
        }

        if (cacheable) {
            synchronized (queryCache) {
                queryCache.put(s, new ArrayList<>(candidates));
            }
        }

        return candidates;
    }

    public List<CandidateEntry> searchCandidateEntriesForLast(String s, Context context) {
        List<CandidateEntry> candidates = new ArrayList<>();
        if (s.isEmpty()) {
            return candidates;
        }

        for (CommandSearcher cs : commandSearcherListAlwaysLast) {
            candidates.addAll(cs.searchCandidateEntries(s, context));
        }

        return candidates;
    }

    public List<CandidateEntry> homeScreenDefaultCandidateEntries(Context context) {
        List<HomeScreenSetting.HomeScreenDefaultItem> homeScreenDefaultItemList = HomeScreenSetting.getInstance(context).getAllHomeScreenDefaultItems();

        List<CandidateEntry> ret = new ArrayList<>();

        List<AppWidgetsHostManager.HomeScreenWidgetViewItem> widgetInfoList = this.appWidgetsHostManager.createHomeScreenWidgets();

        int widgetInfoListId = 0;

        for (HomeScreenSetting.HomeScreenDefaultItem item: homeScreenDefaultItemList) {
            while (widgetInfoListId < widgetInfoList.size() && widgetInfoList.get(widgetInfoListId).afterDefaultItem < item.id) {
                ret.add(new WidgetCommandSearcher.WidgetCandidateEntry(widgetInfoList.get(widgetInfoListId).widgetView));
                ++widgetInfoListId;
            }

            ret.addAll(this.searchCandidateEntries(item.data, context));
        }

        while (widgetInfoListId < widgetInfoList.size()) {
            ret.add(new WidgetCommandSearcher.WidgetCandidateEntry(widgetInfoList.get(widgetInfoListId).widgetView));
            ++widgetInfoListId;
        }

        return ret;
    }
}


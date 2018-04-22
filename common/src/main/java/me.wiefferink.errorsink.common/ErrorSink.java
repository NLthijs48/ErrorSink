package me.wiefferink.errorsink.common;

import ninja.leaping.configurate.ConfigurationNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ErrorSink {

    private static ErrorSinkPlugin plugin;

    public static void init(ErrorSinkPlugin plugin) {
        if (ErrorSink.plugin != null) {
            throw new RuntimeException("Already initialized: " + ErrorSink.plugin);
        }
        ErrorSink.plugin = plugin;
        initMatcherMap();
    }

    private static void initMatcherMap() {
        List<String[]> matchSectionNames = Arrays
                .asList(new String[] { "events", "filters"}, new String[] { "events", "rules" }, new String[] { "breadcrumbs", "filters"}, new String[] { "breadcrumbs", "rules" });
        ConfigurationNode root = ErrorSink.getPlugin().getPluginConfig();
        Map<List<Object>, EventRuleMatcher> matcherMap = ErrorSink.getPlugin().getMatcherMap();

        for(String[] matchSectionName : matchSectionNames) {
            ConfigurationNode matchSection = ErrorSink.getPlugin().getPluginConfig().getNode((Object[]) matchSectionName);
            if(matchSection != null) {
                for(ConfigurationNode eventRule : matchSection.getChildrenMap().values()) {
                    matcherMap.put(Arrays.asList(eventRule.getPath()), new EventRuleMatcher(eventRule, root.getNode("parts")));
                }
            }
        }
    }

    public static ErrorSinkPlugin getPlugin() {
        if (ErrorSink.plugin == null) {
            throw new RuntimeException("Not initialized!");
        }
        return ErrorSink.plugin;
    }

}

package me.wiefferink.errorsink.tools;

import me.wiefferink.errorsink.ErrorSink;
import org.bstats.Metrics;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;

public class Analytics {

    /**
     * Start analytics tracking.
     */
    public static void start() {
        // bStats statistics
        try {
            Metrics metrics = new Metrics(ErrorSink.getInstance());

            // Messages sent since the last collection time (15 minutes)
            metrics.addCustomChart(new Metrics.SingleLineChart("messages_sent") {
                @Override
                public int getValue() {
                    return ErrorSink.getInstance().getAndResetMessageSent();
                }
            });

            // Number of rules defined for each category
            for (String ruleKey : Arrays.asList("events.filters", "events.rules", "breadcrumbs.rules", "breadcrumbs.filters")) {
                metrics.addCustomChart(new Metrics.SingleLineChart(ruleKey.replace(".", "_")) {
                    @Override
                    public int getValue() {
                        ConfigurationSection ruleSection = ErrorSink.getInstance().getConfig().getConfigurationSection(ruleKey);
                        if (ruleSection != null) {
                            return ruleSection.getKeys(false).size();
                        }
                        return 0;
                    }
                });
            }

            Log.debug("Started bstats.org statistics service");
        } catch (Exception e) {
            Log.debug("Could not start bstats.org statistics service");
        }
    }

}

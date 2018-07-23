package me.wiefferink.errorsink.common;

import io.sentry.event.EventBuilder;
import ninja.leaping.configurate.ConfigurationNode;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public interface ErrorSinkPlugin {

    String getServerName();

    String getRelease();

    void increaseMessageSent();

    Map<List<Object>, EventRuleMatcher> getMatcherMap();

    ConfigurationNode getPluginConfig();

    /**
     * Match a rule to an event
     *
     * @param matcherPath The path of the matcher rules to use
     * @param message     The message to match
     * @param level       The level to match
     * @param throwable   The exception to match
     * @param threadName  The thread name to match
     * @param loggerName  The logger name to match
     * @return A map with the captured groups if a match is found, otherwise null
     */
    default Map<String, String> match(List<Object> matcherPath, String message, Level level, Throwable throwable, String threadName, String loggerName) {
        EventRuleMatcher matcher = getMatcherMap().get(matcherPath);
        if(matcher == null) {
            Log.error("Trying to match path", matcherPath, "but there is no EventRuleMatcher!");
            Log.error(matcherPath);
            return null;
        }

        return matcher.matches(message, level, throwable, threadName, loggerName);
    }

    default long getTimeStamp(LogEvent event) {
        return event.getTimeMillis();
    }

    SortedMap<String, String> getLoadedPlugins();

    String getServerVersion();

    int getOnlinePlayers();

    void addExtraData(EventBuilder builder);
}

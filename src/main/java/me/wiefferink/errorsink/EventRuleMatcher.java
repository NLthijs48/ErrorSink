package me.wiefferink.errorsink;

import me.wiefferink.errorsink.tools.Log;
import me.wiefferink.errorsink.tools.Utils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class EventRuleMatcher {

	private Set<Integer> levelMatches;
	private List<Pattern> messagePatterns;
	private List<Pattern> exceptionPatterns;
	private List<Pattern> threadPatterns;
	private List<Pattern> loggerNamePatterns;

	private ConfigurationSection criteria;

	public EventRuleMatcher(ConfigurationSection criteria) {
		if(criteria == null) {
			criteria = new YamlConfiguration();
		}
		this.criteria = criteria;

		Log.debug("Preparing EventRuleMatcher:", criteria.getCurrentPath());

		// Level preparation
		List<String> levels = Utils.singleOrList(criteria, "matchLevel");
		if(levels != null) {
			levelMatches = new HashSet<>();
			for (String levelString : levels) {
				Level level = Level.toLevel(levelString, null);
				if (level == null) {
					Log.warn("Incorrect level \"" + levelString + "\" at", criteria.getCurrentPath() + ".matchLevel");
				} else {
					levelMatches.add(level.intLevel());
				}
			}
		}
		Log.debug("  levels:", levels, levelMatches);

		// Message matching preparation
		messagePatterns = getRegexPatterns(criteria, "matchMessage");
		Log.debug("  messageRegexes:", messagePatterns);

		// Exception matching preparation
		exceptionPatterns = getRegexPatterns(criteria, "matchException");
		Log.debug("  exceptionRegexes:", exceptionPatterns);

		// Thread pattern preparation
		threadPatterns = getRegexPatterns(criteria, "matchThreadName");
		Log.debug("  threadRegexes:", threadPatterns);

		// Logger pattern preparation
		loggerNamePatterns = getRegexPatterns(criteria, "matchLoggerName");
		Log.debug("  loggerNameRegexes:", loggerNamePatterns);

	}

	/**
	 * Get a list of regexes from the config
	 *
	 * @param section The section to get the regexes from
	 * @param path    The path in the section to try get the regexes form
	 * @return List of compiled regexes if the path has one or a list of strings, otherwise null
	 */
	private List<Pattern> getRegexPatterns(ConfigurationSection section, String path) {
		List<Pattern> result = null;
		List<String> regexes = Utils.singleOrList(criteria, path);
		if (regexes != null) {
			result = new ArrayList<>();
			for (String regex : regexes) {
				try {
					result.add(Pattern.compile(regex));
				} catch (PatternSyntaxException e) {
					Log.warn("Incorrect exception regex \"" + regex + "\" at", criteria.getCurrentPath() + "." + path + ":", ExceptionUtils.getStackTrace(e));
				}
			}
		}
		return result;
	}

	/**
	 * Match a list of patterns to an input
	 *
	 * @param input    The input to check
	 * @param patterns The patterns to match
	 * @return true if one of the patterns matches, otherwise false
	 */
	private boolean matchesAny(String input, List<Pattern> patterns) {
		if (input == null || patterns == null) {
			return false;
		}

		for (Pattern messagePattern : patterns) {
			if (messagePattern.matcher(input).find()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if details of an event match this matcher
	 * @param message   The message of the event
	 * @param level     The level of the event
	 * @param throwable The Throwable of the event
	 * @param threadName The name of the thread to match
	 * @return true if the event matches this rule, otherwise false
	 */
	public boolean matches(String message, Level level, Throwable throwable, String threadName, String loggerName) {

		// Level match
		if(levelMatches != null && !levelMatches.contains(level.intLevel())) {
			return false;
		}

		// Message match
		if (messagePatterns != null && !matchesAny(message, messagePatterns)) {
			return false;
		}

		// Exception match
		if (exceptionPatterns != null && throwable != null && !matchesAny(ExceptionUtils.getStackTrace(throwable), exceptionPatterns)) {
			return false;
		}

		// Thread name match
		if (threadPatterns != null && !matchesAny(threadName, threadPatterns)) {
			return false;
		}

		// Logger name match
		if (loggerNamePatterns != null && !matchesAny(loggerName, loggerNamePatterns)) {
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		return "EventRuleMatcher(path: " + criteria.getCurrentPath() + ")";
	}

}

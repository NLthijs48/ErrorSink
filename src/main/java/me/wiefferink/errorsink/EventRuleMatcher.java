package me.wiefferink.errorsink;

import me.wiefferink.errorsink.tools.Log;
import me.wiefferink.errorsink.tools.Utils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class EventRuleMatcher {

	private static final Pattern NAMED_GROUPS = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

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
		if(regexes != null) {
			result = new ArrayList<>();
			for(String regex : regexes) {
				try {
					result.add(Pattern.compile(regex));
				} catch(PatternSyntaxException e) {
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
	private boolean matchesAny(String input, List<Pattern> patterns, Map<String, String> replacements) {
		if(input == null || patterns == null) {
			return false;
		}

		for(Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(input);
			if(matcher.find()) {
				// Collect named matcher groups
				Matcher groupMatcher = NAMED_GROUPS.matcher(pattern.pattern());
				while(groupMatcher.find()) {
					try {
						replacements.put(groupMatcher.group(1), matcher.group(groupMatcher.group(1)));
					} catch(IllegalArgumentException ignored) {

					}
				}

				// Collect numbered matcher groups
				for(int groupIndex = 1; groupIndex <= matcher.groupCount(); groupIndex++) {
					replacements.put(groupIndex + "", matcher.group(groupIndex));
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Match a rule to an event
	 * @param message     The message to match
	 * @param level       The level to match
	 * @param throwable   The exception to match
	 * @param threadName  The thread name to match
	 * @param loggerName  The logger name to match
	 * @return A map with the captured groups if a match is found, otherwise null
	 */
	public Map<String, String> matches(String message, Level level, Throwable throwable, String threadName, String loggerName) {
		Map<String, String> groups = new HashMap<>();

		// Level match
		if(levelMatches != null && !levelMatches.contains(level.intLevel())) {
			return null;
		}

		// Message match
		if(messagePatterns != null && !matchesAny(message, messagePatterns, groups)) {
			return null;
		}

		// Exception match
		if(exceptionPatterns != null && throwable != null && !matchesAny(ExceptionUtils.getStackTrace(throwable), exceptionPatterns, groups)) {
			return null;
		}

		// Thread name match
		if(threadPatterns != null && !matchesAny(threadName, threadPatterns, groups)) {
			return null;
		}

		// Logger name match
		if(loggerNamePatterns != null && !matchesAny(loggerName, loggerNamePatterns, groups)) {
			return null;
		}

		return groups;
	}

	@Override
	public String toString() {
		return "EventRuleMatcher(path: " + criteria.getCurrentPath() + ")";
	}

}

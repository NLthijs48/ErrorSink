package me.wiefferink.errorsink;

import me.wiefferink.errorsink.tools.Log;
import me.wiefferink.errorsink.tools.Utils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class EventRuleMatcher {

	private Set<Integer> levelMatches;
	private Set<Pattern> messagePatterns;
	private Set<Pattern> exceptionPatterns;

	private ConfigurationSection criteria;

	public EventRuleMatcher(ConfigurationSection criteria) {
		if(criteria == null) {
			criteria = new YamlConfiguration();
		}
		this.criteria = criteria;

		Log.debug("Preparing EventRuleMatcher:", criteria.getCurrentPath());

		// Level preparation
		List<String> levels = Utils.singleOrList(criteria, "level");
		if(levels != null) {
			levelMatches = new HashSet<>();
			for(String level : levels) {
				levelMatches.add(Level.toLevel(level, Level.OFF).intLevel());
			}
		}
		Log.debug("  levels:", levels, levelMatches);

		// Message matching preparation
		List<String> messageRegexes = Utils.singleOrList(criteria, "message");
		if(messageRegexes != null) {
			messagePatterns = new HashSet<>();
			for(String messageRegex : messageRegexes) {
				try {
					messagePatterns.add(Pattern.compile(messageRegex));
				} catch(PatternSyntaxException e) {
					Log.warn("Incorrect message regex \"" + messageRegex + "\" at", criteria.getCurrentPath() + ":", ExceptionUtils.getStackTrace(e));
				}
			}
		}
		Log.debug("  messageRegexes:", messageRegexes, messagePatterns);

		// Exception matching preparation
		List<String> exceptionRegexes = Utils.singleOrList(criteria, "exception");
		if(exceptionRegexes != null) {
			exceptionPatterns = new HashSet<>();
			for(String exceptionRegex : exceptionRegexes) {
				try {
					exceptionPatterns.add(Pattern.compile(exceptionRegex));
				} catch(PatternSyntaxException e) {
					Log.warn("Incorrect exception regex \"" + exceptionRegex + "\" at", criteria.getCurrentPath() + ":", ExceptionUtils.getStackTrace(e));
				}
			}
		}
		Log.debug("  exceptionRegexes:", exceptionRegexes, messagePatterns);
	}

	/**
	 * Check if details of an event match this matcher
	 * @param message   The message of the event
	 * @param level     The level of the event
	 * @param throwable The Throwable of the event
	 * @return true if the event matches this rule, otherwise false
	 */
	public boolean matches(String message, Level level, Throwable throwable) {
		//Log.debug("matching", criteria.getCurrentPath());

		// Level match
		if(levelMatches != null && !levelMatches.contains(level.intLevel())) {
			//Log.debug("  no level match");
			return false;
		}

		// Message match
		if(messagePatterns != null) {
			boolean messageMatches = false;
			if(message != null) {
				for(Pattern messagePattern : messagePatterns) {
					if(messagePattern.matcher(message).matches()) {
						messageMatches = true;
						break;
					}
				}
			}
			if(!messageMatches) {
				//Log.debug("  no message match");
				return false;
			}
		}

		// Exception match
		if(exceptionPatterns != null) {
			boolean exceptionMatches = false;
			if(throwable != null) {
				String exception = ExceptionUtils.getStackTrace(throwable);
				for(Pattern exceptionPattern : exceptionPatterns) {
					if(exceptionPattern.matcher(exception).matches()) {
						exceptionMatches = true;
						break;
					}
				}
			}
			if(!exceptionMatches) {
				//Log.debug("  no exception match");
				return false;
			}
		}

		//Log.debug("  matches!");
		return true;
	}

	@Override
	public String toString() {
		return "EventRuleMatcher(path: " + criteria.getCurrentPath() + ")";
	}

}

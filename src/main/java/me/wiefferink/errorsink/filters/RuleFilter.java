package me.wiefferink.errorsink.filters;

import me.wiefferink.errorsink.ErrorSink;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.bukkit.configuration.ConfigurationSection;

public class RuleFilter extends AbstractFilter {

	private ConfigurationSection rules;

	/**
	 * Constructor
	 */
	public RuleFilter() {
		super(Filter.Result.DENY, Filter.Result.NEUTRAL);
		rules = ErrorSink.getInstance().getConfig().getConfigurationSection("eventRules");
	}

	/**
	 * Return result based on the level
	 * @param level The level to check
	 * @return The result
	 */
	private Filter.Result process(String message, Level level, Throwable throwable) {
		if(rules == null) {
			// TODO log
			return Result.NEUTRAL;
		}

		// Match all rules
		for(String ruleKey : rules.getKeys(false)) {
			// Skip rules that are not dropping events
			if(!rules.getBoolean(ruleKey + ".drop")) {
				continue;
			}

			// Match event
			if(ErrorSink.getInstance().match("eventRules." + ruleKey, message, level, throwable)) {
				return onMatch;
			}
		}

		return onMismatch;
	}

	@Override
	public Filter.Result filter(Logger logger, Level level, Marker marker, String message, Object... params) {
		return process(ParameterizedMessage.format(message, params), level, null);
	}

	@Override
	public Filter.Result filter(Logger logger, Level level, Marker marker, Object message, Throwable throwable) {
		return process(message == null ? null : message.toString(), level, throwable);
	}

	@Override
	public Filter.Result filter(Logger logger, Level level, Marker marker, Message message, Throwable throwable) {
		return process(message.getFormattedMessage(), level, throwable);
	}

	@Override
	public Filter.Result filter(LogEvent logEvent) {
		return process(logEvent.getMessage().getFormattedMessage(), logEvent.getLevel(), logEvent.getThrown());
	}

}

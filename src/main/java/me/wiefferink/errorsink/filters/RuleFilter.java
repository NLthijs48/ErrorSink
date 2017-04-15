package me.wiefferink.errorsink.filters;

import me.wiefferink.errorsink.ErrorSink;
import me.wiefferink.errorsink.tools.Log;
import org.apache.commons.lang.exception.ExceptionUtils;
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
		rules = ErrorSink.getInstance().getConfig().getConfigurationSection("events.filters");
	}

	/**
	 * Return result based on the level
	 * @param level The level to check
	 * @return The result
	 */
	private Filter.Result process(String message, Level level, Throwable throwable, String threadName, String loggerName) {
		try {
			if(rules == null) {
				return onMismatch;
			}

			// Match all rules
			for(String ruleKey : rules.getKeys(false)) {
				// Match event
				if(ErrorSink.getInstance().match(
						"events.filters." + ruleKey,
						message,
						level,
						throwable,
						threadName,
						loggerName) != null) {
					return onMatch;
				}
			}
		} catch(Exception e) {
			// Causing exceptions within a filter will crash the server, therefore we catch everything
			Log.error("Filter failed to execute rules:", ExceptionUtils.getStackTrace(e));
		}

		return onMismatch;
	}

	@Override
	public Filter.Result filter(Logger logger, Level level, Marker marker, String message, Object... params) {
		return process(
				ParameterizedMessage.format(message, params),
				level,
				null,
				null,
				logger.getName()
		);
	}

	@Override
	public Filter.Result filter(Logger logger, Level level, Marker marker, Object message, Throwable throwable) {
		return process(
				message == null ? null : message.toString(),
				level,
				throwable,
				null,
				logger == null ? null : logger.getName()
		);
	}

	@Override
	public Filter.Result filter(Logger logger, Level level, Marker marker, Message message, Throwable throwable) {
		return process(
				message == null ? null : message.getFormattedMessage(),
				level,
				throwable,
				null,
				logger == null ? null : logger.getName()
		);
	}

	@Override
	public Filter.Result filter(LogEvent logEvent) {
		if(logEvent == null) {
			return onMismatch;
		}
		return process(
				logEvent.getMessage() == null ? null : logEvent.getMessage().getFormattedMessage(),
				logEvent.getLevel(),
				logEvent.getThrown(),
				logEvent.getThreadName(),
				logEvent.getLoggerName()
		);
	}

}

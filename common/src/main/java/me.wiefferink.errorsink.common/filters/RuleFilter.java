package me.wiefferink.errorsink.common.filters;

import com.google.common.collect.Lists;
import me.wiefferink.errorsink.common.CommonUtils;
import me.wiefferink.errorsink.common.ErrorSink;
import me.wiefferink.errorsink.common.Log;
import ninja.leaping.configurate.ConfigurationNode;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;

public class RuleFilter extends AbstractFilter {

	private ConfigurationNode filters;
	private ConfigurationNode rules;

	/**
	 * Constructor
	 */
	public RuleFilter(ConfigurationNode root) {
		super(Filter.Result.DENY, Filter.Result.NEUTRAL);
		filters = root.getNode("events", "filters");
		rules = root.getNode("events", "rules");
	}

	/**
	 * Return result based on the level
	 * @param level The level to check
	 * @return The result
	 */
	private Filter.Result process(String message, Level level, Throwable throwable, String threadName, String loggerName) {
		try {
			if(filters == null) {
				return onMismatch;
			}

			// Check if this event should bypass the filters
			if(rules != null) {
				// Match all rules
				for(Object ruleKey : rules.getChildrenMap().keySet()) {
					// Match event
					if(rules.getNode(ruleKey, "bypassFilters").getBoolean() && ErrorSink.getPlugin().match(
							Lists.newArrayList("events", "rules", ruleKey),
							message,
							level,
							throwable,
							threadName,
							loggerName) != null) {
						return onMismatch;
					}
				}
			}

			// Match all filters
			for(Object ruleKey : filters.getChildrenMap().keySet()) {
				// Match event
				if(ErrorSink.getPlugin().match(
						Lists.newArrayList("events", "filters", ruleKey),
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
			Log.error("Filter failed to execute filters:", ExceptionUtils.getStackTrace(e));
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

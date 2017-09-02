package me.wiefferink.errorsink.filters;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * Filter messages from the logger of this plugin, preventing recursive logging
 */
public class ErrorSinkFilter extends AbstractFilter {

	private Result filter(String loggerName) {
		if (loggerName != null && loggerName.startsWith("me.wiefferink.errorsink")) {
			return Result.DENY;
		}
		return Result.NEUTRAL;
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
		return filter(logger == null ? null : logger.getName());
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
		return filter(logger == null ? null : logger.getName());
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
		return filter(logger == null ? null : logger.getName());
	}

	@Override
	public Result filter(LogEvent event) {
		return filter(event == null ? null : event.getLoggerName());
	}
}

package me.wiefferink.errorsink.editors;

import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.BreadcrumbBuilder;
import com.getsentry.raven.event.EventBuilder;
import me.wiefferink.errorsink.ErrorSink;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BreadcrumbCollector extends EventEditor {

	private final LinkedList<LogEvent> breadcrumbs = new LinkedList<>();
	private int maximumEntries = 50;
	private Pattern tagPrefix = Pattern.compile("^\\[[a-zA-Z0-9-_]+\\] ");
	private Logger logger;
	private Appender breadcrumbAppender;

	public BreadcrumbCollector(Logger logger) {
		this.logger = logger;
		maximumEntries = ErrorSink.getInstance().getConfig().getInt("breadcrumbs.maximumEntries", 50);
		breadcrumbAppender = new AbstractAppender("Breadcrumb Builder", null, null, false) {
			@Override
			public void append(LogEvent logEvent) {
				synchronized(breadcrumbs) {
					breadcrumbs.add(logEvent);
					if(breadcrumbs.size() > maximumEntries) {
						breadcrumbs.removeFirst();
					}
				}
			}
		};
		breadcrumbAppender.start();
		logger.addAppender(breadcrumbAppender);
	}

	@Override
	public void processEvent(EventBuilder eventBuilder, LogEvent event) {
		List<Breadcrumb> result = new ArrayList<>();
		synchronized(breadcrumbs) {
			for(LogEvent logEvent : breadcrumbs) {
				BreadcrumbBuilder builder = new BreadcrumbBuilder();

				builder.setTimestamp(new Date(logEvent.getMillis()));
				builder.setLevel(getBreadcrumbLevel(logEvent));

				// Match tags in the front of the message and set that as category instead
				builder.setCategory(" "); // Empty to indicate regular logging
				builder.setType("default");
				String message = logEvent.getMessage().getFormattedMessage();
				Matcher matcher = tagPrefix.matcher(message);
				if(matcher.find()) {
					message = message.substring(matcher.group().length());
					builder.setCategory(matcher.group().substring(1, matcher.group().length() - 2));
				} else if(message.contains(" lost connection: ")) {
					builder.setCategory("<<<");
					builder.setType("navigation");
				} else if(message.contains(" logged in with entity id ")) {
					builder.setCategory(">>>");
					builder.setType("navigation");
				} else if(message.contains(" issued server command: ") && !message.contains("CONSOLE issued server command: ")) {
					builder.setCategory("Command");
					builder.setType("user");
				} else if(logEvent.getThreadName().contains("Chat Thread")) {
					builder.setCategory("Chat");
					builder.setType("user");
				}
				builder.setMessage(message);

				Map<String, String> data = new HashMap<>();
				if(logEvent.getThrown() != null) {
					data.put("exception", ExceptionUtils.getStackTrace(logEvent.getThrown()));
				}
				builder.setData(data);

				result.add(builder.build());
			}
		}
		eventBuilder.withBreadcrumbs(result);
	}

	/**
	 * Get a breadcrumb level based on a LogRecord
	 * @param logEvent The record to calculate a level for
	 * @return The level of the record
	 */
	private String getBreadcrumbLevel(LogEvent logEvent) {
		if(logEvent.getLevel().equals(Level.WARN)) {
			return "warning";
		} else if(logEvent.getLevel().isAtLeastAsSpecificAs(Level.ERROR)) {
			return "error";
		} else {
			return "info";
		}
	}

	@Override
	public void shutdown() {
		logger.removeAppender(breadcrumbAppender);
	}

}

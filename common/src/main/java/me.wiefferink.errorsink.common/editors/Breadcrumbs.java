package me.wiefferink.errorsink.common.editors;

import com.google.common.collect.Lists;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.EventBuilder;
import me.wiefferink.errorsink.common.ErrorSink;
import me.wiefferink.errorsink.common.EventEditor;
import me.wiefferink.errorsink.common.Log;
import ninja.leaping.configurate.ConfigurationNode;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Breadcrumbs extends EventEditor {

	private final LinkedList<LogEvent> breadcrumbs = new LinkedList<>();
	private int maximumEntries = 50;
	private Pattern tagPrefix = Pattern.compile("^\\[[a-zA-Z0-9-_]+\\] ");
	private Logger logger;
	private Appender breadcrumbAppender;
	private ConfigurationNode rules;
	private ConfigurationNode filters;
	private boolean hasImmutableMethod = false;

	public Breadcrumbs(Logger logger) {
		this.logger = logger;
		ConfigurationNode root = ErrorSink.getPlugin().getPluginConfig();
		rules = root.getNode("breadcrumbs", "rules");
		filters = root.getNode("breadcrumbs", "filters");
		maximumEntries = root.getNode("breadcrumbs", "maximumEntries").getInt(50);

		// Test if LogEvent has toImmutable()
		try {
			LogEvent.class.getMethod("toImmutable");
			hasImmutableMethod = true;
		} catch(Exception ignored) {
		}

		breadcrumbAppender = new AbstractAppender("Breadcrumb Builder", null, null, false) {
			@Override
			public void append(LogEvent event) {
				String formattedMessage = null;
				if(event.getMessage() != null) {
					formattedMessage = event.getMessage().getFormattedMessage();
				}
				if(filters != null) {
					for(Object filterKey : filters.getChildrenMap().keySet()) {
						if(ErrorSink.getPlugin().match(
								Lists.newArrayList("breadcrumbs", "filters", filterKey),
								formattedMessage,
								event.getLevel(),
								event.getThrown(),
								event.getThreadName(),
								event.getLoggerName()) != null) {
							return;
						}
					}
				}

				synchronized(breadcrumbs) {
					if(hasImmutableMethod) {
						event = event.toImmutable();
					}

					breadcrumbs.add(event);
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
		List<LogEvent> breadcrumbsCopy = new ArrayList<>(breadcrumbs);
		for(LogEvent breadcrumbEvent : breadcrumbsCopy) {
			BreadcrumbBuilder breadcrumb = new BreadcrumbBuilder();

			String message = null;
			if(breadcrumbEvent.getMessage() != null) {
				message = breadcrumbEvent.getMessage().getFormattedMessage();
			}

			// Default to empty message to prevent Raven error
			if(message == null) {
				breadcrumb.setMessage("");
			} else {
				breadcrumb.setMessage(message);
			}

			// Set defaults
			breadcrumb.setTimestamp(new Date(ErrorSink.getPlugin().getTimeStamp(breadcrumbEvent)));
			breadcrumb.setLevel(getBreadcrumbLevel(breadcrumbEvent));
			breadcrumb.setCategory(" "); // Empty to indicate regular logging
			breadcrumb.setType(Breadcrumb.Type.DEFAULT);
			Map<String, String> data = new HashMap<>();
			if(breadcrumbEvent.getThrown() != null) {
				data.put("exception", ExceptionUtils.getStackTrace(breadcrumbEvent.getThrown()));
			}

			if(rules != null) {
				for(Map.Entry<Object, ? extends ConfigurationNode> entry: rules.getChildrenMap().entrySet()) {

					String ruleKey = (String) entry.getKey();
					ConfigurationNode rule = entry.getValue();

					Map<String, String> replacements = ErrorSink.getPlugin().match(
							Arrays.asList(rule.getPath()),
							message,
							breadcrumbEvent.getLevel(),
							breadcrumbEvent.getThrown(),
							breadcrumbEvent.getThreadName(),
							breadcrumbEvent.getLoggerName()
					);
					if(replacements == null) {
						continue;
					}

					// Category
					String newCategory = applyReplacements(rule.getString("category"), replacements);
					if(newCategory != null) {
						breadcrumb.setCategory(newCategory);
					}

					// Type
					String typeString = applyReplacements(rule.getString("type"), replacements);
					if(typeString != null) {
						try {
							breadcrumb.setType(Breadcrumb.Type.valueOf(typeString.toUpperCase()));
						} catch(IllegalArgumentException e) {
							Log.error("Incorrect breadcrumb type \"" + typeString + "\" for rule", String.join(".", (String[]) rules.getPath()) + "." + ruleKey);
						}
					}

					// Message
					String newMessage = applyReplacements(rule.getString("message"), replacements);
					if(newMessage != null) {
						breadcrumb.setMessage(newMessage);
					}

					// Level
					String levelString = applyReplacements(rule.getString("level"), replacements);
					if(levelString != null) {
						try {
							breadcrumb.setLevel(Breadcrumb.Level.valueOf(levelString.toUpperCase()));
						} catch(IllegalArgumentException e) {
							Log.warn("Incorrect breadcrumb level \"" + levelString + "\" for rule", String.join(".", (String[]) rules.getPath()) + "." + ruleKey);
						}
					}

					// Add data
					ConfigurationNode dataSection = rule.getNode("data");
						for(Object dataKey : dataSection.getChildrenMap().keySet()) {
							// Sentry only supports string values, but we support lists and thing like that this way
							Object dataValue = getValue(dataSection, new Object[] { dataKey }, replacements);
							if (dataValue != null) {
								data.put(applyReplacements((String) dataKey, replacements), dataValue.toString());
							}
						}
				}

			}
			// Set data (merged data from all rules)
			if(!data.isEmpty()) {
				breadcrumb.setData(data);
			}

			result.add(breadcrumb.build());
		}
		eventBuilder.withBreadcrumbs(result);
	}

	/**
	 * Get a breadcrumb level based on a LogRecord
	 * @param logEvent The record to calculate a level for
	 * @return The level of the record
	 */
	private Breadcrumb.Level getBreadcrumbLevel(LogEvent logEvent) {
		if (logEvent.getLevel().equals(Level.WARN)) {
			return Breadcrumb.Level.WARNING;
		} else if (logEvent.getLevel().equals(Level.ERROR)) {
			return Breadcrumb.Level.ERROR;
		} else if (logEvent.getLevel().equals(Level.FATAL)) {
			return Breadcrumb.Level.CRITICAL;
		} else if (logEvent.getLevel().equals(Level.DEBUG)) {
			return Breadcrumb.Level.DEBUG;
		} else {
			return Breadcrumb.Level.INFO;
		}
	}

	@Override
	public void shutdown() {
		logger.removeAppender(breadcrumbAppender);
	}

}

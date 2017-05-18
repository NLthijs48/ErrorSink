package me.wiefferink.errorsink.editors;

import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.BreadcrumbBuilder;
import com.getsentry.raven.event.EventBuilder;
import me.wiefferink.errorsink.ErrorSink;
import me.wiefferink.errorsink.tools.Log;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
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
	private ConfigurationSection rules;
	private ConfigurationSection filters;

	public Breadcrumbs(Logger logger) {
		this.logger = logger;
		rules = ErrorSink.getInstance().getConfig().getConfigurationSection("breadcrumbs.rules");
		filters = ErrorSink.getInstance().getConfig().getConfigurationSection("breadcrumbs.filters");
		maximumEntries = ErrorSink.getInstance().getConfig().getInt("breadcrumbs.maximumEntries", 50);

		breadcrumbAppender = new AbstractAppender("Breadcrumb Builder", null, null, false) {
			@Override
			public void append(LogEvent event) {
				String formattedMessage = null;
				if(event.getMessage() != null) {
					formattedMessage = event.getMessage().getFormattedMessage();
				}
				if(filters != null) {
					for(String filterKey : filters.getKeys(false)) {
						if(ErrorSink.getInstance().match(
								"breadcrumbs.filters." + filterKey,
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
			breadcrumb.setTimestamp(new Date(ErrorSink.getInstance().getTimeStamp(breadcrumbEvent)));
			breadcrumb.setLevel(getBreadcrumbLevel(breadcrumbEvent));
			breadcrumb.setCategory(" "); // Empty to indicate regular logging
			breadcrumb.setType(Breadcrumb.Type.DEFAULT);
			Map<String, String> data = new HashMap<>();
			if(breadcrumbEvent.getThrown() != null) {
				data.put("exception", ExceptionUtils.getStackTrace(breadcrumbEvent.getThrown()));
			}

			if(rules != null) {
				for(String ruleKey : rules.getKeys(false)) {
					ConfigurationSection rule = rules.getConfigurationSection(ruleKey);
					if(rule == null) {
						continue;
					}

					Map<String, String> replacements = ErrorSink.getInstance().match(
							"breadcrumbs.rules." + ruleKey,
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
							Log.error("Incorrect breadcrumb type \"" + typeString + "\" for rule", rules.getCurrentPath() + "." + ruleKey);
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
							Log.warn("Incorrect breadcrumb level \"" + levelString + "\" for rule", rules.getCurrentPath() + "." + ruleKey);
						}
					}

					// Add data
					ConfigurationSection dataSection = rule.getConfigurationSection("data");
					if(dataSection != null) {
						for(String dataKey : dataSection.getKeys(false)) {
							// Sentry only supports string values, but we support lists and thing like that this way
							Object dataValue = getValue(dataSection, dataKey, replacements);
							if (dataValue != null) {
								data.put(applyReplacements(dataKey, replacements), dataValue.toString());
							}
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

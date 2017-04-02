package me.wiefferink.errorsink;

import com.getsentry.raven.Raven;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.interfaces.ExceptionInterface;
import com.getsentry.raven.event.interfaces.MessageInterface;
import com.getsentry.raven.log4j2.SentryAppender;
import com.getsentry.raven.util.Util;
import me.wiefferink.errorsink.editors.EventEditor;
import me.wiefferink.errorsink.tools.Log;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extension of the default SentryAppender class to customize error collection for Bukkit plugins
 */
public class BukkitSentryAppender extends SentryAppender {

	private Set<EventEditor> eventEditors;

	public BukkitSentryAppender(Raven raven) {
		super(raven);

		eventEditors = new HashSet<>();
	}

	/**
	 * Add an EventEditor to enhance events with more information
	 * @param eventEditor The EventEditor to add
	 */
	public void addEventEditor(EventEditor eventEditor) {
		eventEditors.add(eventEditor);
	}

	/**
	 * Shutdown this appender
	 */
	public void shutdown() {
		for(EventEditor editor : eventEditors) {
			editor.shutdown();
		}
	}

	/**
	 * Transforms a {@link Level} into an {@link Event.Level}.
	 * @param level original level as defined in log4j2.
	 * @return log level used within raven.
	 */
	protected static Event.Level formatLevel(Level level) {
		if(level.isAtLeastAsSpecificAs(Level.FATAL)) {
			return Event.Level.FATAL;
		} else if(level.isAtLeastAsSpecificAs(Level.ERROR)) {
			return Event.Level.ERROR;
		} else if(level.isAtLeastAsSpecificAs(Level.WARN)) {
			return Event.Level.WARNING;
		} else if(level.isAtLeastAsSpecificAs(Level.INFO)) {
			return Event.Level.INFO;
		} else {
			return Event.Level.DEBUG;
		}
	}

	/**
	 * Builds an Event based on the logging event.
	 * Copy from the super class with minor changes
	 * @param event Log generated.
	 * @return Event containing details provided by the logging system.
	 */
	@Override
	protected Event buildEvent(LogEvent event) {
		Message eventMessage = event.getMessage();
		EventBuilder eventBuilder = new EventBuilder()
				.withTimestamp(new Date(event.getMillis())) // Changed event.getTimeMillis() to event.getMillis(), Minecraft uses log4j 2.0-beta9, Sentry builds with 2.5
				.withMessage(eventMessage.getFormattedMessage())
				.withLogger(event.getLoggerName())
				.withLevel(formatLevel(event.getLevel()))
				.withExtra(THREAD_NAME, event.getThreadName());

		if(!Util.isNullOrEmpty(serverName)) {
			eventBuilder.withServerName(serverName.trim());
		}

		if(!Util.isNullOrEmpty(release)) {
			eventBuilder.withRelease(release.trim());
		}

		if(!Util.isNullOrEmpty(environment)) {
			eventBuilder.withEnvironment(environment.trim());
		}

		if(!eventMessage.getFormattedMessage().equals(eventMessage.getFormat())) {
			eventBuilder.withSentryInterface(new MessageInterface(
					eventMessage.getFormat(),
					formatMessageParameters(eventMessage.getParameters()),
					eventMessage.getFormattedMessage()));
		}

		Throwable throwable = event.getThrown();
		if(throwable != null) {
			eventBuilder.withSentryInterface(new ExceptionInterface(throwable));
		}

		eventBuilder.withCulprit(event.getLoggerName());

		if(event.getContextStack() != null && !event.getContextStack().asList().isEmpty()) {
			eventBuilder.withExtra(LOG4J_NDC, event.getContextStack().asList());
		}

		if(event.getContextMap() != null) {
			for(Map.Entry<String, String> contextEntry : event.getContextMap().entrySet()) {
				if(extraTags.contains(contextEntry.getKey())) {
					eventBuilder.withTag(contextEntry.getKey(), contextEntry.getValue());
				} else {
					eventBuilder.withExtra(contextEntry.getKey(), contextEntry.getValue());
				}
			}
		}

		if(event.getMarker() != null) {
			eventBuilder.withTag(LOG4J_MARKER, event.getMarker().getName());
		}

		for(Map.Entry<String, String> tagEntry : tags.entrySet()) {
			eventBuilder.withTag(tagEntry.getKey(), tagEntry.getValue());
		}

		raven.runBuilderHelpers(eventBuilder);

		// Run EventEditors
		for(EventEditor eventEditor : eventEditors) {
			try {
				eventEditor.processEvent(eventBuilder, event);
			} catch(Exception e) {
				// TODO log properly
				Log.debug("EventEditor " + eventEditor.getClass().getName() + " failed:");
				e.printStackTrace(System.out);
			}
		}

		Log.debug("sending event to sentry:", eventBuilder);

		return eventBuilder.build();
	}
}

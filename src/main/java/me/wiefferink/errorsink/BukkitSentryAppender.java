package me.wiefferink.errorsink;

import com.getsentry.raven.Raven;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.interfaces.ExceptionInterface;
import com.getsentry.raven.event.interfaces.MessageInterface;
import com.getsentry.raven.event.interfaces.SentryException;
import com.getsentry.raven.log4j2.SentryAppender;
import com.getsentry.raven.util.Util;
import me.wiefferink.errorsink.editors.EventEditor;
import me.wiefferink.errorsink.tools.Log;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;

import java.util.Date;
import java.util.Deque;
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
	 * Shutdown this appender and the used EventEditors
	 */
	public void shutdown() {
		for(EventEditor editor : eventEditors) {
			editor.shutdown();
		}
	}

	/**
	 * Convert a log4j2 level to an Event.Level
	 * SentryAppender#formatLevel() is not used because Level#isMoreSpecificThan() does not exist in the log4j version of 1.11 and lower
	 * @param level Level to convert
	 * @return Event.Level from Raven-Java
	 */
	public Event.Level levelToEventLevel(Level level) {
		if(level.equals(Level.WARN)) {
			return Event.Level.WARNING;
		} else if(level.equals(Level.ERROR) || level.equals(Level.FATAL)) {
			return Event.Level.ERROR;
		} else if(level.equals(Level.DEBUG)) {
			return Event.Level.DEBUG;
		} else {
			return Event.Level.INFO;
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
		// Basics
		Message eventMessage = event.getMessage();
		EventBuilder eventBuilder = new EventBuilder()
				.withTimestamp(new Date(ErrorSink.getInstance().getTimeStamp(event)))
				.withMessage(eventMessage.getFormattedMessage())
				.withLogger(event.getLoggerName())
				.withLevel(levelToEventLevel(event.getLevel()))
				.withExtra(THREAD_NAME, event.getThreadName());

		// Servername
		if(!Util.isNullOrEmpty(serverName)) {
			eventBuilder.withServerName(serverName.trim());
		}

		// Release version
		if(!Util.isNullOrEmpty(release)) {
			eventBuilder.withRelease(release.trim());
		}

		// Environment
		if(!Util.isNullOrEmpty(environment)) {
			eventBuilder.withEnvironment(environment.trim());
		}

		// Message format (if message formatting is used)
		if(eventMessage.getFormattedMessage() != null && !eventMessage.getFormattedMessage().equals(eventMessage.getFormat())) {
			eventBuilder.withSentryInterface(new MessageInterface(
					eventMessage.getFormat(),
					formatMessageParameters(eventMessage.getParameters()),
					eventMessage.getFormattedMessage()));
		}

		// Exception
		Throwable throwable = event.getThrown();
		if(throwable != null) {
			Deque<SentryException> exceptionDeque = SentryException.extractExceptionQueue(throwable);
			if(!exceptionDeque.isEmpty()) {
				SentryException firstException = exceptionDeque.removeFirst();
				if(firstException != null) {
					// If message in exception is empty, use the log message
					String exceptionMessage = firstException.getExceptionMessage();
					if(exceptionMessage == null || exceptionMessage.isEmpty()) {
						exceptionMessage = eventMessage.getFormattedMessage();
					}
					firstException = new SentryException(
							exceptionMessage,
							firstException.getExceptionClassName(),
							firstException.getExceptionPackageName(),
							firstException.getStackTraceInterface()
					);
					exceptionDeque.addFirst(firstException);
				}
			}
			eventBuilder.withSentryInterface(new ExceptionInterface(exceptionDeque));
		}

		// Culprit
		eventBuilder.withCulprit(event.getLoggerName());

		// Log4j metadata
		if(event.getContextStack() != null && !event.getContextStack().asList().isEmpty()) {
			eventBuilder.withExtra(LOG4J_NDC, event.getContextStack().asList());
		}

		// Global context
		if(event.getContextMap() != null) {
			for(Map.Entry<String, String> contextEntry : event.getContextMap().entrySet()) {
				if(extraTags.contains(contextEntry.getKey())) {
					eventBuilder.withTag(contextEntry.getKey(), contextEntry.getValue());
				} else {
					eventBuilder.withExtra(contextEntry.getKey(), contextEntry.getValue());
				}
			}
		}

		// Log4j marker
		if(event.getMarker() != null) {
			eventBuilder.withTag(LOG4J_MARKER, event.getMarker().getName());
		}

		// Global tags
		for(Map.Entry<String, String> tagEntry : tags.entrySet()) {
			eventBuilder.withTag(tagEntry.getKey(), tagEntry.getValue());
		}

		// Event builders registered in Raven
		raven.runBuilderHelpers(eventBuilder);

		// Run EventEditors
		for(EventEditor eventEditor : eventEditors) {
			try {
				eventEditor.processEvent(eventBuilder, event);
			} catch(Exception e) {
				Log.error("EventEditor", eventEditor.getClass().getName(), "failed:", ExceptionUtils.getStackTrace(e));
			}
		}

		Log.debug("Sending event to sentry:", eventBuilder);
		ErrorSink.getInstance().increaseMessageSent();
		return eventBuilder.build();
	}
}

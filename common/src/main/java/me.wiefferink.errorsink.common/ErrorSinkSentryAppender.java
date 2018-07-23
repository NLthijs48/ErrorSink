package me.wiefferink.errorsink.common;

import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.MessageInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.log4j2.SentryAppender;
import me.wiefferink.errorsink.common.editors.PluginInformation;
import me.wiefferink.errorsink.common.editors.RuleData;
import me.wiefferink.errorsink.common.editors.ServerInformation;
import me.wiefferink.errorsink.common.editors.StackInformation;
import me.wiefferink.errorsink.common.filters.ErrorSinkFilter;
import me.wiefferink.errorsink.common.filters.RuleFilter;
import ninja.leaping.configurate.ConfigurationNode;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;

import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Extension of the default SentryAppender class to customize error collection for Minecraft servers
 */
public class ErrorSinkSentryAppender extends SentryAppender {

	private Set<EventEditor> eventEditors;

	public ErrorSinkSentryAppender() {
	    ConfigurationNode rootNode = ErrorSink.getPlugin().getPluginConfig();
		eventEditors = new HashSet<>();

		// Filters
		this.addFilter(new ErrorSinkFilter());
		this.addFilter(new RuleFilter(rootNode));


		// Editors
		this.addEventEditor(new RuleData(rootNode));
		this.addEventEditor(new StackInformation());
		this.addEventEditor(new ServerInformation());
		this.addEventEditor(new PluginInformation());
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
	@Override
	public void stop() {
		super.stop();
		for(EventEditor editor : eventEditors) {
			editor.shutdown();
		}
	}

	// Change the name of the appender, multiple with the same name does not work (probably used as key in a map somewhere)
	@Override
	public String getName() {
		return "ErrorSinkAppender";
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
	protected EventBuilder createEventBuilder(LogEvent event) {
		// Basics
		Message eventMessage = event.getMessage();
		EventBuilder eventBuilder = new EventBuilder()
				.withSdkIntegration("log4j2")
				.withTimestamp(new Date(ErrorSink.getPlugin().getTimeStamp(event)))
				.withMessage(eventMessage.getFormattedMessage())
				.withLogger(event.getLoggerName())
				.withLevel(levelToEventLevel(event.getLevel()))
				.withExtra(THREAD_NAME, event.getThreadName());

		// Message format (if message formatting is used)
		if(eventMessage.getFormat() != null
				&& eventMessage.getFormattedMessage() != null
				&& !eventMessage.getFormattedMessage().equals(eventMessage.getFormat())) {
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

		// Log4j marker
		if(event.getMarker() != null) {
			eventBuilder.withTag(LOG4J_MARKER, event.getMarker().getName());
		}

		// Run EventEditors
		for(EventEditor eventEditor : eventEditors) {
			try {
				eventEditor.processEvent(eventBuilder, event);
			} catch(Exception e) {
				Log.error("EventEditor", eventEditor.getClass().getName(), "failed:", ExceptionUtils.getStackTrace(e));
			}
		}

		Log.debug("Sending event to sentry:", eventBuilder);
		ErrorSink.getPlugin().increaseMessageSent();
		return eventBuilder;
	}
}

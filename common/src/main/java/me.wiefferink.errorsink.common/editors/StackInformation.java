package me.wiefferink.errorsink.common.editors;

import io.sentry.event.EventBuilder;
import me.wiefferink.errorsink.common.EventEditor;
import org.apache.logging.log4j.core.LogEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Add information about the current stack
public class StackInformation extends EventEditor {

	// package+class+method prefixes to ignore
	// Dots will be seen as anything, but that does not really matter
	// TODO add to config?
	private final static Set<String> ignoreStack = new HashSet<>(Arrays.asList(
			"^me.wiefferink.errorsink", // Own collection classes
			"^java.util.logging.Logger", // Logging getting to this class
			"^io.sentry", // Sentry building the event
			"^java.util.logging.Logger", // Java logging
			"^org.apache.logging.log4j", // Log4j
			"^java.lang.Thread.getStackTrace", // Getting stacktrace

			// Spigot internal logging
			"^org.bukkit.plugin.PluginLogger", // Bukkit logging
			"^org.bukkit.craftbukkit.[0-9a-zA-Z_]+.util.ForwardLogHandler", // Log forwarder of Spigot
			"^org.bukkit.craftbukkit.[0-9a-zA-Z_]+.LoggerOutputStream.flush",
			"^sun.nio.cs.StreamEncoder.",
			"^java.io.PrintStream.",
			"^java.io.OutputStreamWriter."
	));
	private final static Set<Pattern> ignorePatterns = new HashSet<>();
	static {
		for(String igoreRegex : ignoreStack) {
			ignorePatterns.add(Pattern.compile(igoreRegex));
		}
	}

	@Override
	public void processEvent(EventBuilder eventBuilder, LogEvent event) {
		StringBuilder result = new StringBuilder();

		StackTraceElement[] elements = Thread.currentThread().getStackTrace();
		// Filter StackTraceElements
		// After finding the first not-ignored frame we don't want to ignore anything anymore
		boolean foundCorrect = false;
		List<StackTraceElement> filteredElements = new ArrayList<>();
		for(StackTraceElement element : elements) {
			// Check the ignore list
			String prefix = element.getClassName() + "." + element.getMethodName();
			if(!foundCorrect) {
				boolean ignore = false;
				for(Pattern ignorePattern : ignorePatterns) {
					Matcher matcher = ignorePattern.matcher(prefix);
					if(matcher.find()) {
						ignore = true;
						break;
					}
				}
				if(ignore) {
					continue;
				}
			}
			foundCorrect = true;
			filteredElements.add(element);
		}

		// Build breadcrumb data
		for (StackTraceElement element : filteredElements) {
			result
					.append(element.getClassName())
					.append(".")
					.append(element.getMethodName())
					.append("(")
					.append(element.getFileName())
					.append(":")
					.append(element.getLineNumber())
					.append(")")
					.append("\n");
		}

		eventBuilder.withExtra("Stack", result.toString());
	}
}

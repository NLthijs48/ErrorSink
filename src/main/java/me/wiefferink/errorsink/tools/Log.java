package me.wiefferink.errorsink.tools;

import org.apache.commons.lang3.StringUtils;

import java.util.logging.Logger;

public class Log {

	private static Logger logger;
	private static boolean debug;

	/**
	 * Initialize the logger
	 * @param logger The Logger to use
	 */
	public static void setLogger(Logger logger) {
		Log.logger = logger;
	}

	/**
	 * Set if debug messages should be printed
	 * @param debug true to print debug messages, otherwise false
	 */
	public static void setDebug(boolean debug) {
		Log.debug = debug;
	}


	/**
	 * Sends an debug message to the console
	 * @param message The message that should be printed to the console
	 */
	public static void debug(Object... message) {
		if(debug && logger != null) {
			logger.info("Debug: " + StringUtils.join(message, " "));
		}
	}

	/**
	 * Print an information message to the console
	 * @param message The message to print
	 */
	public static void info(Object... message) {
		if(logger != null) {
			logger.info(StringUtils.join(message, " "));
		}
	}

	/**
	 * Print a warning to the console
	 * @param message The message to print
	 */
	public static void warn(Object... message) {
		if(logger != null) {
			logger.warning(StringUtils.join(message, " "));
		}
	}

	/**
	 * Print an error to the console
	 * @param message The message to print
	 */
	public static void error(Object... message) {
		if(logger != null) {
			logger.severe(StringUtils.join(message, " "));
		}
	}
}

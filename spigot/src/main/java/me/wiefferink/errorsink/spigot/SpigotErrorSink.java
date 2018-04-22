package me.wiefferink.errorsink.spigot;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.dsn.InvalidDsnException;
import io.sentry.event.EventBuilder;
import me.wiefferink.errorsink.common.CommonUtils;
import me.wiefferink.errorsink.common.ErrorSink;
import me.wiefferink.errorsink.common.ErrorSinkPlugin;
import me.wiefferink.errorsink.common.ErrorSinkSentryAppender;
import me.wiefferink.errorsink.common.EventRuleMatcher;
import me.wiefferink.errorsink.common.Log;
import me.wiefferink.errorsink.common.editors.Breadcrumbs;
import me.wiefferink.errorsink.spigot.tools.Analytics;
import me.wiefferink.errorsink.spigot.tools.Utils;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

// TODO try changing System.err logging to level error instead of warn?
// TODO command to reload config?
public class SpigotErrorSink extends JavaPlugin implements ErrorSinkPlugin {

	private static SpigotErrorSink instance;
	private SentryClient sentryClient;
	private ErrorSinkSentryAppender appender;
	private int messagesSent = 0;
	public static boolean hasOldLog4j2;
	private ConfigurationNode rootNode;
    private Map<List<Object>, EventRuleMatcher> matcherMap = new HashMap<>();

	private BukkitSentryClientFactory bukkitSentryClientFactory;

	/**
	 * Constructor
	 * Collection is already enabled here instead of onEnable to also capture
	 * loading bugs in other plugins (like missing dependencies)
	 */
	public SpigotErrorSink() {
		instance = this;
		Log.setLogger(getLogger());
		Log.setDebug(getConfig().getBoolean("debug"));
		saveDefaultConfig();

		YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder().setPath(Paths.get(getDataFolder().getPath()).resolve("config.yml")).build();
		try {
			this.rootNode = loader.load();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		ErrorSink.init(this);

		List<String> oldLogVersions = Arrays.asList("1.7", "1.8", "1.9", "1.10", "1.11");
		for(String oldLogVersion : oldLogVersions) {
			String version = Bukkit.getBukkitVersion();
			// Detects '1.8', '1.8.3', '1.8-pre1' style versions
			if(version.equals(oldLogVersion)
					|| version.startsWith(oldLogVersion + ".")
					|| version.startsWith(oldLogVersion + "-")) {
				hasOldLog4j2 = true;
				break;
			}
		}
		Log.debug("Has old Log4j2:", hasOldLog4j2);

		bukkitSentryClientFactory = new BukkitSentryClientFactory();

		String dsn = getConfig().getString("dsn");
		if(dsn == null || dsn.isEmpty()) {
			Log.error("Provide a DSN from Sentry.io in the config to get started!");
			return;
		}


		startCollecting(dsn);
	}

	@Override
	public void onEnable() {
		SpigotErrorSink.getInstance().getServer().getPluginManager().registerEvents(bukkitSentryClientFactory, SpigotErrorSink.getInstance());
		// All plugins loaded now, update packages
		bukkitSentryClientFactory.updateInAppFrames();
		// Services should now be registered
		Utils.run(bukkitSentryClientFactory::updateInAppFrames);
		// Just to be sure
		Utils.run(20L, bukkitSentryClientFactory::updateInAppFrames);

		if(getConfig().getBoolean("sendStats")) {
			Analytics.start();
		}

		this.getCommand("exception").setExecutor(new DeliberateException());
	}

	@Override
	public void onDisable() {
		// Remove and shutdown appender to prevent double appenders because of disable/enable
		Logger logger = (Logger)LogManager.getRootLogger();
		if(appender != null) {
			logger.removeAppender(appender);
			appender.stop();
		}

		HandlerList.unregisterAll(this);
	}

	/**
	 * Get the currenlty active instance of ErrorSink
	 * @return ErrorSink instance
	 */
	public static SpigotErrorSink getInstance() {
		return instance;
	}

	/**
	 * Add a message sent
	 */
	@Override
	public void increaseMessageSent() {
		messagesSent++;
	}

    @Override
    public Map<List<Object>, EventRuleMatcher> getMatcherMap() {
        return this.matcherMap;
    }

    @Override
    public ConfigurationNode getPluginConfig() {
        return this.rootNode;
    }

    /**
	 * Get the message count sent since last reset
	 *
	 * @return Messages sent since last reset
	 */
	public int getAndResetMessageSent() {
		int result = messagesSent;
		messagesSent = 0;
		return result;
	}

	/**
	 * Start collecting events
	 * @param dsn The Sentry DSN to use to send the events
	 */
	public void startCollecting(String dsn) {
		// Setup connection to Sentry.io
		try {
			sentryClient = Sentry.init(dsn, new BukkitSentryClientFactory());
		} catch(InvalidDsnException | IllegalArgumentException e) {
			Log.error("Provided Sentry DSN is invalid:", ExceptionUtils.getStackTrace(e));
			return;
		}


		Logger logger = (Logger)LogManager.getRootLogger();



		CommonUtils.initialzeMatchers();

		// Start collecting errors from the Logger
		appender = new ErrorSinkSentryAppender();

		// Default data
		sentryClient.setServerName(getServerName());
		sentryClient.setRelease(getRelease());

		// Start the collector
		appender.start();
		logger.addAppender(appender);

		// Add later to prevent reported messages also appearing in the breadcrumbs
		appender.addEventEditor(new Breadcrumbs(logger));
	}

	/**
	 * Get the timestamp from a LogEvent
	 * @param event LogEvent to get the timestamp from
	 * @return Timestamp of the LogEvent
	 */
	@Override
	public long getTimeStamp(LogEvent event) {
		// Changed event.getTimeMillis() to event.getMillis(), Minecraft 1.11 and lower uses log4j 2.0-beta9, Sentry builds with 2.5
		if(hasOldLog4j2) {
			try {
				Method method = event.getClass().getMethod("getMillis");
				return (long) method.invoke(event);
			} catch(NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				Log.debug("Failed to use getMillis on LogEvent:", ExceptionUtils.getStackTrace(e));
				// Return something that is kind of close
				return Calendar.getInstance().getTimeInMillis();
			}
		}

		// Normal new log4j
		return event.getTimeMillis();
	}

	@Override
	public SortedMap<String, String> getLoadedPlugins() {
		SortedMap<String, String> pluginVersions = new TreeMap<>();
		for(Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
			pluginVersions.put(plugin.getName(), plugin.getDescription().getVersion());
		}
		return pluginVersions;
	}

	@Override
	public String getServerVersion() {
		// Clean Bukkit version
		String bukkitVersion = Bukkit.getBukkitVersion();
		if(bukkitVersion.endsWith("-SNAPSHOT")) {
			bukkitVersion = bukkitVersion.substring(0, bukkitVersion.lastIndexOf("-SNAPSHOT"));
		}
		return bukkitVersion;
	}

	@Override
	public int getOnlinePlayers() {
		return Bukkit.getOnlinePlayers().size();
	}

	@Override
	public void addExtraData(EventBuilder builder) {
		builder.withExtra("Bukkit", Bukkit.getBukkitVersion());
		builder.withExtra("CraftBukkit", Bukkit.getVersion());
	}

	/**
	 * Get the server name that should be used
	 * @return The server name
	 */
	@Override
	public String getServerName() {
		String serverName = getConfig().getString("serverName");
		if(serverName == null || serverName.isEmpty()) {
			serverName = Bukkit.getServerName();
		}

		// Server name can never be null/empty, this will cause Raven to lookup the hostname and kills the server somehow
		if (serverName == null || serverName.isEmpty()) {
			serverName = "ServerName";
		}
		return serverName;
	}

	/**
	 * Get the release to use
	 * @return The release (version of the ErrorSink plugin)
	 */
	@Override
	public String getRelease() {
		return getDescription().getVersion();
	}

	/**
	 * Get the Raven instance, for example for adding extra BuilderHelpers
	 * @return The used Raven instance
	 */
	public SentryClient getSentryClient() {
		return sentryClient;
	}

}

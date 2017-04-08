package me.wiefferink.errorsink.editors;

import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import me.wiefferink.errorsink.ErrorSink;
import me.wiefferink.errorsink.tools.Log;
import me.wiefferink.errorsink.tools.Utils;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class RuleData extends EventEditor {

	private String bukkitVersion;
	private ConfigurationSection rules;

	public RuleData() {
		// Clean Bukkit version
		bukkitVersion = Bukkit.getBukkitVersion();
		if(bukkitVersion.endsWith("-SNAPSHOT")) {
			bukkitVersion = bukkitVersion.substring(0, bukkitVersion.lastIndexOf("-SNAPSHOT"));
		}
		rules = ErrorSink.getInstance().getConfig().getConfigurationSection("eventRules");
	}

	@Override
	public void processEvent(EventBuilder eventBuilder, LogEvent event) {
		// Plugin information
		eventBuilder.withServerName(ErrorSink.getInstance().getServerName());
		eventBuilder.withRelease(ErrorSink.getInstance().getRelease());

		// Server information
		eventBuilder.withTag("API", bukkitVersion);
		eventBuilder.withExtra("Online players", Bukkit.getOnlinePlayers().size());
		eventBuilder.withExtra("Bukkit", Bukkit.getBukkitVersion());
		eventBuilder.withExtra("CraftBukkit", Bukkit.getVersion());

		// Config rules
		if(rules != null) {
			for(String ruleKey : rules.getKeys(false)) {
				// Skip rules that are dropping events (if it would be a match, the event would not get here)
				if(rules.getBoolean(ruleKey + ".drop")) {
					continue;
				}

				// Match event
				if(!ErrorSink.getInstance().match("eventRules." + ruleKey, event.getMessage().getFormattedMessage(), event.getLevel(), event.getThrown())) {
					continue;
				}

				// Add tags
				ConfigurationSection tagsSection = rules.getConfigurationSection(ruleKey + ".tags");
				if(tagsSection != null) {
					for(String tagKey : tagsSection.getKeys(false)) {
						String tagValue = tagsSection.getString(tagKey);
						if(tagValue != null && !tagValue.isEmpty()) {
							eventBuilder.withTag(tagKey, tagValue);
						}
					}
				}

				// Add data
				ConfigurationSection dataSection = rules.getConfigurationSection(ruleKey + ".data");
				if(dataSection != null) {
					for(String datakey : dataSection.getKeys(false)) {
						Object dataValue = getValue(dataSection, datakey);
						if(dataValue != null) {
							eventBuilder.withExtra(datakey, dataValue);
						}
					}
				}

				// Fingerprint
				List<String> fingerPrint = Utils.singleOrList(rules, ruleKey + ".fingerprint");
				if(fingerPrint != null) {
					eventBuilder.withFingerprint(fingerPrint);
				}

				// Level
				String levelString = rules.getString(ruleKey + ".level");
				if (levelString != null) {
					try {
						eventBuilder.withLevel(Event.Level.valueOf(levelString));
					} catch (IllegalArgumentException e) {
						Log.warn("Incorrect level \"" + levelString + "\" for rule", rules.getCurrentPath() + "." + ruleKey);
					}
				}

				// Environment
				String environment = rules.getString(ruleKey + ".environment");
				if (environment != null) {
					eventBuilder.withEnvironment(environment);
				}

				// Culprit
				String culprit = rules.getString(ruleKey + ".culprit");
				if (culprit != null) {
					eventBuilder.withCulprit(culprit);
				}

				// Logger
				String logger = rules.getString(ruleKey + ".logger");
				if (logger != null) {
					eventBuilder.withLogger(logger);
				}

				// Release
				String release = rules.getString(ruleKey + ".release");
				if (release != null) {
					eventBuilder.withRelease(release);
				}

				// Platform
				String platform = rules.getString(ruleKey + ".platform");
				if (platform != null) {
					eventBuilder.withPlatform(platform);
				}

			}
		}

	}

	/**
	 * Deep get a Map/value structure from a ConfigurationSection
	 * @param source The source ConfigurationSection
	 * @param path   The path to get data from
	 * @return Value, List, Map, or null if nothing
	 */
	private Object getValue(ConfigurationSection source, String path) {
		if(source.isList(path)) {
			return source.getList(path, null);
		} else if(source.isConfigurationSection(path)) {
			ConfigurationSection child = source.getConfigurationSection(path);
			if(child != null) {
				SortedMap<String, Object> childMap = new TreeMap<>();
				for(String childKey : child.getKeys(false)) {
					Object childValue = getValue(child, childKey);
					if(childValue != null) {
						childMap.put(childKey, childValue);
					}
				}
				if(!childMap.isEmpty()) {
					return childMap;
				}
			}
		} else {
			return source.getString(path, null);
		}
		return null;
	}

}

package me.wiefferink.errorsink.editors;

import com.getsentry.raven.event.EventBuilder;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.configuration.ConfigurationSection;

import java.util.SortedMap;
import java.util.TreeMap;

public abstract class EventEditor {

	/**
	 * Process and incoming event
	 * @param builder The builder to apply changes to
	 * @param event   The event that is happening
	 */
	public abstract void processEvent(EventBuilder builder, LogEvent event);

	/**
	 * Stop operation and cleanup
	 */
	public void shutdown() {
	}


	/**
	 * Deep get a Map/value structure from a ConfigurationSection
	 *
	 * @param source The source ConfigurationSection
	 * @param path   The path to get data from
	 * @return Value, List, Map, or null if nothing
	 */
	public Object getValue(ConfigurationSection source, String path) {
		if (source.isList(path)) {
			return source.getList(path, null);
		} else if (source.isConfigurationSection(path)) {
			ConfigurationSection child = source.getConfigurationSection(path);
			if (child != null) {
				SortedMap<String, Object> childMap = new TreeMap<>();
				for (String childKey : child.getKeys(false)) {
					Object childValue = getValue(child, childKey);
					if (childValue != null) {
						childMap.put(childKey, childValue);
					}
				}
				if (!childMap.isEmpty()) {
					return childMap;
				}
			}
		} else {
			return source.getString(path, null);
		}
		return null;
	}
}

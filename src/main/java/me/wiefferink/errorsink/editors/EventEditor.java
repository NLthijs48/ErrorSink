package me.wiefferink.errorsink.editors;

import io.sentry.event.EventBuilder;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class EventEditor {

	private Random random;

	public EventEditor() {
		random = new Random();
	}

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
	 * @param replacements The replacements to apply
	 * @return Value, List, Map, or null if nothing
	 */
	public Object getValue(ConfigurationSection source, String path, Map<String, String> replacements) {
		if (source.isList(path)) {
			return applyReplacements(source.getStringList(path), replacements);
		} else if (source.isConfigurationSection(path)) {
			ConfigurationSection child = source.getConfigurationSection(path);
			if (child != null) {
				SortedMap<String, Object> childMap = new TreeMap<>();
				for (String childKey : child.getKeys(false)) {
					Object childValue = getValue(child, childKey, replacements);
					if (childValue != null) {
						childMap.put(childKey, childValue);
					}
				}
				if (!childMap.isEmpty()) {
					return childMap;
				}
			}
		} else {
			return applyReplacements(source.getString(path), replacements);
		}
		return null;
	}

	/**
	 * Apply replacements to a target string
	 *
	 * @param target       The string to apply the replacements to
	 * @param replacements The replacements to apply
	 * @return target string with the replacementes applied, null when target is null
	 */
	public String applyReplacements(String target, Map<String, String> replacements) {
		if(target == null) {
			return null;
		}

		// Apply given replacements
		for(String replaceKey : replacements.keySet()) {
			target = target.replace("{" + replaceKey + "}", replacements.get(replaceKey));
		}

		// Apply static replacements
		target = target.replace("{random}", new BigInteger(130, random).toString(32));

		return target;
	}

	/**
	 * Apply replacements to a target string list
	 *
	 * @param target       The string to apply the replacements to
	 * @param replacements The replacements to apply
	 * @return target string with the replacementes applied, null when target is null
	 */
	public List<String> applyReplacements(List<String> target, Map<String, String> replacements) {
		if(target == null) {
			return null;
		}
		List<String> resultList = new ArrayList<>();
		for(String entry : target) {
			resultList.add(applyReplacements(entry, replacements));
		}
		return resultList;
	}
}

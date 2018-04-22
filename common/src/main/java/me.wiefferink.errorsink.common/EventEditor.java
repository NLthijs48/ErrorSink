package me.wiefferink.errorsink.common;

import com.google.common.reflect.TypeToken;
import io.sentry.event.EventBuilder;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.apache.logging.log4j.core.LogEvent;

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
	public Object getValue(ConfigurationNode source, Object[] path, Map<String, String> replacements) {
		ConfigurationNode node = source.getNode(path);
		try {
			if (node.hasListChildren()) {
				return applyReplacements(node.getList(TypeToken.of(String.class)), replacements);
			} else if (node.hasMapChildren()) {
				SortedMap<Object[], Object> childMap = new TreeMap<>();
				for (ConfigurationNode entry: node.getChildrenMap().values()) {
					Object childValue = getValue(node, entry.getPath(), replacements);
					if (childValue != null) {
						childMap.put(entry.getPath(), childValue);
					}
				}
				if (!childMap.isEmpty()) {
					return childMap;
				}
			} else {
				return applyReplacements(node.getString(), replacements);
			}
			return null;
		} catch (ObjectMappingException e) {
			throw new RuntimeException(e);
		}
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

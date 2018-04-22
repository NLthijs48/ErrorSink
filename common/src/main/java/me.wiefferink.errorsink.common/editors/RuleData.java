package me.wiefferink.errorsink.common.editors;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import me.wiefferink.errorsink.common.CommonUtils;
import me.wiefferink.errorsink.common.ErrorSink;
import me.wiefferink.errorsink.common.EventEditor;
import me.wiefferink.errorsink.common.Log;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.apache.logging.log4j.core.LogEvent;

import java.util.List;
import java.util.Map;

public class RuleData extends EventEditor {

	private ConfigurationNode rules;

	public RuleData(ConfigurationNode root) {
		rules = root.getNode("events", "rules");
	}

	@Override
	public void processEvent(EventBuilder eventBuilder, LogEvent event) {
		// Plugin information
		eventBuilder.withServerName(ErrorSink.getPlugin().getServerName());
		eventBuilder.withRelease(ErrorSink.getPlugin().getRelease());

		// Config rules
		if(rules != null) {
			String formattedMessage = null;
			if (event.getMessage() != null) {
				formattedMessage = event.getMessage().getFormattedMessage();
			}
			for(Map.Entry<Object, ? extends ConfigurationNode> entry : rules.getChildrenMap().entrySet()) {
				String ruleKey = (String) entry.getKey();
				ConfigurationNode rule = entry.getValue();

				// Match event
				Map<String, String> replacements = ErrorSink.getPlugin().match(
						Lists.newArrayList("events", "rules", ruleKey),
						formattedMessage,
						event.getLevel(),
						event.getThrown(),
						event.getThreadName(),
						event.getLoggerName()
				);
				if(replacements == null) {
					continue;
				}

				// Add tags
				ConfigurationNode tagsSection = rule.getNode("tags");
				if(tagsSection != null) {
					for(Map.Entry<Object, ? extends ConfigurationNode> tagEntry: tagsSection.getChildrenMap().entrySet()) {
						String tagValue = applyReplacements(tagEntry.getValue().getString(), replacements);
						if(tagValue != null && !tagValue.isEmpty()) {
							eventBuilder.withTag(applyReplacements((String) tagEntry.getKey(), replacements), tagValue);
						}
					}
				}

				// Add data
				ConfigurationNode dataSection = rule.getNode("data");
				if(dataSection != null) {
					for(Object datakey : dataSection.getChildrenMap().keySet()) {
						Object dataValue = getValue(dataSection, new String[] { (String) datakey }, replacements);
						if(dataValue != null) {
							eventBuilder.withExtra(applyReplacements((String) datakey, replacements), dataValue);
						}
					}
				}

				// Fingerprint
				List<String> fingerPrint = null;
				try {
					fingerPrint = applyReplacements(rule.getNode("fingerprint").getList(TypeToken.of(String.class)), replacements);
				} catch (ObjectMappingException e) {
					throw new RuntimeException(e);
				}
				if(fingerPrint != null) {
					eventBuilder.withFingerprint(fingerPrint);
				}

				// Level
				String levelString = applyReplacements(rule.getNode("level").getString(), replacements);
				if (levelString != null) {
					try {
						eventBuilder.withLevel(Event.Level.valueOf(levelString.toUpperCase()));
					} catch (IllegalArgumentException e) {
						Log.warn("Incorrect level \"" + levelString + "\" for rule", String.join(".", (String[]) rules.getPath()) + "." + ruleKey);
					}
				}

				// Environment
				String environment = applyReplacements(rule.getNode("environment").getString(), replacements);
				if (environment != null) {
					eventBuilder.withEnvironment(environment);
				}

				// Culprit
				String culprit = applyReplacements(rule.getNode("culprit").getString(), replacements);
				if (culprit != null) {
					eventBuilder.withCulprit(culprit);
				}

				// Logger
				String logger = applyReplacements(rule.getNode("logger").getString(), replacements);
				if (logger != null) {
					eventBuilder.withLogger(logger);
				}

				// Release
				String release = applyReplacements(rule.getNode("release").getString(), replacements);
				if (release != null) {
					eventBuilder.withRelease(release);
				}

				// Platform
				String platform = applyReplacements(rule.getNode("platform").getString(), replacements);
				if (platform != null) {
					eventBuilder.withPlatform(platform);
				}

			}
		}

	}

}

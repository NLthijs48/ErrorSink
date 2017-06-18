package me.wiefferink.errorsink.editors;

import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import me.wiefferink.errorsink.ErrorSink;
import me.wiefferink.errorsink.tools.Log;
import me.wiefferink.errorsink.tools.Utils;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

public class RuleData extends EventEditor {

	private ConfigurationSection rules;

	public RuleData() {
		rules = ErrorSink.getInstance().getConfig().getConfigurationSection("events.rules");
	}

	@Override
	public void processEvent(EventBuilder eventBuilder, LogEvent event) {
		// Plugin information
		eventBuilder.withServerName(ErrorSink.getInstance().getServerName());
		eventBuilder.withRelease(ErrorSink.getInstance().getRelease());

		// Config rules
		if(rules != null) {
			String formattedMessage = null;
			if (event.getMessage() != null) {
				formattedMessage = event.getMessage().getFormattedMessage();
			}
			for(String ruleKey : rules.getKeys(false)) {
				ConfigurationSection rule = rules.getConfigurationSection(ruleKey);
				if(rule == null) {
					continue;
				}

				// Match event
				Map<String, String> replacements = ErrorSink.getInstance().match(
						"events.rules." + ruleKey,
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
				ConfigurationSection tagsSection = rule.getConfigurationSection("tags");
				if(tagsSection != null) {
					for(String tagKey : tagsSection.getKeys(false)) {
						String tagValue = applyReplacements(tagsSection.getString(tagKey), replacements);
						if(tagValue != null && !tagValue.isEmpty()) {
							eventBuilder.withTag(applyReplacements(tagKey, replacements), tagValue);
						}
					}
				}

				// Add data
				ConfigurationSection dataSection = rule.getConfigurationSection("data");
				if(dataSection != null) {
					for(String datakey : dataSection.getKeys(false)) {
						Object dataValue = getValue(dataSection, datakey, replacements);
						if(dataValue != null) {
							eventBuilder.withExtra(applyReplacements(datakey, replacements), dataValue);
						}
					}
				}

				// Fingerprint
				List<String> fingerPrint = applyReplacements(Utils.singleOrList(rule, "fingerprint"), replacements);
				if(fingerPrint != null) {
					eventBuilder.withFingerprint(fingerPrint);
				}

				// Level
				String levelString = applyReplacements(rule.getString("level"), replacements);
				if (levelString != null) {
					try {
						eventBuilder.withLevel(Event.Level.valueOf(levelString.toUpperCase()));
					} catch (IllegalArgumentException e) {
						Log.warn("Incorrect level \"" + levelString + "\" for rule", rules.getCurrentPath() + "." + ruleKey);
					}
				}

				// Environment
				String environment = applyReplacements(rule.getString("environment"), replacements);
				if (environment != null) {
					eventBuilder.withEnvironment(environment);
				}

				// Culprit
				String culprit = applyReplacements(rule.getString("culprit"), replacements);
				if (culprit != null) {
					eventBuilder.withCulprit(culprit);
				}

				// Logger
				String logger = applyReplacements(rule.getString("logger"), replacements);
				if (logger != null) {
					eventBuilder.withLogger(logger);
				}

				// Release
				String release = applyReplacements(rule.getString("release"), replacements);
				if (release != null) {
					eventBuilder.withRelease(release);
				}

				// Platform
				String platform = applyReplacements(rule.getString("platform"), replacements);
				if (platform != null) {
					eventBuilder.withPlatform(platform);
				}

			}
		}

	}

}

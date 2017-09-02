package me.wiefferink.errorsink.editors;

import io.sentry.event.EventBuilder;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.SortedMap;
import java.util.TreeMap;

public class PluginInformation extends EventEditor {

	@Override
	public void processEvent(EventBuilder builder, LogEvent event) {
		SortedMap<String, String> pluginVersions = new TreeMap<>();
		for(Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
			pluginVersions.put(plugin.getName(), plugin.getDescription().getVersion());
		}
		builder.withExtra("Plugins", pluginVersions);
	}

}

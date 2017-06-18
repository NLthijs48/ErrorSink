package me.wiefferink.errorsink.editors;

import com.getsentry.raven.event.EventBuilder;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.Bukkit;

public class ServerInformation extends EventEditor {

	private String bukkitVersion;

	public ServerInformation() {
		// Clean Bukkit version
		bukkitVersion = Bukkit.getBukkitVersion();
		if(bukkitVersion.endsWith("-SNAPSHOT")) {
			bukkitVersion = bukkitVersion.substring(0, bukkitVersion.lastIndexOf("-SNAPSHOT"));
		}
	}

	@Override
	public void processEvent(EventBuilder builder, LogEvent event) {
		// Server information
		builder.withTag("API", bukkitVersion);
		builder.withExtra("Online players", Bukkit.getOnlinePlayers().size());
		builder.withExtra("Bukkit", Bukkit.getBukkitVersion());
		builder.withExtra("CraftBukkit", Bukkit.getVersion());
	}

}

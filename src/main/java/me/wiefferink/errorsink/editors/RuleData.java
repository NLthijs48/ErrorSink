package me.wiefferink.errorsink.editors;

import com.getsentry.raven.event.EventBuilder;
import me.wiefferink.errorsink.ErrorSink;
import org.apache.logging.log4j.core.LogEvent;
import org.bukkit.Bukkit;

public class RuleData extends EventEditor {

	private String bukkitVersion;

	public RuleData() {
		// Clean Bukkit version
		bukkitVersion = Bukkit.getBukkitVersion();
		if(bukkitVersion.endsWith("-SNAPSHOT")) {
			bukkitVersion = bukkitVersion.substring(0, bukkitVersion.lastIndexOf("-SNAPSHOT"));
		}
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
		// TODO implement
	}

}

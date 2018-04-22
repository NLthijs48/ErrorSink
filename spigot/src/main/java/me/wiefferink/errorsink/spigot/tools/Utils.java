package me.wiefferink.errorsink.spigot.tools;

import me.wiefferink.errorsink.spigot.SpigotErrorSink;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utils {

	/**
	 * Only a utility class
	 */
	private Utils() {
	}

	/**
	 * Get a string list from the config, with fallback to single string as list
	 * @param section The section to look in
	 * @param key     The key in the section to get
	 * @return A list
	 */
	public static List<String> singleOrList(ConfigurationSection section, String key) {
		if(section.isList(key)) {
			return section.getStringList(key);
		} else if(section.isSet(key)) {
			return new ArrayList<>(Collections.singletonList(section.getString(key)));
		}
		return null;
	}

	public interface Run {
		void run();
	}

	public static void run(Run task) {
		new BukkitRunnable() {
			@Override
			public void run() {
				task.run();
			}
		}.runTask(SpigotErrorSink.getInstance());
	}

	public static void run(long delay, Run task) {
		new BukkitRunnable() {
			@Override
			public void run() {
				task.run();
			}
		}.runTaskLater(SpigotErrorSink.getInstance(), delay);
	}

}

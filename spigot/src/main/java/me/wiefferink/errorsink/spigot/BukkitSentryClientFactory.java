package me.wiefferink.errorsink.spigot;

import io.sentry.DefaultSentryClientFactory;
import io.sentry.dsn.Dsn;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.HashSet;

public class BukkitSentryClientFactory extends DefaultSentryClientFactory implements Listener {

	private Collection<String> inAppFrames;

	public BukkitSentryClientFactory() {
		super();

		inAppFrames = new HashSet<>();
		updateInAppFrames();
	}

	/**
	 * Update the inAppFrames using the list of loaded plugins
	 */
	public void updateInAppFrames() {
		// Packages used by the loaded plugins
		for(Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
			addPackageByClassName(plugin.getDescription().getMain());
		}

		// Packages used by the registered services
		for(Class<?> clazz : Bukkit.getServicesManager().getKnownServices()) {
			addPackageByClassName(clazz.getName());
			for(RegisteredServiceProvider provider : Bukkit.getServicesManager().getRegistrations(clazz)) {
				if(provider == null || provider.getProvider() == null) {
					continue;
				}
				addPackageByClassName(provider.getProvider().getClass().getName());
			}
		}
	}

	private void addPackageByClassName(String name) {
		if(name == null || !name.contains(".")) {
			return;
		}

		// Strip name of the main class
		String pluginPackage = name.substring(0, name.lastIndexOf('.'));

		// Trim down to tld+domain
		// For example main class of WorldEdit is at 'com.sk89q.worldedit.bukkit', this trims to 'com.sk89q'
		while(pluginPackage.indexOf('.') != pluginPackage.lastIndexOf('.')) {
			pluginPackage = pluginPackage.substring(0, pluginPackage.lastIndexOf('.'));
		}

		if(!pluginPackage.isEmpty()) {
			inAppFrames.add(pluginPackage);
		}
	}

	@Override
	protected Collection<String> getInAppFrames(Dsn dsn) {
		return inAppFrames;
	}

	// Update list when a new service registers (supporting hot plugin loading)
	@EventHandler(priority = EventPriority.MONITOR)
	public void onServiceRegister(ServiceRegisterEvent event) {
		RegisteredServiceProvider provider = event.getProvider();
		if(provider == null || provider.getProvider() == null) {
			return;
		}
		addPackageByClassName(provider.getProvider().getClass().getName());
	}

	// Update list when a plugin enables (supporting hot plugin loading)
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPluginEnable(PluginEnableEvent event) {
		Plugin plugin = event.getPlugin();
		if(plugin == null) {
			return;
		}
		addPackageByClassName(plugin.getDescription().getMain());
	}

}

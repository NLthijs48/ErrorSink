package me.wiefferink.errorsink.sponge;

import io.sentry.DefaultSentryClientFactory;
import io.sentry.dsn.Dsn;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.service.ChangeServiceProviderEvent;
import org.spongepowered.api.plugin.PluginContainer;

import java.util.Collection;
import java.util.HashSet;

public class SpongeSentryClientFactory extends DefaultSentryClientFactory {

    private Collection<String> inAppFrames;

    public SpongeSentryClientFactory() {
        super();

        inAppFrames = new HashSet<>();
        updateInAppFrames();
    }

    /**
     * Update the inAppFrames using the list of loaded plugins
     */
    public void updateInAppFrames() {
        // Packages used by the loaded plugins
        for(PluginContainer plugin : Sponge.getPluginManager().getPlugins()) {
            plugin.getInstance().ifPresent(p -> addPackageByClassName(p.getClass().getName()));
        }

        // Packages used by the registered services
        /*for(Class<?> clazz : Sponge.getServiceManager().get) {
            addPackageByClassName(clazz.getName());
            for(RegisteredServiceProvider provider : Bukkit.getServicesManager().getRegistrations(clazz)) {
                if(provider == null || provider.getProvider() == null) {
                    continue;
                }
                addPackageByClassName(provider.getProvider().getClass().getName());
            }
        }*/
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
    @Listener(order = Order.POST)
    public void onServiceRegister(ChangeServiceProviderEvent event) {
        addPackageByClassName(event.getNewProviderRegistration().getProvider().getClass().getName());
    }

}

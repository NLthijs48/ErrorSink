package me.wiefferink.errorsink.sponge;

import com.google.inject.Inject;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.dsn.InvalidDsnException;
import io.sentry.event.EventBuilder;
import me.wiefferink.errorsink.common.ErrorSink;
import me.wiefferink.errorsink.common.ErrorSinkPlugin;
import me.wiefferink.errorsink.common.ErrorSinkSentryAppender;
import me.wiefferink.errorsink.common.EventRuleMatcher;
import me.wiefferink.errorsink.common.Log;
import me.wiefferink.errorsink.common.editors.Breadcrumbs;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Plugin(id = "errorsink", name = "ErrorSink")
public class SpongeErrorSink implements ErrorSinkPlugin {

    @Inject
    private Logger logger;

    @Inject
    private PluginContainer container;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private Path defaultConfig;

    private YAMLConfigurationLoader configManager;

    private ConfigurationNode rootNode;
    private SpongeSentryClientFactory spongeSentryClientFactory;
    private SentryClient sentryClient;
    private ErrorSinkSentryAppender appender;
    private Map<List<Object>, EventRuleMatcher> matcherMap = new HashMap<>();

    private Path getYmlPath(Path confPath) {
        int ext = confPath.getFileName().toString().indexOf(".conf");
        return confPath.resolveSibling(confPath.getFileName().toString().substring(0, ext) + ".yml");
    }

    @Listener
    public void onPreInit(GamePreInitializationEvent event) throws Exception {
        this.defaultConfig = this.getYmlPath(this.defaultConfig);
        if (!Files.exists(this.defaultConfig)) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                Files.copy(is, this.defaultConfig);
            }
        }
        this.configManager = YAMLConfigurationLoader.builder().setPath(this.defaultConfig).build();
        this.rootNode = this.configManager.load();
        ErrorSink.init(this);

        spongeSentryClientFactory = new SpongeSentryClientFactory();

        String dsn = this.rootNode.getNode("dsn").getString();
        if(dsn == null || dsn.isEmpty()) {
            this.logger.error("Provide a DSN from Sentry.io in the config to get started!");
            return;
        }

        Sponge.getCommandManager().register(this, CommandSpec.builder()
                .arguments(GenericArguments.string(Text.of("message")))
                .executor(new CommandExecutor() {

                    @Override
                    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
                        throw new RuntimeException(args.<String>getOne(Text.of("message")).get());
                    }
                }).build(),
        "exception");

        startCollecting(dsn);
    }

    /**
     * Start collecting events
     * @param dsn The Sentry DSN to use to send the events
     */
    public void startCollecting(String dsn) {
        // Setup connection to Sentry.io
        try {
            sentryClient = Sentry.init(dsn, spongeSentryClientFactory);
        } catch(InvalidDsnException | IllegalArgumentException e) {
            Log.error("Provided Sentry DSN is invalid:", ExceptionUtils.getStackTrace(e));
            return;
        }

        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger)LogManager.getRootLogger();
        org.apache.logging.log4j.core.Logger fmlLogger = (org.apache.logging.log4j.core.Logger)LogManager.getLogger("FML");

        // Start collecting errors from the Logger
        appender = new ErrorSinkSentryAppender();

        // Default data
        sentryClient.setServerName(getServerName());
        sentryClient.setRelease(getRelease());

        // Start the collector
        appender.start();
        logger.addAppender(appender);
        fmlLogger.addAppender(appender);

        // Add later to prevent reported messages also appearing in the breadcrumbs
        appender.addEventEditor(new Breadcrumbs(logger, fmlLogger));
    }

    /**
     * Get the server name that should be used
     * @return The server name
     */
    @Override
    public String getServerName() {
        String serverName = this.rootNode.getNode(("serverName")).getString();
        if(serverName == null || serverName.isEmpty()) {
            serverName = null; // TODO
        }

        // Server name can never be null/empty, this will cause Raven to lookup the hostname and kills the server somehow
        if (serverName == null || serverName.isEmpty()) {
            serverName = "ServerName";
        }
        return serverName;
    }

    /**
     * Get the release to use
     * @return The release (version of the ErrorSink plugin)
     */
    @Override
    public String getRelease() {
        return this.container.getVersion().orElse("<No version>");
    }

    @Override
    public void increaseMessageSent() {

    }

    @Override
    public Map<List<Object>, EventRuleMatcher> getMatcherMap() {
        return this.matcherMap;
    }

    @Override
    public ConfigurationNode getPluginConfig() {
        return this.rootNode;
    }

    @Override
    public SortedMap<String, String> getLoadedPlugins() {
        SortedMap<String, String> pluginVersions = new TreeMap<>();
        for(PluginContainer plugin : Sponge.getPluginManager().getPlugins()) {
            pluginVersions.put(plugin.getName(), plugin.getVersion().orElse("<no_version>"));
        }
        return pluginVersions;
    }

    @Override
    public String getServerVersion() {
        return Sponge.getGame().getPlatform().getContainer(Platform.Component.IMPLEMENTATION).getVersion().orElse("UNKNOWN-SPONGE-IMPL");
    }

    @Override
    public int getOnlinePlayers() {
        return Sponge.getServer().getOnlinePlayers().size();
    }

    @Override
    public void addExtraData(EventBuilder builder) {
        builder.withExtra("SpongeAPI", Sponge.getGame().getPlatform().getContainer(Platform.Component.API).getVersion().orElse("UNKNOWN-SPONGE-API"));
    }

}

package me.wiefferink.errorsink.common.editors;

import io.sentry.event.EventBuilder;
import me.wiefferink.errorsink.common.ErrorSink;
import me.wiefferink.errorsink.common.EventEditor;
import org.apache.logging.log4j.core.LogEvent;

public class PluginInformation extends EventEditor {

	@Override
	public void processEvent(EventBuilder builder, LogEvent event) {
		builder.withExtra("Plugins", ErrorSink.getPlugin().getLoadedPlugins());
	}

}

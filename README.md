# ErrorSink
Spigot plugin to send all warnings and errors to Sentry.io

After installing the plugin create an account on Sentry.io, create a project, got to the setting of the project and copy the DSN (Client Keys).
Start your server to generate a default config, after this put the DSN into the config and set your servername.
Now restart your server and the ErrorSink plugin should not have any errors/warnings anymore.

If any plugin produces an error or warning it should show up in Sentry.
You might want to disable data scrubbing in the Sentry project settings, because otherwise all ips are stripped from the messages.

## TODO
- Add proper guide to setup the plugin
- Setup issue labels etc
- Setup Jenkins build server
- Implement missing config options

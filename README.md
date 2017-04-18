# ErrorSink
Spigot plugin to send all warnings and errors to Sentry.io:
* Easily see all issues on your server in a beautiful web interface.
* Get email notification for new (and returned) issues.
* Detailed reports of issues so that plugin developers can easily fix them.
* Lots of configuration options to filter and change events.

## Information
* **Build server:** http://jenkins.wiefferink.me/job/ErrorSink/
* **Javadocs:** https://wiefferink.me/ErrorSink/javadocs

## Setup
1. Create an account on [Sentry.io](http://sentry.io) (of course this is for free):
    1. Click `Try for free` at the homepage.
    1. Fill in your name, email and a password.
    1. Fill in an organisation name, usually your server name (can easily be changed later).
        * In Sentry you have a user account, which can be part of one or more organisations, each organisation has zero or more projects (a project for example represents a minecraft server, website, app, etc.).
1. Setup a Sentry project, it should already ask for this, you can use the name of your server again (can easily be changed later).
1. Sentry now asks you to setup your application, you only need to copy the DSN:
    1. Click the small `get your DSN` link.
        * If you missed this link, click `Project Settings` at the top right of your project and go to the `Client Keys (DSN)` tab.
    1. Copy the top (non-public) DSN to your clipboard or a text file, we need to put this in the config of ErrorSink.
1. Install ErrorSink on your server:
    1. Copy the jar file to the `plugins` directory.
    1. Start/reload the server.
    1. Open the config file at `plugins/ErrorSink/config.yml`.
    1. Enter the DSN in the `dsn` config option (the one we got in the web interface).
    1. Restart/reload your server.
1. Setup complete, ErrorSink will now send all errors and warnings to the web interface. You probably already have an event in the web interface of Sentry which you can check out.
1. If you have spammy events of plugins you cannot fix or want to change the collected data you can use the `config.yml` file to adapt ErrorSink. The free version of Sentry has a limit of 10000 events per month, so filtering spammy warnings/errors is recommended.

## Frequently Asked Questions
1. Why are messages with ips not shown in Sentry?
    * By default Sentry has some data scrubbers enabled, you can turn them off in the `Project Settings`:
        * Disable `Data Scrubber`
        * Disable `Use Default Scrubbers`

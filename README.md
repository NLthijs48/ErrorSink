# ErrorSink
Spigot plugin to send all warnings and errors to Sentry.io

After installing the plugin create an account on Sentry.io, create a project, got to the setting of the project and copy the DSN (Client Keys).
Start your server to generate a default config, after this put the DSN into the config and set your servername.
Now restart your server and the ErrorSink plugin should not have any errors/warnings anymore.

If any plugin produces an error or warning it should show up in Sentry.
You might want to disable data scrubbing in the Sentry project settings, because otherwise all ips are stripped from the messages.

## Information
* **Build server:** http://jenkins.wiefferink.me/job/ErrorSink/
* **Javadocs:** https://wiefferink.me/ErrorSink/javadocs

## Setup
1. Create an account on [Sentry.io](http://sentry.io) (of course this is for free).
    1. Click `Try for free` at the homepage.
    1. Fill in your name, email and a password.
    1. Fill in an organisation name, usually your server name
        * In Sentry you have a user account, which can be part of one or more organisations, each organisation has zero or more projects (a project for example represents a minecraft server, website, app, etc.).
1. Setup a project with the name of your server (or anything else you like)
1. Install ErrorSink on your server

## TODO
- Add proper guide to setup the plugin
- Setup issue labels etc
- Implement missing config options

package me.wiefferink.errorsink.spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public class DeliberateException implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        throw new RuntimeException("Explicit exception: " + Arrays.toString(strings));
    }
}

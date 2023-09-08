package me.fisch37.betterresourcepack;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static me.fisch37.betterresourcepack.Utils.sendMessage;

public class ReloadPackTask {
    private final Plugin plugin;
    private final PackInfo packInfo;
    private final boolean sync;
    private final boolean push;
    private final CommandSender taskAuthor;
    private final AsyncScheduler asyncScheduler;

    public ReloadPackTask(Plugin plugin, CommandSender taskAuthor, PackInfo packInfo, boolean sync, boolean push, AsyncScheduler asyncScheduler) {
        this.plugin = plugin;
        this.taskAuthor = taskAuthor;
        this.packInfo = packInfo;
        this.sync = sync;
        this.push = push;
        this.asyncScheduler = asyncScheduler;
    }

    private class FetchTask implements Runnable {
        private final PackInfo packInfo;
        private Boolean isSuccessful;

        public FetchTask(PackInfo packInfo) {
            this.packInfo = packInfo;
        }

        @Override
        public void run() {
            try {
                this.packInfo.updateSha1();
                this.isSuccessful = true;
            } catch (IOException e) {
                this.isSuccessful = false;
            }
        }

        public Boolean getSuccessState() {
            return this.isSuccessful;
        }
    }

    public void start() {
        asyncScheduler.runAtFixedRate(plugin, (task) -> {
            FetchTask executingTask = new FetchTask(packInfo);

            if (!sync) {
                // Schedule the task to run asynchronously
                asyncScheduler.runNow(plugin, (t) -> executingTask.run());
            } else {
                executingTask.run();
            }

            // Success state is null when the task is still running
            if (executingTask.getSuccessState() == null) return;

            boolean op_success = executingTask.getSuccessState();
            if (!op_success) {
                sendToAuthor(ChatColor.RED + "Could not fetch resource pack!");
                // Logging sync allows me to essentially debug the situation. Intention is that only /reload executes with sync
                Bukkit.getLogger().warning("[BSP] Could not fetch resource pack in reload task! Sync: " + sync);
            } else if (saveHash()) {
                sendToAuthor("Updated pack hash!");
                Bukkit.getLogger().info("[BSP] Updated pack hash");
                if (push) pushPackToPlayers();
            }
        }, 0L, 2L, TimeUnit.SECONDS);
    }

    private boolean saveHash() {
        try {
            packInfo.saveHash();
            return true;
        } catch (IOException e) {
            sendToAuthor(ChatColor.RED + "Could not save hash! The hash is still updated but will reset on the next restart.");
            Bukkit.getLogger().warning("Could not save hash to the cache file in the reload task. Sync: " + sync);
            return false;
        }
    }

    private void pushPackToPlayers() {
        sendToAuthor("Pushing update to all players");
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            player.setResourcePack(
                    packInfo.getUrl().toString(),
                    packInfo.getSha1()
            );
        }
    }

    private void sendToAuthor(String message) {
        if (taskAuthor != null) sendMessage(taskAuthor, message);
    }
}

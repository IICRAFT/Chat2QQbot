package com.willzcode;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static com.willzcode.Chat2QQbot.plugin;

/**
 * Created by willz on 2018/4/5.
 * Listen Events
 */
public class EventListener implements Listener {
    @EventHandler
    public void onPlayerSay(AsyncPlayerChatEvent event)
    {
        if(!event.isCancelled()) {
            if(plugin.enableAskyblock) {
                try {
                    long lvl = plugin.getIslandLevel(event.getPlayer().getUniqueId());
                    plugin.sendToGroup(String.format("[空岛%s级]%s:%s", lvl, event.getPlayer().getName(), event.getMessage()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else
                plugin.sendToGroup(String.format("%s:%s", event.getPlayer().getName(), event.getMessage()));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        plugin.sendToGroup(String.format("[%s] %s 上线了", plugin.servername, event.getPlayer().getName()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.sendToGroup(String.format("[%s] %s 下线了", plugin.servername, event.getPlayer().getName()));
    }
}

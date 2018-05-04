package com.willzcode;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.willzcode.Chat2QQbot.plugin;

/**
 * Created by willz on 2018/4/5.
 * Listen Events
 */
public class EventListener implements Listener {
    private Map<UUID, Long> connectTimeMap = new HashMap<>();
    private final long THROTTLE_TIME = 60L * 1000L;

    @EventHandler
    public void onPlayerSay(AsyncPlayerChatEvent event)
    {
        String msg = event.getMessage();
        if(!event.isCancelled() && (msg.startsWith("!") || msg.startsWith("！"))) {
            msg = String.format("%s", msg.subSequence(1, msg.length()));
            if(plugin.enableAskyblock) {
                try {
                    long lvl = ASkyblockConnector.getIslandLevel(event.getPlayer().getUniqueId());
                    plugin.sendToGroup(String.format("[空岛%s级]%s:%s", lvl, event.getPlayer().getName(), msg));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else
                plugin.sendToGroup(String.format("%s:%s", event.getPlayer().getName(), msg));
        }
    }

    private long getPlayerConnectionTime(UUID uuid) {
        return connectTimeMap.getOrDefault(uuid, 0L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        long time = System.currentTimeMillis();
        if (getPlayerConnectionTime(event.getPlayer().getUniqueId()) + THROTTLE_TIME < time) {
            plugin.sendToGroup(String.format("[%s] %s 上线了", plugin.servername, event.getPlayer().getName()));
            connectTimeMap.put(event.getPlayer().getUniqueId(), time);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        long time = System.currentTimeMillis();
        if (getPlayerConnectionTime(event.getPlayer().getUniqueId()) + THROTTLE_TIME < time) {
            plugin.sendToGroup(String.format("[%s] %s 下线了", plugin.servername, event.getPlayer().getName()));
            connectTimeMap.put(event.getPlayer().getUniqueId(), time);
        }
    }
}

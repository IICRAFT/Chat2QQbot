package com.willzcode;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

import java.util.*;

/**
 * Created by willz on 2018/4/22.
 */
public class ASkyblockConnector {
    public static long getIslandLevel(UUID uuid) {
        return ASkyBlockAPI.getInstance().getIslandLevel(uuid);
    }

    public static Set<ChunkSnapshot> getIslandChunk(Island island) {
        World iworld = ASkyBlockAPI.getInstance().getIslandWorld();
        Set<ChunkSnapshot> chunks = new HashSet<>();
        for(int x = island.getMinProtectedX(); x < island.getMinProtectedX() + island.getProtectionSize() + 16; x += 16) {
            for(int z = island.getMinProtectedZ(); z < island.getMinProtectedZ() + island.getProtectionSize() + 16; z += 16) {
                if(!iworld.getBlockAt(x, 0, z).getChunk().isLoaded()) {
                    iworld.getBlockAt(x, 0, z).getChunk().load();
                    chunks.add(iworld.getBlockAt(x, 0, z).getChunk().getChunkSnapshot());
                    iworld.getBlockAt(x, 0, z).getChunk().unload();
                } else {
                    chunks.add(iworld.getBlockAt(x, 0, z).getChunk().getChunkSnapshot());
                }
            }
        }
        return chunks;
    }



    public static void checkIsland(String player, boolean fromchat, CommandSender sender) {
        if (!fromchat && sender == null) {
            throw new NullPointerException();
        }

        UUID targetPlayer = Bukkit.getOfflinePlayer(player).getUniqueId();
        Island island = ASkyBlock.getPlugin().getGrid().getIsland(targetPlayer);
        Set<ChunkSnapshot> chunks = getIslandChunk(island);
        int worldHeight = ASkyBlockAPI.getInstance().getIslandWorld().getMaxHeight();
        Bukkit.getServer().getScheduler().runTaskAsynchronously(Chat2QQbot.plugin, () -> {
            String str = "玩家 " + player + " 的岛屿上有：";
            Iterator it = chunks.iterator();

            Map<MaterialData, Integer> countMap = new HashMap<>();
            while(it.hasNext()) {
                ChunkSnapshot chunk = (ChunkSnapshot)it.next();
                for(int x = 0; x < 16; ++x) {
                    if (chunk.getX() * 16 + x >= island.getMinProtectedX() && chunk.getX() * 16 + x < island.getMinProtectedX() + island.getProtectionSize()) {
                        for (int z = 0; z < 16; ++z) {
                            if (chunk.getZ() * 16 + z >= island.getMinProtectedZ() && chunk.getZ() * 16 + z < island.getMinProtectedZ() + island.getProtectionSize()) {
                                for (int y = 0; y < worldHeight; ++y) {
                                    int id = chunk.getBlockTypeId(x, y, z);
                                    MaterialData data = new MaterialData(id, (byte) chunk.getBlockData(x, y, z));
                                    int n = countMap.getOrDefault(data, 0);
                                    countMap.put(data, ++n);
                                }
                            }
                        }
                    }
                }
            }

            for (Map.Entry<MaterialData, Integer> e : countMap.entrySet()) {
                MaterialData data = e.getKey();
                if (data.getItemTypeId() == 0)
                    continue;
                int count = e.getValue();
                ItemStack item = data.toItemStack(count);
                str += ItemHelper.getShownString(item) + " ";
            }

            if (fromchat) {
                Chat2QQbot.plugin.sendToGroup(str);
            } else {
                sender.sendMessage(str);
            }
        });
    }


    public static void checkContainer(String player) {
        UUID targetPlayer = Bukkit.getOfflinePlayer(player).getUniqueId();
        Island island = ASkyBlock.getPlugin().getGrid().getIsland(targetPlayer);
        Set<ChunkSnapshot> chunks = getIslandChunk(island);
        World iworld = ASkyBlockAPI.getInstance().getIslandWorld();

        int worldHeight = ASkyBlockAPI.getInstance().getIslandWorld().getMaxHeight();
        Bukkit.getServer().getScheduler().runTaskAsynchronously(Chat2QQbot.plugin, () -> {
            String str = "玩家 " + player + " 岛屿上的容器里有：";

            for (ChunkSnapshot chunkSnapshot : chunks) {
                Chunk chunk = iworld.getChunkAt(chunkSnapshot.getX(), chunkSnapshot.getZ());
                for (int x = 0; x < 16; ++x) {
                    if (chunk.getX() * 16 + x >= island.getMinProtectedX() && chunk.getX() * 16 + x < island.getMinProtectedX() + island.getProtectionSize()) {
                        for (int z = 0; z < 16; ++z) {
                            if (chunk.getZ() * 16 + z >= island.getMinProtectedZ() && chunk.getZ() * 16 + z < island.getMinProtectedZ() + island.getProtectionSize()) {
                                for (int y = 0; y < worldHeight; ++y) {
                                    BlockState blockState = chunk.getBlock(x, y, z).getState();
                                    if (blockState instanceof InventoryHolder) {
                                        Inventory inventory = ((InventoryHolder)blockState).getInventory();
                                        for (ItemStack item:inventory.getContents()) {
                                            if (item != null && item.getType() != Material.AIR) {
                                                if (ItemHelper.isContainer(item)) {
                                                    str += ItemHelper.getContainerItemsRecursively(item);
                                                } else
                                                    str += ItemHelper.getShownString(item) + " ";
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Chat2QQbot.plugin.sendToGroup(str);
        });
    }
}

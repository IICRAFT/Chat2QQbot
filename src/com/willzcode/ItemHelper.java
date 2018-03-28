package com.willzcode;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.nbt.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.Metadatable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by willz on 2018/3/19.
 */
public class ItemHelper {
    public static ItemStack fromNbtCompound(NbtCompound tag) {
        int id = tag.getShortOrDefault("id");
        int count = tag.getByteOrDefault("Count");
        short damage = tag.getShortOrDefault("Damage");
        ItemStack item = new ItemStack(id, count, damage);
        //item = (ItemStack) MinecraftReflection.getMinecraftItemStack(item);
        if (tag.containsKey("tag")) {
            item = MinecraftReflection.getBukkitItemStack(item);
            NbtFactory.setItemTag(item, tag.getCompound("tag"));
        }
        return item;
    }

    public static List<ItemStack> fromContainer(ItemStack container) {
        //Object nmsitem = MinecraftReflection.getMinecraftItemStack(container);
        container = MinecraftReflection.getBukkitItemStack(container);
        NbtWrapper<?> tag = NbtFactory.fromItemTag(container);
        List<ItemStack> items = new ArrayList<>();
        if (tag.getType() == NbtType.TAG_LIST) {
            NbtList list = (NbtList) tag;
            for(Iterator it = list.iterator(); it.hasNext();) {
                NbtCompound tagitem = (NbtCompound) it.next();
                items.add(fromNbtCompound(tagitem));
            }
        } else if (tag.getType() == NbtType.TAG_COMPOUND) {
            NbtCompound compound = (NbtCompound) tag;
            for(Iterator it = compound.iterator(); it.hasNext();) {
                NbtWrapper<?> tagitem = (NbtWrapper<?>) it.next();
                if(tagitem.getType() == NbtType.TAG_COMPOUND)
                    items.add(fromNbtCompound((NbtCompound) tagitem));
            }
        }
        return items;
    }

    public static String getContainerItemsRecursively(ItemStack container) {
        String bagstr = "";
        List<ItemStack> items = ItemHelper.fromContainer(container);
        for (Iterator it = items.iterator(); it.hasNext(); ) {
            ItemStack item = (ItemStack) it.next();
            if (item != null) {
                if(isContainer(item))
                    bagstr += getContainerItemsRecursively(item);
                else
                    bagstr += getShownString(item) + " ";
            }
        }
        return bagstr;
    }

    public static boolean isContainer(ItemStack itemStack) {
        return itemStack.getTypeId() == 5227;
    }

    public static String getShownString(ItemStack i) {
        return getLocalizedName(i, true)+ "×" + i.getAmount();
    }

    public static String getLocalizedName(ItemStack i, boolean oldname) {
        try {
            if (oldname) {
                ItemMeta meta = i.getItemMeta();
                meta.setDisplayName("");
                i.setItemMeta(meta);
            }
            Object item = MinecraftReflection.getMinecraftItemStack(i);
            return (String)MinecraftReflection.getItemStackClass().getMethod("func_82833_r").invoke(item);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}

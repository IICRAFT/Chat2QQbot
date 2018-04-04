package com.willzcode;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.nbt.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by willz on 2018/3/19.
 * ItemHelper deal with ItemStack
 */
@SuppressWarnings("deprecation")
class ItemHelper {
    private static ItemStack fromNbtCompound(NbtCompound tag) {
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

    private static List<ItemStack> fromContainer(ItemStack container) {
        //Object nmsitem = MinecraftReflection.getMinecraftItemStack(container);
        container = MinecraftReflection.getBukkitItemStack(container);
        NbtWrapper<?> tag = NbtFactory.fromItemTag(container);
        List<ItemStack> items = new ArrayList<>();
        if (tag.getType() == NbtType.TAG_LIST) {
            NbtList list = (NbtList) tag;
            for (Object aList : list) {
                NbtCompound tagItem = (NbtCompound) aList;
                items.add(fromNbtCompound(tagItem));
            }
        } else if (tag.getType() == NbtType.TAG_COMPOUND) {
            NbtCompound compound = (NbtCompound) tag;
            for (NbtBase<?> aCompound : compound) {
                NbtWrapper<?> tagItem = (NbtWrapper<?>) aCompound;
                if (tagItem.getType() == NbtType.TAG_COMPOUND)
                    items.add(fromNbtCompound((NbtCompound) tagItem));
            }
        }
        return items;
    }

    static String getContainerItemsRecursively(ItemStack container) {
        String bagstr = "";
        List<ItemStack> items = ItemHelper.fromContainer(container);
        for (ItemStack item : items) {
            if (item != null) {
                if (isContainer(item))
                    bagstr += getContainerItemsRecursively(item);
                else
                    bagstr += getShownString(item) + " ";
            }
        }
        return bagstr;
    }

    static boolean isContainer(ItemStack itemStack) {
        return itemStack.getTypeId() == 5227;
    }

    static String getShownString(ItemStack i) {
        return getLocalizedName(i, true)+ "×" + i.getAmount();
    }

    @SuppressWarnings("SameParameterValue")
    private static String getLocalizedName(ItemStack i, boolean oldname) {
        try {
            if (oldname) {
                i = new ItemStack(i);
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

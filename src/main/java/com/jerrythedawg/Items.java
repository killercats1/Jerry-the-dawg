package com.jerrythedawg;

import java.util.Collections;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The pet items. 1.8.8 has no custom items, so they are named vanilla items with a hidden marker. */
enum Items {
    WHISTLE(Material.GOLD_NUGGET, "§eDog Whistle", "§7Calls your pets to you"),
    TREATS(Material.COOKIE, "§6Dog Treats", "§7Right-click your dog to give one"),
    BRUSH(Material.FEATHER, "§fPet Brush", "§7Right-click your dog to brush him"),
    BALL(Material.SLIME_BALL, "§aTennis Ball", "§7Right-click to play fetch"),
    // Furniture: placed like normal blocks. He naps on his bed and eats from his bowl.
    BED(Material.CARPET, 14, "§cDog Bed", "§7Place it: he naps on it when he's sleepy"),
    BOWL(Material.CAULDRON_ITEM, 0, "§7Food Bowl", "§7Place it, then fill it with treats or meat");

    private static final String MARKER = "§8Jerry the Dawg";
    /** The everyday items (the furniture is handed out separately, since it gets placed). */
    static final Items[] CARRIED = {WHISTLE, TREATS, BRUSH, BALL};

    private final Material material;
    private final short data;
    private final String name;
    private final String hint;

    Items(Material material, String name, String hint) {
        this(material, 0, name, hint);
    }

    Items(Material material, int data, String name, String hint) {
        this.material = material;
        this.data = (short) data;
        this.name = name;
        this.hint = hint;
    }

    ItemStack create() {
        ItemStack stack = new ItemStack(material, 1, data);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Arrays.asList(hint, MARKER));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Which pet item this is, or null for anything else. */
    static Items of(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : Collections.<String>emptyList();
        if (!lore.contains(MARKER) || !meta.hasDisplayName()) {
            return null;
        }
        for (Items item : values()) {
            if (item.material == stack.getType() && item.name.equals(meta.getDisplayName())) {
                return item;
            }
        }
        return null;
    }

    static boolean has(Player player, Items item) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (of(stack) == item) {
                return true;
            }
        }
        return false;
    }

    /** Gives any of the everyday pet items the player doesn't already have. */
    static void giveAll(Player player) {
        for (Items item : CARRIED) {
            if (!has(player, item)) {
                give(player, item);
            }
        }
    }

    static void give(Player player, Items item) {
        for (ItemStack left : player.getInventory().addItem(item.create()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }
}

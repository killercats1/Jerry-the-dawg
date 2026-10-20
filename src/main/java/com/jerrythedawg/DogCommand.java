package com.jerrythedawg;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /dog: help, tricks, naming, calling your pets, status... and /dog <trick>. */
final class DogCommand implements CommandExecutor {
    private final JerryPlugin plugin;
    private final Pets pets;
    private final Speech speech;

    DogCommand(JerryPlugin plugin, Pets pets, Speech speech) {
        this.plugin = plugin;
        this.pets = pets;
        this.speech = speech;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("ai")) {
            ai(sender, args); // works from the server console too (the best place to type the key)
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players have pets!");
            return true;
        }
        Player player = (Player) sender;
        PetData data = plugin.data(player);
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        String rest = join(args, 1);

        if (sub.equals("help")) {
            for (String line : Text.HELP) {
                pets.tell(player, line);
            }
        } else if (sub.equals("tricks")) {
            for (String line : Text.TRICK_LIST) {
                pets.tell(player, line);
            }
            StringBuilder stars = new StringBuilder("§7Practice: ");
            for (java.util.Map.Entry<String, String> trick : Text.TRICK_NAMES.entrySet()) {
                int done = Math.min(Pets.MASTER_AT, Pets.skill(data, trick.getKey()));
                stars.append("§f").append(trick.getValue()).append(' ').append(done >= Pets.MASTER_AT ? "§a(mastered) " : "§e" + done + "/" + Pets.MASTER_AT + " ");
            }
            pets.tell(player, stars.toString());
        } else if (sub.equals("name") || sub.equals("rename")) {
            if (rest.isEmpty()) {
                pets.tell(player, "§7Your dog is called §bJerry§7. Rename him with §f/dog name <name>");
            } else if (!pets.rename(player, "dog", rest, false)) {
                pets.tell(player, "§7That name didn't work. Try letters and numbers (up to 20).");
            }
        } else if (sub.equals("catname")) {
            if (!data.hasCat) {
                pets.tell(player, Text.NO_CAT);
            } else if (rest.isEmpty()) {
                pets.tell(player, "§7Your cat is called §dMochi§7. Rename her with §f/dog catname <name>");
            } else if (!pets.rename(player, "cat", rest, false)) {
                pets.tell(player, "§7That name didn't work. Try letters and numbers (up to 20).");
            }
        } else if (sub.equals("pupname")) {
            if (!data.hasPup) {
                pets.tell(player, Text.NO_PUP);
            } else if (rest.isEmpty()) {
                pets.tell(player, "§7Your puppy is called §aPip§7. Rename him with §f/dog pupname <name>");
            } else if (!pets.rename(player, "pup", rest, false)) {
                pets.tell(player, "§7That name didn't work. Try letters and numbers (up to 20).");
            }
        } else if (sub.equals("catcoat") || sub.equals("coat")) {
            pets.setCatCoat(player, rest);
        } else if (sub.equals("call") || sub.equals("whistle")) {
            pets.whistle(player);
        } else if (sub.equals("status")) {
            pets.showStatus(player);
        } else if (sub.equals("items")) {
            Items.giveAll(player);
            pets.home().giveFurniture(player); // (a bed or bowl you've already put down stays where it is)
            pets.tell(player, "§7Here are your pet items!");
        } else if (sub.equals("quiet")) {
            data.quiet = !data.quiet;
            plugin.markDirty();
            pets.tell(player, data.quiet ? Text.QUIET_ON : Text.QUIET_OFF);
        } else if (sub.equals("reset")) {
            pets.reset(player);
        } else {
            // Anything else is a trick: "/dog roll over", "/dog sit then shake"
            speech.hear(player, join(args, 0));
        }
        return true;
    }

    /** /dog ai [on|off|key <key>]: Smart Jerry. Anyone can see how to use it; only ops can change it. */
    private void ai(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        boolean admin = !(sender instanceof Player) || sender.isOp() || sender.hasPermission("jerrythedawg.admin");
        if (what.isEmpty() || "help".equals(what)) {
            for (String line : Text.AI_HELP) {
                say(sender, line);
            }
            boolean on = plugin.getConfig().getBoolean("smart-jerry.enabled", false);
            boolean hasKey = !plugin.getConfig().getString("smart-jerry.groq-api-key", "").trim().isEmpty();
            say(sender, !on ? Text.AI_OFF : hasKey ? Text.AI_ON : Text.AI_NEEDS_KEY);
            return;
        }
        if (!admin) {
            say(sender, Text.AI_ADMIN_ONLY);
            return;
        }
        if ("on".equals(what) || "off".equals(what)) {
            plugin.getConfig().set("smart-jerry.enabled", "on".equals(what));
            plugin.saveConfig();
            boolean hasKey = !plugin.getConfig().getString("smart-jerry.groq-api-key", "").trim().isEmpty();
            say(sender, "off".equals(what) ? Text.AI_OFF : hasKey ? Text.AI_ON : Text.AI_NEEDS_KEY);
        } else if ("key".equals(what) && args.length > 2) {
            // Saved, never shown back (or logged by us).
            plugin.getConfig().set("smart-jerry.groq-api-key", args[2].trim());
            plugin.saveConfig();
            say(sender, Text.AI_KEY_SAVED);
            if (!plugin.getConfig().getBoolean("smart-jerry.enabled", false)) {
                say(sender, "§7Turn it on with §f/dog ai on");
            }
        } else {
            say(sender, "§7Use §f/dog ai on§7, §f/dog ai off§7 or §f/dog ai key <key>");
        }
    }

    private void say(CommandSender sender, String text) {
        if (sender instanceof Player) {
            pets.tell((Player) sender, text);
        } else {
            sender.sendMessage(text.replaceAll("§.", ""));
        }
    }

    private static String join(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            out.append(i > from ? " " : "").append(args[i]);
        }
        return out.toString().trim();
    }
}

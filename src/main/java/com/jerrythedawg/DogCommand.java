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

    private static String join(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            out.append(i > from ? " " : "").append(args[i]);
        }
        return out.toString().trim();
    }
}

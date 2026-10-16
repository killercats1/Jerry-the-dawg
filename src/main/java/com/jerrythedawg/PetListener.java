package com.jerrythedawg;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

final class PetListener implements Listener {
    private final Pets pets;
    private final Speech speech;

    PetListener(Pets pets, Speech speech) {
        this.pets = pets;
        this.speech = speech;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        pets.onJoin(event.getPlayer());
    }

    /** Talk to your dog in chat: "Jerry, sit!" */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String message = event.getMessage();
        // Chat arrives off the main thread; pets can only be touched on it.
        pets.later(1, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    speech.heardChat(player, message);
                }
            }
        });
    }

    /** Your pets can't get hurt, so you never lose them. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if ((event.getEntity() instanceof Wolf || event.getEntity() instanceof Ocelot) && pets.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractPet(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity target = event.getRightClicked();
        if (!(target instanceof Wolf || target instanceof Ocelot) || pets.ownerOf(target) != player) {
            return;
        }
        ItemStack hand = player.getItemInHand();
        Items item = Items.of(hand);
        if (item != null) {
            event.setCancelled(true); // don't sit him down or feed him the cookie
            if (item == Items.TREATS) {
                pets.treat(player, target);
            } else if (item == Items.BRUSH) {
                pets.brush(player, target);
            } else if (item == Items.BALL) {
                pets.play(player);
            } else {
                pets.whistle(player);
            }
            return;
        }
        if (target instanceof Wolf && (hand == null || hand.getType() == Material.AIR) && player.isSneaking()) {
            event.setCancelled(true); // sneak + right-click = pet him (a normal right-click still sits him)
            pets.pet(player, (Wolf) target);
        }
        // Name tags rename him as usual; the next pet tick notices the new name.
    }

    @EventHandler
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Items item = Items.of(event.getItem());
        if (item == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (item == Items.WHISTLE) {
            pets.whistle(player);
        } else if (item == Items.BALL) {
            pets.play(player);
        } else {
            pets.tell(player, Text.USE_ON_DOG);
        }
    }

    /** If the whistle made a new dog while the old one was in an unloaded chunk, remove the old copy. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Wolf || entity instanceof Ocelot) {
                pets.removeIfStaleCopy(entity);
            }
        }
    }
}

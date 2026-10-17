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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
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

    /** Your pets can't get hurt, so you never lose them. And if you get hurt, he comes to check on you. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (pets.isThrownBall(event.getEntity())) {
            event.setCancelled(true); // the tennis ball survives lava, cactus, fire and explosions
        } else if ((event.getEntity() instanceof Wolf || event.getEntity() instanceof Ocelot) && pets.isPet(event.getEntity())) {
            event.setCancelled(true);
        } else if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            LivingEntity attacker = attackerOf(event);
            if (attacker != null && attacker != player && !pets.isPet(attacker)) {
                pets.defend(player, attacker); // something hit you: he goes after it
            } else {
                pets.onOwnerHurt(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityCombustEvent event) {
        if (pets.isThrownBall(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Hoppers can't swallow the ball mid-fetch. */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (pets.isThrownBall(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (pets.isThrownBall(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Who hit you (the shooter, for arrows and other projectiles), or null. */
    private static LivingEntity attackerOf(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent)) {
            return null;
        }
        Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof LivingEntity) {
            return (LivingEntity) ((Projectile) damager).getShooter();
        }
        return damager instanceof LivingEntity ? (LivingEntity) damager : null;
    }

    /**
     * Pets don't run off to fight random things (that's why vanilla tamed wolves wander away)...
     * but anything that attacks you is fair game.
     */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Wolf || event.getEntity() instanceof Ocelot) || !pets.isPet(event.getEntity())) {
            return;
        }
        if (event.getTarget() == null) {
            return; // the fight is over (target died or got away): always let him stop
        }
        if (pets.isPup(event.getEntity())) {
            event.setCancelled(true); // the puppy is a lover, not a fighter
            return;
        }
        EntityTargetEvent.TargetReason reason = event.getReason();
        boolean defending = reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_OWNER
            || reason == EntityTargetEvent.TargetReason.CUSTOM; // CUSTOM = set by defend()
        if (!defending) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        pets.onOwnerEat(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        pets.onBedtime(event.getPlayer());
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
        if (item != null && item != Items.BED && item != Items.BOWL) {
            event.setCancelled(true); // don't sit him down or feed him the cookie
            if (item == Items.TREATS) {
                pets.treat(player, target);
            } else if (item == Items.BRUSH) {
                pets.brush(player, target);
            } else if (item == Items.BALL) {
                pets.throwBall(player);
            } else if (player.isSneaking()) {
                pets.showStatus(player);
            } else {
                pets.whistle(player);
            }
            return;
        }
        if ((hand == null || hand.getType() == Material.AIR) && player.isSneaking()) {
            event.setCancelled(true); // sneak + right-click = pet him (a normal right-click still sits him)
            if (target instanceof Wolf) {
                pets.pet(player, (Wolf) target);
            } else {
                pets.petCat(player, (Ocelot) target); // purrrr
            }
        }
        // Name tags rename him and dyes recolour his collar as usual; the next pet tick notices.
    }

    @EventHandler
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Filling the food bowl (with treats or meat).
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && pets.home().use(event.getPlayer(), event.getClickedBlock(), event.getItem())) {
            event.setCancelled(true);
            return;
        }
        Items item = Items.of(event.getItem());
        if (item == null || item == Items.BED || item == Items.BOWL) {
            return; // (the bed and bowl get placed like normal blocks)
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (item == Items.WHISTLE) {
            if (player.isSneaking()) {
                pets.showStatus(player); // sneak + whistle = how's he doing?
            } else {
                pets.whistle(player);
            }
        } else if (item == Items.BALL) {
            pets.throwBall(player);
        } else {
            pets.tell(player, Text.USE_ON_DOG);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Items item = Items.of(event.getItemInHand());
        if (item == Items.BED || item == Items.BOWL) {
            pets.home().placed(event.getPlayer(), item, event.getBlockPlaced());
        }
    }

    /** Breaking the bed or bowl gives you the Dog Bed / Food Bowl back. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (pets.home().broken(event.getBlock())) {
            event.setCancelled(true); // (we removed the block and dropped our item ourselves)
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

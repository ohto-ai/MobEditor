package com.ohtoai.mobeditor.listener;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.egg.SpawnEggExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles MobEditor spawn egg usage.
 *
 * Minecraft's entity_data loading order sets Health BEFORE Attributes,
 * so health gets clamped to base max_health. This listener intercepts
 * MobEditor egg use and manually spawns the entity with correct health.
 */
public class EggUseListener implements Listener {

    private final MobEditor plugin;
    private static final NamespacedKey KEY_HEALTH = SpawnEggExtractor.KEY_SAVED_HEALTH;

    /** Track recently spawned entities that need health fix (UUID -> savedHealth). */
    private final Map<UUID, Float> pendingHealthFixes = new ConcurrentHashMap<>();

    public EggUseListener(MobEditor plugin) {
        this.plugin = plugin;
    }

    // ── Intercept egg usage ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.getType().name().endsWith("_SPAWN_EGG")) return;

        // Check if this is a MobEditor egg
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        Boolean isMobEditor = meta.getPersistentDataContainer()
                .get(SpawnEggExtractor.KEY_EGG, PersistentDataType.BOOLEAN);
        if (!Boolean.TRUE.equals(isMobEditor)) return;

        // Cancel vanilla spawn
        event.setCancelled(true);

        // Read entity data from PDC
        String b64 = meta.getPersistentDataContainer()
                .get(SpawnEggExtractor.KEY_ENTITY_DATA, PersistentDataType.STRING);
        Float savedHealth = meta.getPersistentDataContainer()
                .get(KEY_HEALTH, PersistentDataType.FLOAT);

        if (b64 == null || b64.isEmpty()) {
            player.sendMessage("§c刷怪蛋数据损坏。");
            return;
        }

        // Calculate spawn location
        Location loc;
        if (event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            BlockFace face = event.getBlockFace();
            loc = block.getRelative(face).getLocation().add(0.5, 0, 0.5);
        } else {
            loc = player.getLocation().add(
                    player.getLocation().getDirection().multiply(2));
        }
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(0);

        spawnFromEgg(player, item, loc, b64, savedHealth);
    }

    // ── CreatureSpawnEvent — apply delayed health fix ────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Health fix: if this entity was spawned by us, apply saved health
        // after Minecraft has fully loaded attributes from equipment
        Float savedHealth = pendingHealthFixes.remove(event.getEntity().getUniqueId());
        if (savedHealth != null && savedHealth > 0
                && event.getEntity() instanceof LivingEntity living) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Apply health on next tick — by then equipment attributes are fully active
                if (living.isValid() && !living.isDead()) {
                    double maxHealth = living.getAttribute(
                            org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                    living.setHealth(Math.min(savedHealth, maxHealth));
                }
            });
        }
    }

    // ── Manual spawn logic ───────────────────────────────────────────────

    private void spawnFromEgg(Player player, ItemStack egg, Location loc,
                               String nbtBase64, Float savedHealth) {
        try {
            byte[] nbtBytes = Base64.getDecoder().decode(nbtBase64);
            CompoundTag entityData = SpawnEggExtractor.deserializeNbt(nbtBytes);

            // Remove Health temporarily so load() doesn't clamp it
            // (only for LivingEntity types — non-living entities don't have Health)
            boolean hasHealth = entityData.contains("Health");
            float health = 0f;
            if (hasHealth) {
                health = savedHealth != null ? savedHealth : entityData.getFloat("Health");
                entityData.remove("Health");
            }

            // Get NMS world
            CraftWorld craftWorld = (CraftWorld) loc.getWorld();
            net.minecraft.server.level.ServerLevel nmsWorld = craftWorld.getHandle();

            // Resolve entity type
            String entityTypeId = entityData.getString("id");
            var optType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getOptional(new net.minecraft.resources.ResourceLocation(entityTypeId));
            if (optType.isEmpty()) {
                player.sendMessage("§c无法识别的实体类型: " + entityTypeId);
                return;
            }
            net.minecraft.world.entity.EntityType<?> entityType = optType.get();

            // Create entity (simple create, then load NBT data)
            net.minecraft.world.entity.Entity nmsEntity = entityType.create(nmsWorld);

            if (nmsEntity == null) {
                player.sendMessage("§c无法创建实体。");
                return;
            }

            // Load all data. For living entities, restore Health after load
            // so it's available to set after attributes are active.
            if (hasHealth) {
                entityData.putFloat("Health", health);
            }
            nmsEntity.load(entityData);

            // Set position
            nmsEntity.setPos(loc.getX(), loc.getY(), loc.getZ());
            nmsEntity.setYRot(loc.getYaw());
            nmsEntity.setXRot(loc.getPitch());

            // Add main entity to world FIRST
            nmsWorld.addFreshEntity(nmsEntity);

            // Handle passengers (riders) — spawn and mount each recursively
            if (entityData.contains("Passengers", Tag.TAG_LIST)) {
                ListTag passengerList = entityData.getList("Passengers", Tag.TAG_COMPOUND);
                for (int i = 0; i < passengerList.size(); i++) {
                    CompoundTag pTag = passengerList.getCompound(i);
                    spawnPassenger(nmsWorld, pTag, nmsEntity, loc);
                }
            }

            // Fix health: load clamped it, so override now that attributes are active
            if (hasHealth && nmsEntity instanceof net.minecraft.world.entity.LivingEntity nmsLiving) {
                double maxH = nmsLiving.getMaxHealth();
                nmsLiving.setHealth((float) Math.min(health, maxH));
            }

            // Consume one egg
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                egg.setAmount(egg.getAmount() - 1);
                player.getInventory().setItemInMainHand(egg);
            }

            if (nmsEntity.getCustomName() != null) {
                player.sendMessage("§a已生成: " + nmsEntity.getCustomName().getString());
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn from MobEditor egg: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§c生成实体失败: " + e.getMessage());
        }
    }

    /**
     * Recursively spawn a passenger entity and mount it on the vehicle.
     */
    private void spawnPassenger(net.minecraft.server.level.ServerLevel world, CompoundTag pTag,
                                 net.minecraft.world.entity.Entity vehicle, Location loc) {
        // Only manipulate Health for living entities
        boolean pHasHealth = pTag.contains("Health");
        float pHealth = 0f;
        if (pHasHealth) {
            pHealth = pTag.getFloat("Health");
            pTag.remove("Health");
        }

        String typeId = pTag.getString("id");
        var optType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getOptional(new net.minecraft.resources.ResourceLocation(typeId));
        if (optType.isEmpty()) return;
        net.minecraft.world.entity.EntityType<?> pType = optType.get();

        net.minecraft.world.entity.Entity pEntity = pType.create(world);
        if (pEntity == null) return;

        if (pHasHealth) {
            pTag.putFloat("Health", pHealth);
        }
        pEntity.load(pTag);
        pEntity.setPos(loc.getX(), loc.getY(), loc.getZ());
        world.addFreshEntity(pEntity);

        // Mount on vehicle
        pEntity.startRiding(vehicle, true);

        // Fix health after attributes are active
        if (pHasHealth && pEntity instanceof net.minecraft.world.entity.LivingEntity pLiving) {
            double maxH = pLiving.getMaxHealth();
            pLiving.setHealth((float) Math.min(pHealth, maxH));
        }

        // Recursively handle nested passengers
        if (pTag.contains("Passengers", Tag.TAG_LIST)) {
            ListTag nested = pTag.getList("Passengers", Tag.TAG_COMPOUND);
            for (int i = 0; i < nested.size(); i++) {
                spawnPassenger(world, nested.getCompound(i), pEntity, loc);
            }
        }
    }
}

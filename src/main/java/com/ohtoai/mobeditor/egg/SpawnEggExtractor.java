package com.ohtoai.mobeditor.egg;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Extracts full entity data into a spawn egg ItemStack.
 *
 * Strategy for 1.20.6:
 * 1. Save entity to CompoundTag via NMS entity.saveWithoutId()
 * 2. Strip transient tags (Pos, Motion, UUID, etc.) — KEEP Attributes & Health
 * 3. Create matching spawn egg item (fall back to PIG_SPAWN_EGG for entities
 *    that don't have one, e.g. armor_stand, minecart, item)
 * 4. Set DataComponents.ENTITY_DATA with the cleaned entity NBT
 * 5. Store full NBT as Base64 in PDC
 */
public final class SpawnEggExtractor {

    // Transient tags stripped from top-level entity data.
    // Strip reason per tag:
    //   Pos/Motion/Rotation/UUID/Dimension — reassigned on spawn
    //   FallDistance/Fire/Air/OnGround/HurtTime/HurtByTimestamp/DeathTime/PortalCooldown — runtime state
    //   Leash — references world-specific leash knot
    //   SleepingX/Y/Z — bed/pos reference, invalid after respawn
    //   AbsorptionAmount — runtime temp HP, regenerates naturally
    //
    // KEPT intentionally:
    //   Attributes — equipment modifiers must survive (e.g. +max_health)
    //   Health   — preserved & corrected post-load by EggUseListener
    //   Brain    — AI memories (villager profession, piglin behavior, etc.);
    //               stale position/UUID refs are handled gracefully by Minecraft
    //   Passengers — riders preserved with recursive cleaning
    //   ActiveEffects, HandItems, ArmorItems, HandDropChances, ArmorDropChances — equipment/effects
    //   CustomName, Silent, NoAI, Invulnerable, PersistenceRequired — entity flags
    //   Owner, Tame, Variant, Color, Type, VillagerData, Offers, Items — entity-specific data
    private static final Set<String> STRIP_TAGS = Set.of(
            "Pos", "Motion", "Rotation", "UUID", "WorldUUIDMost", "WorldUUIDLeast",
            "Dimension", "FallDistance", "Fire", "Air", "OnGround", "HurtTime",
            "HurtByTimestamp", "DeathTime", "PortalCooldown",
            "Leash", "SleepingX", "SleepingY", "SleepingZ",
            "AbsorptionAmount"
    );

    /** Transient tags to strip from PASSENGER entities recursively. */
    private static final Set<String> PASSENGER_STRIP_TAGS = Set.of(
            "Pos", "Motion", "Rotation", "UUID", "WorldUUIDMost", "WorldUUIDLeast",
            "Dimension", "FallDistance", "Fire", "Air", "OnGround", "HurtTime",
            "HurtByTimestamp", "DeathTime", "PortalCooldown",
            "Leash", "SleepingX", "SleepingY", "SleepingZ",
            "AbsorptionAmount"
    );

    /** PDC key: marker that this is a MobEditor egg */
    public static final NamespacedKey KEY_EGG = new NamespacedKey("mobeditor", "egg");
    /** PDC key: full entity NBT (GZip + Base64) for guaranteed fidelity */
    public static final NamespacedKey KEY_ENTITY_DATA = new NamespacedKey("mobeditor", "entity_data");
    /** PDC key: saved health value (float in NBT, stored as string) for post-spawn fix */
    public static final NamespacedKey KEY_SAVED_HEALTH = new NamespacedKey("mobeditor", "health");

    private SpawnEggExtractor() {}

    /**
     * Create a spawn egg ItemStack containing the full entity data.
     * <p>
     * Works with any entity type — both living (mobs) and non-living
     * (armor stands, minecarts, dropped items, etc.). For entities without
     * a dedicated spawn egg item, a {@code PIG_SPAWN_EGG} is used as the base
     * item — the {@code minecraft:entity_data} component still points to the
     * correct entity type, so the egg spawns the right entity regardless.
     *
     * @param entity the entity to extract (any Entity, not just LivingEntity)
     * @return the spawn egg ItemStack (never null)
     */
    public static ItemStack createSpawnEgg(Entity entity) throws Exception {
        // 1. Get NMS entity handle
        CraftEntity craftEntity = (CraftEntity) entity;
        net.minecraft.world.entity.Entity nmsEntity = craftEntity.getHandle();

        // 2. Save ALL entity data to CompoundTag
        CompoundTag entityData = new CompoundTag();
        nmsEntity.saveWithoutId(entityData);

        // Remember health for post-spawn fix (only relevant for LivingEntity)
        Float savedHealth = null;
        if (entity instanceof LivingEntity living) {
            savedHealth = entityData.contains("Health")
                    ? entityData.getFloat("Health")
                    : (float) living.getHealth();
        }

        // 3. Ensure the entity type ID is set
        String entityTypeId = nmsEntity.getType().builtInRegistryHolder()
                .key().location().toString();
        entityData.putString("id", entityTypeId);

        // 4. Strip volatile/transient tags (NOT Attributes, Health, or Passengers)
        for (String tag : STRIP_TAGS) {
            entityData.remove(tag);
        }

        // 5. Recursively clean passenger NBT (strip transient tags from riders)
        cleanPassengerTags(entityData);

        // 6. Get the spawn egg material via Bukkit Material name
        // If the entity has no dedicated spawn egg (e.g. armor_stand, minecart, item),
        // fall back to PIG_SPAWN_EGG — the entity_data component already contains the
        // correct entity type id, so the egg will spawn the right entity.
        String eggMaterialName = entityTypeId.substring(entityTypeId.indexOf(':') + 1).toUpperCase() + "_SPAWN_EGG";
        Material eggMaterial = Material.getMaterial(eggMaterialName);
        if (eggMaterial == null) {
            eggMaterial = Material.PIG_SPAWN_EGG;
        }

        // 7. Create spawn egg ItemStack with entity_data component
        ItemStack bukkitBase = new ItemStack(eggMaterial);
        net.minecraft.world.item.ItemStack nmsEgg = CraftItemStack.asNMSCopy(bukkitBase);
        nmsEgg.set(DataComponents.ENTITY_DATA, CustomData.of(entityData));

        // 8. Set custom name on the egg
        String customName = entity.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            nmsEgg.set(DataComponents.ITEM_NAME,
                    net.minecraft.network.chat.Component.literal("§e" + customName + " 的刷怪蛋"));
        } else {
            nmsEgg.set(DataComponents.ITEM_NAME,
                    net.minecraft.network.chat.Component.literal("§e" + entity.getType().getKey().getKey() + " 刷怪蛋"));
        }

        // 9. Convert to Bukkit
        ItemStack bukkitEgg = CraftItemStack.asBukkitCopy(nmsEgg);

        // 10. Add PDC markers
        ItemMeta meta = bukkitEgg.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(KEY_EGG, PersistentDataType.BOOLEAN, true);

            // Store full entity NBT as Base64
            byte[] nbtBytes = serializeNbt(entityData);
            meta.getPersistentDataContainer().set(KEY_ENTITY_DATA, PersistentDataType.STRING,
                    Base64.getEncoder().encodeToString(nbtBytes));

            // Store saved health separately for post-spawn correction
            if (savedHealth != null) {
                meta.getPersistentDataContainer().set(KEY_SAVED_HEALTH, PersistentDataType.FLOAT, savedHealth);
            }

            bukkitEgg.setItemMeta(meta);
        }

        return bukkitEgg;
    }

    /**
     * Serialize a CompoundTag to GZip-compressed bytes.
     */
    public static byte[] serializeNbt(CompoundTag tag) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(baos));
        NbtIo.write(tag, dos);
        dos.close();
        return baos.toByteArray();
    }

    /**
     * Recursively strip transient tags from passenger entities.
     * Passenger NBT is a ListTag of CompoundTags, each representing
     * a rider entity. Their own Passengers are cleaned recursively.
     */
    public static void cleanPassengerTags(CompoundTag entityData) {
        if (!entityData.contains("Passengers", Tag.TAG_LIST)) return;
        ListTag passengers = entityData.getList("Passengers", Tag.TAG_COMPOUND);
        for (int i = 0; i < passengers.size(); i++) {
            CompoundTag passenger = passengers.getCompound(i);
            // Strip transient tags from passenger
            for (String tag : PASSENGER_STRIP_TAGS) {
                passenger.remove(tag);
            }
            // Recursively clean nested passengers
            cleanPassengerTags(passenger);
        }
    }

    /**
     * Deserialize a CompoundTag from GZip-compressed bytes.
     */
    public static CompoundTag deserializeNbt(byte[] bytes) throws Exception {
        java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes)));
        CompoundTag tag = NbtIo.read(dis);
        dis.close();
        return tag;
    }
}

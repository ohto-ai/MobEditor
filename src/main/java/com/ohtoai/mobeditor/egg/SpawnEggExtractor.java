package com.ohtoai.mobeditor.egg;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
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
 * 2. Strip volatile/transient tags (Pos, Motion, UUID, etc.)
 * 3. Create matching spawn egg item
 * 4. Set DataComponents.ENTITY_DATA with the cleaned entity NBT
 * 5. Store full NBT as Base64 in PDC for guaranteed fidelity
 */
public final class SpawnEggExtractor {

    // Tags to strip from entity data (will be set on spawn)
    private static final Set<String> STRIP_TAGS = Set.of(
            "Pos", "Motion", "Rotation", "UUID", "WorldUUIDMost", "WorldUUIDLeast",
            "Dimension", "FallDistance", "Fire", "Air", "OnGround", "HurtTime",
            "HurtByTimestamp", "DeathTime", "PortalCooldown", "Passengers",
            "Leash", "Brain", "SleepingX", "SleepingY", "SleepingZ",
            "AbsorptionAmount", "Attributes" // Attributes are auto-calculated, keep manual ones if needed
    );

    private SpawnEggExtractor() {}

    /**
     * Create a spawn egg ItemStack containing the full entity data.
     *
     * @param entity the entity to extract
     * @return the spawn egg ItemStack, or null if this entity type has no spawn egg
     */
    public static ItemStack createSpawnEgg(LivingEntity entity) throws Exception {
        // 1. Get NMS entity handle
        CraftLivingEntity craftEntity = (CraftLivingEntity) entity;
        net.minecraft.world.entity.LivingEntity nmsEntity = craftEntity.getHandle();

        // 2. Save entity data to CompoundTag
        CompoundTag entityData = new CompoundTag();
        nmsEntity.saveWithoutId(entityData);

        // 3. Ensure the entity type ID is set
        String entityTypeId = nmsEntity.getType().builtInRegistryHolder()
                .key().location().toString();
        entityData.putString("id", entityTypeId);

        // 4. Strip volatile tags
        for (String tag : STRIP_TAGS) {
            entityData.remove(tag);
        }

        // 5. Get the spawn egg material via Bukkit Material name (1.20.6 compatible)
        String eggMaterialName = entityTypeId.substring(entityTypeId.indexOf(':') + 1).toUpperCase() + "_SPAWN_EGG";
        Material eggMaterial = Material.getMaterial(eggMaterialName);
        if (eggMaterial == null) {
            return null; // Entity type has no spawn egg
        }

        // 6. Create spawn egg ItemStack from Material, then convert to NMS
        ItemStack bukkitBase = new ItemStack(eggMaterial);
        net.minecraft.world.item.ItemStack nmsEgg = CraftItemStack.asNMSCopy(bukkitBase);

        // 7. Set the entity_data data component
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

        // 9. Convert to Bukkit ItemStack
        ItemStack bukkitEgg = CraftItemStack.asBukkitCopy(nmsEgg);

        // 10. Add PDC marker and full NBT fallback
        ItemMeta meta = bukkitEgg.getItemMeta();
        if (meta != null) {
            // Marker key to identify MobEditor eggs
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("mobeditor", "egg"),
                    PersistentDataType.BOOLEAN,
                    true);

            // Store full entity NBT as Base64 for guaranteed fidelity
            byte[] nbtBytes = serializeNbt(entityData);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("mobeditor", "entity_data"),
                    PersistentDataType.STRING,
                    Base64.getEncoder().encodeToString(nbtBytes));

            bukkitEgg.setItemMeta(meta);
        }

        return bukkitEgg;
    }

    /**
     * Serialize a CompoundTag to GZip-compressed bytes.
     */
    private static byte[] serializeNbt(CompoundTag tag) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(baos));
        NbtIo.write(tag, dos);
        dos.close();
        return baos.toByteArray();
    }
}

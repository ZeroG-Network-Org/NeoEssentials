package com.zerog.neoessentials.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * {@link CompoundTag} accessors with an explicit default value.
 *
 * <p>26.1 port note: as of Minecraft 26.1, {@code CompoundTag}'s single-arg getters
 * (e.g. {@code getInt(String)}) return {@code Optional<Integer>} instead of a raw
 * primitive with an implicit 0/""/false default — Mojang added parallel
 * {@code get<Type>Or(String, default)} methods that behave like the old 1.21.1
 * single-arg getters did. This class just forwards to those, so the rest of the
 * codebase (which was written against the old implicit-default behavior) doesn't
 * need every call site touched individually.</p>
 */
public final class CompoundTagCompat {

    private CompoundTagCompat() {}

    public static String getString(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }

    public static int getInt(CompoundTag tag, String key) {
        return tag.getIntOr(key, 0);
    }

    public static float getFloat(CompoundTag tag, String key) {
        return tag.getFloatOr(key, 0f);
    }

    public static double getDouble(CompoundTag tag, String key) {
        return tag.getDoubleOr(key, 0.0);
    }

    public static long getLong(CompoundTag tag, String key) {
        return tag.getLongOr(key, 0L);
    }

    public static byte getByte(CompoundTag tag, String key) {
        return tag.getByteOr(key, (byte) 0);
    }

    public static short getShort(CompoundTag tag, String key) {
        return tag.getShortOr(key, (short) 0);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBooleanOr(key, false);
    }

    public static ListTag getList(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key);
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    /**
     * 26.1 port: {@code CompoundTag} has no {@code putUUID}/{@code hasUUID}/{@code getUUID(String)}
     * on this version — only the {@link net.minecraft.nbt.IntArrayTag}-backed
     * {@code putIntArray}/{@code getIntArray} survive across both 1.21.1 and 26.1, so these three
     * helpers store a UUID as an int array via {@link UUIDUtil} instead, working identically on
     * both versions rather than relying on either's native UUID tag methods.
     */
    public static void putUUID(CompoundTag tag, String key, UUID value) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(value));
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.getIntArray(key).isPresent();
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        return tag.getIntArray(key).map(UUIDUtil::uuidFromIntArray).orElse(null);
    }

    /**
     * 26.1 port: {@code ItemStack.parseOptional(RegistryAccess, CompoundTag)} was removed
     * along with the old NBT-codec integration — item (de)serialization now goes through
     * {@code ItemStack.CODEC} directly via {@link RegistryOps}. Returns {@link ItemStack#EMPTY}
     * on any parse failure, matching {@code parseOptional}'s old error-tolerant behavior.
     */
    public static ItemStack parseItemStack(HolderLookup.Provider registries, CompoundTag tag) {
        return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag)
            .result().orElse(ItemStack.EMPTY);
    }
}

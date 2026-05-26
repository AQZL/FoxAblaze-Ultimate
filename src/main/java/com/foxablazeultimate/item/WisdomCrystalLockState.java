package com.foxablazeultimate.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class WisdomCrystalLockState {

    private static final String ROOT_TAG = "foxablazeultimate";
    private static final String EATEN_KEY = "wisdom_eaten";
    private static final String LOCKED_KEY = "wisdom_crystal_locked";

    public static final int CRYSTAL_LOCK_THRESHOLD = 10;

    private WisdomCrystalLockState() {}

    public static int incrementEaten(Player player) {
        CompoundTag tag = subTag(player);
        int next = tag.getInt(EATEN_KEY) + 1;
        tag.putInt(EATEN_KEY, next);
        return next;
    }

    public static int getEaten(Player player) {
        return subTag(player).getInt(EATEN_KEY);
    }

    public static boolean addCrystalLock(Player player, ResourceLocation skillId) {
        CompoundTag tag = subTag(player);
        ListTag list = tag.getList(LOCKED_KEY, Tag.TAG_STRING);
        String s = skillId.toString();
        for (int i = 0; i < list.size(); i++) {
            if (s.equals(list.getString(i))) return false;
        }
        list.add(StringTag.valueOf(s));
        tag.put(LOCKED_KEY, list);
        return true;
    }

    public static boolean isCrystalLocked(ServerPlayer player, ResourceLocation skillId) {
        CompoundTag tag = subTag(player);
        if (!tag.contains(LOCKED_KEY)) return false;
        ListTag list = tag.getList(LOCKED_KEY, Tag.TAG_STRING);
        String s = skillId.toString();
        for (int i = 0; i < list.size(); i++) {
            if (s.equals(list.getString(i))) return true;
        }
        return false;
    }

    private static CompoundTag subTag(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag sub;
        if (root.contains(ROOT_TAG)) {
            sub = root.getCompound(ROOT_TAG);
        } else {
            sub = new CompoundTag();
            root.put(ROOT_TAG, sub);
        }
        return sub;
    }
}

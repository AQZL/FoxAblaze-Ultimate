package com.foxablazeultimate.compat.stextras;

import java.lang.reflect.Method;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

public final class STExtrasCompat {

    public static final String MOD_ID = "stextras";

    private static volatile boolean RESOLVED = false;
    private static volatile Method LOCK_SKILL;

    private STExtrasCompat() {}

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    public static boolean lockSkill(ServerPlayer player, ResourceLocation skillId) {
        if (!isLoaded()) return false;
        Method m = resolveLockSkill();
        if (m == null) return false;
        try {
            Object result = m.invoke(null, player, skillId);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            FoxAblazeUltimateMod.LOGGER.warn(
                    "[FoxAblazeUltimate] 调用 STExtras.lockSkill 失败：{}", e.toString());
            return false;
        }
    }

    private static Method resolveLockSkill() {
        if (RESOLVED) return LOCK_SKILL;
        synchronized (STExtrasCompat.class) {
            if (RESOLVED) return LOCK_SKILL;
            try {
                Class<?> player = Class.forName("org.crypticdev.stextras.storage.STExtarsStorage$Player");
                LOCK_SKILL = player.getMethod("lockSkill", ServerPlayer.class, ResourceLocation.class);
            } catch (ReflectiveOperationException e) {
                FoxAblazeUltimateMod.LOGGER.info(
                        "[FoxAblazeUltimate] STExtras lockSkill 反射不可用：{}", e.toString());
                LOCK_SKILL = null;
            }
            RESOLVED = true;
            return LOCK_SKILL;
        }
    }
}

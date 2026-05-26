package com.foxablazeultimate.event;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.item.WisdomCrystalLockState;
import com.foxablazeultimate.network.SyncCrystalLockPayload;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID)
public final class WisdomCrystalSyncHandler {

    private WisdomCrystalSyncHandler() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    public static void syncTo(ServerPlayer player) {
        boolean locked = false;
        ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();
        if (raphael != null) {
            ResourceLocation id = raphael.getRegistryName();
            if (id == null) id = SkillAPI.getSkillRegistry().getId(raphael);
            if (id != null) {
                locked = WisdomCrystalLockState.isCrystalLocked(player, id);
            }
        }
        NetworkManager.sendToPlayer(player, new SyncCrystalLockPayload(locked));
    }
}

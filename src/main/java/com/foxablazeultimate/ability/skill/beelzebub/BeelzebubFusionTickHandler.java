package com.foxablazeultimate.ability.skill.beelzebub;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID)
public final class BeelzebubFusionTickHandler {

    private static final int CHECK_INTERVAL = 20;

    private BeelzebubFusionTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        BeelzebubFusion.tickCeremony(serverPlayer);

        if (player.tickCount % CHECK_INTERVAL == 0) {
            BeelzebubFusion.tryFuse(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BeelzebubFusion.clearCeremony(player.getUUID());
        }
    }
}

package com.foxablazeultimate.ability.skill.beelzebub;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 通过 PlayerTickEvent 周期性检查"暴食者 + 残虐者"是否同时精通，
 * 若是则触发别西卜融合（与 {@code RaphaelFusionTickHandler} 同款机制，避免 Mixin 注入到子类继承方法的麻烦）。
 *
 * <p>融合本身是一个分阶段仪式（{@link BeelzebubFusion#tickCeremony}）：
 * <ul>
 *   <li><b>前置检测</b>：每 {@value #CHECK_INTERVAL} tick（1 秒）跑一次 {@link BeelzebubFusion#tryFuse}，
 *       开销极小；融合成功后原技能被遗忘，后续检测立即在 hasSkill 检查处短路。</li>
 *   <li><b>仪式推进</b>：每 tick 调用 {@link BeelzebubFusion#tickCeremony}，
 *       PENDING 表为空时 O(1) 直接 return，性能可忽略。</li>
 * </ul>
 */
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

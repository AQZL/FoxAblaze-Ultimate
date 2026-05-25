package com.foxablazeultimate.ability.skill.raphael;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 通过 PlayerTickEvent 周期性检查"大贤者 + 变质者"是否同时精通，
 * 若是则触发拉斐尔融合（避免 Mixin 无法注入到子类继承方法的问题）。
 *
 * <p>融合本身现在是一个分阶段仪式（{@link RaphaelFusion#tickCeremony}）：
 * <ul>
 *   <li><b>前置检测</b>：每 {@value #CHECK_INTERVAL} tick（1 秒）跑一次 {@link RaphaelFusion#tryFuse}，
 *       开销极小；一旦融合成功原技能被遗忘，后续检测立即在 hasSkill 检查处短路。</li>
 *   <li><b>仪式推进</b>：每 tick 调用 {@link RaphaelFusion#tickCeremony}，
 *       PENDING 表为空时 O(1) 直接 return，性能可忽略。</li>
 * </ul>
 */
@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID)
public final class RaphaelFusionTickHandler {

    /** 前置检测间隔（tick）；仪式推进无视此值，每 tick 都跑。 */
    private static final int CHECK_INTERVAL = 20;

    private RaphaelFusionTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // 仪式状态机每 tick 推进，否则延迟节奏会被拉到 20 tick 一档不平滑。
        RaphaelFusion.tickCeremony(serverPlayer);

        // 前置检测仍维持 1 秒一次足够；融合成功后会被 hasSkill 短路。
        if (player.tickCount % CHECK_INTERVAL == 0) {
            RaphaelFusion.tryFuse(serverPlayer);
        }
    }

    /** 玩家退出时立即清场，避免在内存里挂一份永远不会再被推进的仪式。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RaphaelFusion.clearCeremony(player.getUUID());
        }
    }
}

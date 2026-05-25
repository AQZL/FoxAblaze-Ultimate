package com.foxablazeultimate.ability.skill.beelzebub;

import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.config.BeelzebubConfig;
import com.foxablazeultimate.item.beelzebub.CapturedEntityItem;
import com.foxablazeultimate.registry.FoxAblazeUltimateItems;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;
import com.foxablazeultimate.world.FoxAblazeGameRules;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.subclass.ISpatialStorage;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 暴食之王 · 实体捕获事件监听器。
 *
 * <h3>触发条件链（短路返回不捕，全部满足才捕）</h3>
 * <ol>
 *   <li>玩家 isShiftKeyDown</li>
 *   <li>持有别西卜（已学得，{@code mastery >= 0}）</li>
 *   <li>target 为 LivingEntity 且 isAlive，target ≠ player</li>
 *   <li>target 不是 boss / 玩家 / 黑名单 entity（按配置项）</li>
 *   <li>EP 检定：getMaxEP(target) ≤ getMaxEP(player)</li>
 *   <li>容量检定：虚数仓库尚有空槽</li>
 *   <li>防刷 CD（每次成功捕获后 N 秒内不再触发）</li>
 * </ol>
 *
 * <h3>通过</h3>
 * <ul>
 *   <li>把目标实体序列化为 {@link CapturedEntityItem} ItemStack 放进虚数仓库</li>
 *   <li>{@code target.discard()} 移除世界中目标</li>
 *   <li>粒子 + 音效 + 给玩家发"捕获 X"绿字提示</li>
 *   <li>取消事件，避免后续 hand swing 与默认 vanilla interact 冲突</li>
 * </ul>
 *
 * <h3>失败</h3>
 * <p>红字提示原因（容量满 / EP 不足 / 黑名单 / CD 中），事件不取消，让玩家保留默认右键交互体验。
 */
@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID)
public final class BeelzebubCaptureHandler {

    /** 玩家级别捕获冷却记录：UUID → 上次成功捕获的服务器 tickCount。 */
    private static final WeakHashMap<UUID, Long> LAST_CAPTURE_TICK = new WeakHashMap<>();

    private BeelzebubCaptureHandler() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player p = event.getEntity();
        if (p.level().isClientSide()) return;
        if (!(p instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        if (!(event.getTarget() instanceof LivingEntity raw) || !raw.isAlive()) return;
        if (raw.is(player)) return;

        // —— 多体节生物处理（v3 hotfix）——
        // 玩家右键打到的可能是 Tempest Serpent / Evil Centipede 等多体节生物的「中间一节」，
        // 这些"体节"实现 ILivingPartEntity（getHead 反查头）。直接吞体节会留下"飘着的尾巴"。
        // 这里先用 ILivingPartEntity.checkForHead 反查到真正的头，把它作为捕获目标；
        // 同时用 raw 自己作为"被点到的那一节"备查（仅日志层面，逻辑上头才是吞噬主体）。
        LivingEntity target = ILivingPartEntity.checkForHead(raw);
        if (!target.isAlive()) return;
        if (target.is(player)) return;

        // —— 玩家是否持有别西卜并已学得 ——
        Optional<ManasSkillInstance> opt = SkillAPI.getSkillsFrom(player)
                .getSkill(FoxAblazeUltimateSkills.BEELZEBUB.get());
        if (opt.isEmpty() || opt.get().getMastery() < 0.0) return;
        ManasSkillInstance instance = opt.get();
        if (!(instance.getSkill() instanceof ISpatialStorage spatial)) return;
        if (!(instance.getSkill() instanceof io.github.manasmods.tensura.ability.skill.Skill tSkill)) return;

        // —— 模式门槛：玩家必须把别西卜切到"虚数空间"模式（mode 1）才能吞噬 ——
        // 不在 mode 1 时静默放行（不提示、不取消事件），让 vanilla 右键正常生效。
        if (!tSkill.isInSlot(player, instance, 1)) return;

        // —— gamerule 门槛：服务器若开启 beelzebubDisableCapture，则虚数空间不再吞噬实体 ——
        // 静默放行，让 vanilla 默认右键交互（驯服、对话等）正常进行。
        if (player.level().getGameRules().getBoolean(FoxAblazeGameRules.BEELZEBUB_DISABLE_CAPTURE)) return;

        BeelzebubConfig.BeelzebubSettings cfg = BeelzebubConfig.get().Beelzebub;

        // —— 黑名单：玩家 / boss / 配置中明确禁止的实体类型（静默拒绝）——
        if (checkCaptureForbidden(target, cfg) != null) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // —— EP 检定（静默）——
        double targetEP = EnergyHelper.getMaxEP(target);
        double selfEP = EnergyHelper.getMaxEP(player);
        if (targetEP > selfEP) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // —— 防刷 CD ——
        long now = player.level().getGameTime();
        Long last = LAST_CAPTURE_TICK.get(player.getUUID());
        if (last != null && now - last < (long) cfg.captureCooldownSeconds * 20) {
            // 静默跳过：避免连点鼠标时刷屏
            return;
        }

        // —— 容量检定（静默）——
        SpatialStorageContainer container = spatial.getSpatialStorage(instance, player.registryAccess());
        if (!hasFreeSlot(container)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // —— 通过 → 序列化、入仓库、清场 ——
        ItemStack template = new ItemStack(FoxAblazeUltimateItems.CAPTURED_ENTITY.get());
        ItemStack stack = CapturedEntityItem.captureToStack(target, targetEP, template);

        if (!spatial.addItemToSpatialStorage(instance, player, stack)) {
            // 与 hasFreeSlot 双保险：极端情况下 addItem 也可能因 maxStackSize 失败
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        // 视觉 + 音效反馈（保留）；文字提示全部静默
        ServerLevel sl = player.serverLevel();
        sl.sendParticles(ParticleTypes.PORTAL,
                target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                30, 0.4, target.getBbHeight() * 0.5, 0.4, 0.05);
        sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.6F);

        // 成功提示"吞噬 %s"（仅此一条允许显示；所有失败路径全部静默）
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        Component name = target.getDisplayName() != null
                ? target.getDisplayName()
                : Component.literal(typeId == null ? "?" : typeId.getPath());
        player.sendSystemMessage(Component.translatable(
                "foxablazeultimate.beelzebub.capture.success",
                Component.empty().append(name).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        target.discard();

        // —— v3 多体节生物体节清理 ——
        // 头被 discard 之后，体节自己的 tick() 在下一帧会自检 parent.isRemoved() 并自我 discard，
        // 但这中间会有 1 帧"飘着的尾巴"。我们这里立刻扫一遍，把所有指向该头 UUID 的体节
        // 当场清掉，不让玩家看到错位的尾巴。范围用足够大的 AABB 兜住整个生物（蛇身可以拉很长）。
        discardAttachedBodies(sl, target);

        LAST_CAPTURE_TICK.put(player.getUUID(), now);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * 把世界中所有指向 {@code head} 的体节也清掉。仅在头被吞噬后调用一次。
     * <p>实现：以头位置为中心，扫一个保守大 AABB（半径 64 格，足以覆盖最长的 Tempest Serpent 蛇身），
     * 找出所有 {@link ILivingPartEntity} 体节中 {@code getHeadId() == head.getUUID()} 的实体，
     * 调 {@code discard()}。已被 discard 的实体 {@code isAlive()} 返回 false，
     * 二次 discard 是 no-op，不会出问题。
     * <p>不依赖 LivingMultipartBody 的 cleanup 路径——它的自检要等下一 tick 才生效，那中间会留 1 帧错位。
     */
    private static void discardAttachedBodies(ServerLevel level, LivingEntity head) {
        UUID headId = head.getUUID();
        AABB sweep = head.getBoundingBox().inflate(64.0);
        for (Entity nearby : level.getEntities(head, sweep)) {
            if (nearby == head) continue;
            if (!(nearby instanceof ILivingPartEntity part)) continue;
            UUID linkedHead = part.getHeadId();
            if (linkedHead == null || !linkedHead.equals(headId)) continue;
            nearby.discard();
        }
    }

    /** @return 不可捕获的提示翻译 key；可捕获返回 {@code null}。 */
    private static String checkCaptureForbidden(LivingEntity target, BeelzebubConfig.BeelzebubSettings cfg) {
        if (target instanceof Player) {
            return cfg.captureAllowPlayers ? null : "foxablazeultimate.beelzebub.capture.fail.is_player";
        }
        if (!cfg.captureAllowBosses && isBoss(target)) {
            return "foxablazeultimate.beelzebub.capture.fail.is_boss";
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (id != null && cfg.captureBlacklist.contains(id.toString())) {
            return "foxablazeultimate.beelzebub.capture.fail.blacklisted";
        }
        return null;
    }

    /**
     * 简易 boss 判定：覆盖 vanilla 三大 boss（凋零 / 末影龙 / Warden 不在此范围内但属于"BOSS"标签）。
     * <p>使用 isAffectedByPotions 这类间接特征过于宽泛；用类型实例判断最稳。
     * Warden 没专用类，但很少有玩家 EP 能高过它，因此 EP 检定会自然把它挡掉。
     */
    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof WitherBoss || entity instanceof EnderDragon;
    }

    private static boolean hasFreeSlot(SpatialStorageContainer container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) return true;
        }
        return false;
    }

}

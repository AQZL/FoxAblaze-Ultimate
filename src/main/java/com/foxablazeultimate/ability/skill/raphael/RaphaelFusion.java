package com.foxablazeultimate.ability.skill.raphael;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.foxablazeultimate.config.RaphaelConfig;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;
import com.foxablazeultimate.registry.FoxAblazeUltimateSounds;
import com.foxablazeultimate.world.FoxAblazeGameRules;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.storage.unique.ITrulyUnique;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 大贤者 + 变质者 → 智慧之王·拉斐尔 融合逻辑。
 * <p>由 {@link RaphaelFusionTickHandler} 周期性调用 {@link #tryFuse(LivingEntity)}。
 */
public final class RaphaelFusion {

    private RaphaelFusion() {}

    /**
     * 进行中的融合仪式表。
     * <p>所有访问均发生在服务器主线程（PlayerTickEvent），使用 ConcurrentHashMap 只是防御性写法；
     * 服务器关闭 / 重启会清空，玩家若中途离线则下次满足前置时由 {@link #tryFuse} 重新启动仪式。
     */
    private static final Map<UUID, FusionCeremony> PENDING = new ConcurrentHashMap<>();

    /** 每个玩家一次性的仪式进度。 */
    private static final class FusionCeremony {
        /** 自仪式开始累计的 tick 数；只在 {@link #tickCeremony} 中递增。 */
        int ticksSinceStart = 0;
        /** 第二步「fusion_message」是否已发出，避免重复发送。 */
        boolean fusionMessageSent = false;
    }

    /**
     * 启动融合仪式。流程：
     * <ol>
     *   <li>校验前置（已拥有 / 双件套精通 / 种族 / TrulyUnique 占用）；任一不满足即静默退出</li>
     *   <li>发送台词「告。个体辅助权限提升……」</li>
     *   <li>记录仪式状态，由 {@link #tickCeremony} 按 {@link RaphaelConfig.RaphaelSettings#fusionDeclarationDelayTicks}
     *       延迟发送 fusion_message，再按 {@link RaphaelConfig.RaphaelSettings#fusionGrantDelayTicks}
     *       延迟正式授予技能 / 遗忘原技能 / 登记 TrulyUnique</li>
     * </ol>
     * 真正的状态变更全部集中在 {@link #doGrant}，台词阶段只发消息不动数据。
     */
    public static void tryFuse(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }

        ManasSkill greatSage = UniqueSkills.GREAT_SAGE.get();
        ManasSkill degenerate = UniqueSkills.DEGENERATE.get();
        ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();

        if (SkillUtils.hasSkill(entity, raphael)) {
            return;
        }
        if (!SkillUtils.isSkillMastered(entity, greatSage)) {
            return;
        }
        if (!SkillUtils.isSkillMastered(entity, degenerate)) {
            return;
        }

        // —— 种族前置：必须为勇者种 / 魔王种之一（含未觉醒卵态与已觉醒形态） ——
        // 与其他前置一致：静默 return，避免每秒刷屏。
        if (!isEligibleRace(entity)) {
            return;
        }

        // —— TrulyUnique 冲突检查：拉斐尔全服唯一 ——
        // 由 gamerule {@code raphaelTrulyUnique} 控制，关闭时直接放行。
        // 冷处理：直接静默 return，不向被阻挡的玩家发任何提示，避免泄露"已被他人领悟"。
        if (entity instanceof ServerPlayer player
                && isTrulyUniqueEnabled(player)
                && isOwnedByOther(player, raphael.getRegistryName())) {
            return;
        }

        // —— 非玩家 LivingEntity（理论上不会经由 RaphaelFusionTickHandler 走到这）——
        // 没有仪式概念，直接走原本的快速融合，保留兼容。
        if (!(entity instanceof ServerPlayer player)) {
            doGrant(entity, raphael, greatSage, degenerate);
            return;
        }

        // —— 仪式幂等：若已经处于仪式中则不重复启动 ——
        if (PENDING.containsKey(player.getUUID())) {
            return;
        }

        // —— 第一步：发送台词并同步播放语音，把玩家挂进仪式表 ——
        PENDING.put(player.getUUID(), new FusionCeremony());
        MutableComponent declaration = Component.translatable("foxablazeultimate.skill.raphael.fusion_declaration");
        player.sendSystemMessage(declaration);

        // 「脑内系统音」：用 playNotifySound 仅对目标玩家客户端播放，无空间衰减，不打扰其他人。
        SoundEvent voice = FoxAblazeUltimateSounds.RAPHAEL_FUSION_DECLARATION.get();
        if (voice != null) {
            player.playNotifySound(voice, SoundSource.VOICE, 1.0F, 1.0F);
        }
    }

    /**
     * 由 {@code RaphaelFusionTickHandler} 每 tick 调用一次，推进仪式状态机。
     * <p>没有 pending 项时 O(1) 立即返回；轻量到可以每 tick 跑。
     */
    public static void tickCeremony(ServerPlayer player) {
        FusionCeremony ceremony = PENDING.get(player.getUUID());
        if (ceremony == null) return;

        RaphaelConfig.RaphaelSettings c = RaphaelConfig.get().Raphael;
        ceremony.ticksSinceStart++;

        // —— 第二步：到点发 fusion_message ——
        int messageAt = Math.max(0, c.fusionDeclarationDelayTicks);
        if (!ceremony.fusionMessageSent && ceremony.ticksSinceStart >= messageAt) {
            MutableComponent msg = Component.translatable("foxablazeultimate.skill.raphael.fusion_message")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
            player.sendSystemMessage(msg);
            ceremony.fusionMessageSent = true;
        }

        // —— 第三步：再延迟一段后正式授予 ——
        int grantAt = messageAt + Math.max(0, c.fusionGrantDelayTicks);
        if (ceremony.fusionMessageSent && ceremony.ticksSinceStart >= grantAt) {
            ManasSkill greatSage = UniqueSkills.GREAT_SAGE.get();
            ManasSkill degenerate = UniqueSkills.DEGENERATE.get();
            ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();

            // 仪式期间另一名玩家可能抢先占用 TrulyUnique，授予前再做一次终检。
            if (isTrulyUniqueEnabled(player)
                    && isOwnedByOther(player, raphael.getRegistryName())) {
                PENDING.remove(player.getUUID());
                return;
            }
            doGrant(player, raphael, greatSage, degenerate);
            PENDING.remove(player.getUUID());
        }
    }

    /**
     * 真正落地：写入 Raphael 实例并遗忘原两件套。
     * <p>「学习」与「精通」在 Tensura 里是两条独立的判定线，本方法只负责前者：
     * <ul>
     *   <li>{@code mastery < 0} —— 学习中，技能未真正持有，无法使用</li>
     *   <li>{@code mastery >= 0} —— <b>已学习</b>，技能可用、可勾选、可切模式</li>
     *   <li>{@code mastery >= 100} —— <b>已精通</b>，额外触发 {@code isMastered()} 分支
     *       （减半魔素 / 减半冷却 / 100% 解析复制率 / 更高被动加成）</li>
     * </ul>
     * 因此 {@link RaphaelConfig.RaphaelSettings#fusionGrantMastery} 默认 0 —— 融合只跨过「学习」门槛，
     * 让玩家拿到一把完整可用但仍需自行打满熟练度的拉斐尔；想直接跳过精通阶段把这个配置改成 100 即可。
     */
    private static void doGrant(LivingEntity entity, ManasSkill raphael, ManasSkill greatSage, ManasSkill degenerate) {
        RaphaelSkill raphaelSkill = (RaphaelSkill) raphael;
        TensuraSkillInstance instance = new TensuraSkillInstance(raphaelSkill);
        int grantMastery = Math.max(0, Math.min(100, RaphaelConfig.get().Raphael.fusionGrantMastery));
        instance.setMastery(grantMastery);

        // 「融合是唯一合法获取路径」：在 unlock 事件中 bypass Tensura
        // AbilityHandler 的 mp 门槛检查，避免生存模式玩家被
        // {@code magicule.getValue() <= +∞} 拦住。RaphaelSkill
        // {@code getDefaultAcquiringMagiculeCost} 返回 +∞ 是为了关闭「魔素自然觉醒」路线，
        // 这里是唯一合法的「单次绕过」入口。
        CompoundTag tag = instance.getOrCreateTag();
        tag.putBoolean("NoMagiculeCost", true);

        instance.markDirty();
        if (!SkillHelper.learnSkill(entity, instance)) {
            return;
        }

        // —— 写入 TrulyUnique 占用记录：与 onLearnSkill 里的登记形成双保险 ——
        if (entity instanceof ServerPlayer player && isTrulyUniqueEnabled(player)) {
            ResourceLocation rid = raphael.getRegistryName();
            if (rid != null) {
                ServerLevel overworld = overworldOf(player);
                if (overworld != null) {
                    ITrulyUnique unique = TensuraStorages.getUniqueStorageFrom(overworld);
                    UUID owner = unique.getOwner(rid);
                    if (owner == null || !owner.equals(player.getUUID())) {
                        unique.addSkill(rid, player.getUUID());
                        unique.markDirty();
                    }
                }
            }
        }

        SkillAPI.getSkillsFrom(entity).forgetSkill(greatSage);
        SkillAPI.getSkillsFrom(entity).forgetSkill(degenerate);
    }

    /** 玩家退出 / 死亡等场景下清理仪式状态，避免对同一 UUID 永久阻塞重启仪式。 */
    public static void clearCeremony(UUID playerId) {
        if (playerId != null) PENDING.remove(playerId);
    }

    /** 判断拉斐尔是否已被其他玩家占用。仅服务器侧调用。 */
    private static boolean isOwnedByOther(ServerPlayer player, ResourceLocation raphaelId) {
        if (raphaelId == null) return false;
        ServerLevel overworld = overworldOf(player);
        if (overworld == null) return false;
        ITrulyUnique unique = TensuraStorages.getUniqueStorageFrom(overworld);
        if (!unique.hasSkill(raphaelId)) return false;
        UUID owner = unique.getOwner(raphaelId);
        return owner != null && !owner.equals(player.getUUID());
    }

    private static ServerLevel overworldOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.overworld();
    }

    /**
     * 读取 gamerule {@code raphaelTrulyUnique}（默认 true）。
     * <p>使用玩家所在维度的 GameRules（任意维度均一致，因为 gamerule 是全服共享的）。
     */
    static boolean isTrulyUniqueEnabled(ServerPlayer player) {
        return player.level().getGameRules().getBoolean(FoxAblazeGameRules.RAPHAEL_TRULY_UNIQUE);
    }

    /**
     * 种族前置：当 {@link RaphaelConfig.RaphaelSettings#fusionRequireSpecialRace} 为 true 时，
     * 玩家必须为以下任一身份才可融合：
     * <ul>
     *   <li><b>勇者种</b>：勇者卵（{@link IExistence#isHeroEgg()}）或真·勇者（{@link IExistence#isTrueHero()}）</li>
     *   <li><b>魔王种</b>：魔王之卵（{@link IExistence#isDemonLordSeed()}）或真·魔王（{@link IExistence#isTrueDemonLord()}）</li>
     * </ul>
     * 配置关闭时直接返回 true，恢复"两件套精通即可"的旧行为。
     */
    private static boolean isEligibleRace(LivingEntity entity) {
        if (!RaphaelConfig.get().Raphael.fusionRequireSpecialRace) return true;
        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        if (existence == null) return false;
        return existence.isHeroEgg() || existence.isTrueHero()
                || existence.isDemonLordSeed() || existence.isTrueDemonLord();
    }
}

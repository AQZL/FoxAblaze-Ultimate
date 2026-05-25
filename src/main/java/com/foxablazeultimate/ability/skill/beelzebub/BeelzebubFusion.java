package com.foxablazeultimate.ability.skill.beelzebub;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.foxablazeultimate.config.BeelzebubConfig;
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
 * 暴食者 + 残虐者 → 暴食之王·别西卜 融合逻辑。
 * <p>由 {@link BeelzebubFusionTickHandler} 周期性调用 {@link #tryFuse(LivingEntity)}。
 * <p>实现整体复刻 {@code RaphaelFusion}：三阶段仪式（宣告 → 完成消息 → 实际授予 + 遗忘原技能 + 写 NoMagiculeCost）。
 */
public final class BeelzebubFusion {

    private BeelzebubFusion() {}

    /** 进行中的融合仪式表。访问均发生在服务器主线程；ConcurrentHashMap 仅作防御性写法。 */
    private static final Map<UUID, FusionCeremony> PENDING = new ConcurrentHashMap<>();

    private static final class FusionCeremony {
        int ticksSinceStart = 0;
        boolean fusionMessageSent = false;
    }

    /**
     * 启动融合仪式。流程：
     * <ol>
     *   <li>校验前置（已拥有 / 双件套精通 / 种族 / TrulyUnique 占用）；任一不满足即静默退出</li>
     *   <li>发送台词「告。暴食与残虐已被认知为同源贪欲……」并对当前玩家播放融合宣告语音</li>
     *   <li>记录仪式状态，由 {@link #tickCeremony} 按配置延迟发送 fusion_message → 再延迟正式授予 / 遗忘原技能 / 登记 TrulyUnique</li>
     * </ol>
     */
    public static void tryFuse(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }

        ManasSkill gluttony = UniqueSkills.GLUTTONY.get();
        ManasSkill merciless = UniqueSkills.MERCILESS.get();
        ManasSkill beelzebub = FoxAblazeUltimateSkills.BEELZEBUB.get();

        if (SkillUtils.hasSkill(entity, beelzebub)) return;
        if (!SkillUtils.isSkillMastered(entity, gluttony)) return;
        if (!SkillUtils.isSkillMastered(entity, merciless)) return;

        if (!isEligibleRace(entity)) return;

        if (entity instanceof ServerPlayer player
                && isTrulyUniqueEnabled(player)
                && isOwnedByOther(player, beelzebub.getRegistryName())) {
            return;
        }

        if (!(entity instanceof ServerPlayer player)) {
            doGrant(entity, beelzebub, gluttony, merciless);
            return;
        }

        if (PENDING.containsKey(player.getUUID())) return;

        PENDING.put(player.getUUID(), new FusionCeremony());
        MutableComponent declaration = Component.translatable("foxablazeultimate.skill.beelzebub.fusion_declaration");
        player.sendSystemMessage(declaration);

        SoundEvent voice = FoxAblazeUltimateSounds.BEELZEBUB_FUSION_DECLARATION.get();
        if (voice != null) {
            player.playNotifySound(voice, SoundSource.VOICE, 1.0F, 1.0F);
        }
    }

    /**
     * 由 {@code BeelzebubFusionTickHandler} 每 tick 调用，推进仪式状态机。
     * 没有 pending 项时 O(1) 立即返回；轻量到可以每 tick 跑。
     */
    public static void tickCeremony(ServerPlayer player) {
        FusionCeremony ceremony = PENDING.get(player.getUUID());
        if (ceremony == null) return;

        BeelzebubConfig.BeelzebubSettings c = BeelzebubConfig.get().Beelzebub;
        ceremony.ticksSinceStart++;

        int messageAt = Math.max(0, c.fusionDeclarationDelayTicks);
        if (!ceremony.fusionMessageSent && ceremony.ticksSinceStart >= messageAt) {
            MutableComponent msg = Component.translatable("foxablazeultimate.skill.beelzebub.fusion_message")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE));
            player.sendSystemMessage(msg);
            ceremony.fusionMessageSent = true;
        }

        int grantAt = messageAt + Math.max(0, c.fusionGrantDelayTicks);
        if (ceremony.fusionMessageSent && ceremony.ticksSinceStart >= grantAt) {
            ManasSkill gluttony = UniqueSkills.GLUTTONY.get();
            ManasSkill merciless = UniqueSkills.MERCILESS.get();
            ManasSkill beelzebub = FoxAblazeUltimateSkills.BEELZEBUB.get();

            // 仪式期间另一名玩家可能抢先占用 TrulyUnique，授予前再做一次终检
            if (isTrulyUniqueEnabled(player)
                    && isOwnedByOther(player, beelzebub.getRegistryName())) {
                PENDING.remove(player.getUUID());
                return;
            }
            doGrant(player, beelzebub, gluttony, merciless);
            PENDING.remove(player.getUUID());
        }
    }

    /**
     * 真正落地：写入 Beelzebub 实例并遗忘原两件套。
     * <p>「学习」与「精通」是 Tensura 的两条独立判定线，{@link BeelzebubConfig.BeelzebubSettings#fusionGrantMastery}
     * 默认 0 让玩家拿到一把可用但仍需自行打满熟练度的别西卜；想直接跳过精通把这个配置改成 100。
     */
    private static void doGrant(LivingEntity entity, ManasSkill beelzebub, ManasSkill gluttony, ManasSkill merciless) {
        BeelzebubSkill bSkill = (BeelzebubSkill) beelzebub;
        TensuraSkillInstance instance = new TensuraSkillInstance(bSkill);
        int grantMastery = Math.max(0, Math.min(100, BeelzebubConfig.get().Beelzebub.fusionGrantMastery));
        instance.setMastery(grantMastery);

        // 「融合是唯一合法获取路径」：bypass Tensura AbilityHandler 的 mp 门槛检查（getDefaultAcquiringMagiculeCost = +∞）。
        CompoundTag tag = instance.getOrCreateTag();
        tag.putBoolean("NoMagiculeCost", true);

        instance.markDirty();
        if (!SkillHelper.learnSkill(entity, instance)) return;

        // 写入 TrulyUnique 占用记录：与 onLearnSkill 形成双保险
        if (entity instanceof ServerPlayer player && isTrulyUniqueEnabled(player)) {
            ResourceLocation rid = beelzebub.getRegistryName();
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

        SkillAPI.getSkillsFrom(entity).forgetSkill(gluttony);
        SkillAPI.getSkillsFrom(entity).forgetSkill(merciless);
    }

    /** 玩家退出 / 死亡等场景下清理仪式状态，避免对同一 UUID 永久阻塞重启仪式。 */
    public static void clearCeremony(UUID playerId) {
        if (playerId != null) PENDING.remove(playerId);
    }

    /** 判断别西卜是否已被其他玩家占用。仅服务器侧调用。 */
    private static boolean isOwnedByOther(ServerPlayer player, ResourceLocation beelzebubId) {
        if (beelzebubId == null) return false;
        ServerLevel overworld = overworldOf(player);
        if (overworld == null) return false;
        ITrulyUnique unique = TensuraStorages.getUniqueStorageFrom(overworld);
        if (!unique.hasSkill(beelzebubId)) return false;
        UUID owner = unique.getOwner(beelzebubId);
        return owner != null && !owner.equals(player.getUUID());
    }

    private static ServerLevel overworldOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.overworld();
    }

    /**
     * 读取 gamerule {@code beelzebubTrulyUnique}（默认 true）。
     * 与 RaphaelFusion 相同：使用玩家所在维度的 GameRules（gamerule 是全服共享的，任意维度均一致）。
     */
    static boolean isTrulyUniqueEnabled(ServerPlayer player) {
        return player.level().getGameRules().getBoolean(FoxAblazeGameRules.BEELZEBUB_TRULY_UNIQUE);
    }

    /**
     * 种族前置：当 {@link BeelzebubConfig.BeelzebubSettings#fusionRequireSpecialRace} 为 true 时，
     * 玩家必须为以下任一身份才可融合：
     * <ul>
     *   <li>勇者种：勇者卵 / 真·勇者</li>
     *   <li>魔王种：魔王之卵 / 真·魔王</li>
     * </ul>
     * 配置关闭时直接返回 true，恢复"两件套精通即可"的旧行为。
     */
    private static boolean isEligibleRace(LivingEntity entity) {
        if (!BeelzebubConfig.get().Beelzebub.fusionRequireSpecialRace) return true;
        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        if (existence == null) return false;
        return existence.isHeroEgg() || existence.isTrueHero()
                || existence.isDemonLordSeed() || existence.isTrueDemonLord();
    }
}

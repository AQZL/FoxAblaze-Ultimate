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

public final class RaphaelFusion {

    private RaphaelFusion() {}

    private static final Map<UUID, FusionCeremony> PENDING = new ConcurrentHashMap<>();

    private static final class FusionCeremony {
        int ticksSinceStart = 0;
        boolean fusionMessageSent = false;
    }

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

        if (!isEligibleRace(entity)) {
            return;
        }

        if (entity instanceof ServerPlayer player
                && isTrulyUniqueEnabled(player)
                && isOwnedByOther(player, raphael.getRegistryName())) {
            return;
        }

        if (!(entity instanceof ServerPlayer player)) {
            doGrant(entity, raphael, greatSage, degenerate);
            return;
        }

        if (PENDING.containsKey(player.getUUID())) {
            return;
        }

        PENDING.put(player.getUUID(), new FusionCeremony());
        MutableComponent declaration = Component.translatable("foxablazeultimate.skill.raphael.fusion_declaration");
        player.sendSystemMessage(declaration);

        SoundEvent voice = FoxAblazeUltimateSounds.RAPHAEL_FUSION_DECLARATION.get();
        if (voice != null) {
            player.playNotifySound(voice, SoundSource.VOICE, 1.0F, 1.0F);
        }
    }

    public static void tickCeremony(ServerPlayer player) {
        FusionCeremony ceremony = PENDING.get(player.getUUID());
        if (ceremony == null) return;

        RaphaelConfig.RaphaelSettings c = RaphaelConfig.get().Raphael;
        ceremony.ticksSinceStart++;

        int messageAt = Math.max(0, c.fusionDeclarationDelayTicks);
        if (!ceremony.fusionMessageSent && ceremony.ticksSinceStart >= messageAt) {
            MutableComponent msg = Component.translatable("foxablazeultimate.skill.raphael.fusion_message")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
            player.sendSystemMessage(msg);
            ceremony.fusionMessageSent = true;
        }

        int grantAt = messageAt + Math.max(0, c.fusionGrantDelayTicks);
        if (ceremony.fusionMessageSent && ceremony.ticksSinceStart >= grantAt) {
            ManasSkill greatSage = UniqueSkills.GREAT_SAGE.get();
            ManasSkill degenerate = UniqueSkills.DEGENERATE.get();
            ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();

            if (isTrulyUniqueEnabled(player)
                    && isOwnedByOther(player, raphael.getRegistryName())) {
                PENDING.remove(player.getUUID());
                return;
            }
            doGrant(player, raphael, greatSage, degenerate);
            PENDING.remove(player.getUUID());
        }
    }

    private static void doGrant(LivingEntity entity, ManasSkill raphael, ManasSkill greatSage, ManasSkill degenerate) {
        RaphaelSkill raphaelSkill = (RaphaelSkill) raphael;
        TensuraSkillInstance instance = new TensuraSkillInstance(raphaelSkill);
        int grantMastery = Math.max(0, Math.min(100, RaphaelConfig.get().Raphael.fusionGrantMastery));
        instance.setMastery(grantMastery);

        CompoundTag tag = instance.getOrCreateTag();
        tag.putBoolean("NoMagiculeCost", true);

        instance.markDirty();
        if (!SkillHelper.learnSkill(entity, instance)) {
            return;
        }

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

    public static void clearCeremony(UUID playerId) {
        if (playerId != null) PENDING.remove(playerId);
    }

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

    static boolean isTrulyUniqueEnabled(ServerPlayer player) {
        return player.level().getGameRules().getBoolean(FoxAblazeGameRules.RAPHAEL_TRULY_UNIQUE);
    }

    private static boolean isEligibleRace(LivingEntity entity) {
        if (!RaphaelConfig.get().Raphael.fusionRequireSpecialRace) return true;
        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        if (existence == null) return false;
        return existence.isHeroEgg() || existence.isTrueHero()
                || existence.isDemonLordSeed() || existence.isTrueDemonLord();
    }
}

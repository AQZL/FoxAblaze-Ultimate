package com.foxablazeultimate.ability.skill.raphael;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.config.RaphaelConfig;
import com.foxablazeultimate.network.OpenRaphaelNamingPayload;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.unique.DegenerateSkill;
import io.github.manasmods.tensura.ability.skill.unique.GreatSageSkill;
import io.github.manasmods.tensura.ability.subclass.IRefining;
import io.github.manasmods.tensura.ability.subclass.IRepeatCrafting;
import io.github.manasmods.tensura.ability.subclass.ISynthesisSeparation;
import io.github.manasmods.tensura.data.TensuraItemTags;
import io.github.manasmods.tensura.data.TensuraSkillTags;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.menu.UncraftingMenu;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import io.github.manasmods.tensura.network.s2c.OpenDegenerateMenuPayload;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.player.ITensuraPlayer;
import io.github.manasmods.tensura.storage.unique.ITrulyUnique;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public class RaphaelSkill extends Skill
        implements IRefining<RaphaelSkill>, IRepeatCrafting<RaphaelSkill>, ISynthesisSeparation {

    private static final ResourceLocation RAPHAEL_MOVEMENT       = id("movement_speed");
    private static final ResourceLocation RAPHAEL_ATTACK         = id("attack_speed");
    private static final ResourceLocation RAPHAEL_CHANT          = id("chant_speed");
    private static final ResourceLocation RAPHAEL_DODGE_M        = id("melee_dodge");
    private static final ResourceLocation RAPHAEL_DODGE_P        = id("projectile_dodge");
    private static final ResourceLocation RAPHAEL_LEARNING       = id("learning");
    private static final ResourceLocation RAPHAEL_MASTERY        = id("mastery");
    private static final ResourceLocation RAPHAEL_PRESENCE_SENSE = id("presence_sense");
    private static final ResourceLocation RAPHAEL_AURA_GAIN      = id("aura_gain");
    private static final ResourceLocation RAPHAEL_MAGICULE_GAIN  = id("magicule_gain");

    private static RaphaelConfig.RaphaelSettings cfg() {
        return RaphaelConfig.get().Raphael;
    }

    public RaphaelSkill() {
        super(SkillType.ULTIMATE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "textures/skill/raphael.png");
    }

    @Override
    public MutableComponent getColoredName() {
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer()) {
            MutableComponent custom = ClientCustomNameAccessor.tryGetLocalPlayerCustomName();
            if (custom != null) return custom.withStyle(ChatFormatting.RED);
        }

        MutableComponent name = super.getName();
        return name == null ? null : name.withStyle(ChatFormatting.RED);
    }

    public static final String NBT_CUSTOM_NAME = "RaphaelCustomName";
    public static final String NBT_NAMED_FLAG = "RaphaelNamed";

    public static String readCustomName(ManasSkillInstance instance) {
        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.contains(NBT_CUSTOM_NAME)) return null;
        String value = tag.getString(NBT_CUSTOM_NAME);
        return (value == null || value.isBlank()) ? null : value;
    }

    public static void applyCustomName(ManasSkillInstance instance, String rawName) {
        applyCustomName(instance, rawName, null);
    }

    public static void applyCustomName(ManasSkillInstance instance, String rawName, ServerPlayer player) {
        CompoundTag tag = instance.getOrCreateTag();
        boolean cleared;
        if (rawName == null || rawName.isBlank()) {
            tag.remove(NBT_CUSTOM_NAME);
            cleared = true;
        } else {
            tag.putString(NBT_CUSTOM_NAME, rawName);
            cleared = false;
        }
        tag.putBoolean(NBT_NAMED_FLAG, true);
        instance.markDirty();

        if (player != null) {
            MutableComponent feedback = cleared
                    ? Component.translatable("foxablazeultimate.skill.raphael.naming.cleared")
                            .withStyle(ChatFormatting.GRAY)
                    : Component.translatable("foxablazeultimate.skill.raphael.naming.applied",
                            Component.literal(rawName).withStyle(ChatFormatting.RED))
                            .withStyle(ChatFormatting.GRAY);
            player.sendSystemMessage(feedback);
        }
    }

    public static void sendNamingPrompt(ServerPlayer player, ManasSkillInstance instance) {
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getBoolean(NBT_NAMED_FLAG)) return;

        ManasSkill skill = instance.getSkill();
        ResourceLocation skillId = skill.getRegistryName();
        if (skillId == null) return;

        String defaultLangKey = String.format("%s.skill.%s",
                skillId.getNamespace(), skillId.getPath().replace('/', '.'));

        player.nextContainerCounter();
        NetworkManager.sendToPlayer(player,
                new OpenRaphaelNamingPayload(player.containerCounter, defaultLangKey, skillId));
    }

    private static final class ClientCustomNameAccessor {
        static MutableComponent tryGetLocalPlayerCustomName() {
            try {
                net.minecraft.client.player.LocalPlayer local =
                        net.minecraft.client.Minecraft.getInstance().player;
                if (local == null) return null;
                ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();
                if (raphael == null) return null;
                java.util.Optional<ManasSkillInstance> opt = SkillAPI.getSkillsFrom(local).getSkill(raphael);
                if (opt.isEmpty()) return null;
                String custom = readCustomName(opt.get());
                if (custom == null) return null;
                return Component.literal(custom);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        boolean mastered = instance.isMastered(entity);
        RaphaelConfig.RaphaelSettings c = cfg();
        return switch (mode) {
            case 1 -> mastered ? c.magiculeCostAnalysisMastered : c.magiculeCostAnalysis;
            case 4 -> mastered ? c.magiculeCostSynthesiseMastered : c.magiculeCostSynthesise;
            case 5 -> mastered ? c.magiculeCostSeparateMastered : c.magiculeCostSeparate;
            default -> 0.0;
        };
    }

    @Override
    public boolean checkAcquiringRequirement(net.minecraft.world.entity.player.Player entity, double newEP) {
        return false;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0;
    }

    private static final int MODE_COUNT = 6;

    private static int sanitizeMode(int mode) {
        return Math.floorMod(mode, MODE_COUNT);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_COUNT;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int safe = sanitizeMode(mode);
        if (reverse) {
            return safe == 0 ? MODE_COUNT - 1 : safe - 1;
        }
        return safe == MODE_COUNT - 1 ? 0 : safe + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (sanitizeMode(mode)) {
            case 0 -> "raphael.analytical_appraisal";
            case 1 -> "raphael.analysis";
            case 2 -> "raphael.refine";
            case 3 -> "raphael.crafting";
            case 4 -> "raphael.synthesise";
            case 5 -> "raphael.separate";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        if (instance.isToggled()) return true;
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return false;
        CompoundTag tag = instance.getTag();
        return tag != null && (tag.getBoolean("Brewing") || tag.getBoolean("Repeating"));
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (instance.isToggled()) {
            gainMastery(instance, entity);
        }
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            if (this.onRepeatCraftingTick(instance, player)) {
                gainMastery(instance, entity);
            }
            if (this.onRefiningTick(instance, player)) {
                gainMastery(instance, entity);
            }
        }
    }

    private enum AnalysisOutcome {
        SUCCESS,
        FAIL_COOLDOWN,
        FAIL_NO_COOLDOWN
    }

    private AnalysisOutcome doAnalysis(ManasSkillInstance instance, LivingEntity entity) {
        RaphaelConfig.RaphaelSettings c = cfg();

        TensuraProjectile projectile = ObjectSelectionHelper.getTargetingEntity(
                TensuraProjectile.class, entity, c.analysisProjectileRange, 0.5, true, true, false);
        if (projectile != null && projectile.isAlive()) {
            ManasSkillInstance projSkill = projectile.getSkill();
            if (projSkill != null && projSkill.getMastery() >= 0.0
                    && projSkill.is(TensuraSkillTags.COPIABLE_MAGIC)) {
                entity.swing(InteractionHand.MAIN_HAND, true);
                return tryCopySkill(instance, entity, projSkill, projectile.getOwner());
            }
            sendFail(entity, "tensura.ability.activation_failed");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, c.analysisEntityRange, false);
        if (target == null || !target.isAlive()) {
            sendFail(entity, "tensura.targeting.not_targeted");
            return AnalysisOutcome.FAIL_NO_COOLDOWN;
        }
        if (target instanceof net.minecraft.world.entity.player.Player p
                && p.getAbilities().invulnerable) {
            sendFail(entity, "tensura.targeting.not_allowed");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_NO_COOLDOWN;
        }

        entity.swing(InteractionHand.MAIN_HAND, true);

        double chance = instance.isMastered(entity) ? c.analysisCopyChanceMastered : c.analysisCopyChance;
        if (entity.getRandom().nextInt(100) > chance) {
            sendFail(entity, "tensura.ability.activation_failed");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        List<ManasSkillInstance> copiable = SkillAPI.getSkillsFrom(target).getLearnedSkills().stream()
                .filter(s -> canCopySkill(s, c))
                .toList();
        if (copiable.isEmpty()) {
            sendFail(entity, "tensura.ability.activation_failed.plunder.empty");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        ManasSkillInstance picked = copiable.get(target.getRandom().nextInt(copiable.size()));
        return tryCopySkill(instance, entity, picked, target);
    }

    private static AnalysisOutcome tryCopySkill(ManasSkillInstance instance,
                                                LivingEntity entity,
                                                ManasSkillInstance targetSkill,
                                                net.minecraft.world.entity.Entity sourceEntity) {
        Changeable<ManasSkill> changeable = Changeable.of(targetSkill.getSkill());
        boolean blocked = TensuraSkillEvents.SKILL_PLUNDER.invoker()
                .plunder(sourceEntity, entity, false, changeable).isFalse();
        if (blocked) {
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }
        if (!SkillHelper.learnSkill(entity, changeable.get(), instance.getRemoveTime())) {
            ManasSkill failedSkill = changeable.get();
            entity.sendSystemMessage(Component.translatable(
                            "tensura.ability.activation_failed.plunder", failedSkill.getChatDisplayName(true))
                    .withStyle(ChatFormatting.RED));
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        SoundEvent castSound = TensuraSoundEvents.GENERIC_CAST.get();
        if (castSound != null) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    castSound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return AnalysisOutcome.SUCCESS;
    }

    private static void sendFail(LivingEntity entity, String translationKey) {
        entity.sendSystemMessage(Component.translatable(translationKey).withStyle(ChatFormatting.RED));
    }

    private static void playCastFail(LivingEntity entity) {
        SoundEvent fail = TensuraSoundEvents.GENERIC_CAST_FAIL.get();
        if (fail != null) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    fail, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static boolean canCopySkill(ManasSkillInstance instance, RaphaelConfig.RaphaelSettings c) {
        if (instance.isTemporarySkill()) return false;
        if (instance.getMastery() < 0.0) return false;
        if (instance.is(TensuraSkillTags.NO_PLUNDERING)) return false;

        ResourceLocation rl = instance.getSkill().getRegistryName();
        if (rl == null) return false;

        if (!c.analysisAllowedNamespaces.contains(rl.getNamespace())) return false;
        if (c.analysisBlacklist.contains(rl.toString())) return false;

        if (!(instance.getSkill() instanceof Skill skill)) return false;
        String typeName = skill.getType().getNamespace();
        boolean typeAllowed = false;
        for (String allowed : c.analysisAllowedTypes) {
            if (allowed != null && allowed.equalsIgnoreCase(typeName)) {
                typeAllowed = true;
                break;
            }
        }
        if (!typeAllowed) return false;

        if ("unique".equalsIgnoreCase(typeName)
                && !c.analysisUniqueWhitelist.isEmpty()
                && !c.analysisUniqueWhitelist.contains(rl.toString())) {
            return false;
        }
        return true;
    }

    private static void applyCooldownAndMastery(ManasSkillInstance instance,
                                                LivingEntity entity,
                                                int mode,
                                                boolean activated) {
        boolean mastered = instance.isMastered(entity);
        RaphaelConfig.RaphaelSettings c = cfg();
        int cd;
        if (activated && mode == 5) {
            cd = mastered ? c.activationCooldownSeparateSuccessMastered : c.activationCooldownSeparateSuccess;
        } else {
            cd = activated
                    ? (mastered ? c.activationCooldownSuccessMastered : c.activationCooldownSuccess)
                    : (mastered ? c.activationCooldownFailMastered    : c.activationCooldownFail);
        }
        instance.setCoolDown(cd, mode);
        if (activated) {
            instance.addMasteryPoint(entity);
        }
    }

    private static void gainMastery(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }
        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        GreatSageSkill sage = (GreatSageSkill) UniqueSkills.GREAT_SAGE.get();
        DegenerateSkill degen = (DegenerateSkill) UniqueSkills.DEGENERATE.get();

        switch (mode) {
            case 0 -> RaphaelDelegateHelper.delegateOnPressed(
                    instance, RaphaelDelegateHelper.SAGE_TAG, sage, entity, keyNumber, mode);
            case 1 -> {
                if (instance.onCoolDown(mode)) return;
                if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
                AnalysisOutcome outcome = doAnalysis(instance, entity);
                switch (outcome) {
                    case SUCCESS         -> applyCooldownAndMastery(instance, entity, mode, true);
                    case FAIL_COOLDOWN   -> applyCooldownAndMastery(instance, entity, mode, false);
                    case FAIL_NO_COOLDOWN -> {  }
                }
            }
            case 2 -> openRefineOrRepeatCrafting(instance, entity);
            case 3 -> openUncraftingOrSynthesis(instance, entity);
            case 4 -> onSynthesisePressed(instance, entity, keyNumber, mode, degen);
            case 5 -> {
                if (instance.onCoolDown(mode)) return;
                if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
                boolean activated = RaphaelDelegateHelper.delegateOnPressed(
                        instance, RaphaelDelegateHelper.DEGENERATE_TAG, degen, entity, keyNumber, mode - 3);
                applyCooldownAndMastery(instance, entity, mode, activated);
            }
            default -> {}
        }
    }


    public static final String NBT_SYNTHESISE_SUBMODE = "RaphaelSynthSub";

    public static final int SYNTH_SUBMODE_DEFAULT = 0;
    public static final int SYNTH_SUBMODE_EVOLVE_URIEL = 1;

    private void onSynthesisePressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber,
                                     int mode, DegenerateSkill degen) {
        if (entity instanceof Player player && player.isShiftKeyDown()) {
            cycleSynthesiseSubMode(instance, player);
            return;
        }

        int subMode = readSynthesiseSubMode(instance);
        if (subMode == SYNTH_SUBMODE_EVOLVE_URIEL && canEvolveToUriel(instance, entity)) {
            if (instance.onCoolDown(mode)) return;
            if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
            boolean activated = doEvolveToUriel(entity, instance);
            applyCooldownAndMastery(instance, entity, mode, activated);
            return;
        }

        if (instance.onCoolDown(mode)) return;
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
        boolean activated = RaphaelDelegateHelper.delegateOnPressed(
                instance, RaphaelDelegateHelper.DEGENERATE_TAG, degen, entity, keyNumber, mode - 3);
        applyCooldownAndMastery(instance, entity, mode, activated);
    }

    private static int readSynthesiseSubMode(ManasSkillInstance instance) {
        CompoundTag tag = instance.getOrCreateTag();
        int v = tag.getInt(NBT_SYNTHESISE_SUBMODE);
        return (v == SYNTH_SUBMODE_EVOLVE_URIEL) ? SYNTH_SUBMODE_EVOLVE_URIEL : SYNTH_SUBMODE_DEFAULT;
    }

    private void cycleSynthesiseSubMode(ManasSkillInstance instance, Player player) {
        int current = readSynthesiseSubMode(instance);
        int next = current;
        boolean canUriel = canEvolveToUriel(instance, player);
        if (current == SYNTH_SUBMODE_DEFAULT && canUriel) {
            next = SYNTH_SUBMODE_EVOLVE_URIEL;
        } else {
            next = SYNTH_SUBMODE_DEFAULT;
        }
        if (next != current) {
            instance.getOrCreateTag().putInt(NBT_SYNTHESISE_SUBMODE, next);
            instance.markDirty();
        }
        announceSynthesiseSubMode(player, next);
    }

    private void announceSynthesiseSubMode(Player player, int subMode) {
        Component label = switch (subMode) {
            case SYNTH_SUBMODE_EVOLVE_URIEL -> Component.translatable(
                    "foxablazeultimate.skill.raphael.synthesise.submode.evolve",
                    FoxAblazeUltimateSkills.URIEL.get().getColoredName());
            default -> Component.translatable("foxablazeultimate.skill.mode.raphael.synthesise");
        };
        player.displayClientMessage(
                Component.translatable("foxablazeultimate.skill.raphael.synthesise.submode.switched", label)
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    private static boolean canEvolveToUriel(ManasSkillInstance instance, LivingEntity entity) {
        ManasSkill uriel = FoxAblazeUltimateSkills.URIEL.get();
        if (uriel == null) return false;
        if (SkillUtils.hasSkill(entity, uriel)) return false;
        ManasSkill prison = UniqueSkills.INFINITY_PRISON.get();
        if (prison == null) return false;
        return SkillUtils.isSkillMastered(entity, prison);
    }

    private boolean doEvolveToUriel(LivingEntity entity, ManasSkillInstance instance) {
        ManasSkill uriel = FoxAblazeUltimateSkills.URIEL.get();
        ManasSkill prison = UniqueSkills.INFINITY_PRISON.get();
        if (uriel == null || prison == null) return false;
        if (entity.level().isClientSide()) return false;

        TensuraSkillInstance newInstance = new TensuraSkillInstance(uriel);
        newInstance.setMastery(0);
        newInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        newInstance.markDirty();
        if (!SkillHelper.learnSkill(entity, newInstance)) return false;

        SkillAPI.getSkillsFrom(entity).forgetSkill(prison);

        instance.getOrCreateTag().putInt(NBT_SYNTHESISE_SUBMODE, SYNTH_SUBMODE_DEFAULT);
        instance.markDirty();
        return true;
    }

    private void openRefineOrRepeatCrafting(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.isShiftKeyDown()) {
            this.openRefiningMenu(entity, instance);
        } else {
            this.openRepeatCraftingMenu(entity, instance);
        }
    }

    private void openUncraftingOrSynthesis(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        player.closeContainer();
        if (player.isShiftKeyDown()) {
            this.openSynthesisSeparationMenu(player, instance);
        } else {
            player.nextContainerCounter();
            NetworkManager.sendToPlayer(player, new OpenDegenerateMenuPayload(
                    OpenDegenerateMenuPayload.MenuType.CRAFTING,
                    player.containerCounter, player.getId(), this.getRegistryName()));
            player.containerMenu = new UncraftingMenu(player.containerCounter, player.getInventory(), this);
            player.initMenu(player.containerMenu);
        }
        player.playNotifySound(TensuraSoundEvents.SPATIAL_STORAGE.get(), SoundSource.PLAYERS, 0.75F, 1.0F);
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() < 0.0 || instance.isTemporarySkill()) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            registerTrulyUnique(player);
            unlockAllSchematics(player);
            sendNamingPrompt(player, instance);
        }
    }

    @Override
    public void onSkillMastered(ManasSkillInstance instance, LivingEntity entity) {
        super.onSkillMastered(instance, entity);
        if (instance.isTemporarySkill()) return;
        if (instance.isToggled()) {
            removePassiveAttributes(entity);
            applyPassiveAttributes(entity, true);
        }
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        super.onToggleOn(instance, entity);
        if (instance.isTemporarySkill()) return;
        applyPassiveAttributes(entity, instance.isMastered(entity));
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        super.onToggleOff(instance, entity);
        removePassiveAttributes(entity);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        removePassiveAttributes(entity);
        if (entity instanceof ServerPlayer player) {
            unregisterTrulyUnique(player);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer player, boolean conqueredEnd) {
        super.onRespawn(instance, player, conqueredEnd);
        if (instance.isToggled() && instance.getMastery() >= 0.0 && !instance.isTemporarySkill()) {
            applyPassiveAttributes(player, instance.isMastered(player));
        }
    }

    @Override
    public boolean onDeath(ManasSkillInstance instance, LivingEntity owner, DamageSource source) {
        return true;
    }


    @Override
    public @NotNull SpatialStorageContainer getSpatialStorage(ManasSkillInstance instance, HolderLookup.Provider provide) {
        SpatialStorageContainer container = new SpatialStorageContainer(20, 128);
        container.fromTag(instance.getOrCreateTag().getList("SpatialStorage", 10), provide);
        return container;
    }

    @Override
    public int getSpatialStorageIdOffset() {
        return 11;
    }

    @Override
    public boolean isAutoRefiningAllowed() {
        return true;
    }

    @Override
    public boolean hasAutoCraftingTab() {
        return true;
    }

    @Override
    public void openSpatialStoragePage(ServerPlayer player, LivingEntity owner, ManasSkillInstance instance, int page) {
        this.openRepeatCraftingMenu(player, owner, instance);
    }


    @Override
    public int getMaximumBonusLevel() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getMaximumBonusLevel();
    }

    @Override
    public List<String> getSeparateBlacklistEnchantments() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getSeparateBlacklistEnchantments();
    }

    @Override
    public List<String> getSynthesisBlacklistEnchantments() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getSynthesisBlacklistEnchantments();
    }

    @Override
    public List<String> getBonusLevelBlackListEnchantments() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getBonusLevelBlackListEnchantments();
    }


    private void registerTrulyUnique(ServerPlayer player) {
        if (!RaphaelFusion.isTrulyUniqueEnabled(player)) return;
        ServerLevel overworld = overworldOf(player);
        if (overworld == null) return;
        ITrulyUnique unique = TensuraStorages.getUniqueStorageFrom(overworld);
        ResourceLocation id = this.getRegistryName();
        if (id == null) return;
        UUID currentOwner = unique.getOwner(id);
        if (currentOwner == null || !currentOwner.equals(player.getUUID())) {
            unique.addSkill(id, player.getUUID());
            unique.markDirty();
        }
    }

    private void unregisterTrulyUnique(ServerPlayer player) {
        ServerLevel overworld = overworldOf(player);
        if (overworld == null) return;
        ITrulyUnique unique = TensuraStorages.getUniqueStorageFrom(overworld);
        ResourceLocation id = this.getRegistryName();
        if (id == null) return;
        UUID owner = unique.getOwner(id);
        if (owner != null && owner.equals(player.getUUID())) {
            unique.removeSkill(id);
            unique.markDirty();
        }
    }

    private static ServerLevel overworldOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.overworld();
    }


    private static void unlockAllSchematics(ServerPlayer player) {
        ITensuraPlayer data = TensuraStorages.getPlayerDataFrom(player);
        if (data == null) return;
        boolean anyUnlocked = false;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultInstance().is(TensuraItemTags.SCHEMATICS) && !data.hasSchematic(item)) {
                data.unlockSchematic(item);
                anyUnlocked = true;
            }
        }
        if (anyUnlocked) {
            data.markDirty();
        }
    }

    private static void applyPassiveAttributes(LivingEntity entity, boolean mastered) {
        RaphaelConfig.RaphaelSettings c = cfg();
        double learning = mastered ? c.learningBonusMastered      : c.learningBonus;
        double mastery  = mastered ? c.masteryBonusMastered       : c.masteryBonus;
        double sense    = mastered ? c.presenceSenseBonusMastered : c.presenceSenseBonus;
        double epGain   = mastered ? c.epGainBonusMastered        : c.epGainBonus;

        addModifier(entity, Attributes.MOVEMENT_SPEED,                       RAPHAEL_MOVEMENT,       c.movementBonus,     Operation.ADD_MULTIPLIED_BASE);
        addModifier(entity, Attributes.ATTACK_SPEED,                         RAPHAEL_ATTACK,         c.attackSpeedBonus,  Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.CHANT_SPEED,                   RAPHAEL_CHANT,          c.chantSpeedBonus,   Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.AUTO_MELEE_DODGE_CHANCE,       RAPHAEL_DODGE_M,        c.dodgeChanceBonus,  Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE,  RAPHAEL_DODGE_P,        c.dodgeChanceBonus,  Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.ABILITY_LEARNING_GAIN,         RAPHAEL_LEARNING,       learning,            Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.ABILITY_MASTERY_GAIN,          RAPHAEL_MASTERY,        mastery,             Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.PRESENCE_SENSE,                RAPHAEL_PRESENCE_SENSE, sense,               Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.AURA_GAIN,                     RAPHAEL_AURA_GAIN,      epGain,              Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.MAGICULE_GAIN,                 RAPHAEL_MAGICULE_GAIN,  epGain,              Operation.ADD_VALUE);
    }

    private static void removePassiveAttributes(LivingEntity entity) {
        removeModifier(entity, Attributes.MOVEMENT_SPEED,                       RAPHAEL_MOVEMENT);
        removeModifier(entity, Attributes.ATTACK_SPEED,                         RAPHAEL_ATTACK);
        removeModifier(entity, TensuraAttributes.CHANT_SPEED,                   RAPHAEL_CHANT);
        removeModifier(entity, TensuraAttributes.AUTO_MELEE_DODGE_CHANCE,       RAPHAEL_DODGE_M);
        removeModifier(entity, TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE,  RAPHAEL_DODGE_P);
        removeModifier(entity, TensuraAttributes.ABILITY_LEARNING_GAIN,         RAPHAEL_LEARNING);
        removeModifier(entity, TensuraAttributes.ABILITY_MASTERY_GAIN,          RAPHAEL_MASTERY);
        removeModifier(entity, TensuraAttributes.PRESENCE_SENSE,                RAPHAEL_PRESENCE_SENSE);
        removeModifier(entity, TensuraAttributes.AURA_GAIN,                     RAPHAEL_AURA_GAIN);
        removeModifier(entity, TensuraAttributes.MAGICULE_GAIN,                 RAPHAEL_MAGICULE_GAIN);
    }

    private static void addModifier(LivingEntity entity,
                                    Holder<Attribute> attr,
                                    ResourceLocation id,
                                    double amount,
                                    Operation op) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static void removeModifier(LivingEntity entity,
                                       Holder<Attribute> attr,
                                       ResourceLocation id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "raphael/" + path);
    }
}

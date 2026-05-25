package com.foxablazeultimate.ability.skill.uriel;

import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.config.UrielConfig;
import com.foxablazeultimate.registry.FoxAblazeUltimateSounds;
import com.mojang.datafixers.util.Pair;

import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.subclass.ISpatialStorage;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.data.TensuraSkillTags;
import io.github.manasmods.tensura.data.TensuraTags;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.entity.magic.beam.BeamProjectile;
import io.github.manasmods.tensura.entity.magic.beam.SpatialRayProjectile;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.entity.MiscEntityTypes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.AttributeHelper;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class UrielSkill extends Skill implements ISpatialStorage {

    private static UrielConfig.UrielSettings cfg() {
        return UrielConfig.get().Uriel;
    }

    private static final ResourceLocation URIEL_MULTILAYER       = makeId("uriel_multilayer");
    private static final ResourceLocation URIEL_ALLY_MULTILAYER  = makeId("uriel_ally_multilayer");
    private static final ResourceLocation URIEL_LAW              = makeId("uriel_law_degradation");
    private static final ResourceLocation URIEL_SPACE_BOOST      = makeId("uriel_space_boost");

    private static ResourceLocation makeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "uriel/" + path);
    }

    private static final int MODE_COUNT = 7;
    public static final int MODE_IMPRISON = 0;
    public static final int MODE_SPATIAL  = 1;
    public static final int MODE_CLEANSE  = 2;
    public static final int MODE_TAKEOVER = 3;
    public static final int MODE_BARRIER  = 4;
    public static final int MODE_RAY      = 5;
    public static final int MODE_STORM    = 6;

    public UrielSkill() {
        super(SkillType.ULTIMATE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "textures/skill/uriel.png");
    }

    @Override
    public MutableComponent getColoredName() {
        MutableComponent name = super.getName();
        return name == null ? null : name.withStyle(ChatFormatting.GOLD);
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean checkAcquiringRequirement(Player entity, double newEP) {
        return false;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_COUNT;
    }

    private static int sanitizeMode(int mode) {
        return Math.floorMod(mode, MODE_COUNT);
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int safe = sanitizeMode(mode);

        boolean takeoverUnlocked = instance.isMastered(entity);
        if (reverse) {
            int prev = safe == 0 ? MODE_COUNT - 1 : safe - 1;
            if (prev == MODE_TAKEOVER && !takeoverUnlocked) prev = MODE_TAKEOVER - 1;
            return prev;
        }
        int next = safe == MODE_COUNT - 1 ? 0 : safe + 1;
        if (next == MODE_TAKEOVER && !takeoverUnlocked) next = MODE_TAKEOVER + 1;
        return next;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (sanitizeMode(mode)) {
            case MODE_IMPRISON -> "uriel.imprison";
            case MODE_SPATIAL  -> "uriel.spatial";
            case MODE_CLEANSE  -> "uriel.cleanse";
            case MODE_TAKEOVER -> "uriel.takeover";
            case MODE_BARRIER  -> "uriel.barrier";
            case MODE_RAY      -> "uriel.ray";
            case MODE_STORM    -> "uriel.storm";
            default            -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        UrielConfig.UrielSettings c = cfg();
        return switch (sanitizeMode(mode)) {
            case MODE_IMPRISON -> c.magiculeCostImprison;
            case MODE_BARRIER  -> c.barrierMagiculeCost;
            case MODE_RAY      -> c.rayMagiculeCost;
            case MODE_STORM    -> c.stormMagiculeCost;
            default            -> 0.0;
        };
    }

    private static int masteredCd(int baseCd, ManasSkillInstance instance, LivingEntity entity) {
        if (!instance.isMastered(entity)) return baseCd;
        return Math.max(1, (int) Math.round(baseCd * cfg().masteredCooldownMultiplier));
    }

    private static double masteredPower(double base, ManasSkillInstance instance, LivingEntity entity) {
        if (!instance.isMastered(entity)) return base;
        return base * cfg().masteredPowerMultiplier;
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() < 0.0 || instance.isTemporarySkill()) return;

        if (entity instanceof ServerPlayer player) {
            MutableComponent declaration = Component.translatable(
                    "foxablazeultimate.skill.uriel.skill_declaration");
            player.sendSystemMessage(declaration);

            SoundEvent voice = FoxAblazeUltimateSounds.URIEL_SKILL_DECLARATION.get();
            if (voice != null) {
                player.playNotifySound(voice, SoundSource.VOICE, 1.0F, 1.0F);
            }
        }

        UrielConfig.UrielSettings c = cfg();
        AttributeInstance water = entity.getAttribute(TensuraAttributes.WATER_CAPACITY);
        if (water != null) water.setBaseValue(water.getValue() + c.waterCapacity);
        AttributeInstance lava = entity.getAttribute(TensuraAttributes.LAVA_CAPACITY);
        if (lava != null) lava.setBaseValue(lava.getValue() + c.lavaCapacity);

        AttributeHelper.addPermanentAttributeIfHigher(entity, TensuraAttributes.SPACE_BOOST,
                URIEL_SPACE_BOOST, 0.5, Operation.ADD_VALUE);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        if (instance.getMastery() < 0.0) return;

        UrielConfig.UrielSettings c = cfg();
        AttributeInstance water = entity.getAttribute(TensuraAttributes.WATER_CAPACITY);
        if (water != null) water.setBaseValue(Math.max(0.0, water.getValue() - c.waterCapacity));
        AttributeInstance lava = entity.getAttribute(TensuraAttributes.LAVA_CAPACITY);
        if (lava != null) lava.setBaseValue(Math.max(0.0, lava.getValue() - c.lavaCapacity));

        AttributeHelper.removeAttribute(entity, TensuraAttributes.SPACE_BOOST, URIEL_SPACE_BOOST);

        AttributeInstance multi = entity.getAttribute(TensuraAttributes.MULTILAYER_BARRIER);
        if (multi != null) {
            if (multi.getModifier(URIEL_MULTILAYER) != null) multi.removeModifier(URIEL_MULTILAYER);
            if (multi.getModifier(URIEL_ALLY_MULTILAYER) != null) multi.removeModifier(URIEL_ALLY_MULTILAYER);
        }
        AttributeInstance law = entity.getAttribute(TensuraAttributes.LAW_DEGRADATION);
        if (law != null && law.getModifier(URIEL_LAW) != null) law.removeModifier(URIEL_LAW);
    }

    @Override
    public boolean onDeath(ManasSkillInstance instance, LivingEntity owner, DamageSource source) {
        return true;
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner,
                                 DamageSource damageSource, Changeable<Float> amount) {
        if (!this.isInSlot(owner, instance)) return true;
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        if (damageSource.is(TensuraTags.DamageTypes.BYPASS_DISTORTION_FIELD)) return true;
        if (((io.github.manasmods.tensura.damage.TensuraDamageSource) damageSource).tensura$getBarrierBypassLevel() >= 2.0F) return true;

        UrielConfig.UrielSettings c = cfg();
        Entity attackerEntity = damageSource.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) return true;

        IExistence ownerEx = TensuraStorages.getExistenceFrom(owner);
        IExistence atkEx = TensuraStorages.getExistenceFrom(attacker);

        double epGate = c.guardEPGate;
        if (instance.isMastered(owner)) epGate *= cfg().masteredPowerMultiplier;
        if (atkEx.getEP() >= ownerEx.getEP() * epGate) return true;

        float damageAmount = amount.get();
        double cost = (int) (damageAmount * c.guardMagiculeCost);

        if (instance.isMastered(owner)) cost *= c.masteredCooldownMultiplier;
        double lacked = EnergyHelper.isOutOfMagiculeConsuming(owner, cost);
        if (lacked > 0.0) {
            damageAmount = (float) (damageAmount - lacked / Math.max(0.0001, c.guardMagiculeCost));
        }

        if (damageAmount < amount.get()) {
            amount.set(amount.get() - damageAmount);
            return true;
        }
        return false;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        switch (sanitizeMode(mode)) {
            case MODE_IMPRISON -> onImprison(instance, entity, mode);
            case MODE_SPATIAL  -> this.openSpatialStorage(entity, instance);
            case MODE_CLEANSE  -> onCleanse(instance, entity, mode);
            case MODE_TAKEOVER -> onTakeover(instance, entity, mode);
            case MODE_BARRIER  -> onBarrier(instance, entity, mode);
            case MODE_RAY      -> {  }
            case MODE_STORM    -> onStorm(instance, entity, mode);
            default -> {}
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (sanitizeMode(mode) != MODE_RAY) return false;
        if (instance.onCoolDown(mode)) return false;
        if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return false;
        }
        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0 && heldTicks > 0) {
            instance.addMasteryPoint(entity);
        }

        UrielConfig.UrielSettings c = cfg();
        Pair<Double, Double> cost = Pair.of(this.getAuraCost(entity, instance, mode),
                this.getMagiculeCost(entity, instance, mode));
        float dmg = (float) masteredPower(c.rayDamage, instance, entity);
        BeamProjectile.spawnLastingBeam(
                (EntityType) MiscEntityTypes.SPATIAL_RAY.get(),
                dmg, 0.5F, c.rayRange, entity, instance, mode, cost, cost, heldTicks);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                TensuraSoundEvents.CAST_SPACE.get(), SoundSource.PLAYERS, 0.8F, 0.5F);
        if ((float) heldTicks > c.rayDuration) {
            instance.setCoolDown(masteredCd(c.rayCooldown, instance, entity), mode);
            return false;
        }
        return true;
    }

    private void onImprison(ManasSkillInstance instance, LivingEntity entity, int mode) {
        if (instance.onCoolDown(mode)) return;

        UrielConfig.UrielSettings c = cfg();
        LivingEntity target = (LivingEntity) ObjectSelectionHelper.getTargetingEntity(
                LivingEntity.class, entity, c.imprisonRange, 0.75, false, true, false);
        if (target == null) {
            entity.sendSystemMessage(Component.translatable("tensura.targeting.not_targeted")
                    .withStyle(ChatFormatting.RED));
            instance.setCoolDown(masteredCd(c.imprisonCooldown / 2, instance, entity), mode);
            return;
        }

        if (target.hasEffect(TensuraMobEffects.getReference(TensuraMobEffects.INFINITE_IMPRISONMENT))) {
            target.removeEffect(TensuraMobEffects.getReference(TensuraMobEffects.INFINITE_IMPRISONMENT));
            entity.swing(InteractionHand.MAIN_HAND, true);
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    TensuraSoundEvents.DEBUFF_DEACTIVATE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            TensuraParticleHelper.addServerParticlesAroundSelf(target, ParticleTypes.FLASH, 1.0);
            return;
        }

        if (target instanceof Player p && p.getAbilities().invulnerable) return;

        double epGate = c.imprisonEPGate;
        if (instance.isMastered(entity)) epGate *= cfg().masteredPowerMultiplier;
        if (TensuraStorages.getExistenceFrom(target).getEP() >
                TensuraStorages.getExistenceFrom(entity).getEP() * epGate) {
            entity.sendSystemMessage(Component.translatable("tensura.targeting.not_allowed")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        double cost = c.magiculeCostImprison;
        if (EnergyHelper.isOutOfEnergy(entity, 0.0, cost)) return;

        instance.addMasteryPoint(entity);
        instance.setCoolDown(masteredCd(c.imprisonCooldown, instance, entity), mode);
        int duration = (int) Math.round(masteredPower(c.imprisonDuration, instance, entity));
        MobEffectInstance prison = new MobEffectInstance(
                TensuraMobEffects.getReference(TensuraMobEffects.INFINITE_IMPRISONMENT),
                duration, 0, false, false, false);
        TensuraMobEffect.addEffect(target, prison, entity, this, mode);
        TensuraDamageHelper.markHurt(target, entity);
        entity.swing(InteractionHand.MAIN_HAND, true);
        TensuraParticleHelper.addServerParticlesAroundSelf(target, ParticleTypes.FLASH, 1.0);
        TensuraParticleHelper.addServerParticlesAroundSelf(target, ParticleTypes.EXPLOSION, 1.0);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                TensuraSoundEvents.DEBUFF_ACTIVATE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private void onCleanse(ManasSkillInstance instance, LivingEntity entity, int mode) {
        if (instance.onCoolDown(mode)) return;

        UrielConfig.UrielSettings c = cfg();
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, c.cleanseRange, false);
        LivingEntity actual = (target != null && entity.isShiftKeyDown()) ? target : entity;

        boolean success = TensuraMobEffect.removePredicateEffect(actual, holder ->
                holder.is(TensuraTags.MobEffects.AFFECTED_BY_LAW_MANIPULATION)
                        || holder.equals(TensuraMobEffects.getReference(TensuraMobEffects.MAGIC_INTERFERENCE)));

        if (success) {
            instance.addMasteryPoint(entity);
            instance.setCoolDown(masteredCd(c.cleanseCooldown, instance, entity), mode);
            entity.swing(InteractionHand.MAIN_HAND, true);
            entity.level().playSound(null, actual.getX(), actual.getY(), actual.getZ(),
                    TensuraSoundEvents.DEBUFF_DEACTIVATE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            TensuraParticleHelper.spawnServerParticles(actual.level(),
                    TensuraParticleUtils.getPurpleWave(0.9F, actual.getBbWidth() * 3.0F, -0.5F, true),
                    actual.getX(), actual.getY() + actual.getBbHeight() * 0.5, actual.getZ());
        } else {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private void onTakeover(ManasSkillInstance instance, LivingEntity entity, int mode) {
        if (!instance.isMastered(entity)) {

            return;
        }
        if (instance.onCoolDown(mode)) return;

        UrielConfig.UrielSettings c = cfg();
        TensuraProjectile projectile = (TensuraProjectile) ObjectSelectionHelper.getTargetingEntity(
                TensuraProjectile.class, entity, c.takeoverRange, 0.5, true, false, false);
        if (projectile == null || !projectile.isAlive()) {
            entity.sendSystemMessage(Component.translatable("tensura.ability.activation_failed")
                    .withStyle(ChatFormatting.RED));
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }

        ManasSkillInstance projSkill = projectile.getSkill();
        if (projSkill == null || !projSkill.is(TensuraSkillTags.MAGIC)) {
            entity.sendSystemMessage(Component.translatable("tensura.ability.activation_failed")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Entity ownerEntity = projectile.getOwner();
        if (ownerEntity instanceof LivingEntity owner) {

            if (EnergyHelper.getMaxEP(owner) >= EnergyHelper.getMaxEP(entity) * c.takeoverEPGate) {
                entity.sendSystemMessage(Component.translatable("tensura.targeting.not_allowed")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            Skills skills = SkillAPI.getSkillsFrom(owner);
            Optional<ManasSkillInstance> targetOpt = skills.getSkill(projSkill.getSkill());
            if (targetOpt.isPresent()) {
                ManasSkillInstance targetSkill = targetOpt.get();
                int brokenCd = (int) Math.round(masteredPower(c.takeoverBrokenMagicCooldown, instance, entity));
                targetSkill.setCoolDown(
                        Math.max(targetSkill.getCoolDown(projectile.getMode()), brokenCd),
                        projectile.getMode());
                skills.markDirty();
            }
            projectile.setOwner(entity);

            instance.addMasteryPoint(entity);
            instance.setCoolDown(masteredCd(c.takeoverCooldown, instance, entity), mode);
            entity.swing(InteractionHand.MAIN_HAND, true);
            entity.level().playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                    TensuraSoundEvents.DEBUFF_ACTIVATE.get(), SoundSource.PLAYERS, 0.75F, 1.0F);
            if (entity instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable("tensura.skill.mode.law_manipulation.takeover.success",
                                projectile.getName()).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private void onBarrier(ManasSkillInstance instance, LivingEntity entity, int mode) {
        if (instance.onCoolDown(mode)) return;
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;

        UrielConfig.UrielSettings c = cfg();
        LivingEntity target = entity.isShiftKeyDown()
                ? ObjectSelectionHelper.getTargetingEntity(entity, 5.0, false)
                : null;

        if (target != null) {

            AttributeInstance attr = Objects.requireNonNull(target.getAttribute(TensuraAttributes.MULTILAYER_BARRIER));
            if (attr.getModifier(URIEL_MULTILAYER) != null) {

                entity.sendSystemMessage(Component.translatable("tensura.ability.activation_failed")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (attr.getModifier(URIEL_ALLY_MULTILAYER) != null) {
                attr.removeModifier(URIEL_ALLY_MULTILAYER);
                if (attr.getValue() <= 0.0) attr.removeModifiers();
                target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        TensuraSoundEvents.BARRIER_BREAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
                instance.addMasteryPoint(entity);
                instance.setCoolDown(masteredCd(c.barrierCooldown, instance, entity), mode);
                double points = masteredPower(target.getMaxHealth() * c.barrierAllyMultiplier, instance, entity);
                attr.addOrReplacePermanentModifier(new AttributeModifier(
                        URIEL_ALLY_MULTILAYER, points, Operation.ADD_VALUE));
                target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        TensuraSoundEvents.DEFENCE_ACTIVATE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                spawnBarrierParticles(target);
            }
            return;
        }

        AttributeInstance attr = Objects.requireNonNull(entity.getAttribute(TensuraAttributes.MULTILAYER_BARRIER));
        attr.removeModifier(URIEL_ALLY_MULTILAYER);
        if (attr.getModifier(URIEL_MULTILAYER) != null) {
            attr.removeModifier(URIEL_MULTILAYER);
            if (attr.getValue() <= 0.0) attr.removeModifiers();
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    TensuraSoundEvents.BARRIER_BREAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            instance.addMasteryPoint(entity);
            instance.setCoolDown(masteredCd(c.barrierCooldown, instance, entity), mode);
            double points = masteredPower(entity.getMaxHealth() * c.barrierSelfMultiplier, instance, entity);
            attr.addOrReplacePermanentModifier(new AttributeModifier(
                    URIEL_MULTILAYER, points, Operation.ADD_VALUE));
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    TensuraSoundEvents.DEFENCE_ACTIVATE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            spawnBarrierParticles(entity);
        }
    }

    private static void spawnBarrierParticles(LivingEntity who) {
        TensuraParticleHelper.spawnServerParticles(who.level(),
                TensuraParticleUtils.getBlueWave(0.8F, who.getBbWidth() * 2.5F, -0.2F, true),
                who.getX(), who.getY() + who.getBbHeight() * 0.75, who.getZ());
        TensuraParticleHelper.spawnServerParticles(who.level(),
                TensuraParticleUtils.getPurpleWave(0.8F, who.getBbWidth() * 3.0F, -0.2F, true),
                who.getX(), who.getY() + who.getBbHeight() * 0.5, who.getZ());
        TensuraParticleHelper.spawnServerParticles(who.level(),
                TensuraParticleUtils.getBlueWave(0.8F, who.getBbWidth() * 2.5F, -0.2F, true),
                who.getX(), who.getY() + who.getBbHeight() * 0.25, who.getZ());
    }

    private void onStorm(ManasSkillInstance instance, LivingEntity entity, int mode) {
        if (instance.onCoolDown(mode)) return;
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;

        UrielConfig.UrielSettings c = cfg();
        Level level = entity.level();
        Entity targetForPos = ObjectSelectionHelper.getTargetingEntity(entity, c.stormRange, false, false);
        Vec3 pos;
        if (targetForPos != null) {
            pos = targetForPos.getEyePosition();
        } else {
            BlockHitResult hit = ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, Fluid.NONE, 30.0);
            pos = hit.getLocation();
        }

        instance.setCoolDown(masteredCd(c.stormCooldown, instance, entity), mode);
        level.playSound(null, pos.x(), pos.y(), pos.z(),
                TensuraSoundEvents.CAST_SPACE.get(), SoundSource.PLAYERS, 5.0F, 0.5F);
        ((ServerLevel) level).sendParticles(ParticleTypes.FLASH, pos.x(), pos.y(), pos.z(),
                1, 0.0, 0.0, 0.0, 0.0);

        int rayCount = (int) Math.round(masteredPower(c.stormRayCount, instance, entity));
        float dmg = (float) masteredPower(c.stormDamage, instance, entity);
        RandomSource rand = entity.getRandom();
        for (int i = 0; i < rayCount; ++i) {
            Vec3 startOffset = new Vec3(0.0, 1.0 - rand.nextDouble() * 2.0, 0.6)
                    .normalize().scale(20.0)
                    .yRot(360.0F * (float) i * ((float) Math.PI / 180F) / (float) rayCount);
            SpatialRayProjectile ray = new SpatialRayProjectile(level, entity);
            ray.setFollowingOwner(false);
            ray.setLife(20);
            ray.setSize(0.75F);
            ray.setRange(10.0F);
            Vec3 rayPos = pos.add(startOffset);
            ray.setPos(rayPos.add(pos.subtract(rayPos).normalize().scale(10.0)));
            ray.setTargetPos(pos.x(), pos.y(), pos.z());
            ray.setDamage(dmg);
            ray.setSkill(entity, instance, this, mode);
            level.addFreshEntity(ray);
            TensuraParticleHelper.addServerParticlesAroundSelf(ray, ParticleTypes.FLASH);
        }
        instance.addMasteryPoint(entity);
    }

    @Override
    public @NotNull SpatialStorageContainer getSpatialStorage(ManasSkillInstance instance, HolderLookup.Provider provide) {
        UrielConfig.UrielSettings c = cfg();
        SpatialStorageContainer container = new SpatialStorageContainer(c.spatialStorageSize, c.spatialStorageMaxStackSize);
        container.fromTag(instance.getOrCreateTag().getList("SpatialStorage", 10), provide);
        return container;
    }

}

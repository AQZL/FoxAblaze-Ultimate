package com.foxablazeultimate.ability.skill.beelzebub;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.config.BeelzebubConfig;
import com.foxablazeultimate.menu.BeelzebubStorageMenu;
import com.foxablazeultimate.network.OpenBeelzebubStoragePayload;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.unique.GluttonySkill;
import io.github.manasmods.tensura.ability.subclass.ISpatialStorage;
import io.github.manasmods.tensura.config.ability.skill.UniqueSkillConfig;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.unique.ITrulyUnique;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class BeelzebubSkill extends Skill implements ISpatialStorage {

    private static final ResourceLocation BEELZEBUB_CORROSION_SPEED = id("corrosion_speed");

    private static BeelzebubConfig.BeelzebubSettings cfg() {
        return BeelzebubConfig.get().Beelzebub;
    }

    public BeelzebubSkill() {
        super(SkillType.ULTIMATE);

        this.addHeldAttributeModifier(Attributes.MOVEMENT_SPEED, BEELZEBUB_CORROSION_SPEED,
                GluttonySkill.CONFIG.corrosionSpeedMultiplier - 1.0,
                Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "textures/skill/beelzebub.png");
    }

    @Override
    public MutableComponent getColoredName() {
        MutableComponent name = super.getName();
        return name == null ? null : name.withStyle(ChatFormatting.DARK_PURPLE);
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean checkAcquiringRequirement(net.minecraft.world.entity.player.Player entity, double newEP) {
        return false;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return false;
    }

    @Override
    public boolean canScroll(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == 0 && instance.getMastery() >= 0.0;
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == 0 && entity.isShiftKeyDown();
    }

    private static final int MODE_COUNT = 7;

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
            case 0 -> "beelzebub.predation";
            case 1 -> "beelzebub.stomach";
            case 2 -> "beelzebub.isolation";
            case 3 -> "beelzebub.corrosion";
            case 4 -> "beelzebub.receive";
            case 5 -> "beelzebub.steal";
            case 6 -> "beelzebub.consume";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        boolean mastered = instance.isMastered(entity);
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double base = switch (sanitizeMode(mode)) {
            case 2 -> c.magiculeCostIsolation;
            case 3 -> c.magiculeCostCorrosion;
            case 5 -> c.magiculeCostSteal;
            case 6 -> c.magiculeCostConsume;
            default -> 0.0;
        };
        return mastered ? base * 0.5 : base;
    }

    @Override
    public double getAttributeModifierAmplifier(ManasSkillInstance instance, LivingEntity entity,
                                                Holder<Attribute> holder, AttributeTemplate template, int mode) {
        return sanitizeMode(mode) == 3 ? 1.0 : 0.0;
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (instance.onCoolDown(mode)) return false;
        int safe = sanitizeMode(mode);
        GluttonySkill gluttony = (GluttonySkill) UniqueSkills.GLUTTONY.get();
        BeelzebubDelegateHelper.DelegateResult result = switch (safe) {
            case 0 -> {
                preInitPredationRange(instance, entity);
                BeelzebubDelegateHelper.DelegateResult d = BeelzebubDelegateHelper.delegateOnHeld(
                        instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, heldTicks, 0);
                enhancePredationMist(instance, entity);
                yield d;
            }
            case 3 -> {
                BeelzebubDelegateHelper.DelegateResult d = BeelzebubDelegateHelper.delegateOnHeld(
                        instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, heldTicks, 4);
                if (heldTicks % 10 == 0) corrosionExtraPulse(instance, entity, safe);
                yield d;
            }
            case 5 -> {
                boolean active = stealEnhanced(instance, entity, heldTicks, safe);
                yield new BeelzebubDelegateHelper.DelegateResult(active, 0);
            }
            default -> BeelzebubDelegateHelper.DelegateResult.IDLE;
        };
        applyCooldownMultiplier(instance, entity, safe, result.childCooldown());
        return result.activated();
    }

    private void preInitPredationRange(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag businessTag = subInstanceBusinessTag(instance, BeelzebubDelegateHelper.GLUTTONY_TAG);
        if (businessTag.getDouble("range") >= 3.0) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double init = GluttonySkill.CONFIG.predationRange * c.rangeMultiplier;
        if (instance.isMastered(entity)) init *= c.masteredExtraMultiplier;
        businessTag.putDouble("range", init);
        instance.markDirty();
    }

    private void enhancePredationMist(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag businessTag = subInstanceBusinessTag(instance, BeelzebubDelegateHelper.GLUTTONY_TAG);
        if (!businessTag.contains("Mist")) return;
        int mistId = businessTag.getInt("Mist");
        if (mistId == 0) return;
        if (!(entity.level().getEntity(mistId) instanceof TensuraProjectile mist)) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double dmgMul = c.damageMultiplier;
        if (instance.isMastered(entity)) dmgMul *= c.masteredExtraMultiplier;
        mist.setDamage((float)(GluttonySkill.CONFIG.predationDamage * dmgMul));
    }

    private void corrosionExtraPulse(ManasSkillInstance instance, LivingEntity entity, int mode) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double radius = GluttonySkill.CONFIG.corrosionRadius * c.rangeMultiplier;
        if (instance.isMastered(entity)) radius *= c.masteredExtraMultiplier;
        float damage = (float)(GluttonySkill.CONFIG.corrosionDamage * c.damageMultiplier);
        if (instance.isMastered(entity)) damage *= (float) c.masteredExtraMultiplier;

        Level level = entity.level();
        List<LivingEntity> list = level.getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(radius),
                (t) -> !t.is(entity) && t.isAlive() && !t.isAlliedTo(entity));
        if (list.isEmpty()) return;

        DamageSource source = ((TensuraDamageSource) this
                .createSource(instance, entity, TensuraDamageTypes.CORROSION, mode))
                .tensura$setDodgeBypass();
        for (LivingEntity target : list) {
            target.hurt(source, damage);
        }
    }

    private static CompoundTag subInstanceRoot(ManasSkillInstance instance, String subTagKey) {
        CompoundTag parentTag = instance.getOrCreateTag();
        if (!parentTag.contains(subTagKey, 10)) {
            parentTag.put(subTagKey, new CompoundTag());
        }
        return parentTag.getCompound(subTagKey);
    }

    private static CompoundTag subInstanceBusinessTag(ManasSkillInstance instance, String subTagKey) {
        CompoundTag subRoot = subInstanceRoot(instance, subTagKey);
        if (!subRoot.contains("tag", 10)) {
            subRoot.put("tag", new CompoundTag());
        }
        return subRoot.getCompound("tag");
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        int safe = sanitizeMode(mode);
        GluttonySkill gluttony = (GluttonySkill) UniqueSkills.GLUTTONY.get();
        BeelzebubDelegateHelper.DelegateResult result = switch (safe) {
            case 0 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 0);
            case 1 -> {
                this.openSpatialStorage(entity, instance);
                yield BeelzebubDelegateHelper.DelegateResult.IDLE;
            }
            case 2 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 3);
            case 3 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 4);
            case 4 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 5);
            default -> BeelzebubDelegateHelper.DelegateResult.IDLE;
        };
        applyCooldownMultiplier(instance, entity, safe, result.childCooldown());
    }

    private boolean stealEnhanced(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return false;
        }
        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0 && heldTicks > 0) {
            instance.addMasteryPoint(entity);
        }

        BeelzebubConfig.BeelzebubSettings c = cfg();
        UniqueSkillConfig.Merciless mc = ConfigRegistry.getConfig(UniqueSkillConfig.class).Merciless;
        double radius      = (double) mc.stealRadius * c.rangeMultiplier;
        double hpThreshold = mc.stealHP * c.damageMultiplier;
        double ownerEPGate = EnergyHelper.getMaxEP(entity) * mc.stealEP * c.damageMultiplier;
        double fearLevel   = Math.max(0.0, mc.stealFear - 2.0);
        float damageMul    = (float) (10.0 * c.damageMultiplier);

        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
        TensuraParticleHelper.addServerParticlesAroundSelf(entity,
                (ParticleOptions) TensuraParticleTypes.SOUL.get(), 1.0);

        List<LivingEntity> list = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(radius),
                (t) -> !t.is(entity) && t.isAlive() && !t.isAlliedTo(entity)
                        && !t.getType().is(TensuraEntityTags.NO_SPIRITUAL_DAMAGE));
        if (list.isEmpty()) return true;

        for (LivingEntity target : list) {
            if (target instanceof Player p && p.getAbilities().invulnerable) continue;

            MobEffectInstance fear = target.getEffect(
                    TensuraMobEffects.getReference(TensuraMobEffects.FEAR));
            boolean shouldConsume =
                    (double) target.getHealth() < (double) target.getMaxHealth() * hpThreshold
                            || EnergyHelper.getMaxEP(target) < ownerEPGate
                            || (fear != null && (double) fear.getAmplifier() >= fearLevel);

            if (shouldConsume) {
                DamageSource source = ((TensuraDamageSource) this
                        .createSource(instance, entity, TensuraDamageTypes.SOUL_CONSUMED, mode))
                        .tensura$setDodgeBypass();
                target.hurt(source, target.getMaxHealth() * damageMul);
                TensuraParticleHelper.addServerParticlesAroundSelf(target,
                        (ParticleOptions) TensuraParticleTypes.SOUL.get(), 1.0);
            }
        }
        return true;
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target,
                                  DamageSource source, Changeable<Float> amount) {
        if (!this.isInSlot(owner, instance, 6)) return true;
        if (EnergyHelper.isOutOfEnergy(owner, instance, 6)) return true;

        BeelzebubConfig.BeelzebubSettings c = cfg();
        UniqueSkillConfig.Merciless mc = ConfigRegistry.getConfig(UniqueSkillConfig.class).Merciless;
        int duration = (int) Math.max(1, mc.drainDuration * c.durationMultiplier);
        int level    = Math.max(0, mc.drainLevel);

        MobEffectInstance soulDrain = new MobEffectInstance(
                TensuraMobEffects.getReference(TensuraMobEffects.SOUL_DRAIN),
                duration, level, false, false, false);
        TensuraMobEffect.addEffect(target, soulDrain, (net.minecraft.world.entity.Entity) owner, this, 6);
        return true;
    }

    private static void applyCooldownMultiplier(ManasSkillInstance parent, LivingEntity entity, int mode, int childCooldown) {
        if (childCooldown <= 0) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double mult = c.cooldownMultiplier;
        if (parent.isMastered(entity)) mult *= 1.0 / Math.max(0.0001, c.masteredExtraMultiplier);
        int adjusted = Math.max(1, (int) Math.round(childCooldown * mult));
        parent.setCoolDown(adjusted, mode);
    }

    @Override
    public void onScroll(ManasSkillInstance instance, LivingEntity entity, double delta, int mode) {
        if (sanitizeMode(mode) != 0) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double baseMax = instance.isMastered(entity)
                ? GluttonySkill.CONFIG.predationRangeMastered
                : GluttonySkill.CONFIG.predationRange;
        double maxRange = baseMax * c.rangeMultiplier;
        if (instance.isMastered(entity)) maxRange *= c.masteredExtraMultiplier;

        CompoundTag businessTag = subInstanceBusinessTag(instance, BeelzebubDelegateHelper.GLUTTONY_TAG);
        double oldRange = businessTag.getDouble("range");
        double newRange = oldRange + delta;
        if (newRange > maxRange) newRange = maxRange;
        else if (newRange < 3.0) newRange = 3.0;

        if (oldRange != newRange) {
            businessTag.putDouble("range", newRange);
            instance.markDirty();

            if (entity instanceof Player player) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("tensura.skill.range", newRange)
                                .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.DARK_AQUA)),
                        true);
            }
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() < 0.0 || instance.isTemporarySkill()) return;

        AttributeInstance water = entity.getAttribute(TensuraAttributes.WATER_CAPACITY);
        if (water != null) {
            water.setBaseValue(water.getValue() + GluttonySkill.CONFIG.waterCapacity);
        }
        AttributeInstance lava = entity.getAttribute(TensuraAttributes.LAVA_CAPACITY);
        if (lava != null) {
            lava.setBaseValue(lava.getValue() + GluttonySkill.CONFIG.lavaCapacity);
        }

        if (entity instanceof ServerPlayer player) {
            registerTrulyUnique(player);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        if (instance.getMastery() < 0.0) return;

        AttributeInstance water = entity.getAttribute(TensuraAttributes.WATER_CAPACITY);
        if (water != null) {
            water.setBaseValue(Math.max(0.0, water.getValue() - GluttonySkill.CONFIG.waterCapacity));
        }
        AttributeInstance lava = entity.getAttribute(TensuraAttributes.LAVA_CAPACITY);
        if (lava != null) {
            lava.setBaseValue(Math.max(0.0, lava.getValue() - GluttonySkill.CONFIG.lavaCapacity));
        }

        if (entity instanceof ServerPlayer player) {
            unregisterTrulyUnique(player);
        }
    }

    @Override
    public boolean onDeath(ManasSkillInstance instance, LivingEntity owner, DamageSource source) {
        return true;
    }

    @Override
    public @NotNull SpatialStorageContainer getSpatialStorage(ManasSkillInstance instance, HolderLookup.Provider provide) {
        BeelzebubConfig.BeelzebubSettings c = cfg();
        int pagesMin = Math.max(1, c.spatialStoragePagesMin);
        int pagesMax = Math.max(pagesMin, c.spatialStoragePagesMax);
        int maxMastery = Math.max(1, this.getMaxMastery());
        double ratio = Math.max(0.0, Math.min(1.0, instance.getMastery() / (double) maxMastery));
        int pages = pagesMin + (int) Math.floor(ratio * (pagesMax - pagesMin));
        int size = pages * 27;

        net.minecraft.nbt.ListTag tag = instance.getOrCreateTag().getList("SpatialStorage", 10);
        int savedMax = 0;
        for (int i = 0; i < tag.size(); i++) {
            int slot = tag.getCompound(i).getByte("Slot") & 255;
            if (slot + 1 > savedMax) savedMax = slot + 1;
        }
        if (savedMax > size) size = ((savedMax + 26) / 27) * 27;

        SpatialStorageContainer container = new SpatialStorageContainer(size, c.spatialStorageMaxStackSize);
        container.fromTag(tag, provide);
        return container;
    }

    @Override
    public void openSpatialStoragePage(ServerPlayer player, LivingEntity owner, ManasSkillInstance instance, int page) {
        player.nextContainerCounter();
        ManasSkill skill = instance.getSkill();
        SpatialStorageContainer container = this.getSpatialStorage(instance, owner.registryAccess());
        NetworkManager.sendToPlayer(player, new OpenBeelzebubStoragePayload(
                player.containerCounter,
                container.getContainerSize(),
                container.getMaxStackSize(),
                page,
                owner.getId(),
                skill.getRegistryName()));
        player.containerMenu = new BeelzebubStorageMenu(
                player.containerCounter, player.getInventory(), owner, container, skill, page);
        player.initMenu(player.containerMenu);

    }

    private void registerTrulyUnique(ServerPlayer player) {
        if (!BeelzebubFusion.isTrulyUniqueEnabled(player)) return;
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "beelzebub/" + path);
    }
}

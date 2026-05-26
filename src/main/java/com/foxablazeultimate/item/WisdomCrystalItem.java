package com.foxablazeultimate.item;

import java.util.Optional;

import com.foxablazeultimate.registry.FoxAblazeUltimateMobEffects;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class WisdomCrystalItem extends Item {

    public static final int BUFF_DURATION_TICKS = 12000;

    public static final double RAPHAEL_GAIN_RATIO = 0.10;
    public static final double SAGE_GAIN_RATIO = 0.25;

    private static final ResourceLocation GREAT_SAGE_ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "great_sage");
    private static final ResourceLocation SAGE_ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "sage");

    public WisdomCrystalItem(Properties properties) {
        super(properties
                .rarity(Rarity.RARE)
                .food(new FoodProperties.Builder()
                        .nutrition(0)
                        .saturationModifier(0.0F)
                        .alwaysEdible()
                        .build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        ItemStack result = super.finishUsingItem(stack, level, living);
        if (level.isClientSide) return result;
        if (!(living instanceof ServerPlayer player)) return result;

        SkillStorage skills = SkillAPI.getSkillsFrom(player);

        ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();
        addMasteryByRatio(player, skills, raphael, RAPHAEL_GAIN_RATIO);

        ManasSkill greatSage = SkillAPI.getSkillRegistry().get(GREAT_SAGE_ID);
        ManasSkill sage = SkillAPI.getSkillRegistry().get(SAGE_ID);
        addMasteryByRatio(player, skills, greatSage, SAGE_GAIN_RATIO);
        addMasteryByRatio(player, skills, sage, SAGE_GAIN_RATIO);

        player.addEffect(new MobEffectInstance(
                FoxAblazeUltimateMobEffects.WISDOM_BUFF,
                BUFF_DURATION_TICKS, 0, false, true, true));

        int eaten = WisdomCrystalLockState.incrementEaten(player);
        if (eaten >= WisdomCrystalLockState.CRYSTAL_LOCK_THRESHOLD) {
            applyCrystalLock(player);
        }

        return result;
    }

    private static void addMasteryByRatio(ServerPlayer player, SkillStorage skills,
                                          ManasSkill skill, double ratio) {
        if (skill == null) return;
        Optional<ManasSkillInstance> opt = skills.getSkill(skill);
        if (opt.isEmpty()) return;
        ManasSkillInstance instance = opt.get();
        if (instance.getMastery() < 0.0) return;       
        if (instance.isMastered(player)) return;       
        double max = instance.getMaxMastery();
        if (max <= 0.0) return;
        double next = Math.min(max, instance.getMastery() + max * ratio);
        instance.setMastery(next);
        instance.markDirty();
        skills.markDirty();
    }

    private static void applyCrystalLock(ServerPlayer player) {
        ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();
        if (raphael == null) return;
        ResourceLocation raphaelId = raphael.getRegistryName();
        if (raphaelId == null) raphaelId = SkillAPI.getSkillRegistry().getId(raphael);
        if (raphaelId == null) return;
        WisdomCrystalLockState.addCrystalLock(player, raphaelId);
        com.foxablazeultimate.event.WisdomCrystalSyncHandler.syncTo(player);
    }
}

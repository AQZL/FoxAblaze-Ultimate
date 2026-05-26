package com.foxablazeultimate.effect;

import java.awt.Color;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public class WisdomBuffEffect extends TensuraMobEffect {

    private static final ResourceLocation WISDOM_BUFF =
            ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "wisdom_buff");

    public WisdomBuffEffect() {
        super(MobEffectCategory.BENEFICIAL, new Color(220, 130, 235).getRGB());
        this.addAttributeModifier(TensuraAttributes.ABILITY_LEARNING_GAIN,
                WISDOM_BUFF, 0.5, Operation.ADD_VALUE);
        this.addAttributeModifier(TensuraAttributes.ABILITY_MASTERY_GAIN,
                WISDOM_BUFF, 0.5, Operation.ADD_VALUE);
    }
}

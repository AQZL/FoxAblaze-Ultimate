package com.foxablazeultimate.loot;

import com.foxablazeultimate.registry.FoxAblazeUltimateItems;
import com.foxablazeultimate.registry.FoxAblazeUltimateLootModifiers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class AddWisdomCrystalLootModifier extends LootModifier {

    public static final MapCodec<AddWisdomCrystalLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst ->
                    codecStart(inst).apply(inst, AddWisdomCrystalLootModifier::new));

    public AddWisdomCrystalLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                 LootContext context) {
        generatedLoot.add(new ItemStack(FoxAblazeUltimateItems.WISDOM_CRYSTAL.get()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return FoxAblazeUltimateLootModifiers.ADD_WISDOM_CRYSTAL.get();
    }
}

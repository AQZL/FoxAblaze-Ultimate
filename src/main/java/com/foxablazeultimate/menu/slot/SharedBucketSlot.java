package com.foxablazeultimate.menu.slot;

import java.util.Arrays;

import com.foxablazeultimate.menu.BeelzebubStorageMenu;

import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.item.TensuraConsumableItems;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.IAbility;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public class SharedBucketSlot extends Slot {

    private final BeelzebubStorageMenu menu;

    public SharedBucketSlot(BeelzebubStorageMenu menu, Container container, int slotIndex, int xPosition, int yPosition) {
        super(container, slotIndex, xPosition, yPosition);
        this.menu = menu;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.hasEffects()) return false;

        FillEntry entry = lookup(stack.getItem());
        if (entry == null) return false;

        ItemStack currentOutput = this.menu.bucketOutput.getItem(0);
        ItemStack expectedOutput = entry.output().getDefaultInstance();
        if (currentOutput.isEmpty()) return true;
        if (currentOutput.getCount() >= currentOutput.getMaxStackSize()) return false;
        return ItemStack.isSameItemSameComponents(expectedOutput, currentOutput);
    }

    @Override
    public void set(ItemStack stack) {
        super.set(stack);
        if (stack.isEmpty()) return;

        FillEntry entry = lookup(stack.getItem());
        if (entry == null) return;

        IAbility ability = TensuraStorages.getAbilityFrom(this.menu.getStorageOwner());
        double maxCapacity = entry.kind() == FluidKind.WATER
                ? this.menu.getPlayer().getAttributeValue(TensuraAttributes.WATER_CAPACITY)
                : this.menu.getPlayer().getAttributeValue(TensuraAttributes.LAVA_CAPACITY);
        double currentPoint = entry.kind() == FluidKind.WATER ? ability.getWaterPoint() : ability.getLavaPoint();

        if (currentPoint >= maxCapacity) return;

        double newPoint = Math.min(maxCapacity, currentPoint + entry.delta());
        if (entry.kind() == FluidKind.WATER) ability.setWaterPoint(newPoint);
        else ability.setLavaPoint(newPoint);
        ability.markDirty();

        SoundEvent sound = entry.kind() == FluidKind.WATER ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_EMPTY_LAVA;
        this.menu.getPlayer().playSound(sound);

        ItemStack currentOutput = this.menu.bucketOutput.getItem(0);
        if (currentOutput.isEmpty()) {
            this.menu.bucketOutput.setItem(0, entry.output().getDefaultInstance());
        } else {
            ItemStack newOutput = currentOutput.copy();
            newOutput.grow(1);
            this.menu.bucketOutput.setItem(0, newOutput);
        }
        this.menu.bucketOutput.setChanged();

        this.container.setItem(this.getSlotIndex(), ItemStack.EMPTY);
        this.container.setChanged();
    }


    public enum FluidKind { WATER, LAVA }

    public record FillEntry(Item input, double delta, Item output, FluidKind kind) {}

    public static FillEntry lookup(Item item) {
        return Arrays.stream(FILL_TABLE).filter(e -> e.input().equals(item)).findFirst().orElse(null);
    }

    private static final FillEntry[] FILL_TABLE = buildTable();

    private static FillEntry[] buildTable() {
        Item magicWater  = orItems(TensuraConsumableItems.WATER_MAGIC_BOTTLE);
        Item magicVacWater = orItems(TensuraConsumableItems.VACUUMED_WATER_MAGIC_BOTTLE);
        Item magicEmpty  = orItems(TensuraConsumableItems.MAGIC_BOTTLE);

        return new FillEntry[] {
                new FillEntry(Items.WATER_BUCKET,    3.0, Items.BUCKET,      FluidKind.WATER),
                new FillEntry(Items.WET_SPONGE,      3.0, Items.SPONGE,      FluidKind.WATER),
                new FillEntry(magicWater,            1.0, magicEmpty,        FluidKind.WATER),
                new FillEntry(magicVacWater,         1.0, magicEmpty,        FluidKind.WATER),
                new FillEntry(Items.POTION,          1.0, Items.GLASS_BOTTLE,FluidKind.WATER),
                new FillEntry(Items.SPLASH_POTION,   1.0, Items.GLASS_BOTTLE,FluidKind.WATER),
                new FillEntry(Items.LINGERING_POTION,1.0, Items.GLASS_BOTTLE,FluidKind.WATER),

                new FillEntry(Items.LAVA_BUCKET,     3.0, Items.BUCKET,      FluidKind.LAVA),
                new FillEntry(Items.MAGMA_BLOCK,     1.0, Items.COBBLESTONE, FluidKind.LAVA),
        };
    }

    private static Item orItems(Object supplier) {
        if (!(supplier instanceof dev.architectury.registry.registries.RegistrySupplier<?> rs)) return Items.AIR;
        Object value = rs.get();
        return value instanceof Item it ? it : Items.AIR;
    }
}

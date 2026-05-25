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

/**
 * 暴食之王 · 虚数空间 · 共享桶槽（仅放入 / fill-only）。
 *
 * <h3>历史</h3>
 * <p>v1 阶段曾试图复刻 Tensura 原版 {@code WaterStorageInputSlot} / {@code LavaStorageInputSlot}
 * 的双向结构（放空桶取水、放水桶填水）。但本 GUI 与原版有一处关键差异：
 * 原版是<b>左右两条独立 slot</b>（左 water 槽、右 lava 槽），所以"放空桶"时它知道走哪一路；
 * 我们空间紧张只放<b>单个共享 slot</b>，"光标空桶"无法在 set 时区分意图（取水？还是取岩浆？）。
 * 最终方案是把"取液体"改到"光标拿物品右键水/熔岩条"那条路上去（详见
 * {@link com.foxablazeultimate.network.RequestBeelzebubFluidExtractPayload}），
 * 本槽位仅处理"放液体进虚数空间"。
 *
 * <h3>接受</h3>
 * <ul>
 *   <li><b>水路（+ waterPoint）：</b>WATER_BUCKET (+3 → BUCKET), WET_SPONGE (+3 → SPONGE),
 *       WATER_MAGIC_BOTTLE (+1 → MAGIC_BOTTLE), VACUUMED_WATER_MAGIC_BOTTLE (+1 → MAGIC_BOTTLE),
 *       WATER_BOTTLE (+1 → GLASS_BOTTLE), SPLASH_POTION (+1 → GLASS_BOTTLE),
 *       LINGERING_POTION (+1 → GLASS_BOTTLE)</li>
 *   <li><b>岩浆路（+ lavaPoint）：</b>LAVA_BUCKET (+3 → BUCKET), MAGMA_BLOCK (+1 → COBBLESTONE)</li>
 * </ul>
 *
 * <h3>拒绝</h3>
 * <ul>
 *   <li>空桶 / 普通海绵 / 玻璃瓶 —— 这三个原本是 "取液体" 的入口，本版本由右键水/熔岩条触发，
 *       从本槽位拒收，避免玩家误放后无任何反应（mayPlace=false 即光标会保持原态）</li>
 *   <li>挂有效药水效果的瓶子（{@code PotionContents.hasEffects()}）—— 与原版 Tensura 一致，避免玩家
 *       拿强力药水当 +1 水点喂虚数空间</li>
 * </ul>
 *
 * <h3>放入流程</h3>
 * <ol>
 *   <li>{@link #mayPlace(ItemStack)} 校验来源物品在 fill 表中、且当前输出格能容纳对应输出物</li>
 *   <li>{@link #set(ItemStack)} 在 vanilla set 完之后查表，调用 {@code IAbility.setWater/LavaPoint} 增加点数；
 *       清空本槽（vanilla 已经把光标→槽位，但这是输入吸收），向 menu.bucketOutput 推送对应输出物</li>
 *   <li>由于 maxStackSize=1，玩家若在槽位还没被消化前再次塞入，vanilla 会先清出旧 stack；不会发生堆叠</li>
 * </ol>
 */
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
        // 拒绝挂有效效果的药水瓶（与 Tensura 原版 Water/LavaStorageInputSlot 一致）
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.hasEffects()) return false;

        FillEntry entry = lookup(stack.getItem());
        if (entry == null) return false;

        // 输出格能否承接对应输出物
        ItemStack currentOutput = this.menu.bucketOutput.getItem(0);
        ItemStack expectedOutput = entry.output().getDefaultInstance();
        if (currentOutput.isEmpty()) return true;
        if (currentOutput.getCount() >= currentOutput.getMaxStackSize()) return false;
        return ItemStack.isSameItemSameComponents(expectedOutput, currentOutput);
    }

    /**
     * Vanilla 把光标 stack set 进本格之后立即被本方法吸收：
     * 加点数、向 bucketOutput 推送输出物、清空本格。
     */
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

        // 容量已满 → 静默拒绝（玩家可看到光标 / 槽位无变化）
        if (currentPoint >= maxCapacity) return;

        double newPoint = Math.min(maxCapacity, currentPoint + entry.delta());
        if (entry.kind() == FluidKind.WATER) ability.setWaterPoint(newPoint);
        else ability.setLavaPoint(newPoint);
        ability.markDirty();

        // 音效：水路 BUCKET_EMPTY，岩浆路 BUCKET_EMPTY_LAVA（"emptying"指 玩家把桶里的内容物倒进虚数空间）
        SoundEvent sound = entry.kind() == FluidKind.WATER ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_EMPTY_LAVA;
        this.menu.getPlayer().playSound(sound);

        // 推送输出物
        ItemStack currentOutput = this.menu.bucketOutput.getItem(0);
        if (currentOutput.isEmpty()) {
            this.menu.bucketOutput.setItem(0, entry.output().getDefaultInstance());
        } else {
            ItemStack newOutput = currentOutput.copy();
            newOutput.grow(1);
            this.menu.bucketOutput.setItem(0, newOutput);
        }
        this.menu.bucketOutput.setChanged();

        // 清空输入格
        this.container.setItem(this.getSlotIndex(), ItemStack.EMPTY);
        this.container.setChanged();
    }

    // ===========================================================
    // |        Fill 路由表（仅"加点数"方向，extract 不在此）       |
    // ===========================================================

    /** 流体大类。决定 set 走 setWaterPoint 还是 setLavaPoint，以及音效。 */
    public enum FluidKind { WATER, LAVA }

    /** 单条 fill 规则：input 物品 → 增加 delta 点数 → 输出物品。 */
    public record FillEntry(Item input, double delta, Item output, FluidKind kind) {}

    /** 路由查表。null 即非可放入。 */
    public static FillEntry lookup(Item item) {
        return Arrays.stream(FILL_TABLE).filter(e -> e.input().equals(item)).findFirst().orElse(null);
    }

    /**
     * 完整的 fill 路由表。来源于 Tensura 原版 {@code WaterStorageInputSlot.WaterStorage}
     * / {@code LavaStorageInputSlot.LavaStorage}，但<b>仅保留正向（加点数）项</b>。
     * <ul>
     *   <li>magic_bottle 系列单独走 Tensura registry，未注册到 Items 上</li>
     *   <li>BUCKET / SPONGE / GLASS_BOTTLE 那三条 -3 / -18 / -1 反向项<b>不在此表</b>，
     *       它们的功能改由右键水/熔岩条触发（见 menu.extractFluid）</li>
     * </ul>
     */
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

    /** Tensura RegistrySupplier 的 null 安全解包：未加载时返回 AIR，确保 lookup 总能返回 null。 */
    private static Item orItems(Object supplier) {
        if (!(supplier instanceof dev.architectury.registry.registries.RegistrySupplier<?> rs)) return Items.AIR;
        Object value = rs.get();
        return value instanceof Item it ? it : Items.AIR;
    }
}

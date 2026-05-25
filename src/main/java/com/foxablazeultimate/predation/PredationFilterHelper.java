package com.foxablazeultimate.predation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.foxablazeultimate.network.SyncPredationFilterPayload;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 拉斐尔捕食过滤器存储助手。
 *
 * <p>过滤器数据保存在玩家拉斐尔技能实例的 NBT 上：
 * <ul>
 *   <li>{@link #NBT_FILTER}: {@code ListTag}，每一项是 {@code ItemStack#saveOptional} 输出的 {@code CompoundTag}，
 *       并额外写入一个 byte {@code "Slot"} 标识在过滤器里的位置；</li>
 *   <li>{@link #NBT_EXTRA_SLOTS}: int，管理员命令额外开放的过滤槽数。</li>
 * </ul>
 *
 * <p>过滤逻辑只比较 {@link Item}，不比较 NBT，以减少误判。
 */
public final class PredationFilterHelper {
    public static final int BASE_SLOTS = 9;
    public static final int MASTERED_SLOTS = 12;
    public static final int MAX_TOTAL_SLOTS = 54;
    public static final int MAX_EXTRA_SLOTS = MAX_TOTAL_SLOTS - MASTERED_SLOTS;
    public static final String NBT_FILTER = "RaphaelPredationFilter";
    public static final String NBT_EXTRA_SLOTS = "RaphaelPredationFilterExtraSlots";

    private PredationFilterHelper() {}

    public static Optional<ManasSkillInstance> getRaphael(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(FoxAblazeUltimateSkills.RAPHAEL.get());
    }

    public static boolean hasRaphael(Player player) {
        return getRaphael(player).isPresent();
    }

    public static int getSlotCount(Player player) {
        return getRaphael(player).map(instance -> getSlotCount(player, instance)).orElse(0);
    }

    public static int getSlotCount(Player player, ManasSkillInstance instance) {
        int base = instance.isMastered(player) ? MASTERED_SLOTS : BASE_SLOTS;
        return Math.min(MAX_TOTAL_SLOTS, base + getExtraSlots(instance));
    }

    public static int getExtraSlots(ManasSkillInstance instance) {
        return Math.max(0, Math.min(MAX_EXTRA_SLOTS, instance.getOrCreateTag().getInt(NBT_EXTRA_SLOTS)));
    }

    public static int addExtraSlots(ManasSkillInstance instance, int amount) {
        int next = Math.max(0, Math.min(MAX_EXTRA_SLOTS, getExtraSlots(instance) + amount));
        instance.getOrCreateTag().putInt(NBT_EXTRA_SLOTS, next);
        instance.markDirty();
        return next;
    }

    /** 读取过滤器内容为 {@code slots} 长度的 list，缺省项为 {@link ItemStack#EMPTY}。 */
    public static List<ItemStack> loadList(Player player, int slots) {
        List<ItemStack> list = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) list.add(ItemStack.EMPTY);
        Optional<ManasSkillInstance> opt = getRaphael(player);
        if (opt.isEmpty()) return list;
        readFromNbt(list, opt.get(), player.registryAccess());
        return list;
    }

    /**
     * 服务端：单槽更新接口。客户端发包后调用，最后会通过 {@link #sendSync} 推回全量同步。
     *
     * <p>放置规则（与客户端 {@code PredationFilterOverlay.canAccept} 保持一致）：
     * <ul>
     *   <li>{@code ghost} 非空时必须是 {@link BlockItem}（方块对应物品）；</li>
     *   <li>除目标槽位外，其它已用槽位不能已经有同一种 {@link Item}；</li>
     *   <li>清空槽位（{@code ghost} 为空）永远允许。</li>
     * </ul>
     * <p>违反任一条都会拒收并 {@link #sendSync} 回滚客户端可能的乐观更新。
     */
    public static void setSlot(ServerPlayer player, int slot, ItemStack ghost) {
        Optional<ManasSkillInstance> opt = getRaphael(player);
        if (opt.isEmpty()) return;
        ManasSkillInstance instance = opt.get();
        int slots = getSlotCount(player, instance);
        if (slot < 0 || slot >= slots) return;

        ItemStack normalized = ghost.isEmpty() ? ItemStack.EMPTY : ghost.copyWithCount(1);
        List<ItemStack> list = loadList(player, slots);

        if (!normalized.isEmpty()) {
            // 只允许 BlockItem
            if (!(normalized.getItem() instanceof BlockItem)) {
                sendSync(player); // 把客户端可能的乐观更新滚回
                return;
            }
            // 不允许与其它槽位重复
            for (int i = 0; i < list.size(); i++) {
                if (i == slot) continue;
                ItemStack entry = list.get(i);
                if (!entry.isEmpty() && entry.is(normalized.getItem())) {
                    sendSync(player);
                    return;
                }
            }
        }

        list.set(slot, normalized);
        saveList(player, list, instance);
        sendSync(player);
    }

    /** 服务端：向客户端推送完整过滤器状态（槽数 + 内容）。 */
    public static void sendSync(ServerPlayer player) {
        Optional<ManasSkillInstance> opt = getRaphael(player);
        if (opt.isEmpty()) {
            NetworkManager.sendToPlayer(player, new SyncPredationFilterPayload(0, Collections.emptyList()));
            return;
        }
        int slots = getSlotCount(player, opt.get());
        NetworkManager.sendToPlayer(player, new SyncPredationFilterPayload(slots, loadList(player, slots)));
    }

    private static void saveList(Player player, List<ItemStack> list, ManasSkillInstance instance) {
        ListTag tag = new ListTag();
        HolderLookup.Provider provider = player.registryAccess();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (stack.isEmpty()) continue;
            Tag saved = stack.copyWithCount(1).saveOptional(provider);
            if (!(saved instanceof CompoundTag ct)) continue;
            ct.putByte("Slot", (byte) i);
            tag.add(ct);
        }
        instance.getOrCreateTag().put(NBT_FILTER, tag);
        instance.markDirty();
        SkillAPI.getSkillsFrom(player).markDirty();
    }

    private static void readFromNbt(List<ItemStack> list, ManasSkillInstance instance, HolderLookup.Provider provider) {
        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.contains(NBT_FILTER, Tag.TAG_LIST)) return;
        ListTag listTag = tag.getList(NBT_FILTER, Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag itemTag = listTag.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < 0 || slot >= list.size()) continue;
            ItemStack stack = ItemStack.parseOptional(provider, itemTag);
            if (!stack.isEmpty()) list.set(slot, stack.copyWithCount(1));
        }
    }

    public static boolean isBlockFiltered(Player player, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return false;
        return isItemFiltered(player, item);
    }

    public static boolean isStackFiltered(Player player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return isItemFiltered(player, stack.getItem());
    }

    private static boolean isItemFiltered(Player player, Item item) {
        Optional<ManasSkillInstance> opt = getRaphael(player);
        if (opt.isEmpty()) return false;
        int slots = getSlotCount(player, opt.get());
        for (ItemStack entry : loadList(player, slots)) {
            if (!entry.isEmpty() && entry.is(item)) return true;
        }
        return false;
    }
}

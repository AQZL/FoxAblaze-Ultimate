package com.foxablazeultimate.item.beelzebub;

import java.util.List;
import java.util.Optional;

import com.foxablazeultimate.config.BeelzebubConfig;
import com.foxablazeultimate.registry.FoxAblazeUltimateDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 暴食之王 · 虚数胃袋中存放的"被捕实体"物品形态。
 * <p>附带 {@link FoxAblazeUltimateDataComponents#CAPTURED_ENTITY} 组件，包含实体原始 NBT、显示名、捕获时 EP。
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>{@link #getDefaultMaxStackSize()} = 1：实体快照不可叠加（每个实体的 NBT 都是独一无二的）</li>
 *   <li>不进创造模式物品栏：阻止 {@code /give} 拿空白模板，避免出现"空捕获物品"卡死的对象</li>
 *   <li>{@link #getName(ItemStack)} 返回捕获时的实体名，hover 显示 EP / 类型</li>
 *   <li>{@link #use} 在玩家面前 1.5 格反序列化生成实体；释放成功 ItemStack 消耗 1，失败保留并红字提示</li>
 *   <li>是否冻结实体的 tick 计数由 {@link BeelzebubConfig.BeelzebubSettings#captureFreezeTicks} 控制 ——
 *       这里没有持续 tick 的概念（实体本身没在世界里），冻结表现在"释放后 ageInTicks 回滚到捕获时刻"，
 *       所以我们只需要在序列化时一并把 {@code Age} / {@code TickCount} 等存进 {@code entityNbt} 即可，反序列化天然得到原值。</li>
 * </ul>
 */
public class CapturedEntityItem extends Item {

    public CapturedEntityItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    /**
     * 优先用 DataComponent 中的 displayName 显示物品名；fallback 到 super（即翻译键 {@code item.foxablazeultimate.captured_entity}）。
     */
    @Override
    public Component getName(ItemStack stack) {
        CapturedEntityData data = stack.get(FoxAblazeUltimateDataComponents.CAPTURED_ENTITY.get());
        if (data != null && !data.displayName().isEmpty()) {
            return Component.translatable("item.foxablazeultimate.captured_entity.named", data.displayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        return super.getName(stack).copy().withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /**
     * tooltip：显示实体类型注册名 + 捕获时 EP。EP 用 1e6 划档，防止 number 太长。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        CapturedEntityData data = stack.get(FoxAblazeUltimateDataComponents.CAPTURED_ENTITY.get());
        if (data == null) {
            tooltip.add(Component.translatable("item.foxablazeultimate.captured_entity.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.foxablazeultimate.captured_entity.type",
                Component.literal(data.entityType().toString()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.foxablazeultimate.captured_entity.ep",
                Component.literal(formatEp(data.cachedEP())).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.foxablazeultimate.captured_entity.release_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 释放：右键空气在玩家面前 {@code releaseDistance} 格生成实体。
     * <p>实际逻辑全部委托给 {@link #releaseFromStack}；本方法只按 {@link ReleaseStatus#shouldConsume()}
     * 决定是否 shrink stack。无任何聊天消息（按用户要求全部静默）。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        ReleaseStatus status = releaseFromStack(sp, stack);
        if (status.shouldConsume()) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return status == ReleaseStatus.RELEASED
                    ? InteractionResultHolder.sidedSuccess(stack, false)
                    : InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    /**
     * 释放结果。按用户要求所有失败/成功均不发聊天消息，调用方只需要知道：
     * <ul>
     *   <li>{@link #RELEASED}：实体已成功生成，stack 应消耗（释放成功）</li>
     *   <li>{@link #DISCARDED}：物品已损坏（空数据 / 未识别类型 / NBT 损坏 / 创建失败 / 生成失败），
     *       stack <b>静默消耗</b>（垃圾自动清理，无任何提示）</li>
     *   <li>{@link #OBSTRUCTED}：位置被阻挡，stack 保留让玩家换位置再试（也无提示）</li>
     * </ul>
     */
    public enum ReleaseStatus {
        RELEASED(true),
        DISCARDED(true),
        OBSTRUCTED(false);

        private final boolean consume;
        ReleaseStatus(boolean consume) { this.consume = consume; }
        public boolean shouldConsume() { return this.consume; }
    }

    /**
     * 共享释放路径：从 {@code stack} 取 CapturedEntityData，在玩家面前 1.5 格生成实体。
     * <p><b>全程不发任何聊天消息</b>（按用户要求）。视觉/音效反馈仅在 RELEASED 成功时播一次。
     * <p>损坏路径（未识别实体类型 / NBT 反序列化失败 / 创建失败 / 生成失败）返回 {@link ReleaseStatus#DISCARDED}，
     * 调用方应消耗 stack（垃圾物品自动清理）。
     *
     * @return 释放结果；调用方用 {@link ReleaseStatus#shouldConsume()} 决定 shrink。
     */
    public static ReleaseStatus releaseFromStack(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel server)) return ReleaseStatus.DISCARDED;

        CapturedEntityData data = stack.get(FoxAblazeUltimateDataComponents.CAPTURED_ENTITY.get());
        if (data == null) return ReleaseStatus.DISCARDED;

        // 未识别实体类型 → 直接当损坏物品丢弃（mod 缺失场景，留着也无用）
        Optional<EntityType<?>> typeOpt = EntityType.byString(data.entityType().toString());
        if (typeOpt.isEmpty()) return ReleaseStatus.DISCARDED;

        BeelzebubConfig.BeelzebubSettings cfg = BeelzebubConfig.get().Beelzebub;
        Vec3 lookVec = player.getLookAngle();
        Vec3 spawnVec = player.position().add(
                lookVec.x * cfg.releaseDistance,
                Math.max(0.0, lookVec.y * cfg.releaseDistance),
                lookVec.z * cfg.releaseDistance);
        BlockPos spawnPos = BlockPos.containing(spawnVec);

        if (cfg.releaseRefundOnObstruction && !isAirOrNonColliding(server, spawnPos)) {
            return ReleaseStatus.OBSTRUCTED;
        }

        Entity entity = typeOpt.get().create(server);
        if (entity == null) return ReleaseStatus.DISCARDED;

        try {
            CompoundTag nbt = data.entityNbt().copy();
            nbt.remove("UUID");
            if (!cfg.captureFreezeTicks) {
                long elapsedMs = Math.max(0L, System.currentTimeMillis() - data.captureTimeMillis());
                int elapsedTicks = (int) Math.min(Integer.MAX_VALUE, elapsedMs / 50L);
                if (elapsedTicks > 0) {
                    if (nbt.contains("Age")) nbt.putInt("Age", nbt.getInt("Age") + elapsedTicks);
                    if (nbt.contains("PortalCooldown")) {
                        nbt.putInt("PortalCooldown", Math.max(0, nbt.getInt("PortalCooldown") - elapsedTicks));
                    }
                }
            }
            entity.load(nbt);
        } catch (Exception e) {
            // NBT 损坏 → 销毁实体壳，物品也一并丢弃
            entity.discard();
            return ReleaseStatus.DISCARDED;
        }
        entity.moveTo(spawnVec.x, spawnVec.y, spawnVec.z, player.getYRot(), 0);

        if (!server.addFreshEntity(entity)) return ReleaseStatus.DISCARDED;

        // 仅成功时播一次释放音效（视觉反馈，无文字）
        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.5F);
        return ReleaseStatus.RELEASED;
    }

    private static boolean isAirOrNonColliding(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /** 把 EP 数字格式化为带后缀的紧凑显示。 */
    private static String formatEp(double ep) {
        if (ep < 1_000.0) return String.format("%.0f", ep);
        if (ep < 1_000_000.0) return String.format("%.1fK", ep / 1_000.0);
        if (ep < 1_000_000_000.0) return String.format("%.2fM", ep / 1_000_000.0);
        return String.format("%.2fB", ep / 1_000_000_000.0);
    }

    /**
     * 工厂方法：依据当前世界中的活体生成"完整快照"ItemStack，配套 {@link CapturedEntityData}。
     * 由 {@code BeelzebubCaptureHandler} 在判断捕获通过后调用。
     */
    public static ItemStack captureToStack(LivingEntity target, double cachedEP, ItemStack template) {
        CompoundTag nbt = new CompoundTag();
        target.saveWithoutId(nbt);
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        String name = target.getDisplayName() != null ? target.getDisplayName().getString() : id.getPath();
        CapturedEntityData data = new CapturedEntityData(id, nbt, name, cachedEP, System.currentTimeMillis());
        ItemStack stack = template.copy();
        if (stack.isEmpty()) stack.setCount(1);
        stack.set(FoxAblazeUltimateDataComponents.CAPTURED_ENTITY.get(), data);
        return stack;
    }
}

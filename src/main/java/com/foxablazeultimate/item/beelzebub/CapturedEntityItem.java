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

public class CapturedEntityItem extends Item {

    public CapturedEntityItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public Component getName(ItemStack stack) {
        CapturedEntityData data = stack.get(FoxAblazeUltimateDataComponents.CAPTURED_ENTITY.get());
        if (data != null && !data.displayName().isEmpty()) {
            return Component.translatable("item.foxablazeultimate.captured_entity.named", data.displayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        return super.getName(stack).copy().withStyle(ChatFormatting.LIGHT_PURPLE);
    }

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

    public enum ReleaseStatus {
        RELEASED(true),
        DISCARDED(true),
        OBSTRUCTED(false);

        private final boolean consume;
        ReleaseStatus(boolean consume) { this.consume = consume; }
        public boolean shouldConsume() { return this.consume; }
    }

    public static ReleaseStatus releaseFromStack(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel server)) return ReleaseStatus.DISCARDED;

        CapturedEntityData data = stack.get(FoxAblazeUltimateDataComponents.CAPTURED_ENTITY.get());
        if (data == null) return ReleaseStatus.DISCARDED;

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
            entity.discard();
            return ReleaseStatus.DISCARDED;
        }
        entity.moveTo(spawnVec.x, spawnVec.y, spawnVec.z, player.getYRot(), 0);

        if (!server.addFreshEntity(entity)) return ReleaseStatus.DISCARDED;

        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.5F);
        return ReleaseStatus.RELEASED;
    }

    private static boolean isAirOrNonColliding(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static String formatEp(double ep) {
        if (ep < 1_000.0) return String.format("%.0f", ep);
        if (ep < 1_000_000.0) return String.format("%.1fK", ep / 1_000.0);
        if (ep < 1_000_000_000.0) return String.format("%.2fM", ep / 1_000_000.0);
        return String.format("%.2fB", ep / 1_000_000_000.0);
    }

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

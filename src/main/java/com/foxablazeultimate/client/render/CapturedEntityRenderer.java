package com.foxablazeultimate.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.item.beelzebub.CapturedEntityData;
import com.foxablazeultimate.registry.FoxAblazeUltimateDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 暴食之王 · 被捕实体物品的 3D BEWLR 渲染器。
 *
 * <h3>实现思路（最小可用版）</h3>
 * <ul>
 *   <li>读取物品上的 {@link CapturedEntityData}，按 {@code entityType} 取一个 dummy 实体（非真正世界中实体）</li>
 *   <li>每个 EntityType 缓存一份 dummy，避免每帧重建（昂贵且会触发 mob AI 注册）</li>
 *   <li>根据实体高度自动缩放到 16×16 像素框内（GUI 模式）/ 1× 缩放（其他模式）</li>
 *   <li>位置：放置于 PoseStack 中点，朝向 GUI 时让模型微旋面对观察者</li>
 *   <li>失败兜底：data 缺失或 entityType 无法解析时不画任何东西，让 vanilla baked model 兜底显示 missing</li>
 * </ul>
 *
 * <h3>性能 & 可靠性</h3>
 * <ul>
 *   <li>dummy entity 不进入世界，不 tick AI，不参与碰撞，仅供 EntityRenderDispatcher 取模型与贴图</li>
 *   <li>未对 entityNbt 做反序列化 —— 大量 mod 实体的 NBT 需要复杂依赖，反序列化失败极常见；
 *       先不还原 HP / 装备 / 自定义名等动态状态，后续 v1.1 可在 dummy 上选择性 load 关键字段（变体、宝宝形态等）</li>
 *   <li>{@code Minecraft.getInstance().level} 必须存在才能创建 entity（否则会 NPE）—— 单人开图前不会被调用本类</li>
 * </ul>
 */
public class CapturedEntityRenderer extends BlockEntityWithoutLevelRenderer {

    /** dummy entity 缓存：每个 EntityType 一份。 */
    private final Map<EntityType<?>, Entity> dummyCache = new HashMap<>();

    public CapturedEntityRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        CapturedEntityData data = stack.get(FoxAblazeUltimateDataComponents.CAPTURED_ENTITY.get());
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Optional<EntityType<?>> typeOpt = EntityType.byString(data.entityType().toString());
        if (typeOpt.isEmpty()) return;

        Entity dummy = dummyCache.computeIfAbsent(typeOpt.get(), t -> {
            try {
                Entity e = t.create(mc.level);
                if (e == null) return null;
                // 防御性：把 dummy 的 yaw / pitch 重置为 0，避免视觉上随机化
                e.setYRot(0);
                e.setXRot(0);
                if (e instanceof LivingEntity le) {
                    le.yHeadRot = 0;
                    le.yBodyRot = 0;
                }
                return e;
            } catch (Throwable err) {
                FoxAblazeUltimateMod.LOGGER.debug(
                        "[Beelzebub BEWLR] 无法为 {} 创建 dummy 实体（mod 缺失或构造异常），跳过 3D 渲染",
                        BuiltInRegistries.ENTITY_TYPE.getKey(t), err);
                return null;
            }
        });
        if (dummy == null) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        pose.pushPose();
        // 把模型放到物品坐标系 (0.5, 0.5, 0.5) 中心
        pose.translate(0.5, 0.5, 0.5);

        // 自适应缩放：以 entity bbHeight / bbWidth 中较大者为准。
        // ★ 关键：实际渲染模型经常比 BoundingBox 大很多（狼的头/僵尸的脚/苦力怕的躯干等模型贴图超出 hitbox），
        //   所以即便 BoundingBox 缩到 0.9 格也会"溢出 slot"。这里大幅收紧到 ~0.55 格，
        //   并 floor 一个最小分母 0.6 防止迷你实体（兔子 / 蝙蝠）被过度放大，再额外加 0.75 上限。
        float bbMax = Math.max(dummy.getBbHeight(), dummy.getBbWidth());
        if (bbMax <= 0.0F) bbMax = 1.0F;
        float scale = 0.55F / Math.max(0.6F, bbMax);
        if (scale > 0.75F) scale = 0.75F;
        pose.scale(scale, scale, scale);

        // 让模型的脚位于格子下沿；同时根据上下文决定额外旋转
        switch (context) {
            case GUI -> {
                pose.translate(0, -dummy.getBbHeight() / 2.0, 0);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F + getGuiSpinAngle()));
            }
            case GROUND, FIXED -> pose.translate(0, -dummy.getBbHeight() / 2.0, 0);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND ->
                    pose.translate(0, -dummy.getBbHeight() / 2.0, 0);
            default -> pose.translate(0, -dummy.getBbHeight() / 2.0, 0);
        }

        try {
            dispatcher.render(dummy, 0.0, 0.0, 0.0, 0.0F, 0.0F, pose, buffer, packedLight);
        } catch (Throwable err) {
            // 罕见的"实体没注册渲染器"或"渲染过程内部抛"路径：吞掉，避免每帧刷屏崩溃
            FoxAblazeUltimateMod.LOGGER.debug("[Beelzebub BEWLR] 渲染异常 {}，跳过", err.toString());
        }
        pose.popPose();
    }

    /** GUI 中给捕获物品做缓慢自转，让 3D 模型的细节更易被玩家看到。 */
    private static float getGuiSpinAngle() {
        long ms = System.currentTimeMillis();
        return (ms % 4000L) / 4000.0F * 360.0F;
    }

    /**
     * vanilla 兼容性占位：BEWLR 父类要求拿 {@link ItemRenderer} 的 baked model 缓存，
     * 这里直接返回 minecraft:item/empty 对应的 BakedModel —— 仅用于 fallback，本 renderer
     * 永远走 {@link #renderByItem} 自定义路径。
     */
    public static BakedModel emptyBakedModel() {
        return Minecraft.getInstance().getItemRenderer()
                .getItemModelShaper().getModelManager().getMissingModel();
    }
}

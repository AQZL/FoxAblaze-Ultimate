package com.foxablazeultimate.network;

import java.util.Optional;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.ability.skill.raphael.RaphaelSkill;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S：玩家在拉斐尔命名 GUI 中点确认时发送，把"自定义名"写到玩家自己拉斐尔 instance 的 NBT 上。
 *
 * <h3>语义</h3>
 * <ul>
 *   <li><b>每玩家独立</b>：本 payload 只更新当前请求玩家的 instance；其他玩家不受影响。多人服各自取名互不干扰</li>
 *   <li><b>可空</b>：{@code name} 为空字符串视为"恢复默认"——instance 上写入 {@code RaphaelCustomName=""}（或干脆移除字段）；
 *       UI 端读取时空字符串走 fallback 到 {@code skill.getName()}</li>
 *   <li><b>长度限制</b>：64 字符，与 Tensura 原版 NamingScreen 一致；过长会被 readUtf(64) 截断</li>
 * </ul>
 *
 * <h3>幂等</h3>
 * <p>本 payload 不依赖"是否已命名"标记——玩家可以多次发起请求改名（虽然 GUI 只在初次 learnSkill 时弹一次，
 * 但若未来加入 /raphael rename 指令，本 payload 仍可复用）。{@link RaphaelSkill#applyCustomName} 是
 * 纯赋值，不做"是否变化"判断，markDirty 一律调用。
 */
public record RequestRaphaelRenamePayload(String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestRaphaelRenamePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "request_raphael_rename"));

    public static final StreamCodec<FriendlyByteBuf, RequestRaphaelRenamePayload> STREAM_CODEC =
            CustomPacketPayload.codec(RequestRaphaelRenamePayload::encode, RequestRaphaelRenamePayload::new);

    public RequestRaphaelRenamePayload(FriendlyByteBuf buf) {
        this(buf.readUtf(64));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.name, 64);
    }

    public static void handle(RequestRaphaelRenamePayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;

        context.queue(() -> {
            Optional<ManasSkillInstance> opt = SkillAPI.getSkillsFrom(player)
                    .getSkill(FoxAblazeUltimateSkills.RAPHAEL.get());
            if (opt.isEmpty()) {
                FoxAblazeUltimateMod.LOGGER.debug(
                        "[FoxAblazeUltimate] 玩家 {} 发起拉斐尔重命名但本人没有该技能，忽略",
                        player.getGameProfile().getName());
                return;
            }
            // 名字净化：trim 防止前后空白；空白视作"恢复默认"
            String sanitized = payload.name == null ? "" : payload.name.trim();
            RaphaelSkill.applyCustomName(opt.get(), sanitized, player);
        });
    }

    @Override
    public CustomPacketPayload.Type<RequestRaphaelRenamePayload> type() {
        return TYPE;
    }
}

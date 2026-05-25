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
            String sanitized = payload.name == null ? "" : payload.name.trim();
            RaphaelSkill.applyCustomName(opt.get(), sanitized, player);
        });
    }

    @Override
    public CustomPacketPayload.Type<RequestRaphaelRenamePayload> type() {
        return TYPE;
    }
}

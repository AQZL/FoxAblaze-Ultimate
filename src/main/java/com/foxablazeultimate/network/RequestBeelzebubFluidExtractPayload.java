package com.foxablazeultimate.network;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.menu.BeelzebubStorageMenu;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S：玩家在虚数空间 GUI 里"光标拿物品右键水/熔岩条"时发送，请求服务端尝试抽取液体。
 *
 * <h3>字段</h3>
 * <ul>
 *   <li>{@code containerId} —— 客户端当前打开的 menu container id；用于服务端校验请求与当前 menu 一致，
 *       防止客户端通过陈旧或伪造 id 触发其他玩家 menu 的抽取</li>
 *   <li>{@code fluidType} —— 0 = 水，1 = 熔岩；其余值视为非法请求被丢弃</li>
 * </ul>
 *
 * <h3>服务端处理</h3>
 * <p>{@link #handle} 把请求转交给 {@link BeelzebubStorageMenu#extractFluid}，所有抽取规则与点数检查
 * 都在 menu 一侧；payload 仅做协议层基础校验（玩家在线、菜单类型、id 匹配）。
 */
public record RequestBeelzebubFluidExtractPayload(
        int containerId,
        byte fluidType) implements CustomPacketPayload {

    public static final byte FLUID_WATER = 0;
    public static final byte FLUID_LAVA  = 1;

    public static final CustomPacketPayload.Type<RequestBeelzebubFluidExtractPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "request_beelzebub_fluid_extract"));

    public static final StreamCodec<FriendlyByteBuf, RequestBeelzebubFluidExtractPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RequestBeelzebubFluidExtractPayload::encode, RequestBeelzebubFluidExtractPayload::new);

    public RequestBeelzebubFluidExtractPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.containerId);
        buf.writeByte(this.fluidType);
    }

    public static void handle(RequestBeelzebubFluidExtractPayload payload, NetworkManager.PacketContext context) {
        // architectury C2S 契约：context.getPlayer() 在 server 端必为 ServerPlayer
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        if (payload.fluidType != FLUID_WATER && payload.fluidType != FLUID_LAVA) return;

        context.queue(() -> {
            // 防止玩家关菜单后竞态、或客户端伪造 id 给别的容器发包
            if (!(player.containerMenu instanceof BeelzebubStorageMenu menu)) return;
            if (menu.containerId != payload.containerId) return;
            menu.extractFluid(player, payload.fluidType);
        });
    }

    @Override
    public CustomPacketPayload.Type<RequestBeelzebubFluidExtractPayload> type() {
        return TYPE;
    }
}

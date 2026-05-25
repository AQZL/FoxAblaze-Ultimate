package com.foxablazeultimate.command;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.predation.PredationFilterHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class PredationFilterCommand {
    private PredationFilterCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("foxablazeultimate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("predation_filter")
                        .then(Commands.literal("add_slots")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, PredationFilterHelper.MAX_EXTRA_SLOTS))
                                                .executes(ctx -> addSlots(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"))))))));
    }

    private static int addSlots(CommandSourceStack source, ServerPlayer player, int amount) {
        return PredationFilterHelper.getRaphael(player).map(instance -> {
            int extra = PredationFilterHelper.addExtraSlots(instance, amount);
            int total = PredationFilterHelper.getSlotCount(player, instance);
            source.sendSuccess(() -> Component.translatable(
                    "foxablazeultimate.predation_filter.command.added",
                    player.getDisplayName(), amount, total, extra).withStyle(ChatFormatting.GREEN), true);
            return total;
        }).orElseGet(() -> {
            source.sendFailure(Component.translatable(
                    "foxablazeultimate.predation_filter.command.no_raphael",
                    player.getDisplayName()).withStyle(ChatFormatting.RED));
            return 0;
        });
    }
}

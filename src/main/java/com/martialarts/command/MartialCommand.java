package com.martialarts.command;

import com.martialarts.data.MartialCapability;
import com.martialarts.data.MartialData;
import com.martialarts.data.MartialStyle;
import com.martialarts.network.ModNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /martial info              — показать свой уровень и стиль
 * /martial setlevel <1-20>   — (оп) установить уровень мастерства
 * /martial learnall          — (оп) выучить все стили
 * /martial reset             — (оп) сбросить прогресс
 */
public final class MartialCommand {
    private MartialCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("martial")
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    MartialCapability.get(p).ifPresent(d -> p.sendSystemMessage(Component.translatable(
                            "msg.martialarts.info", d.style.displayName(), d.level, d.xp,
                            d.level >= MartialData.MAX_LEVEL ? "MAX" : String.valueOf(d.xpForNext()),
                            (int) d.chi, (int) d.maxChi()).withStyle(ChatFormatting.YELLOW)));
                    return 1;
                }))
                .then(Commands.literal("setlevel")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, MartialData.MAX_LEVEL))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int lvl = IntegerArgumentType.getInteger(ctx, "level");
                                    MartialCapability.get(p).ifPresent(d -> {
                                        d.level = lvl;
                                        d.xp = 0;
                                        d.chi = d.maxChi();
                                        ModNetwork.sync(p);
                                    });
                                    p.sendSystemMessage(Component.translatable("msg.martialarts.level_set", lvl));
                                    return 1;
                                })))
                .then(Commands.literal("learnall")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            MartialCapability.get(p).ifPresent(d -> {
                                for (MartialStyle s : MartialStyle.values()) d.learn(s);
                                ModNetwork.sync(p);
                            });
                            p.sendSystemMessage(Component.translatable("msg.martialarts.learned_all"));
                            return 1;
                        }))
                .then(Commands.literal("reset")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            MartialCapability.get(p).ifPresent(d -> {
                                d.copyFrom(new MartialData());
                                ModNetwork.sync(p);
                            });
                            p.sendSystemMessage(Component.translatable("msg.martialarts.reset"));
                            return 1;
                        })));
    }
}

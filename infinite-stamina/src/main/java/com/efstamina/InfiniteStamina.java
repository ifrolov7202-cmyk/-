package com.efstamina;

import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Бесконечная выносливость Epic Fight: на сервере каждый тик выносливость игрока = максимум.
 * Включается и выключается прямо в игре: /gamerule efInfiniteStamina true|false
 */
@Mod(InfiniteStamina.MODID)
public class InfiniteStamina {
    public static final String MODID = "ef_infinite_stamina";

    public static final GameRules.Key<GameRules.BooleanValue> RULE =
            GameRules.register("efInfiniteStamina", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));

    public InfiniteStamina() {
        MinecraftForge.EVENT_BUS.addListener(InfiniteStamina::onPlayerTick);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) return;
        if (!event.player.level().getGameRules().getBoolean(RULE)) return;
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(event.player, PlayerPatch.class);
        if (patch == null) return;
        float max = patch.getMaxStamina();
        if (patch.getStamina() < max) {
            patch.setStamina(max);
        }
    }
}

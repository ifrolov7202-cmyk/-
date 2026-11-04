package com.bamartial.client;

import com.bamartial.BAMartial;
import com.bamartial.network.ModNetwork;
import com.bamartial.network.RushPacket;
import com.bamartial.skills.MartialBattleStyle;
import com.mojang.blaze3d.platform.InputConstants;
import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import yesman.epicfight.client.input.EpicFightKeyMappings;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Зажатая клавиша атаки Epic Fight (по умолчанию ЛКМ) голой рукой в рукопашном стиле:
 * после короткого удержания сервер начинает серию ударов, пока кнопка не отпущена или не кончится выносливость.
 */
@Mod.EventBusSubscriber(modid = BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientRush {
    /** Сколько тиков держать атаку, чтобы началась серия (обычные клики остаются связкой). */
    private static final int HOLD_TICKS = 7;

    private static int held;
    private static boolean sent;
    private static boolean broken;

    private ClientRush() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || broken) return;
        try {
            if (canRush()) {
                held++;
                if (!sent && held >= HOLD_TICKS) {
                    ModNetwork.CHANNEL.sendToServer(new RushPacket(true));
                    sent = true;
                }
            } else {
                held = 0;
                if (sent) {
                    ModNetwork.CHANNEL.sendToServer(new RushPacket(false));
                    sent = false;
                }
            }
        } catch (Throwable t) {
            broken = true;
            held = 0;
            sent = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        held = 0;
        sent = false;
    }

    private static boolean canRush() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isSpectator() || !player.getMainHandItem().isEmpty()) return false;
        if (!physicallyDown(EpicFightKeyMappings.ATTACK)) return false;
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        if (patch == null || !patch.isEpicFightMode()) return false;
        SkillContainer style = patch.getSkill(BattleArtsSkillSlots.BATTLE_STYLE);
        return style != null && style.getSkill() instanceof MartialBattleStyle;
    }

    /** Состояние самой кнопки (Epic Fight может «отжимать» клавишу после удара). */
    private static boolean physicallyDown(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        return mapping.isDown();
    }
}

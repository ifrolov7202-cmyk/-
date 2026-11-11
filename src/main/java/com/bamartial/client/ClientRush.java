package com.bamartial.client;

import com.bamartial.BAMartial;
import com.bamartial.MartialConfig;
import com.bamartial.network.ModNetwork;
import com.bamartial.network.RushPacket;
import com.bamartial.skills.MartialBattleStyle;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;
import yesman.epicfight.api.client.input.InputManager;
import yesman.epicfight.api.client.input.action.EpicFightInputAction;
import yesman.epicfight.client.input.EpicFightKeyMappings;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.config.ClientConfig;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

/**
 * Зажатая атака Epic Fight (по умолчанию ЛКМ) голой рукой в рукопашном стиле — серия ударов.
 * <p>
 * Как и сам Epic Fight, решаем в момент нажатия: если нажатие пошло на добычу блока (Epic Fight отдал его
 * ванильной игре), на предмет в руке, в меню или верхом — серии не будет до следующего нажатия.
 * Серия начинается тогда же, когда Epic Fight засчитывает «долгое нажатие» (у голых рук оно ничего не делает),
 * поэтому короткий клик — обычный удар связки, удержание — серия. Удары, выносливость и конец серии считает сервер.
 */
@Mod.EventBusSubscriber(modid = BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientRush {
    private static final Logger LOG = LogUtils.getLogger();
    /** Если атака и навык оружия на разных кнопках: сколько тиков держать атаку до начала серии. */
    private static final int SEPARATE_KEY_HOLD_TICKS = 5;

    /** Сколько тиков подряд зажата кнопка атаки. */
    private static int held;
    /** Нажатие началось в подходящий момент. */
    private static boolean valid;
    /** Серия запрошена у сервера. */
    private static boolean sent;
    /** Ввод Epic Fight другой версии — опрашиваем кнопку напрямую. */
    private static boolean rawInput;
    /** Непредвиденная ошибка — серия выключена до перезапуска игры. */
    private static boolean broken;

    private ClientRush() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || broken) return;
        try {
            tick();
        } catch (Throwable t) {
            broken = true;
            LOG.warn("[ba_martialarts] hold-attack rush disabled on this client", t);
            try {
                stop(RushPacket.CANCEL);
            } catch (Throwable ignored) {
            }
            held = 0;
            valid = false;
            sent = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        held = 0;
        valid = false;
        sent = false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) {
            held = 0;
            valid = false;
            sent = false;
            return;
        }
        if (!attackDown()) {
            // кнопку отпустили: сервер завершит серию (после 5+ ударов — завершающим ударом стиля)
            stop(RushPacket.RELEASE);
            held = 0;
            valid = false;
            return;
        }
        if (held++ == 0) valid = canStart(mc, player);
        if (!valid) return;
        if (!stillAllowed(mc, player)) {
            // открыли меню, взяли предмет, сменили стиль — серия прерывается без завершающего удара
            valid = false;
            stop(RushPacket.CANCEL);
            return;
        }
        if (!sent && held >= holdTicks()) {
            ModNetwork.CHANNEL.sendToServer(new RushPacket(RushPacket.HOLD));
            sent = true;
        }
    }

    private static void stop(byte reason) {
        if (!sent) return;
        sent = false;
        if (Minecraft.getInstance().getConnection() != null) {
            ModNetwork.CHANNEL.sendToServer(new RushPacket(reason));
        }
    }

    /** Условия в момент нажатия: те же, при которых Epic Fight проигрывает удар (а не отдаёт клик ванильной игре). */
    private static boolean canStart(Minecraft mc, LocalPlayer player) {
        if (!MartialConfig.get(MartialConfig.RUSH_ENABLED, true) || !stillAllowed(mc, player)) return false;
        LocalPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, LocalPlayerPatch.class);
        return patch != null && patch.canPlayAttackAnimation();
    }

    /** Условия, которые должны держаться всю серию. */
    private static boolean stillAllowed(Minecraft mc, LocalPlayer player) {
        if (mc.screen != null || player.isSpectator() || player.isPassenger() || !player.getMainHandItem().isEmpty()) return false;
        LocalPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, LocalPlayerPatch.class);
        if (patch == null || !patch.isEpicFightMode()) return false;
        SkillContainer style = patch.getSkill(BattleArtsSkillSlots.BATTLE_STYLE);
        return style != null && style.getSkill() instanceof MartialBattleStyle;
    }

    /** Через сколько тиков удержания начинать серию. */
    private static int holdTicks() {
        if (!rawInput) {
            try {
                if (InputManager.isBoundToSamePhysicalInput(EpicFightInputAction.ATTACK, EpicFightInputAction.WEAPON_INNATE_SKILL)) {
                    return Math.max(3, ClientConfig.longPressCounter + 2);
                }
                return SEPARATE_KEY_HOLD_TICKS;
            } catch (LinkageError e) {
                rawInput = true;
            }
        }
        boolean same = EpicFightKeyMappings.ATTACK.getKey().equals(EpicFightKeyMappings.WEAPON_INNATE_SKILL.getKey());
        return same ? 4 : SEPARATE_KEY_HOLD_TICKS;
    }

    /** Кнопка атаки нажата физически (Epic Fight может «отжимать» клавишу после удара). */
    private static boolean attackDown() {
        if (!rawInput) {
            try {
                return InputManager.isActionPhysicallyActive(EpicFightInputAction.ATTACK);
            } catch (LinkageError e) {
                rawInput = true;
            }
        }
        KeyMapping mapping = EpicFightKeyMappings.ATTACK;
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

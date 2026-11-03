package com.martialarts.client;

import com.martialarts.skill.Skill;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.martialarts";

    public static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_V);
    public static final KeyMapping POWER_STRIKE = key("power_strike", GLFW.GLFW_KEY_R);
    public static final KeyMapping GUARD = key("guard", GLFW.GLFW_KEY_G);
    public static final KeyMapping SPIN_KICK = key("spin_kick", GLFW.GLFW_KEY_Z);
    public static final KeyMapping FLURRY = key("flurry", GLFW.GLFW_KEY_B);
    public static final KeyMapping SIGNATURE = key("signature", GLFW.GLFW_KEY_H);
    public static final KeyMapping CYCLE_STYLE = key("cycle_style", GLFW.GLFW_KEY_K);

    /** Клавиши техник в порядке Skill.values(). */
    public static final KeyMapping[] SKILL_KEYS = {DASH, POWER_STRIKE, GUARD, SPIN_KICK, FLURRY, SIGNATURE};

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.martialarts." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static KeyMapping forSkill(Skill skill) {
        return SKILL_KEYS[skill.ordinal()];
    }

    private KeyBindings() {}
}

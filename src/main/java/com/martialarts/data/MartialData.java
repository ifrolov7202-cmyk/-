package com.martialarts.data;

import com.martialarts.skill.Skill;
import net.minecraft.nbt.CompoundTag;

/**
 * Данные игрока: Ци, уровень мастерства, опыт, выученные стили.
 * Сохраняемые поля пишутся в NBT; остальные живут только пока игрок в сети.
 */
public class MartialData {
    public static final int MAX_LEVEL = 20;

    // --- сохраняемое ---
    public float chi = 100;
    public int level = 1;
    public int xp = 0;
    public MartialStyle style = MartialStyle.NONE;
    public int learnedMask = 0;

    // --- временное (не сохраняется) ---
    public final long[] cooldownEnd = new long[Skill.values().length];
    public int combo = 0;
    public long lastHitTick = 0;
    public float lastAttackStrength = 1.0f;
    public long guardStart = 0;
    public long guardEnd = 0;
    public int flurryTicks = 0;
    public int flurryTarget = -1;
    public long lastTrainTick = 0;
    public boolean dirty = true;

    public float maxChi() {
        return 100 + (level - 1) * 5;
    }

    public int xpForNext() {
        return 30 + level * 15;
    }

    public boolean isLearned(MartialStyle s) {
        return s == MartialStyle.NONE || (learnedMask & (1 << s.id)) != 0;
    }

    public void learn(MartialStyle s) {
        learnedMask |= (1 << s.id);
    }

    public boolean isGuarding(long now) {
        return now < guardEnd;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Chi", chi);
        tag.putInt("Level", level);
        tag.putInt("Xp", xp);
        tag.putInt("Style", style.id);
        tag.putInt("Learned", learnedMask);
        return tag;
    }

    public void load(CompoundTag tag) {
        level = Math.max(1, Math.min(MAX_LEVEL, tag.getInt("Level")));
        xp = Math.max(0, tag.getInt("Xp"));
        style = MartialStyle.byId(tag.getInt("Style"));
        learnedMask = tag.getInt("Learned");
        chi = tag.contains("Chi") ? Math.min(tag.getFloat("Chi"), maxChi()) : maxChi();
        dirty = true;
    }

    public void copyFrom(MartialData other) {
        load(other.save());
    }
}

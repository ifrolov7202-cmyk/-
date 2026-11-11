package com.bamartial.anim;

import com.bamartial.BAMartial;
import com.bamartial.MartialConfig;
import com.bamartial.skills.MartialBattleStyle;
import com.bamartial.skills.StyleKind;
import com.mojang.logging.LogUtils;
import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.DodgeAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.particle.HitParticleType;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Все приёмы аддона — настоящие атакующие анимации Epic Fight со своими хитбоксами, оглушением,
 * импактом, звуками и частицами. Ключевые кадры берутся из установленных модов (по приоритету для
 * каждого приёма): Weapons of Miracles, Epic Fight - Nightfall, Battle Arts; если их нет — из самого Epic Fight.
 */
@Mod.EventBusSubscriber(modid = BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MartialMoves {
    private static final Logger LOG = LogUtils.getLogger();

    public enum Role { COMBO, FINISHER, DASH, AIR, ART, FLURRY, BARRAGE }

    public record Info(Role role, StyleKind kind) {}

    // =====================================================================
    //  Источники ключевых кадров
    // =====================================================================
    private static final String EF = "epicfight";
    private static final String BA = "battle_arts";
    private static final String WOM = "wom";
    private static final String EFN = "efn";

    // Epic Fight (есть всегда)
    private static final Clip EF_JAB_L = Clip.of(EF, "biped/combat/fist_auto1", 0.08F, 0.05F, 0.05F, 0.15F, 0.15F, Clip.J.TOOL_L).basis(3.2F);
    private static final Clip EF_JAB_R = Clip.of(EF, "biped/combat/fist_auto2", 0.08F, 0.05F, 0.05F, 0.15F, 0.15F, Clip.J.TOOL_R).basis(3.2F);
    private static final Clip EF_HOOK = Clip.of(EF, "biped/combat/fist_auto3", 0.08F, 0.05F, 0.05F, 0.15F, 0.5F, Clip.J.TOOL_L).basis(3.2F);
    // рывок плечом: на сервере Epic Fight не двигает игрока анимацией, плечо само до цели не достаёт —
    // хитбокс перед бойцом (так рывок попадает и с места, например контрударом дзюдо)
    private static final Clip EF_DASH = Clip.of(EF, "biped/combat/fist_dash", 0.06F, 0.05F, 0.15F, 0.3F, 0.7F, Clip.J.SHOULDER_R).fixed(1.0F).rootHitbox();
    private static final Clip EF_AIR = Clip.of(EF, "biped/combat/fist_airslash", 0.1F, 0.15F, 0.15F, 0.26F, 0.4F, Clip.J.TOOL_R).basis(4.0F);
    private static final Clip EF_KICK = Clip.of(EF, "biped/skill/revelation_normal", 0.05F, 0.0F, 0.05F, 0.1F, 0.35F, Clip.J.LEG_R).fixed(1.0F);
    private static final Clip EF_PALMS = Clip.of(EF, "biped/skill/revelation_twohand", 0.1F, 0.0F, 0.05F, 0.1F, 0.35F, Clip.J.ROOT).fixed(1.0F);
    private static final Clip EF_BARRAGE = Clip.multi(EF, "biped/skill/relentless_combo", 0.05F,
            Clip.win(0.0F, 0.016F, 0.066F, 0.133F, 0.133F, Clip.J.ROOT),
            Clip.win(0.133F, 0.133F, 0.183F, 0.25F, 0.25F, Clip.J.ROOT),
            Clip.win(0.25F, 0.25F, 0.3F, 0.366F, 0.366F, Clip.J.ROOT),
            Clip.win(0.366F, 0.366F, 0.416F, 0.483F, 0.483F, Clip.J.ROOT),
            Clip.win(0.483F, 0.483F, 0.533F, 0.6F, 0.6F, Clip.J.ROOT),
            Clip.win(0.6F, 0.6F, 0.65F, 0.716F, 0.716F, Clip.J.ROOT),
            Clip.win(0.716F, 0.716F, 0.766F, 0.833F, 0.833F, Clip.J.ROOT),
            Clip.win(0.833F, 0.833F, 0.883F, 1.1F, Float.MAX_VALUE, Clip.J.ROOT)).basis(4.0F);
    private static final Clip EF_FLIP_KICK = Clip.multi(EF, "biped/skill/phantom_ascent_forward", 0.05F,
            Clip.win(0.0F, 0.05F, 0.2F, 0.2F, 0.2F, Clip.J.ROOT),
            Clip.win(0.2F, 0.2F, 0.38F, 0.38F, 0.38F, Clip.J.ROOT),
            Clip.win(0.38F, 0.38F, 0.58F, 0.7F, Float.MAX_VALUE, Clip.J.ROOT)).fixed(1.0F);
    private static final Clip EF_STEP_L = Clip.pose(EF, "biped/skill/step_left");
    private static final Clip EF_STEP_R = Clip.pose(EF, "biped/skill/step_right");
    private static final Clip EF_STEP_B = Clip.pose(EF, "biped/skill/step_backward");
    private static final Clip EF_IDLE = Clip.pose(EF, "biped/living/idle");

    // Battle Arts (кулачные стили Iron Lotus и Journeyman)
    private static final Clip BA_JAB_R = Clip.of(BA, "battle_style/unique/iron_lotus/fist/auto1", 0.1F, 0.0F, 0.1F, 0.2F, 0.25F, Clip.J.TOOL_R).basis(3.2F);
    private static final Clip BA_JAB_L = Clip.of(BA, "battle_style/unique/iron_lotus/fist/auto2", 0.1F, 0.0F, 0.1F, 0.2F, 0.25F, Clip.J.TOOL_L).basis(3.2F);
    private static final Clip BA_KICK = Clip.of(BA, "battle_style/unique/iron_lotus/fist/auto3", 0.1F, 0.0F, 0.1F, 0.2F, 1.0F, Clip.J.LEG_R).basis(3.2F);
    private static final Clip BA_LUNGE = Clip.of(BA, "battle_style/unique/iron_lotus/fist/dash_attack", 0.1F, 0.0F, 0.2F, 0.4F, 1.0F, Clip.J.ROOT).basis(5.0F);
    private static final Clip BA_AXE_KICK = Clip.of(BA, "battle_style/unique/iron_lotus/fist/axe_dive", 0.1F, 0.0F, 0.2F, 0.25F, 1.0F, Clip.J.ROOT).basis(5.0F);
    private static final Clip BA_PUNCH_R = Clip.of(BA, "battle_style/novice/journeyman/auto1", 0.2F, 0.0F, 0.05F, 0.15F, 0.5F, Clip.J.HAND_R).fixed(2.0F);
    private static final Clip BA_PUNCH_L = Clip.of(BA, "battle_style/novice/journeyman/auto2", 0.3F, 0.0F, 0.05F, 0.15F, 0.5F, Clip.J.HAND_L).fixed(2.0F);
    private static final Clip BA_UPPERCUT = Clip.of(BA, "battle_style/novice/journeyman/dash", 0.3F, 0.0F, 0.1F, 0.3F, 2.4F, Clip.J.HAND_R).basis(4.0F);
    private static final Clip BA_HAMMER = Clip.of(BA, "battle_style/novice/journeyman/sledgehammer", 0.2F, 0.0F, 0.3F, 0.45F, 1.0F, Clip.J.HAND_R).fixed(1.0F);
    private static final Clip BA_HEAVY = Clip.of(BA, "battle_style/novice/journeyman/suppressing_blow", 0.3F, 0.0F, 0.5F, 0.65F, 1.6F, Clip.J.HAND_R).fixed(1.0F);
    private static final Clip BA_FLAME_POSE = Clip.pose(BA, "battle_style/unique/iron_lotus/fist/flame_counter_pose");

    // Weapons of Miracles (удары ногами, сильный удар, теневые шаги)
    // в ударном окне strong_punch кисть ещё не у цели — хитбокс перед бойцом (проверено на сервере)
    private static final Clip WOM_PUNCH = Clip.of(WOM, "biped/skill/strong_punch", 0.3F, 0.1F, 0.1F, 0.15F, 0.35F, Clip.J.HAND_L).fixed(1.0F).rootHitbox();
    private static final Clip WOM_KICK = Clip.of(WOM, "biped/skill/kick", 0.1F, 0.05F, 0.05F, 0.15F, 0.35F, Clip.J.LEG_R).fixed(1.0F);
    private static final Clip WOM_KICK_1 = Clip.of(WOM, "biped/skill/kick_1", 0.2F, 0.2F, 0.2F, 0.35F, 0.5F, Clip.J.LEG_R).basis(3.2F);
    private static final Clip WOM_KICK_2 = Clip.multi(WOM, "biped/skill/kick_2", 0.2F,
            Clip.win(0.0F, 0.4F, 0.55F, 0.6F, 0.6F, Clip.J.LEG_L),
            Clip.win(0.6F, 0.65F, 0.85F, 1.1F, Float.MAX_VALUE, Clip.J.LEG_R)).basis(3.2F);
    private static final Clip WOM_KICK_3 = Clip.of(WOM, "biped/skill/kick_3", 0.2F, 0.45F, 0.45F, 0.55F, 0.9F, Clip.J.LEG_L).basis(3.2F);
    private static final Clip WOM_SHOULDER = Clip.multi(WOM, "biped/skill/ravanger_charge", 0.25F,
            Clip.win(0.0F, 0.15F, 0.45F, 0.85F, Float.MAX_VALUE, Clip.J.ROOT)).fixed(1.0F);
    private static final Clip WOM_AVATAR = Clip.pose(WOM, "biped/skill/biped_avatar_of_might");
    private static final Clip WOM_STEP_L = Clip.pose(WOM, "biped/skill/shadow_step_left");
    private static final Clip WOM_STEP_R = Clip.pose(WOM, "biped/skill/shadow_step_right");
    private static final Clip WOM_STEP_B = Clip.pose(WOM, "biped/skill/shadow_step_backward");

    // Epic Fight - Nightfall (шаги уклонения)
    private static final Clip EFN_STEP_L = Clip.pose(EFN, "biped/dodge/step_l");
    private static final Clip EFN_STEP_R = Clip.pose(EFN, "biped/dodge/step_r");
    private static final Clip EFN_STEP_B = Clip.pose(EFN, "biped/dodge/step_b");

    // =====================================================================
    //  Параметры попаданий
    // =====================================================================
    private record Hit(float damage, StunType stun, float impact, float armorNegation, int maxStrikes,
                       Supplier<SoundEvent> hitSound, Supplier<SoundEvent> swingSound,
                       RegistryObject<HitParticleType> particle) {}

    private static final Hit JAB = new Hit(1.0F, StunType.SHORT, -1F, 0F, 0, EpicFightSounds.BLUNT_HIT, EpicFightSounds.WHOOSH_SMALL, EpicFightParticles.HIT_BLUNT);
    private static final Hit KICK = new Hit(1.15F, StunType.SHORT, -1F, 0F, 0, EpicFightSounds.BLUNT_HIT, EpicFightSounds.WHOOSH, EpicFightParticles.HIT_BLUNT);
    private static final Hit FINISH = new Hit(1.7F, StunType.LONG, 5.0F, 0F, 0, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);
    private static final Hit DASH = new Hit(1.4F, StunType.LONG, 4.0F, 0F, 2, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.HIT_BLUNT);
    private static final Hit AIR = new Hit(1.5F, StunType.KNOCKDOWN, 5.0F, 0F, 2, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);

    private static final Hit UPPERCUT = new Hit(3.5F, StunType.KNOCKDOWN, 8.0F, 10F, 1, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);
    private static final Hit KIAI = new Hit(2.6F, StunType.NEUTRALIZE, 6.0F, 20F, 6, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);
    private static final Hit KNEE = new Hit(3.5F, StunType.KNOCKDOWN, 12.0F, 10F, 2, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);
    private static final Hit CHI = new Hit(3.0F, StunType.LONG, 30.0F, 40F, 5, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);
    private static final Hit SPIN = new Hit(1.3F, StunType.HOLD, 2.0F, 0F, 6, EpicFightSounds.BLUNT_HIT, EpicFightSounds.WHOOSH, EpicFightParticles.HIT_BLUNT);
    private static final Hit SPIN_FINAL = new Hit(2.2F, StunType.KNOCKDOWN, 10.0F, 0F, 6, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);
    private static final Hit THROW = new Hit(3.0F, StunType.KNOCKDOWN, 3.0F, 20F, 1, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.HIT_BLUNT);
    private static final Hit BARRAGE = new Hit(0.7F, StunType.HOLD, 2.0F, 0F, 2, EpicFightSounds.BLUNT_HIT, EpicFightSounds.WHOOSH_SMALL, EpicFightParticles.HIT_BLUNT);
    /** Серия при зажатой атаке: слабые быстрые удары, держат врага в оглушении. */
    private static final Hit RUSH = new Hit(0.6F, StunType.HOLD, 1.0F, 0F, 2, EpicFightSounds.BLUNT_HIT, EpicFightSounds.WHOOSH_SMALL, EpicFightParticles.HIT_BLUNT);
    private static final Hit BARRAGE_FINAL = new Hit(2.5F, StunType.KNOCKDOWN, 16.0F, 0F, 2, EpicFightSounds.BLUNT_HIT_HARD, EpicFightSounds.WHOOSH_BIG, EpicFightParticles.AIR_BURST);

    // =====================================================================
    //  Зарегистрированные приёмы
    // =====================================================================
    private static final Map<ResourceLocation, Info> INFO = new HashMap<>();
    private static final Map<StyleKind, List<AnimationManager.AnimationAccessor<? extends AttackAnimation>>> COMBOS = new EnumMap<>(StyleKind.class);
    private static final Map<StyleKind, AnimationManager.AnimationAccessor<? extends AttackAnimation>> ARTS = new EnumMap<>(StyleKind.class);
    private static final Map<StyleKind, AnimationManager.AnimationAccessor<? extends AttackAnimation>> COUNTERS = new EnumMap<>(StyleKind.class);
    private static final Map<StyleKind, List<AnimationManager.AnimationAccessor<? extends AttackAnimation>>> BARRAGES = new EnumMap<>(StyleKind.class);
    private static final Map<String, String> CHOSEN = new HashMap<>();

    public static AnimationManager.AnimationAccessor<AttackAnimation> FLURRY;
    public static AnimationManager.AnimationAccessor<DodgeAnimation> DODGE_LEFT;
    public static AnimationManager.AnimationAccessor<DodgeAnimation> DODGE_RIGHT;
    public static AnimationManager.AnimationAccessor<DodgeAnimation> DODGE_BACK;
    public static AnimationManager.AnimationAccessor<ActionAnimation> AWAKEN;
    private static volatile boolean awakenAvailable = false;

    private MartialMoves() {}

    @SubscribeEvent
    public static void onAnimationRegistry(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(BAMartial.MODID, MartialMoves::build);
    }

    private static void build(AnimationManager.AnimationBuilder b) {
        INFO.clear();
        COMBOS.clear();
        ARTS.clear();
        COUNTERS.clear();
        BARRAGES.clear();

        // ---------------- Бокс: джеб — кросс — хук — прямой ----------------
        combo(b, StyleKind.BOXING,
                hit(b, "boxing/jab", StyleKind.BOXING, Role.COMBO, JAB, BA_JAB_L, EF_JAB_L),
                hit(b, "boxing/cross", StyleKind.BOXING, Role.COMBO, JAB, BA_JAB_R, EF_JAB_R),
                hit(b, "boxing/hook", StyleKind.BOXING, Role.COMBO, JAB, BA_PUNCH_L, EF_HOOK),
                hit(b, "boxing/power_straight", StyleKind.BOXING, Role.FINISHER, FINISH, BA_PUNCH_R, WOM_PUNCH, EF_JAB_R),
                dash(b, "boxing/dash", StyleKind.BOXING, BA_LUNGE, EF_DASH),
                air(b, "boxing/air", StyleKind.BOXING, BA_HAMMER, EF_AIR));
        art(b, "art/crushing_uppercut", StyleKind.BOXING, MartialColliders.HEAVY, UPPERCUT, UPPERCUT, 1.25F, BA_UPPERCUT, EF_DASH);

        // ---------------- Каратэ: цуки — цуки — маэ-гери — гяку-цуки ----------------
        combo(b, StyleKind.KARATE,
                hit(b, "karate/oi_zuki", StyleKind.KARATE, Role.COMBO, JAB, BA_JAB_R, EF_JAB_R),
                hit(b, "karate/kizami_zuki", StyleKind.KARATE, Role.COMBO, JAB, BA_JAB_L, EF_JAB_L),
                hit(b, "karate/mae_geri", StyleKind.KARATE, Role.COMBO, KICK, WOM_KICK_1, BA_KICK, EF_KICK),
                hit(b, "karate/gyaku_zuki", StyleKind.KARATE, Role.FINISHER, FINISH, BA_PUNCH_R, WOM_PUNCH, EF_JAB_R),
                dash(b, "karate/dash", StyleKind.KARATE, BA_LUNGE, EF_DASH),
                air(b, "karate/air", StyleKind.KARATE, BA_AXE_KICK, EF_AIR));
        art(b, "art/kiai", StyleKind.KARATE, MartialColliders.WAVE, KIAI, KIAI, 1.2F, WOM_PUNCH, BA_HEAVY, EF_PALMS);

        // ---------------- Муай-тай: джеб — кросс — двойной удар ногами — лоу-кик ----------------
        combo(b, StyleKind.MUAY_THAI,
                hit(b, "muay_thai/jab", StyleKind.MUAY_THAI, Role.COMBO, JAB, BA_JAB_L, EF_JAB_L),
                hit(b, "muay_thai/cross", StyleKind.MUAY_THAI, Role.COMBO, JAB, BA_PUNCH_R, EF_JAB_R),
                hit(b, "muay_thai/teep", StyleKind.MUAY_THAI, Role.COMBO, KICK, WOM_KICK_2, BA_KICK, EF_KICK),
                hit(b, "muay_thai/roundhouse", StyleKind.MUAY_THAI, Role.FINISHER, FINISH, WOM_KICK_3, BA_KICK, EF_KICK),
                dash(b, "muay_thai/dash", StyleKind.MUAY_THAI, BA_LUNGE, EF_DASH),
                air(b, "muay_thai/air", StyleKind.MUAY_THAI, BA_AXE_KICK, EF_AIR));
        art(b, "art/flying_knee", StyleKind.MUAY_THAI, MartialColliders.LUNGE, KNEE, KNEE, 1.3F, BA_LUNGE, WOM_KICK, EF_DASH);

        // ---------------- Кунг-фу: ладонь — ладонь — удар ногой — двойная ладонь ----------------
        combo(b, StyleKind.KUNG_FU,
                hit(b, "kung_fu/palm_right", StyleKind.KUNG_FU, Role.COMBO, JAB, BA_JAB_R, EF_JAB_R),
                hit(b, "kung_fu/palm_left", StyleKind.KUNG_FU, Role.COMBO, JAB, BA_JAB_L, EF_JAB_L),
                hit(b, "kung_fu/crane_kick", StyleKind.KUNG_FU, Role.COMBO, KICK, BA_KICK, WOM_KICK_1, EF_KICK),
                hit(b, "kung_fu/twin_palms", StyleKind.KUNG_FU, Role.FINISHER, FINISH, EF_PALMS),
                dash(b, "kung_fu/dash", StyleKind.KUNG_FU, BA_LUNGE, EF_DASH),
                air(b, "kung_fu/air", StyleKind.KUNG_FU, BA_AXE_KICK, EF_AIR));
        art(b, "art/chi_palm", StyleKind.KUNG_FU, MartialColliders.WAVE, CHI, CHI, 1.0F, EF_PALMS);

        // ---------------- Тхэквондо: только ноги ----------------
        combo(b, StyleKind.TAEKWONDO,
                hit(b, "taekwondo/ap_chagi", StyleKind.TAEKWONDO, Role.COMBO, KICK, WOM_KICK_1, BA_KICK, EF_KICK),
                hit(b, "taekwondo/double_kick", StyleKind.TAEKWONDO, Role.COMBO, KICK, WOM_KICK_2, BA_JAB_L, EF_JAB_L),
                hit(b, "taekwondo/dollyo_chagi", StyleKind.TAEKWONDO, Role.COMBO, KICK, WOM_KICK, BA_KICK, EF_KICK),
                hit(b, "taekwondo/dwi_chagi", StyleKind.TAEKWONDO, Role.FINISHER, FINISH, WOM_KICK_3, BA_KICK, EF_KICK),
                dash(b, "taekwondo/dash", StyleKind.TAEKWONDO, BA_LUNGE, EF_DASH),
                air(b, "taekwondo/naeryeo_chagi", StyleKind.TAEKWONDO, BA_AXE_KICK, EF_AIR));
        art(b, "art/tornado_kick", StyleKind.TAEKWONDO, MartialColliders.SPIN, SPIN, SPIN_FINAL, 1.0F, EF_FLIP_KICK);

        // ---------------- Дзюдо: толчки, захват, бросок ----------------
        combo(b, StyleKind.JUDO,
                hit(b, "judo/push_right", StyleKind.JUDO, Role.COMBO, JAB, BA_PUNCH_R, EF_JAB_R),
                hit(b, "judo/push_left", StyleKind.JUDO, Role.COMBO, JAB, BA_PUNCH_L, EF_JAB_L),
                hit(b, "judo/grip_strike", StyleKind.JUDO, Role.COMBO, JAB, BA_JAB_R, EF_HOOK),
                hit(b, "judo/hip_check", StyleKind.JUDO, Role.FINISHER, FINISH, BA_UPPERCUT, WOM_SHOULDER, EF_DASH),
                dash(b, "judo/tackle", StyleKind.JUDO, WOM_SHOULDER, BA_LUNGE, EF_DASH),
                air(b, "judo/air", StyleKind.JUDO, BA_HAMMER, EF_AIR));
        art(b, "art/ippon", StyleKind.JUDO, MartialColliders.HEAVY, THROW, THROW, 1.2F, WOM_SHOULDER, BA_HEAVY, EF_DASH);

        // ---------------- Серия ударов при зажатой атаке ----------------
        // руки — хитбокс перед бойцом; ноги — на самой ноге (у разворотных ударов корень поворачивается)
        rush(b, "rush/boxing_left", StyleKind.BOXING, 1.6F, true, BA_JAB_L, EF_JAB_L);
        rush(b, "rush/boxing_right", StyleKind.BOXING, 1.6F, true, BA_JAB_R, EF_JAB_R);
        rush(b, "rush/karate_right", StyleKind.KARATE, 1.5F, true, BA_JAB_R, EF_JAB_R);
        rush(b, "rush/karate_left", StyleKind.KARATE, 1.5F, true, BA_JAB_L, EF_JAB_L);
        rush(b, "rush/muay_thai_left", StyleKind.MUAY_THAI, 1.5F, true, BA_JAB_L, EF_JAB_L);
        rush(b, "rush/muay_thai_right", StyleKind.MUAY_THAI, 1.5F, true, BA_PUNCH_R, EF_JAB_R);
        rush(b, "rush/kung_fu_right", StyleKind.KUNG_FU, 1.8F, true, BA_JAB_R, EF_JAB_R);
        rush(b, "rush/kung_fu_left", StyleKind.KUNG_FU, 1.8F, true, BA_JAB_L, EF_JAB_L);
        rush(b, "rush/taekwondo_kick_a", StyleKind.TAEKWONDO, 1.6F, false, WOM_KICK_1, BA_KICK, EF_KICK);
        rush(b, "rush/taekwondo_kick_b", StyleKind.TAEKWONDO, 1.6F, false, WOM_KICK, BA_KICK, EF_KICK);
        rush(b, "rush/judo_right", StyleKind.JUDO, 1.5F, true, BA_PUNCH_R, EF_JAB_R);
        rush(b, "rush/judo_left", StyleKind.JUDO, 1.5F, true, BA_PUNCH_L, EF_JAB_L);

        // ---------------- Шквал ударов (все стили) ----------------
        FLURRY = b.nextAccessor("art/flurry_of_blows", acc -> {
            Clip c = pick("art/flurry_of_blows", EF_BARRAGE);
            AttackAnimation a = new AttackAnimation(transition(c), acc, Armatures.BIPED, phases(c, MartialColliders.BARRAGE, true));
            hits(a, BARRAGE, BARRAGE_FINAL);
            speed(a, c);
            a.setResourceLocation(c.namespace, c.path);
            return a;
        });
        INFO.put(FLURRY.registryName(), new Info(Role.FLURRY, null));

        // ---------------- Ультра инстинкт: уклонения и пробуждение ----------------
        DODGE_LEFT = b.nextAccessor("ultra_instinct/dodge_left", acc -> dodge(acc, "ultra_instinct/dodge_left", WOM_STEP_L, EFN_STEP_L, EF_STEP_L));
        DODGE_RIGHT = b.nextAccessor("ultra_instinct/dodge_right", acc -> dodge(acc, "ultra_instinct/dodge_right", WOM_STEP_R, EFN_STEP_R, EF_STEP_R));
        DODGE_BACK = b.nextAccessor("ultra_instinct/dodge_back", acc -> dodge(acc, "ultra_instinct/dodge_back", WOM_STEP_B, EFN_STEP_B, EF_STEP_B));
        AWAKEN = b.nextAccessor("ultra_instinct/awaken", acc -> {
            Clip c = pickOptional("ultra_instinct/awaken", WOM_AVATAR, BA_FLAME_POSE);
            awakenAvailable = c != null;
            if (c == null) c = EF_IDLE;
            ActionAnimation a = new ActionAnimation(0.15F, acc, Armatures.BIPED);
            a.setResourceLocation(c.namespace, c.path);
            return a;
        });
    }

    // =====================================================================
    //  Фабрики
    // =====================================================================

    private static AnimationManager.AnimationAccessor<BasicAttackAnimation> hit(AnimationManager.AnimationBuilder b, String id,
            StyleKind kind, Role role, Hit h, Clip... candidates) {
        AnimationManager.AnimationAccessor<BasicAttackAnimation> accessor = b.nextAccessor(id, acc -> {
            Clip c = pick(id, candidates);
            BasicAttackAnimation a = new BasicAttackAnimation(transition(c), acc, Armatures.BIPED, phases(c, MartialColliders.STRIKE, false));
            hits(a, h, h);
            speed(a, c);
            a.setResourceLocation(c.namespace, c.path);
            return a;
        });
        INFO.put(accessor.registryName(), new Info(role, kind));
        if (role == Role.FINISHER) COUNTERS.put(kind, accessor);
        return accessor;
    }

    private static AnimationManager.AnimationAccessor<DashAttackAnimation> dash(AnimationManager.AnimationBuilder b, String id,
            StyleKind kind, Clip... candidates) {
        AnimationManager.AnimationAccessor<DashAttackAnimation> accessor = b.nextAccessor(id, acc -> {
            Clip c = pick(id, candidates);
            DashAttackAnimation a = new DashAttackAnimation(transition(c), acc, Armatures.BIPED, phases(c, MartialColliders.LUNGE, false));
            hits(a, DASH, DASH);
            speed(a, c);
            a.setResourceLocation(c.namespace, c.path);
            return a;
        });
        INFO.put(accessor.registryName(), new Info(Role.DASH, kind));
        return accessor;
    }

    private static AnimationManager.AnimationAccessor<AirSlashAnimation> air(AnimationManager.AnimationBuilder b, String id,
            StyleKind kind, Clip... candidates) {
        AnimationManager.AnimationAccessor<AirSlashAnimation> accessor = b.nextAccessor(id, acc -> {
            Clip c = pick(id, candidates);
            AirSlashAnimation a = new AirSlashAnimation(transition(c), acc, Armatures.BIPED, phases(c, MartialColliders.DIVE, false));
            hits(a, AIR, AIR);
            speed(a, c);
            a.setResourceLocation(c.namespace, c.path);
            return a;
        });
        INFO.put(accessor.registryName(), new Info(Role.AIR, kind));
        return accessor;
    }

    /** Боевое искусство стиля: хитбокс техники фиксирован перед бойцом, скорость постоянная. */
    private static void art(AnimationManager.AnimationBuilder b, String id, StyleKind kind, Collider collider,
            Hit h, Hit last, float playSpeed, Clip... candidates) {
        AnimationManager.AnimationAccessor<AttackAnimation> accessor = b.nextAccessor(id, acc -> {
            Clip c = pick(id, candidates).withFixedSpeed(playSpeed);
            AttackAnimation a = new AttackAnimation(transition(c), acc, Armatures.BIPED, phases(c, collider, true));
            hits(a, h, last);
            speed(a, c);
            a.setResourceLocation(c.namespace, c.path);
            return a;
        });
        INFO.put(accessor.registryName(), new Info(Role.ART, kind));
        ARTS.put(kind, accessor);
    }

    /**
     * Удар серии: высокая постоянная скорость (следующий удар прерывает предыдущий).
     * rootHitbox — хитбокс перед бойцом, иначе на суставе удара (рука/нога).
     */
    private static void rush(AnimationManager.AnimationBuilder b, String id, StyleKind kind, float playSpeed, boolean rootHitbox,
            Clip... candidates) {
        AnimationManager.AnimationAccessor<AttackAnimation> accessor = b.nextAccessor(id, acc -> {
            Clip c = pick(id, candidates).withFixedSpeed(playSpeed);
            AttackAnimation a = new AttackAnimation(transition(c), acc, Armatures.BIPED, phases(c, MartialColliders.BARRAGE, rootHitbox));
            hits(a, RUSH, RUSH);
            speed(a, c);
            a.setResourceLocation(c.namespace, c.path);
            return a;
        });
        INFO.put(accessor.registryName(), new Info(Role.BARRAGE, kind));
        BARRAGES.computeIfAbsent(kind, k -> new ArrayList<>()).add(accessor);
    }

    private static DodgeAnimation dodge(AnimationManager.AnimationAccessor<DodgeAnimation> acc, String id, Clip... candidates) {
        Clip c = pick(id, candidates);
        DodgeAnimation a = new DodgeAnimation(0.05F, 0.2F, acc, 0.6F, 1.65F, Armatures.BIPED);
        a.setResourceLocation(c.namespace, c.path);
        return a;
    }

    /**
     * Связка стиля для BasicAttack Epic Fight: [удары..., рывок, в прыжке]. Удары повторены трижды,
     * чтобы счётчик комбо, оставшийся от другого оружия, никогда не выходил за пределы списка.
     */
    @SafeVarargs
    private static void combo(AnimationManager.AnimationBuilder b, StyleKind kind,
            AnimationManager.AnimationAccessor<? extends AttackAnimation>... moves) {
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> hits = new ArrayList<>();
        for (int i = 0; i < moves.length - 2; i++) hits.add(moves[i]);
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> list = new ArrayList<>();
        for (int r = 0; r < 3; r++) list.addAll(hits);
        list.add(moves[moves.length - 2]);
        list.add(moves[moves.length - 1]);
        COMBOS.put(kind, Collections.unmodifiableList(list));
    }

    private static AttackAnimation.Phase[] phases(Clip c, Collider rootCollider, boolean forceRoot) {
        HumanoidArmature arm = Armatures.BIPED.get();
        AttackAnimation.Phase[] out = new AttackAnimation.Phase[c.wins.size()];
        for (int i = 0; i < out.length; i++) {
            Clip.Win w = c.wins.get(i);
            Clip.J joint = forceRoot || c.rootHit ? Clip.J.ROOT : w.joint();
            Collider collider = joint == Clip.J.ROOT ? rootCollider : (joint.isLeg() ? MartialColliders.KICK : MartialColliders.FIST);
            out[i] = new AttackAnimation.Phase(w.start(), w.antic(), w.preDelay(), w.contact(), w.recovery(), w.end(), joint.get(arm), collider);
        }
        return out;
    }

    private static void hits(AttackAnimation a, Hit h, Hit last) {
        int n = a.phases.length;
        for (int i = 0; i < n; i++) apply(a, i, i == n - 1 ? last : h);
    }

    private static void apply(AttackAnimation a, int phase, Hit h) {
        a.addProperty(AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(h.damage()), phase);
        if (h.stun() != null) a.addProperty(AnimationProperty.AttackPhaseProperty.STUN_TYPE, h.stun(), phase);
        if (h.impact() >= 0) a.addProperty(AnimationProperty.AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.setter(h.impact()), phase);
        if (h.armorNegation() > 0) a.addProperty(AnimationProperty.AttackPhaseProperty.ARMOR_NEGATION_MODIFIER, ValueModifier.adder(h.armorNegation()), phase);
        if (h.maxStrikes() > 1) a.addProperty(AnimationProperty.AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.setter(h.maxStrikes()), phase);
        a.addProperty(AnimationProperty.AttackPhaseProperty.HIT_SOUND, h.hitSound().get(), phase);
        a.addProperty(AnimationProperty.AttackPhaseProperty.SWING_SOUND, h.swingSound().get(), phase);
        a.addProperty(AnimationProperty.AttackPhaseProperty.PARTICLE, h.particle(), phase);
    }

    /**
     * Время перехода к приёму. У приёмов с постоянной скоростью — целое число тиков: Epic Fight считает
     * состояние перехода по «сырой» скорости (длина клипа × скорость атаки) и не замечает начало удара,
     * если ударное окно открывается внутри перехода, — тогда цель, задетая прошлым ударом, не получает урона
     * (так не попадал второй удар ногой в серии тхэквондо). При целом числе тиков переход кончается ровно
     * к началу кадров приёма, и окно всегда открывается уже в самом приёме.
     */
    private static float transition(Clip c) {
        if (c.basis || c.speed <= 0) return c.convert;
        float perTick = c.speed * 0.05F;
        int ticks = Math.max(1, Math.round(c.convert / perTick));
        return ticks * perTick - 1.0E-4F;
    }

    private static void speed(StaticAnimation a, Clip c) {
        if (c.basis) {
            a.addProperty(AnimationProperty.AttackAnimationProperty.BASIS_ATTACK_SPEED, c.speed);
        } else {
            final float s = c.speed;
            a.addProperty(AnimationProperty.StaticAnimationProperty.PLAY_SPEED_MODIFIER, (self, entitypatch, playSpeed, prevElapsed, elapsed) -> s);
        }
    }

    // =====================================================================
    //  Выбор источника кадров
    // =====================================================================

    /** Первый доступный источник; последний в списке (Epic Fight) — запасной. */
    private static Clip pick(String id, Clip... candidates) {
        Clip c = pickOptional(id, candidates);
        if (c == null) {
            c = candidates[candidates.length - 1];
            CHOSEN.put(id, c + " (fallback)");
        }
        return c;
    }

    private static Clip pickOptional(String id, Clip... candidates) {
        boolean otherMods = MartialConfig.get(MartialConfig.OTHER_MOD_ANIMATIONS, true);
        for (Clip c : candidates) {
            if (!EF.equals(c.namespace)) {
                if (!ModList.get().isLoaded(c.namespace)) continue;
                if (!otherMods && !BA.equals(c.namespace)) continue;
            }
            if (!exists(c)) continue;
            CHOSEN.put(id, c.toString());
            LOG.debug("[ba_martialarts] {} <- {}", id, c);
            return c;
        }
        return null;
    }

    /**
     * Есть ли файл кадров в jar-файле мода. Проверяем сам файл мода, а не ресурсы: на выделенном сервере
     * Epic Fight читает анимации прямо из jar, и выбор должен совпадать на сервере и у клиентов.
     */
    private static boolean exists(Clip c) {
        try {
            IModFileInfo info = ModList.get().getModFileById(c.namespace);
            if (info != null) {
                String[] segments = ("assets/" + c.namespace + "/animmodels/animations/" + c.path + ".json").split("/");
                Path path = info.getFile().findResource(segments);
                return Files.exists(path);
            }
        } catch (Throwable ignored) {
        }
        try {
            ResourceManager rm = AnimationManager.getAnimationResourceManager();
            return rm == null || rm.getResource(c.file()).isPresent();
        } catch (Throwable t) {
            return EF.equals(c.namespace);
        }
    }

    // =====================================================================
    //  Доступ для навыков и миксина
    // =====================================================================

    /** Связка ударов стиля для голых рук или null (обычные кулаки Epic Fight). */
    public static List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> comboFor(PlayerPatch<?> patch) {
        try {
            if (patch == null || patch.getOriginal() == null || !patch.getOriginal().getMainHandItem().isEmpty()) return null;
            SkillContainer c = patch.getSkill(BattleArtsSkillSlots.BATTLE_STYLE);
            if (c == null || !(c.getSkill() instanceof MartialBattleStyle style)) return null;
            return COMBOS.get(style.getKind());
        } catch (Throwable t) {
            return null;
        }
    }

    public static AnimationManager.AnimationAccessor<? extends AttackAnimation> art(StyleKind kind) {
        return ARTS.get(kind);
    }

    /** Контрудар стиля (добивающий удар связки). */
    public static AnimationManager.AnimationAccessor<? extends AttackAnimation> counter(StyleKind kind) {
        return COUNTERS.get(kind);
    }

    /** Удар серии при зажатой атаке (чередуются). */
    public static AnimationManager.AnimationAccessor<? extends AttackAnimation> rush(StyleKind kind, int index) {
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> list = BARRAGES.get(kind);
        if (list == null || list.isEmpty()) return null;
        return list.get(Math.floorMod(index, list.size()));
    }

    /** Пауза между ударами серии, тиков. */
    public static int rushInterval(StyleKind kind) {
        return switch (kind) {
            case KUNG_FU -> 3;
            case TAEKWONDO -> 6;
            case MUAY_THAI, JUDO -> 5;
            default -> 4;
        };
    }

    public static boolean awakenAvailable() {
        return awakenAvailable && AWAKEN != null;
    }

    /** Что это за приём (по анимации из источника урона Epic Fight) или null. */
    public static Info info(AssetAccessor<?> animation) {
        if (animation == null) return null;
        return INFO.get(animation.registryName());
    }

    /** Боевая стойка Battle Arts для голых рук (если мод установлен). */
    public static AnimationManager.AnimationAccessor<? extends StaticAnimation> stance(StyleKind kind) {
        if (!ModList.get().isLoaded(BA)) return null;
        String path = switch (kind) {
            case KUNG_FU, TAEKWONDO, KARATE -> "battle_style/unique/iron_lotus/fist/idle";
            default -> "battle_style/novice/journeyman/idle";
        };
        try {
            return AnimationManager.byKey(new ResourceLocation(BA, path));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Какие кадры выбраны для приёмов (для отладки). */
    public static Map<String, String> chosenSources() {
        return Collections.unmodifiableMap(CHOSEN);
    }
}

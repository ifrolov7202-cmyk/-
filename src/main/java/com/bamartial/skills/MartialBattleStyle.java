package com.bamartial.skills;

import com.bamartial.anim.MartialMoves;
import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.forixaim.battle_arts_api.battle_arts_skills.CoreAPIDataKeys;
import net.forixaim.battle_arts_api.battle_arts_skills.battle_style.BattleStyle;
import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPChangeSkill;
import yesman.epicfight.network.server.SPSetRemotePlayerSkill;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillSlot;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;

import java.util.UUID;

/**
 * Рукопашный стиль боя Battle Arts. Пока стиль надет и руки пусты:
 *  - обычная атака Epic Fight — собственная связка ударов стиля (4 удара, рывок, удар в прыжке);
 *    4-й удар — добивающий, с долгим оглушением; удары копят шкалу метра Battle Arts;
 *  - в слот боевого искусства (Z) ставится техника стиля, в слот взрывного (C) — «Шквал ударов»,
 *    в слот абсолютного (V) — «Ультра инстинкт»;
 *  - с Battle Arts — боевая стойка вместо обычной.
 */
public class MartialBattleStyle extends BattleStyle {
    private static final UUID EVENT_UUID = UUID.fromString("0b6c8d2e-5a1f-4b7e-9c3d-2f8e1a6b7c01");

    private final StyleKind kind;

    public MartialBattleStyle(Builder<?> builder, StyleKind kind) {
        super(builder);
        this.kind = kind;
    }

    public StyleKind getKind() {
        return kind;
    }

    @Override
    public void onInitiate(SkillContainer container) {
        // боевая стойка Battle Arts для голых рук (её применяет Battle Arts API)
        if (this.unarmedLivingMotions.isEmpty()) {
            AnimationManager.AnimationAccessor<? extends StaticAnimation> stance = MartialMoves.stance(kind);
            if (stance != null) this.unarmedLivingMotions.put(LivingMotions.IDLE, stance);
        }
        super.onInitiate(container);
        PlayerPatch<?> patch = container.getExecutor();

        if (patch instanceof ServerPlayerPatch server) {
            changeSkill(server, BattleArtsSkillSlots.COMBAT_ART, MartialSkills.COMBAT_ARTS.get(kind));
            changeSkill(server, BattleArtsSkillSlots.BURST_ART, MartialSkills.FLURRY);
            changeSkill(server, BattleArtsSkillSlots.ULTIMATE_ART, MartialSkills.ULTRA_INSTINCT);
            MartialAdvancements.award(server.getOriginal(), "learn_style");
        }

        PlayerEventListener listener = patch.getEventListener();
        // сильнее удары голой рукой
        listener.addEventListener(PlayerEventListener.EventType.MODIFY_DAMAGE_EVENT, EVENT_UUID, event -> {
            if (event.getPlayerPatch().getOriginal().getMainHandItem().isEmpty()) {
                event.attachValueModifier(ValueModifier.adder(kind.unarmedBonus));
            }
        });
        // попадания приёмов: метр и особые эффекты техник
        listener.addEventListener(PlayerEventListener.EventType.DEAL_DAMAGE_EVENT_HURT, EVENT_UUID, event -> {
            ServerPlayer player = event.getPlayerPatch().getOriginal();
            if (!player.getMainHandItem().isEmpty()) return;
            float meter = Techniques.onHit(player, event.getTarget(), event.getDamageSource(), event.getForgeEvent());
            addMeter(container, meter);
        });
        // Ультра инстинкт: уклонение шагом Epic Fight
        listener.addEventListener(PlayerEventListener.EventType.TAKE_DAMAGE_EVENT_ATTACK, EVENT_UUID, event ->
                UltraInstinct.tryDodge(event.getPlayerPatch(), event, kind));
        // дзюдо: контрбросок после уклонения
        listener.addEventListener(PlayerEventListener.EventType.DODGE_SUCCESS_EVENT, EVENT_UUID, event -> {
            ServerPlayer player = event.getPlayerPatch().getOriginal();
            if (kind == StyleKind.JUDO && !UltraInstinct.isActive(player) && player.getMainHandItem().isEmpty()) {
                Techniques.judoCounter(player, event.getDamageSource().getEntity());
                addMeter(container, 30);
            }
        });
    }

    @Override
    public void onRemoved(SkillContainer container) {
        PlayerPatch<?> patch = container.getExecutor();
        PlayerEventListener listener = patch.getEventListener();
        listener.removeListener(PlayerEventListener.EventType.MODIFY_DAMAGE_EVENT, EVENT_UUID);
        listener.removeListener(PlayerEventListener.EventType.DEAL_DAMAGE_EVENT_HURT, EVENT_UUID);
        listener.removeListener(PlayerEventListener.EventType.TAKE_DAMAGE_EVENT_ATTACK, EVENT_UUID);
        listener.removeListener(PlayerEventListener.EventType.DODGE_SUCCESS_EVENT, EVENT_UUID);

        if (patch instanceof ServerPlayerPatch server) {
            changeSkill(server, BattleArtsSkillSlots.COMBAT_ART, null);
            changeSkill(server, BattleArtsSkillSlots.BURST_ART, null);
            changeSkill(server, BattleArtsSkillSlots.ULTIMATE_ART, null);
            UltraInstinct.forceEnd(server.getOriginal(), false);
        }
        super.onRemoved(container);
    }

    /** Добавить метр Battle Arts в контейнер нашего стиля (только сервер). */
    public static void addMeter(SkillContainer container, float amount) {
        if (container == null || amount <= 0 || container.getExecutor().isLogicalClient()) return;
        if (!(container.getSkill() instanceof MartialBattleStyle style)) return;
        if (!container.getDataManager().hasData(CoreAPIDataKeys.METER_FILL.get())) return;
        float max = Math.max(1, style.getMaxMeter()) * 100f;
        container.getDataManager().setDataSyncF(CoreAPIDataKeys.METER_FILL.get(), v -> Math.min(max, v + amount));
    }

    /** Поставить навык в слот игрока и сообщить клиентам (как в Battle Arts). */
    static void changeSkill(ServerPlayerPatch patch, SkillSlot slot, Skill skill) {
        if (skill != null && slot.category() != skill.getCategory()) return;
        ServerPlayer player = patch.getOriginal();
        patch.getSkill(slot).setSkill(skill);
        if (player.connection == null) return;
        EpicFightNetworkManager.sendToPlayer(new SPChangeSkill(slot, player.getId(), skill), player);
        EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(new SPSetRemotePlayerSkill(player.getId(), slot, skill), player);
    }
}

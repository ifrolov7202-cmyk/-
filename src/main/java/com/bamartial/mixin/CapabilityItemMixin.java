package com.bamartial.mixin;

import com.bamartial.anim.MartialMoves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.List;

/**
 * Голые руки в рукопашном стиле: вместо стандартной связки кулаков Epic Fight —
 * собственная связка ударов стиля (BasicAttack Epic Fight сам ведёт счётчик комбо,
 * рывковую и воздушную атаки).
 */
@Mixin(value = CapabilityItem.class, remap = false)
public abstract class CapabilityItemMixin {
    @Inject(method = "getAutoAttackMotion", at = @At("HEAD"), cancellable = true, remap = false)
    private void ba_martialarts$styleCombo(PlayerPatch<?> playerpatch,
            CallbackInfoReturnable<List<AnimationManager.AnimationAccessor<? extends AttackAnimation>>> cir) {
        if ((Object) this != CapabilityItem.EMPTY) return;
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combo = MartialMoves.comboFor(playerpatch);
        if (combo != null) cir.setReturnValue(combo);
    }
}

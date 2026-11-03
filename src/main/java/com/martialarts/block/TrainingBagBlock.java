package com.martialarts.block;

import com.martialarts.data.MartialCapability;
import com.martialarts.skill.Skills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Боксёрская груша: удары пустой рукой дают опыт мастерства и немного Ци.
 * Голой рукой грушу не сломать — только инструментом (лучше топором).
 */
public class TrainingBagBlock extends Block {
    public TrainingBagBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && com.martialarts.skill.Fighting.bareHanded(sp)) {
            MartialCapability.get(sp).ifPresent(d -> {
                long now = level.getGameTime();
                if (now - d.lastTrainTick < 10) return;
                d.lastTrainTick = now;
                d.chi = Math.min(d.maxChi(), d.chi + 2);
                Skills.addXp(sp, d, 1);
                sp.serverLevel().sendParticles(ParticleTypes.CRIT,
                        pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.35, 0.3, 0.35, 0.2);
                level.playSound(null, pos, SoundEvents.WOOL_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.7f);
                Skills.sound(sp, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1.1f);
            });
        }
        super.attack(state, level, pos, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
        if (com.martialarts.skill.Fighting.bareHanded(player)) return 0.0f;
        return super.getDestroyProgress(state, player, getter, pos);
    }
}

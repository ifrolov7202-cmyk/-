package com.bamartial.registry;

import com.bamartial.skills.MartialBattleStyle;
import com.bamartial.skills.MartialSkills;
import com.bamartial.skills.Techniques;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Тренировочная груша: удары голой рукой в рукопашном стиле копят шкалу метра Battle Arts
 * (можно отрабатывать боевые искусства без врагов). Рукой не ломается — только топором.
 */
public class TrainingBagBlock extends Block {
    public TrainingBagBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && sp.getMainHandItem().isEmpty()
                && Techniques.bagHit(sp)) {
            MartialBattleStyle.addMeter(MartialSkills.styleContainer(sp), 15);
            sp.serverLevel().sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    6, 0.35, 0.3, 0.35, 0.2);
            level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 1.0f, 0.7f);
            level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6f, 1.1f);
        }
        super.attack(state, level, pos, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
        if (player.getMainHandItem().isEmpty()) return 0.0f;
        return super.getDestroyProgress(state, player, getter, pos);
    }
}

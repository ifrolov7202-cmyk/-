package com.martialarts.registry;

import com.martialarts.MartialArts;
import com.martialarts.block.TrainingBagBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MartialArts.MODID);

    public static final RegistryObject<Block> TRAINING_BAG = BLOCKS.register("training_bag",
            () -> new TrainingBagBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.5f)
                    .sound(SoundType.WOOL)));

    private ModBlocks() {}
}

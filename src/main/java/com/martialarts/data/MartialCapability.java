package com.martialarts.data;

import com.martialarts.MartialArts;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class MartialCapability {
    public static final Capability<MartialData> DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = new ResourceLocation(MartialArts.MODID, "martial_data");

    private MartialCapability() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(MartialData.class);
    }

    public static Optional<MartialData> get(Player player) {
        return player.getCapability(DATA).resolve();
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final MartialData data = new MartialData();
        private final LazyOptional<MartialData> optional = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == DATA ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}

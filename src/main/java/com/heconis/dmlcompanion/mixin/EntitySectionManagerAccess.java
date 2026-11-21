package com.heconis.dmlcompanion.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Consumer;

@Mixin(PersistentEntitySectionManager.class)
public interface EntitySectionManagerAccess {
    @Invoker("storeChunkSections")
    boolean dmlcompanion$storeChunkSections(long chunk, Consumer<EntityAccess> afterStore);

    @Accessor("permanentStorage")
    EntityPersistentStorage<?> dmlcompanion$permanentStorage();
}

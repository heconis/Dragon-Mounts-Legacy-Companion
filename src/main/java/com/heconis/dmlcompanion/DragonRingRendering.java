package com.heconis.dmlcompanion;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DragonRingRendering {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(DragonMountsCompanion.DRAGON_RING.get(),
                ResourceLocation.fromNamespaceAndPath(DragonMountsCompanion.MOD_ID, "deceased"),
                (stack, level, holder, seed) -> DragonRingItem.isDeadRing(stack) ? 1 : 0));
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 ? DragonRingItem.gemColor(stack) : -1,
                DragonMountsCompanion.DRAGON_RING.get());
    }
}

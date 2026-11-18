package com.heconis.dmlcompanion;

import net.minecraft.world.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;

@Mod(DragonMountsCompanion.MOD_ID)
public final class DragonMountsCompanion {
    public static final String MOD_ID = "dmlcompanion";

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    static final RegistryObject<DragonRingItem> DRAGON_RING = ITEMS.register("dragon_ring", DragonRingItem::new);

    public DragonMountsCompanion(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var ring = DRAGON_RING.get();
            CuriosApi.registerCurio(ring, ring);
        });
    }
}

package com.heconis.dmlcompanion;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    static final RegistryObject<EntityType<DragonMinion>> MINION = ENTITIES.register("minion", () ->
            EntityType.Builder.<DragonMinion>of(DragonMinion::new, MobCategory.MISC)
                    .sized(0.45f, 0.45f).clientTrackingRange(8).updateInterval(2).noSave().noSummon()
                    .build(MOD_ID + ":minion"));

    public DragonMountsCompanion(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        context.registerConfig(ModConfig.Type.CLIENT, MinionDisplay.SPEC);
        DragonNetwork.register();
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var ring = DRAGON_RING.get();
            CuriosApi.registerCurio(ring, ring);
        });
    }
}

package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID)
public final class DragonStorageEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof TameableDragon dragon)) {
            return;
        }
        var server = level.getServer();
        var revision = DragonStorage.revisionOf(dragon);
        var id = dragon.getUUID();
        try {
            var data = DragonStorageData.get(server);
            if (data.get(id) != null && !data.allowsJoin(id, revision, dragon)) {
                event.setCanceled(true);
            }
        } catch (IOException failure) {
            // Without a readable record, leave the world entity alone. Storage operations still fail.
            LOGGER.error("Could not read dragon storage for {}", id, failure);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof TameableDragon dragon)
                || dragon.getRemovalReason() != Entity.RemovalReason.KILLED) {
            return;
        }
        var id = dragon.getUUID();
        var server = level.getServer();
        try {
            var data = DragonStorageData.get(server);
            var entry = data.get(id);
            if (entry == null && !IssuedDragonRings.get(server).contains(id)) {
                return;
            }
            if (!(entry instanceof DragonStorageData.Stored) && data.allowsJoin(id, DragonStorage.revisionOf(dragon), null)) {
                data.commit(id, DragonStorageData.Dead.INSTANCE);
            }
        } catch (IOException failure) {
            LOGGER.error("Could not record death of dragon {}", id, failure);
        }
    }
}

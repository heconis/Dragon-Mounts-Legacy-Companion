package com.heconis.dmlcompanion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DragonShoulderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<UUID, DragonMinion> MINIONS_BY_OWNER = new HashMap<>();
    private final DragonMinionRenderer renderer;

    private DragonShoulderLayer(PlayerRenderer playerRenderer, DragonMinionRenderer renderer) {
        super(playerRenderer);
        this.renderer = renderer;
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        if (!(event.getEntityRenderer(DragonMountsCompanion.MINION.get()) instanceof DragonMinionRenderer renderer)) {
            return;
        }
        for (String skin : event.getSkins()) {
            if (event.getPlayerSkin(skin) instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new DragonShoulderLayer(playerRenderer, renderer));
            }
        }
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        if (MinionDisplay.MODE.get() != MinionDisplay.Mode.SHOULDER || player.isInvisible()) {
            return;
        }
        var minion = MINIONS_BY_OWNER.get(player.getUUID());
        if (minion == null || minion.isRemoved() || minion.level() != player.level()) {
            return;
        }
        poses.pushPose();
        try {
            // Use the parrot's shoulder offset at floor level. DML already renders from the entity's feet.
            poses.translate(-0.4, player.isCrouching() ? 0.2 : 0, 0);
            // Undo the player model's axis flip so the dragon renderer receives an upright coordinate system.
            poses.scale(-1, -1, 1);
            renderer.renderOnShoulder(minion, partialTick, poses, buffers, light);
        } finally {
            poses.popPose();
        }
    }

    @Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT)
    public static final class Tracking {
        private Tracking() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            MINIONS_BY_OWNER.clear();
            var level = Minecraft.getInstance().level;
            if (level != null && MinionDisplay.MODE.get() == MinionDisplay.Mode.SHOULDER) {
                for (var entity : level.entitiesForRendering()) {
                    if (entity instanceof DragonMinion minion && !minion.isRemoved()) {
                        minion.ownerId().ifPresent(id -> MINIONS_BY_OWNER.put(id, minion));
                    }
                }
            }
        }
    }
}

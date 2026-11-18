package com.heconis.dmlcompanion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DragonHeadLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<UUID, DragonMinion> MINIONS_BY_OWNER = new HashMap<>();
    private final DragonMinionRenderer renderer;

    private DragonHeadLayer(PlayerRenderer playerRenderer, DragonMinionRenderer renderer) {
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
                playerRenderer.addLayer(new DragonHeadLayer(playerRenderer, renderer));
            }
        }
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        if (MinionDisplay.currentMode() != MinionDisplay.Mode.HEAD || player.isInvisible()) {
            return;
        }
        var minion = MINIONS_BY_OWNER.get(player.getUUID());
        if (minion == null || minion.isRemoved() || minion.level() != player.level()) {
            return;
        }
        poses.pushPose();
        try {
            var head = getParentModel().head;
            // The seated model's contact point sits above its render origin. Lower that origin for both surfaces.
            float perchHeight = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 7.95f : 9.2f;
            var headRotation = new Quaternionf().rotationZYX(head.zRot, head.yRot, head.xRot);
            var crown = new Vector3f(0, -perchHeight / 16, 0).rotate(headRotation);
            // Keep the torso centering offset in the horizontal plane as the dragon tilts at the crown.
            crown.add(new Vector3f(0, 0, 0.06f).rotateY(head.yRot));
            poses.translate(head.x / 16 + crown.x, head.y / 16 + crown.y, head.z / 16 + crown.z);
            poses.mulPose(headRotation);
            // Undo the player model's axis flip so the dragon renderer receives an upright coordinate system.
            poses.scale(-1, -1, 1);
            renderer.renderOnHead(minion, partialTick, poses, buffers, light);
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
            if (level != null && MinionDisplay.currentMode() == MinionDisplay.Mode.HEAD) {
                for (var entity : level.entitiesForRendering()) {
                    if (entity instanceof DragonMinion minion && !minion.isRemoved()) {
                        minion.ownerId().ifPresent(id -> MINIONS_BY_OWNER.put(id, minion));
                    }
                }
            }
        }
    }
}

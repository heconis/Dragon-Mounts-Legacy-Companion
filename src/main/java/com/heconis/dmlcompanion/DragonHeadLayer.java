package com.heconis.dmlcompanion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
            float clearance = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 0.75f : 2f;
            // Rotate the crown offset with the head. Use only the head's yaw for the dragon's orientation.
            var headRotation = new Quaternionf().rotationZYX(head.zRot, head.yRot, head.xRot);
            var crown = new Vector3f(0, -(8 + clearance) / 16, 0).rotate(headRotation);
            poses.translate(head.x / 16 + crown.x, head.y / 16 + crown.y, head.z / 16 + crown.z);
            poses.mulPose(Axis.YP.rotation(head.yRot));
            poses.translate(0, 0, 0.06);
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

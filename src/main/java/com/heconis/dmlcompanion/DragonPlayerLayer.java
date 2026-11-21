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
import net.minecraft.world.item.ArmorItem;
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
public final class DragonPlayerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<UUID, DragonMinion> MINIONS_BY_OWNER = new HashMap<>();
    private static final double BELLY_DEPTH = 28.0 / 16 * DragonMinionRenderer.FOLLOW_SCALE;
    private static final double BACK_CLEARANCE = 1.0 / 256;
    // Align the wing pivots with the shoulder line after each model's render transforms.
    private static final double FULL_BACK_HEIGHT = 0.184;
    private static final double WINGS_BACK_HEIGHT = -0.052;
    private final DragonMinionRenderer renderer;

    private DragonPlayerLayer(PlayerRenderer playerRenderer, DragonMinionRenderer renderer) {
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
                playerRenderer.addLayer(new DragonPlayerLayer(playerRenderer, renderer));
            }
        }
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        var mode = MinionDisplay.currentMode();
        if (mode == MinionDisplay.Mode.HIDDEN || player.isInvisible()) {
            return;
        }
        var minion = minionFor(player);
        if (minion == null || mode == MinionDisplay.Mode.FOLLOW && !minion.usesAirbornePose()) {
            return;
        }
        poses.pushPose();
        try {
            if (mode == MinionDisplay.Mode.HEAD) {
                renderOnHead(player, minion, partialTick, poses, buffers, light);
            } else {
                getParentModel().body.translateAndRotate(poses);
                if (mode == MinionDisplay.Mode.WINGS) {
                    poses.translate(0, WINGS_BACK_HEIGHT, 0.25);
                    renderer.renderWings(minion, partialTick, poses, buffers, light);
                } else {
                    double backSurface = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem
                            ? 3.0 / 16 : (getParentModel().jacket.visible ? 2.25 : 2.0) / 16;
                    double backDepth = backSurface + BELLY_DEPTH + BACK_CLEARANCE;
                    poses.translate(0, FULL_BACK_HEIGHT, backDepth);
                    // In player model space this rotates the muzzle upward and the feet toward the back.
                    poses.mulPose(Axis.XP.rotationDegrees(-90));
                    poses.scale(-1, -1, 1);
                    renderer.renderOnBack(minion, partialTick, poses, buffers, light);
                }
            }
        } finally {
            poses.popPose();
        }
    }

    private void renderOnHead(AbstractClientPlayer player, DragonMinion minion, float partialTick,
                              PoseStack poses, MultiBufferSource buffers, int light) {
        var head = getParentModel().head;
        // The seated model's contact point sits above its render origin. Lower that origin for both surfaces.
        float perchHeight = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 7.63f : 8.4f;
        var headRotation = new Quaternionf().rotationZYX(head.zRot, head.yRot, head.xRot);
        var crown = new Vector3f(0, -perchHeight / 16, 0).rotate(headRotation);
        crown.add(new Vector3f(0, 0, 0.06f).rotateY(head.yRot));
        poses.translate(head.x / 16 + crown.x, head.y / 16 + crown.y, head.z / 16 + crown.z);
        poses.mulPose(headRotation);
        poses.scale(-1, -1, 1);
        renderer.renderAttached(minion, partialTick, poses, buffers, light);
    }

    static DragonMinion minionFor(AbstractClientPlayer player) {
        var minion = MINIONS_BY_OWNER.get(player.getUUID());
        return minion != null && !minion.isRemoved() && minion.level() == player.level() ? minion : null;
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
            if (level != null) {
                for (var entity : level.entitiesForRendering()) {
                    if (entity instanceof DragonMinion minion && !minion.isRemoved()) {
                        minion.ownerId().ifPresent(id -> MINIONS_BY_OWNER.put(id, minion));
                    }
                }
            }
        }
    }
}

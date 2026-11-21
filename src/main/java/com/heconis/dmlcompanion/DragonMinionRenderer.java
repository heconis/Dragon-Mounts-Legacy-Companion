package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.DMLRegistry;
import com.github.kay9.dragonmounts.client.DragonAnimator;
import com.github.kay9.dragonmounts.client.DragonModel;
import com.github.kay9.dragonmounts.client.DragonRenderer;
import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.github.kay9.dragonmounts.dragon.breed.BreedRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DragonMinionRenderer extends EntityRenderer<DragonMinion> {
    static final float FOLLOW_SCALE = 0.15f;
    private static final float HEAD_SCALE = 0.12f;
    private static final ResourceLocation MISSING_TEXTURE = ResourceLocation.withDefaultNamespace("missingno");
    private final DragonRenderer dragonRenderer;
    private final DragonModel wingModel;
    private final Map<DragonMinion, Preview> previews = new WeakHashMap<>();

    private DragonMinionRenderer(EntityRendererProvider.Context context) {
        super(context);
        dragonRenderer = new DragonRenderer(context);
        wingModel = new DragonModel(context.bakeLayer(DragonRenderer.MODEL_LOCATION));
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DragonMountsCompanion.MINION.get(), DragonMinionRenderer::new);
    }

    @Override
    public boolean shouldRender(DragonMinion minion, Frustum frustum, double x, double y, double z) {
        return MinionDisplay.currentMode() == MinionDisplay.Mode.FOLLOW && !minion.usesAirbornePose()
                && super.shouldRender(minion, frustum, x, y, z);
    }

    @Override
    public void render(DragonMinion minion, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        if (MinionDisplay.currentMode() == MinionDisplay.Mode.FOLLOW && !minion.usesAirbornePose()) {
            renderModel(minion, yaw, partialTick, poses, buffers, light, DisplayPose.FOLLOW);
        }
    }

    void renderAttached(DragonMinion minion, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        // The player layer already supplies the body yaw. DML's renderer rotates by 180 degrees minus its yaw.
        renderModel(minion, 180, partialTick, poses, buffers, light, attachedPose(minion));
    }

    void renderOnBack(DragonMinion minion, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        var pose = minion.usesAirbornePose() ? DisplayPose.BACK_GLIDING : DisplayPose.BACK_STANDING;
        renderModel(minion, 180, partialTick, poses, buffers, light, pose);
    }

    void renderWings(DragonMinion minion, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        var dragon = prepareDragon(minion, 180, partialTick, attachedPose(minion));
        if (dragon == null) {
            return;
        }
        wingModel.prepareMobModel(dragon, 0, 0, partialTick);
        wingModel.setupAnim(dragon, 0, 0, dragon.tickCount + partialTick, 0, 0);
        poses.scale(FOLLOW_SCALE, FOLLOW_SCALE, FOLLOW_SCALE);
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(dragonRenderer.getTextureLocation(dragon)));
        wingModel.renderWings(poses, vertices, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
    }

    private static DisplayPose attachedPose(DragonMinion minion) {
        return minion.usesAirbornePose() ? DisplayPose.GLIDING : DisplayPose.PERCHED;
    }

    private RenderDragon prepareDragon(DragonMinion minion, float yaw, float partialTick, DisplayPose pose) {
        var preview = previewFor(minion);
        if (preview == null) {
            return null;
        }
        var dragon = preview.dragon;
        dragon.displayPose = pose;
        dragon.setFlying(!pose.grounded);
        dragon.setInSittingPose(pose == DisplayPose.PERCHED);
        dragon.setYRot(yaw);
        dragon.yBodyRotO = dragon.yBodyRot = yaw;
        dragon.yHeadRotO = dragon.yHeadRot = yaw;
        if (preview.lastTick != minion.tickCount || preview.lastPose != pose) {
            preview.lastTick = minion.tickCount;
            preview.lastPose = pose;
            dragon.setOldPosAndRot();
            if (pose == DisplayPose.FOLLOW) {
                dragon.setPos(minion.getPosition(partialTick));
            }
            dragon.tickCount = minion.tickCount;
            dragon.getAnimator().tick();
        }
        return dragon;
    }

    private void renderModel(DragonMinion minion, float yaw, float partialTick, PoseStack poses,
                             MultiBufferSource buffers, int light, DisplayPose pose) {
        var dragon = prepareDragon(minion, yaw, partialTick, pose);
        if (dragon == null) {
            return;
        }
        poses.pushPose();
        try {
            dragon.hideHeadAndNeck = minion.isFlyingWithOwner();
            float scale = (pose == DisplayPose.FOLLOW || pose.onBack ? FOLLOW_SCALE : HEAD_SCALE)
                    / Math.max(0.01f, dragon.getScale());
            poses.scale(scale, scale, scale);
            dragonRenderer.render(dragon, yaw, partialTick, poses, buffers, light);
        } finally {
            dragon.restoreHeadAndNeck();
            poses.popPose();
        }
    }

    private enum DisplayPose {
        FOLLOW(false, false),
        PERCHED(true, false),
        GLIDING(false, false),
        BACK_STANDING(true, true),
        BACK_GLIDING(false, true);

        private final boolean grounded;
        private final boolean onBack;

        DisplayPose(boolean grounded, boolean onBack) {
            this.grounded = grounded;
            this.onBack = onBack;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(DragonMinion minion) {
        var preview = previewFor(minion);
        return preview == null ? MISSING_TEXTURE : dragonRenderer.getTextureLocation(preview.dragon);
    }

    private Preview previewFor(DragonMinion minion) {
        var preview = previews.get(minion);
        if (preview != null && preview.dragon.level() != minion.level()) {
            previews.remove(minion);
        }
        return previews.computeIfAbsent(minion, this::createPreview);
    }

    private Preview createPreview(DragonMinion minion) {
        if (minion.breedId().isEmpty()) {
            return null;
        }
        var breed = BreedRegistry.get(minion.breedId(), minion.level().registryAccess());
        if (breed == null) {
            return null;
        }
        var dragon = new RenderDragon(minion.level());
        dragon.setBreed(breed);
        dragon.setPos(minion.position());
        dragon.setOldPosAndRot();
        return new Preview(dragon);
    }

    // The DML renderer needs a dragon and its animator. This instance is never added to the level.
    private static final class RenderDragon extends TameableDragon {
        private DisplayPose displayPose = DisplayPose.FOLLOW;
        private DragonAnimator attachedAnimator;
        private boolean hideHeadAndNeck;
        private HeadVisibility previousHeadVisibility;

        private RenderDragon(Level level) {
            super(DMLRegistry.DRAGON.get(), level);
        }

        @Override
        public DragonAnimator getAnimator() {
            if (displayPose == null || displayPose == DisplayPose.FOLLOW) {
                return super.getAnimator();
            }
            if (attachedAnimator == null) {
                attachedAnimator = new DragonAnimator(this) {
                    @Override
                    public float getModelPitch(float partialTick) {
                        return 0;
                    }

                    @Override
                    public float getModelOffsetY() {
                        // Cancel LivingEntityRenderer's origin shift after it flips the model's Y axis.
                        return displayPose.onBack ? -1.501f : super.getModelOffsetY();
                    }

                    @Override
                    protected void animHeadAndNeck(DragonModel model) {
                        if (hideHeadAndNeck && previousHeadVisibility == null) {
                            previousHeadVisibility = new HeadVisibility(model, model.head.visible, model.neck.visible);
                            model.head.visible = false;
                            // DML copies the neck part's visibility into every segment while posing it.
                            model.neck.visible = false;
                        }
                        super.animHeadAndNeck(model);
                    }

                    @Override
                    protected void animLegs(DragonModel model) {
                        super.animLegs(model);
                        if (!displayPose.onBack) {
                            return;
                        }
                        for (int index = 0; index < model.legs.length; index++) {
                            var leg = model.legs[index];
                            boolean hind = index % 2 == 1;
                            // Fold the knees so the paws reach the same surface as the belly.
                            leg[0].xRot = hind ? 1.16f : -1.32f;
                            leg[0].yRot = 0;
                            leg[1].xRot = -2 * leg[0].xRot;
                            leg[2].xRot = -leg[0].xRot - leg[1].xRot;
                            leg[3].xRot = 0.18f;
                        }
                    }
                };
            }
            return attachedAnimator;
        }

        @Override
        public boolean isNearGround() {
            return displayPose == null || displayPose == DisplayPose.FOLLOW
                    ? super.isNearGround() : displayPose.grounded;
        }

        @Override
        public void onWingsDown(float speed) {}

        private void restoreHeadAndNeck() {
            var previous = previousHeadVisibility;
            if (previous != null) {
                previous.model().head.visible = previous.head();
                previous.model().neck.visible = previous.neck();
                previousHeadVisibility = null;
            }
            hideHeadAndNeck = false;
        }
    }

    private record HeadVisibility(DragonModel model, boolean head, boolean neck) {}

    private static final class Preview {
        private final RenderDragon dragon;
        private int lastTick = -1;
        private DisplayPose lastPose;

        private Preview(RenderDragon dragon) {
            this.dragon = dragon;
        }
    }
}

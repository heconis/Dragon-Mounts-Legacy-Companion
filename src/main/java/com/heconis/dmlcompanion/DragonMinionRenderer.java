package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.DMLRegistry;
import com.github.kay9.dragonmounts.client.DragonAnimator;
import com.github.kay9.dragonmounts.client.DragonRenderer;
import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.github.kay9.dragonmounts.dragon.breed.BreedRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
    private static final float FOLLOW_SCALE = 0.17f;
    private static final float HEAD_SCALE = 0.12f;
    private static final ResourceLocation MISSING_TEXTURE = ResourceLocation.withDefaultNamespace("missingno");
    private final DragonRenderer dragonRenderer;
    private final Map<DragonMinion, Preview> previews = new WeakHashMap<>();

    private DragonMinionRenderer(EntityRendererProvider.Context context) {
        super(context);
        dragonRenderer = new DragonRenderer(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DragonMountsCompanion.MINION.get(), DragonMinionRenderer::new);
    }

    @Override
    public boolean shouldRender(DragonMinion minion, Frustum frustum, double x, double y, double z) {
        return MinionDisplay.currentMode() == MinionDisplay.Mode.FOLLOW
                && super.shouldRender(minion, frustum, x, y, z);
    }

    @Override
    public void render(DragonMinion minion, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        if (MinionDisplay.currentMode() == MinionDisplay.Mode.FOLLOW) {
            renderModel(minion, yaw, partialTick, poses, buffers, light, false);
        }
    }

    void renderOnHead(DragonMinion minion, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        // The player layer already supplies the body yaw. DML's renderer rotates by 180 degrees minus its yaw.
        renderModel(minion, 180, partialTick, poses, buffers, light, true);
    }

    private void renderModel(DragonMinion minion, float yaw, float partialTick, PoseStack poses,
                             MultiBufferSource buffers, int light, boolean perched) {
        var preview = previewFor(minion);
        if (preview == null) {
            return;
        }

        var dragon = preview.dragon;
        dragon.perched = perched;
        dragon.setFlying(!perched);
        dragon.setYRot(yaw);
        dragon.yBodyRotO = dragon.yBodyRot = yaw;
        dragon.yHeadRotO = dragon.yHeadRot = yaw;
        if (preview.lastTick != minion.tickCount || preview.lastPerched != perched) {
            preview.lastTick = minion.tickCount;
            preview.lastPerched = perched;
            dragon.setOldPosAndRot();
            if (!perched) {
                dragon.setPos(minion.getPosition(partialTick));
            }
            dragon.tickCount = minion.tickCount;
            dragon.getAnimator().tick();
        }

        poses.pushPose();
        try {
            float scale = (perched ? HEAD_SCALE : FOLLOW_SCALE) / Math.max(0.01f, dragon.getScale());
            poses.scale(scale, scale, scale);
            dragonRenderer.render(dragon, yaw, partialTick, poses, buffers, light);
        } finally {
            poses.popPose();
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
        private boolean perched;
        private DragonAnimator perchedAnimator;

        private RenderDragon(Level level) {
            super(DMLRegistry.DRAGON.get(), level);
        }

        @Override
        public DragonAnimator getAnimator() {
            if (!perched) {
                return super.getAnimator();
            }
            if (perchedAnimator == null) {
                perchedAnimator = new DragonAnimator(this) {
                    @Override
                    public float getModelPitch(float partialTick) {
                        return 0;
                    }
                };
            }
            return perchedAnimator;
        }

        @Override
        public boolean isNearGround() {
            return perched || super.isNearGround();
        }

        @Override
        public void onWingsDown(float speed) {}
    }

    private static final class Preview {
        private final RenderDragon dragon;
        private int lastTick = -1;
        private boolean lastPerched;

        private Preview(RenderDragon dragon) {
            this.dragon = dragon;
        }
    }
}

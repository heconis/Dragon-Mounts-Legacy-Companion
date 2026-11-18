package com.heconis.dmlcompanion;

import com.github.kay9.dragonmounts.DMLRegistry;
import com.github.kay9.dragonmounts.client.DragonRenderer;
import com.github.kay9.dragonmounts.dragon.TameableDragon;
import com.github.kay9.dragonmounts.dragon.breed.BreedRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = DragonMountsCompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DragonMinionRenderer extends EntityRenderer<DragonMinion> {
    private static final float SCALE = 0.34f;
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
        return MinionDisplay.MODE.get() != MinionDisplay.Mode.HIDDEN
                && super.shouldRender(minion, frustum, x, y, z);
    }

    @Override
    public void render(DragonMinion minion, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        var mode = MinionDisplay.MODE.get();
        if (mode == MinionDisplay.Mode.HIDDEN) {
            return;
        }
        var preview = previewFor(minion);
        if (preview == null) {
            return;
        }

        var dragon = preview.dragon;
        var position = minion.getPosition(partialTick);
        var offset = Vec3.ZERO;
        var owner = minion.owner();
        if (mode == MinionDisplay.Mode.SHOULDER && owner != null) {
            yaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
            var side = Vec3.directionFromRotation(0, yaw + 90).scale(0.58);
            offset = owner.getPosition(partialTick).add(side.x, owner.getEyeHeight() - 0.25, side.z).subtract(position);
        }
        if (preview.lastTick != minion.tickCount) {
            preview.lastTick = minion.tickCount;
            dragon.setOldPosAndRot();
            dragon.setPos(position);
            dragon.tickCount = minion.tickCount;
            dragon.getAnimator().tick();
        }
        dragon.setYRot(yaw);
        dragon.yBodyRotO = dragon.yBodyRot = yaw;
        dragon.yHeadRotO = dragon.yHeadRot = yaw;

        poses.pushPose();
        try {
            poses.translate(offset.x, offset.y, offset.z);
            float scale = SCALE / Math.max(0.01f, dragon.getScale());
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
        dragon.setFlying(true);
        dragon.setPos(minion.position());
        dragon.setOldPosAndRot();
        return new Preview(dragon);
    }

    // The DML renderer needs a dragon and its animator. This instance is never added to the level.
    private static final class RenderDragon extends TameableDragon {
        private RenderDragon(Level level) {
            super(DMLRegistry.DRAGON.get(), level);
        }

        @Override
        public void onWingsDown(float speed) {}
    }

    private static final class Preview {
        private final TameableDragon dragon;
        private int lastTick = -1;

        private Preview(TameableDragon dragon) {
            this.dragon = dragon;
        }
    }
}

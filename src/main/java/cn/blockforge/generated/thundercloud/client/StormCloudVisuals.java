package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.animation.CloudLifecycle;
import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import cn.blockforge.generated.thundercloud.config.StormConfig;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import cn.blockforge.generated.thundercloud.entity.ThunderCloudCoreEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 世界空间厚云：先画不透明主体，再画柔边、内凹风暴眼及贴面电流；生命周期统一变形。 */
public final class StormCloudVisuals {
    private static final int HARD_GLOBAL_MAX = 2048;
    private static final int MAX_CLOUDS = 16;
    // 专属缓冲，不冲刷其他模组/原版的数据。
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(2 * 1024 * 1024));
    private static final Map<MeshKey, VortexCloudMesh> MESHES = new HashMap<>();
    private static StormConfig flowConfig;
    /**
     * 候选云查询结果按 tick 缓存：同一 tick 里 {@code AFTER_CUTOUT_BLOCKS} 与 {@code AFTER_WEATHER}
     * 两个阶段各会调用一次 {@link #renderAll}，而云在这一 tick 内不会移动；
     * 旧实现把 partialTick 也算进缓存键，等于每个画面帧都重做一次「416 格 AABB 实体查询 + 全量距离排序」。
     */
    private static final List<ThunderCloudCoreEntity> QUERY_CACHE = new ArrayList<>();
    private static ClientLevel queryLevel;
    private static long queryGameTime = Long.MIN_VALUE;

    private StormCloudVisuals() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                && event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        renderAll(mc.level, event);
    }

    private static void renderAll(ClientLevel level, RenderLevelStageEvent event) {
        StormConfig config = StormConfigManager.get();
        StormConfig.RenderOptions options = config.render;
        // 同屏预算总额决定最多画几朵云；预算耗尽后更远的云自动省略。
        int budget = Math.max(0, Math.min(options.budgetTotal, HARD_GLOBAL_MAX));
        if (budget == 0) return;
        if (flowConfig != config) {
            MESHES.clear();
            flowConfig = config;
        }
        Vec3 camera = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        double maxDistance = Math.max(32.0, Math.min(512.0, options.maxRenderDistance));
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick();
        // 只按 tick 刷新：云实体在这一 tick 内不会移动，旧实现把 partialTick 也算进键里，
        // 等于每帧都重做一次 416 格实体查询 + 全量距离排序（一帧两次渲染阶段也只共享一次）。
        if (queryLevel != level || queryGameTime != gameTime) {
            QUERY_CACHE.clear();
            QUERY_CACHE.addAll(level.getEntitiesOfClass(ThunderCloudCoreEntity.class,
                    new AABB(camera, camera).inflate(maxDistance + 160.0)));
            QUERY_CACHE.sort(Comparator.comparingDouble(cloud -> cloud.getStormCenter().distanceToSqr(camera)));
            queryLevel = level;
            queryGameTime = gameTime;
        }
        List<ThunderCloudCoreEntity> candidates = QUERY_CACHE;
        List<VisibleCloud> clouds = new ArrayList<>();
        Set<MeshKey> activeMeshes = new HashSet<>();
        for (ThunderCloudCoreEntity cloud : candidates) {
            if (clouds.size() >= MAX_CLOUDS || budget <= 0) break;
            CloudLifecycle.Frame animation = cloud.getAnimationFrame(event.getPartialTick());
            if (!animation.visible()) continue;
            CloudTypeSpec spec = cloud.getSpec();
            Vec3 center = cloud.getStormCenter();
            double distance = Math.sqrt(center.distanceToSqr(camera));
            if (distance > maxDistance) continue;
            double extent = spec.clampedRadius() * 1.23;
            double vertical = spec.clampedThickness() * 1.50 + 3.0;
            if (frustum != null && !frustum.isVisible(new AABB(center.x - extent, center.y - vertical,
                    center.z - extent, center.x + extent, center.y + vertical, center.z + extent))) continue;
            int count = Math.min(budget, spec.clampedBudget(options.budgetPerCloud));
            if (count == 0) continue;
            double fade = AlphaBaker.smoothstep(maxDistance, maxDistance * 0.76, distance);
            if (fade < 0.004) continue;
            // UUID在重新进入世界后也保持不变。
            long seed = cloud.getUUID().getMostSignificantBits() ^ cloud.getUUID().getLeastSignificantBits();
            VortexCloudMesh.Detail detail = detailFor(distance, spec.clampedRadius());
            MeshKey meshKey = new MeshKey(cloud.getUUID(), detail);
            activeMeshes.add(meshKey);
            clouds.add(new VisibleCloud(spec, center, fade, seed, animation, meshKey, detail,
                    new CloudAnimationShape(animation, spec.clampedRadius(), spec.clampedThickness(), seed)));
            budget -= count;
        }
        // 每秒清走离开视野或已删除实体的网格，避免长时间跑图积累客户端内存。
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                && level.getGameTime() % 20L == 0L) {
            MESHES.keySet().removeIf(key -> !activeMeshes.contains(key));
        }
        if (clouds.isEmpty()) return;
        CloudMaterialAtlas.prepare();
        // 两个绘制阶段都显式设为世界观察矩阵；顶点使用单位阵，避免重复视角变换。
        PoseStack modelView = RenderSystem.getModelViewStack();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(event.getPoseStack().last().pose());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            Matrix4f matrix = new Matrix4f();
            double seconds = (level.getGameTime() + event.getPartialTick()) / 20.0;
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                // 主深度在半透明地形、天气和Fabulous合成之前写入。
                VertexConsumer solid = BUFFERS.getBuffer(CloudMaterialAtlas.solidType());
                for (VisibleCloud cloud : clouds) {
                    if (!cloud.animation.solid()) continue;
                    meshFor(cloud).drawSolid(cloud.consumer(solid, camera), matrix,
                            cloud.center.x - camera.x, cloud.center.y - camera.y, cloud.center.z - camera.z,
                            seconds, cloud.seed);
                }
                BUFFERS.endBatch();
            } else {
                VertexConsumer fringe = BUFFERS.getBuffer(CloudMaterialAtlas.type(false));
                for (VisibleCloud cloud : clouds) {
                    VortexCloudMesh mesh = meshFor(cloud);
                    VertexConsumer animated = cloud.consumer(fringe, camera);
                    if (!cloud.animation.solid()) {
                        // 只有形成最初的薄雾及驱散残影进入透明批次；稳定期绝不淡化遮天云核。
                        mesh.drawSolid(animated, matrix,
                                cloud.center.x - camera.x, cloud.center.y - camera.y, cloud.center.z - camera.z,
                                seconds, cloud.seed);
                    }
                    mesh.drawFringe(animated, matrix,
                            cloud.center.x - camera.x, cloud.center.y - camera.y, cloud.center.z - camera.z,
                            seconds, cloud.seed, cloud.fade);
                }
                BUFFERS.endBatch();
                VertexConsumer glow = BUFFERS.getBuffer(CloudMaterialAtlas.type(true));
                for (VisibleCloud cloud : clouds) {
                    double energy = cloud.fade * CloudLifecycle.smooth(cloud.animation.formation())
                            * (1.0 - CloudLifecycle.smooth(cloud.animation.dissipation()));
                    // 形成最初与消散尾段辉光几乎不可见：整段跳过，省下风暴眼与电弧每帧的路径和顶点开销。
                    if (energy < 0.02) continue;
                    VertexConsumer animated = cloud.consumer(glow, camera);
                    VortexCloudMesh mesh = meshFor(cloud);
                    // 只画镜头这一侧的云面特效：另一侧本来就被不透明云核挡得干干净净，
                    // 旧实现两面临画，等于白算一半风暴眼与电弧。
                    StormEyeVisuals.draw(animated, matrix, cloud.spec, mesh.flow(), cloud.seed,
                            cloud.center.x - camera.x, cloud.center.y - camera.y, cloud.center.z - camera.z,
                            seconds, energy, cloud.detail, camera.y);
                    StormArcVisuals.draw(animated, matrix, cloud.spec, mesh.flow(),
                            cloud.center.x - camera.x, cloud.center.y - camera.y, cloud.center.z - camera.z,
                            cloud.seed, seconds, energy, camera.y >= cloud.center.y ? 1 : -1);
                }
                BUFFERS.endBatch();
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
        }
    }

    /**
     * 距离降档：视距 / 云半径 &lt; 1.0 才算近景。旧值 1.45 / 2.75 意味着半径 64 的云
     * 站到 93 格外仍按最密的网格画——那早就只剩一小片了。现在 64 格外就进中档、128 格外进远档。
     */
    private static VortexCloudMesh.Detail detailFor(double distance, double radius) {
        double apparentDistance = distance / Math.max(16.0, radius);
        if (apparentDistance <= 1.0) return VortexCloudMesh.Detail.HIGH;
        if (apparentDistance <= 2.0) return VortexCloudMesh.Detail.MEDIUM;
        return VortexCloudMesh.Detail.LOW;
    }

    private static VortexCloudMesh meshFor(VisibleCloud cloud) {
        return MESHES.computeIfAbsent(cloud.meshKey,
                ignored -> new VortexCloudMesh(cloud.spec, cloud.detail));
    }

    private record MeshKey(UUID cloudId, VortexCloudMesh.Detail detail) {}

    private record VisibleCloud(CloudTypeSpec spec, Vec3 center, double fade, long seed,
                                CloudLifecycle.Frame animation, MeshKey meshKey,
                                VortexCloudMesh.Detail detail, CloudAnimationShape shape) {
        VertexConsumer consumer(VertexConsumer target, Vec3 camera) {
            return animation.steady() ? target : new AnimatedCloudConsumer(target, shape,
                    center.x - camera.x, center.y - camera.y, center.z - camera.z);
        }
    }
}

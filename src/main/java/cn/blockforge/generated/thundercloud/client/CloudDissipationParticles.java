package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.animation.CloudLifecycle;
import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import cn.blockforge.generated.thundercloud.entity.ThunderCloudCoreEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.joml.Vector3f;

/** 驱散阶段的轻量紫色粒子；使用原版粒子，不新增注册项或网络包。 */
public final class CloudDissipationParticles {
    private static final DustParticleOptions BRIGHT_PURPLE =
            new DustParticleOptions(new Vector3f(0.67F, 0.35F, 0.95F), 1.25F);
    private static final DustParticleOptions PALE_PURPLE =
            new DustParticleOptions(new Vector3f(0.88F, 0.72F, 1.00F), 0.90F);
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int MAX_PARTICLES_PER_TICK = 20;
    private static final double MAX_DISTANCE_SQR = 192.0 * 192.0;

    private CloudDissipationParticles() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) return;

        Vec3 camera = minecraft.player.getEyePosition();
        int remaining = MAX_PARTICLES_PER_TICK;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ThunderCloudCoreEntity cloud) || !cloud.isDissipating()) continue;
            if (cloud.distanceToSqr(camera) > MAX_DISTANCE_SQR) continue;
            CloudLifecycle.Frame frame = cloud.getAnimationFrame(0.0F);
            if (!frame.visible()) continue;
            int count = Math.min(remaining, 1 + (int) Math.round(3.0 * Math.sin(Math.PI * frame.dissipation())));
            emit(level, cloud, frame, count);
            remaining -= count;
            if (remaining <= 0) break;
        }
    }

    private static void emit(ClientLevel level, ThunderCloudCoreEntity cloud,
                             CloudLifecycle.Frame frame, int count) {
        CloudTypeSpec spec = cloud.getSpec();
        double radius = spec.clampedRadius();
        double expansion = 1.0 + CloudLifecycle.smooth(frame.dissipation()) * 0.18;
        Vec3 center = cloud.getStormCenter();
        long age = level.getGameTime();
        for (int i = 0; i < count; i++) {
            double fraction = 0.20 + Math.sqrt(RANDOM.nextDouble()) * 0.76;
            double angle = RANDOM.nextDouble() * Math.PI * 2.0
                    + age * 0.055 + SpiralCloudFlow.seedAngle(cloud.getUUID().getLeastSignificantBits());
            double distance = radius * fraction * expansion;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = center.y + (RANDOM.nextDouble() * 2.0 - 1.0)
                    * spec.clampedThickness() * (0.28 + fraction * 0.42);
            double outward = 0.018 + frame.dissipation() * 0.045;
            double tangent = 0.025 * (1.0 - fraction * 0.45);
            double vx = Math.cos(angle) * outward - Math.sin(angle) * tangent;
            double vz = Math.sin(angle) * outward + Math.cos(angle) * tangent;
            double vy = 0.008 + RANDOM.nextDouble() * 0.020;
            level.addParticle(RANDOM.nextInt(4) == 0 ? PALE_PURPLE : BRIGHT_PURPLE,
                    x, y, z, vx, vy, vz);
        }
    }
}

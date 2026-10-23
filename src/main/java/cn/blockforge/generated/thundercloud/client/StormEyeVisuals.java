package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** 辉光沿内凹云面铺开：中心保持暗沉，亮纹沿凹壁旋入，不再用平面光盘盖住凹陷。 */
public final class StormEyeVisuals {
    private static final double TAU = Math.PI * 2.0;
    private StormEyeVisuals() {}

    /** 保留已有绘制签名。 */
    public static void draw(VertexConsumer consumer, Matrix4f matrix, CloudTypeSpec spec,
                            double cx, double cy, double cz, double seconds, double fade) {
        draw(consumer, matrix, spec, new SpiralCloudFlow(spec), 0L, cx, cy, cz, seconds, fade);
    }

    public static void draw(VertexConsumer consumer, Matrix4f matrix, CloudTypeSpec spec,
                            SpiralCloudFlow flow, long seed,
                            double cx, double cy, double cz, double seconds, double fade) {
        draw(consumer, matrix, spec, flow, seed, cx, cy, cz, seconds, fade, VortexCloudMesh.Detail.HIGH);
    }

    public static void draw(VertexConsumer consumer, Matrix4f matrix, CloudTypeSpec spec,
                            SpiralCloudFlow flow, long seed,
                            double cx, double cy, double cz, double seconds, double fade,
                            VortexCloudMesh.Detail detail) {
        draw(consumer, matrix, spec, flow, seed, cx, cy, cz, seconds, fade, detail, Double.NaN);
    }

    /**
     * @param cameraY 相机世界高度；用来只画镜头这一侧的云面辉光。
     *                另一侧本来就被不透明云核挡住，两面都画等于把这一层的面数白翻一倍。
     *                传 {@link Double#NaN} 表示两面都画（回归测试与旧调用走这条）。
     */
    public static void draw(VertexConsumer consumer, Matrix4f matrix, CloudTypeSpec spec,
                            SpiralCloudFlow flow, long seed,
                            double cx, double cy, double cz, double seconds, double fade,
                            VortexCloudMesh.Detail detail, double cameraY) {
        CloudTypeSpec.EyeSpec eye = spec.eye;
        if (eye == null || !eye.enabled || fade <= 0) return;
        int layers = Math.max(1, Math.min(6, eye.layers));
        int color = eye.resolveEyeGlow();
        int slot = CloudMaterialAtlas.slot(eye.texture);
        double alpha = Math.max(0.0, Math.min(1.0, eye.alpha)) * fade;
        double outer = Math.min(spec.clampedRadius() * 0.38,
                flow.inner() * 2.30 * Math.max(0.5, eye.sizeScale) / 3.0);
        double period = spec.rotationPeriod() / Math.max(0.1, Math.min(4.0, eye.speedScale));
        double rotation = (seconds % period) * TAU / period - SpiralCloudFlow.seedAngle(seed);
        int rings = detail.eyeRings;
        int sectors = detail.eyeSectors;
        int ribbonSteps = detail.ribbonSteps;
        boolean bothSides = Double.isNaN(cameraY)
                || Math.abs(cameraY - cy) < spec.clampedThickness();
        int firstSide = bothSides ? -1 : (cameraY > cy ? 1 : -1);
        int lastSide = bothSides ? 1 : firstSide;
        for (int side = firstSide; side <= lastSide; side += 2) {
            for (int layer = 0; layer < layers; layer++) {
                double phase = rotation + layer * 0.21;
                double radius = outer * (1.0 - layer * 0.07);
                double strength = alpha * (0.36 - layer * 0.035);
                for (int ring = 0; ring < rings; ring++) {
                    double r0 = radius * ring / rings, r1 = radius * (ring + 1) / rings;
                    for (int segment = 0; segment < sectors; segment++) {
                        double a0 = TAU * segment / sectors + phase, a1 = TAU * (segment + 1) / sectors + phase;
                        eyeVertex(consumer, matrix, slot, flow, seed, seconds, side, layer, cx, cy, cz, r0, a0, radius, phase, color, strength);
                        eyeVertex(consumer, matrix, slot, flow, seed, seconds, side, layer, cx, cy, cz, r1, a0, radius, phase, color, strength);
                        eyeVertex(consumer, matrix, slot, flow, seed, seconds, side, layer, cx, cy, cz, r1, a1, radius, phase, color, strength);
                        eyeVertex(consumer, matrix, slot, flow, seed, seconds, side, layer, cx, cy, cz, r0, a1, radius, phase, color, strength);
                    }
                }
            }
            int arms = spec.clampedSwirlArms();
            for (int arm = 0; arm < arms; arm++) {
                for (int s = 0; s < ribbonSteps; s++) {
                    double t0 = s / (double) ribbonSteps, t1 = (s + 1) / (double) ribbonSteps;
                    for (int strip = -1; strip < 1; strip++) {
                        ribbonVertex(consumer, matrix, slot, flow, seed, seconds, side, cx, cy, cz, arm, arms, t0, strip, rotation, color, alpha);
                        ribbonVertex(consumer, matrix, slot, flow, seed, seconds, side, cx, cy, cz, arm, arms, t1, strip, rotation, color, alpha);
                        ribbonVertex(consumer, matrix, slot, flow, seed, seconds, side, cx, cy, cz, arm, arms, t1, strip + 1, rotation, color, alpha);
                        ribbonVertex(consumer, matrix, slot, flow, seed, seconds, side, cx, cy, cz, arm, arms, t0, strip + 1, rotation, color, alpha);
                    }
                }
            }
        }
    }

    private static void eyeVertex(VertexConsumer consumer, Matrix4f matrix, int slot,
                                   SpiralCloudFlow flow, long seed, double seconds, int side, int layer,
                                   double cx, double cy, double cz, double r, double angle,
                                   double outer, double phase, int color, double alpha) {
        double f = r / outer;
        double swirl = 0.44 + 0.56 * Math.pow(0.5 + 0.5 * Math.cos((angle - phase) * 3.0 + f * 10.0), 2);
        double opacity = alpha * AlphaBaker.smoothstep(0.04, 0.30, f)
                * AlphaBaker.smoothstep(1.0, 0.48, f) * swirl;
        double x = Math.cos(angle) * r, z = Math.sin(angle) * r;
        double y = flow.surfaceY(x, z, seed, seconds, side) + side * (0.10 + layer * 0.018);
        vertex(consumer, matrix, cx + x, cy + y, cz + z, color, opacity,
                CloudMaterialAtlas.u(slot, 0.5 + Math.cos(angle - phase) * f * 0.48),
                CloudMaterialAtlas.v(slot, 0.5 + Math.sin(angle - phase) * f * 0.48));
    }

    private static void ribbonVertex(VertexConsumer consumer, Matrix4f matrix, int slot,
                                      SpiralCloudFlow flow, long seed, double seconds, int side,
                                      double cx, double cy, double cz, int arm, int arms, double t,
                                      int strip, double rotation, int color, double alpha) {
        double r = flow.inner() * (0.15 + t * 1.80);
        double angle = arm * TAU / arms - Math.log1p(t * 4.0) * 2.5 + rotation;
        r += strip * flow.radius() * (0.0015 + t * 0.003);
        double opacity = strip == 0 ? alpha * 0.34 * Math.sin(Math.PI * t) : 0.0;
        double x = Math.cos(angle) * r, z = Math.sin(angle) * r;
        double y = flow.surfaceY(x, z, seed, seconds, side) + side * 0.17;
        vertex(consumer, matrix, cx + x, cy + y, cz + z, color, opacity,
                CloudMaterialAtlas.u(slot, t), CloudMaterialAtlas.v(slot, 0.5 + strip * 0.45));
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z,
                               int rgb, double alpha, float u, float v) {
        consumer.vertex(matrix, (float) x, (float) y, (float) z)
                .color((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255,
                        (int) (Math.max(0.0, Math.min(1.0, alpha)) * 255.0))
                .uv(u, v).endVertex();
    }
}

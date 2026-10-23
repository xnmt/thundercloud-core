package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 沿云面逐段蔓延、分叉、回闪；白紫亮芯和紫色辉光独立于云体绘制。
 *
 * <p>电弧绝大多数趴在云底（玩家抬头那一面）；云顶只在 topChance 概率下
 * 偶发几条，既符合"云下放电"的观感，也把背面几乎看不见的几何直接省掉。</p>
 */
public final class StormArcVisuals {
    private static final double TAU = Math.PI * 2.0;
    private static final int BOTTOM = -1;
    private static final int TOP = 1;
    /** 闪光只照亮两处分附近的云皮：贴片每顶点都要投影回噪声云面，是全模组最贵的几何 */
    private static final double[] FLASH_SPOTS = {0.3, 0.72};
    /**
     * 四边形截面的 5 个固定角度查表（闭合点重复首项）。
     * 电弧只是 0.2~0.8 格粗的发光弧线，六边形与四边形在屏幕上差不到一个像素，
     * 每段却直接少掉三分之一的顶点。
     */
    private static final double[] RING_COS = new double[5];
    private static final double[] RING_SIN = new double[5];
    static {
        for (int i = 0; i <= 4; i++) {
            RING_COS[i] = Math.cos(i * TAU / 4.0);
            RING_SIN[i] = Math.sin(i * TAU / 4.0);
        }
    }
    /** 透明度低于1/255的顶点写了也看不见，直接跳过 */
    private static final double MIN_OPACITY = 0.0045;
    private StormArcVisuals() {}

    public static void draw(VertexConsumer consumer, Matrix4f matrix, CloudTypeSpec spec,
                            SpiralCloudFlow flow, double cx, double cy, double cz, long seed,
                            double seconds, double fade) {
        draw(consumer, matrix, spec, flow, cx, cy, cz, seed, seconds, fade, 0);
    }

    /**
     * @param visibleSide 只看镜头这一侧：{@code -1} 云底、{@code 1} 云顶、{@code 0} 两侧都画
     *                    （旧签名/回归测试走 0）。云核是不透明的，背面的电弧本来就被挡住。
     */
    public static void draw(VertexConsumer consumer, Matrix4f matrix, CloudTypeSpec spec,
                            SpiralCloudFlow flow, double cx, double cy, double cz, long seed,
                            double seconds, double fade, int visibleSide) {
        CloudTypeSpec.ArcSpec arcs = spec.arcs;
        if (arcs == null || !arcs.enabled) return;
        int count = arcs.clampedCount();
        if (count == 0) return;
        int budget = arcs.clampedConcurrent();
        int glow = CloudTypeSpec.parseColor(arcs.color, 0xCFB0FF);
        int core = CloudTypeSpec.parseColor(arcs.coreColor, 0xFAF3FF);
        double width = Math.max(0.035, Math.min(0.8, arcs.width)) * flow.radius() / 64.0 * 1.7;
        int material = CloudMaterialAtlas.slot("__arc_white__");
        // 先记下本帧仍在存在期内的主弧；超过性能阀时只留最亮的几条，
        // 被舍弃的整条连路径生成一起跳过，电弧数量翻倍也不会让帧开销跟着翻倍。
        SurfaceArcPath.Frame[] live = new SurfaceArcPath.Frame[count];
        int active = 0;
        for (int bolt = 0; bolt < count; bolt++) {
            SurfaceArcPath.Frame frame = SurfaceArcPath.frame(arcs, seed, bolt, seconds);
            live[bolt] = frame;
            if (frame != null) active++;
        }
        int limit = budget <= 0 ? active : Math.min(budget, active);
        for (int slot = 0; slot < limit; slot++) {
            int pick = -1;
            double best = -1.0;
            for (int bolt = 0; bolt < count; bolt++) {
                SurfaceArcPath.Frame frame = live[bolt];
                if (frame == null) continue;
                // 爬得越远、越接近回闪的越优先；同分时按组号定序，避免每帧换人导致忽明忽暗
                double priority = frame.progress() * 2.0 + frame.flash() + bolt * 1.0E-4;
                if (priority > best) {
                    best = priority;
                    pick = bolt;
                }
            }
            if (pick < 0) break;
            SurfaceArcPath.Frame frame = live[pick];
            live[pick] = null;
            if (visibleSide != 0) {
                // 只画镜头这一侧：云核不透明，背面的电弧本来就一个像素都看不到。
                drawBolt(consumer, matrix, spec, flow, material, cx, cy, cz, seed, pick, frame,
                        seconds, fade, width, glow, core, visibleSide < 0 ? BOTTOM : TOP);
                continue;
            }
            drawBolt(consumer, matrix, spec, flow, material, cx, cy, cz, seed, pick, frame,
                    seconds, fade, width, glow, core, BOTTOM);
            // 云顶是否也来一条，由 (种子, 组号, 本次放电) 的哈希裁决；默认概率很低。
            if (arcs.clampedTopChance() > 0.0 && SurfaceArcPath.chance(
                    seed ^ (frame.strike() * 0xBF58476D1CE4E5B9L)
                            ^ ((pick + 1L) * 0x94D049BB133111EBL)) < arcs.clampedTopChance()) {
                drawBolt(consumer, matrix, spec, flow, material, cx, cy, cz, seed, pick, frame,
                        seconds, fade, width, glow, core, TOP);
            }
        }
    }

    private static void drawBolt(VertexConsumer consumer, Matrix4f matrix, CloudTypeSpec spec,
                                 SpiralCloudFlow flow, int material, double cx, double cy, double cz,
                                 long seed, int bolt, SurfaceArcPath.Frame frame, double seconds,
                                 double fade, double width, int glow, int core, int side) {
        SurfaceArcPath.Point[] points = SurfaceArcPath.main(spec, flow, seed, bolt,
                frame.strike(), seconds, side);
        line(consumer, matrix, material, points, cx, cy, cz, width, glow, core, frame, fade, 0, 1);
        for (int branch = 0; branch < 2; branch++) {
            int root = (int) ((points.length - 1) * (0.28 + branch * 0.34));
            SurfaceArcPath.Point[] forks = SurfaceArcPath.branch(flow, points[root], seed,
                    branch, frame.strike(), seconds, side);
            line(consumer, matrix, material, forks, cx, cy, cz, width * 0.52,
                    glow, core, frame, fade * 0.75, root / (points.length - 1.0), 0.23);
        }
        // 闪光只照亮放电附近的云皮，不把整片天空染白。
        if (frame.flash() > 0.04) {
            for (double f : FLASH_SPOTS) {
                int root = (int) ((points.length - 1) * f);
                glowPatch(consumer, matrix, material, flow, points[root], cx, cy, cz,
                        seed, seconds, side, glow, frame.flash() * fade * 0.11);
            }
        }
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, int material,
                             SurfaceArcPath.Point[] points, double cx, double cy, double cz,
                             double width, int glow, int core, SurfaceArcPath.Frame frame, double fade,
                             double start, double span) {
        double age = frame.age() / frame.duration();
        // 尾段淡出按绝对时间收口：存在期缩短后仍是约0.1秒收尾，不会"啪"地切断
        double tail = Math.min(0.16, Math.max(0.06, frame.duration() * 0.22)) / frame.duration();
        double endFade = Math.min(1.0, Math.max(0.0, (1.0 - age) / tail));
        int last = points.length - 1;
        double step = span / last;
        double progress = frame.progress();
        double flash = frame.flash();
        for (int i = 0; i < last; i++) {
            double along = start + i / (double) last * span;
            if (along > progress) break;
            double head = Math.exp(-Math.pow((progress - along) / 0.10, 2.0));
            double energy = (0.26 + head * 0.84 + flash * 0.95) * endFade * fade;
            double fraction = Math.max(0.0, Math.min(1.0, (progress - along) / step));
            SurfaceArcPath.Point from = points[i], to = points[i + 1];
            double ax = from.x() + cx, ay = from.y() + cy, az = from.z() + cz;
            double bx = from.x() + (to.x() - from.x()) * fraction + cx;
            double by = from.y() + (to.y() - from.y()) * fraction + cy;
            double bz = from.z() + (to.z() - from.z()) * fraction + cz;
            // 最外层紫色辉光很软，跨三段只画一片：肉眼仍是连续光晕，顶点再省一档
            if (i % 3 == 0) {
                int next = Math.min(i + 3, last);
                double ex = bx, ey = by, ez = bz;
                if (start + next / (double) last * span <= progress) {
                    SurfaceArcPath.Point endpoint = points[next];
                    ex = endpoint.x() + cx; ey = endpoint.y() + cy; ez = endpoint.z() + cz;
                }
                tube(consumer, matrix, material, ax, ay, az, ex, ey, ez, width * 5.5, glow, energy * 0.055);
            }
            tube(consumer, matrix, material, ax, ay, az, bx, by, bz, width * 2.2, glow, energy * 0.20);
            tube(consumer, matrix, material, ax, ay, az, bx, by, bz, width * 0.50, core, energy * 0.85);
        }
    }

    /** 两层环×10段的双三角贴片：半径更大一档补足密度，顶点数是旧版的三分之一。 */
    private static void glowPatch(VertexConsumer consumer, Matrix4f matrix, int material,
                                  SpiralCloudFlow flow, SurfaceArcPath.Point center,
                                  double cx, double cy, double cz, long seed, double seconds,
                                  int side, int color, double alpha) {
        if (alpha < MIN_OPACITY) return;
        double radius = flow.radius() * 0.15;
        for (int ring = 0; ring < 2; ring++) {
            for (int segment = 0; segment < 10; segment++) {
                double a0 = segment * TAU / 10, a1 = (segment + 1) * TAU / 10;
                patchVertex(consumer, matrix, material, flow, center, cx, cy, cz, seed, seconds, side,
                        color, alpha, radius, ring / 2.0, a0);
                patchVertex(consumer, matrix, material, flow, center, cx, cy, cz, seed, seconds, side,
                        color, alpha, radius, (ring + 1) / 2.0, a0);
                patchVertex(consumer, matrix, material, flow, center, cx, cy, cz, seed, seconds, side,
                        color, alpha, radius, (ring + 1) / 2.0, a1);
                patchVertex(consumer, matrix, material, flow, center, cx, cy, cz, seed, seconds, side,
                        color, alpha, radius, ring / 2.0, a1);
            }
        }
    }

    private static void patchVertex(VertexConsumer consumer, Matrix4f matrix, int material,
                                    SpiralCloudFlow flow, SurfaceArcPath.Point center,
                                    double cx, double cy, double cz, long seed, double seconds, int side,
                                    int color, double alpha, double radius, double fraction, double angle) {
        SurfaceArcPath.Point p = SurfaceArcPath.project(flow, center.x() + Math.cos(angle) * radius * fraction,
                center.z() + Math.sin(angle) * radius * fraction, seed, seconds, side);
        vertex(consumer, matrix, material, cx + p.x(), cy + p.y(), cz + p.z(), color,
                alpha * (1.0 - fraction) * (1.0 - fraction));
    }

    /** 六边形管，不是朝向玩家的光片；环向角度查表，坐标全走标量，不在每帧堆里留数组。 */
    private static void tube(VertexConsumer consumer, Matrix4f matrix, int material,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double radius, int color, double opacity) {
        if (opacity < MIN_OPACITY) return;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) return;
        dx /= length; dy /= length; dz /= length;
        double ux = -dz, uz = dx;
        double len = Math.sqrt(ux * ux + uz * uz);
        if (len < 1.0E-6) {
            ux = 1; uz = 0;
        } else {
            ux /= len; uz /= len;
        }
        double vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
        for (int side = 0; side < 4; side++) {
            double x0 = (ux * RING_COS[side] + vx * RING_SIN[side]) * radius;
            double y0 = vy * RING_SIN[side] * radius;
            double z0 = (uz * RING_COS[side] + vz * RING_SIN[side]) * radius;
            double x1 = (ux * RING_COS[side + 1] + vx * RING_SIN[side + 1]) * radius;
            double y1 = vy * RING_SIN[side + 1] * radius;
            double z1 = (uz * RING_COS[side + 1] + vz * RING_SIN[side + 1]) * radius;
            vertex(consumer, matrix, material, ax + x0, ay + y0, az + z0, color, opacity);
            vertex(consumer, matrix, material, bx + x0, by + y0, bz + z0, color, opacity);
            vertex(consumer, matrix, material, bx + x1, by + y1, bz + z1, color, opacity);
            vertex(consumer, matrix, material, ax + x1, ay + y1, az + z1, color, opacity);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, int material,
                               double x, double y, double z, int rgb, double alpha) {
        consumer.vertex(matrix, (float) x, (float) y, (float) z)
                .color((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255,
                        (int) (Math.max(0.0, Math.min(1.0, alpha)) * 255.0))
                .uv(CloudMaterialAtlas.u(material, 0.5), CloudMaterialAtlas.v(material, 0.5)).endVertex();
    }
}

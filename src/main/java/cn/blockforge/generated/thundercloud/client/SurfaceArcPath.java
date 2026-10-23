package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;

/**
 * 独立的云面放电数学：稳定种子的多尺度折线，每个节点都投影回当前云面。
 *
 * <p>放电不守钟点：每次触发的时刻、蔓延方向、长短寿命都由 splitmix 哈希
 * 当场重掷，整片云上看不到成排同向或等间隔的"转圈"节奏。
 * 随机数全部走无状态的哈希链，一帧内不创建 Random、ArrayList 等短命对象。</p>
 */
public final class SurfaceArcPath {
    /** 主弧节点数：65 → 41。补点还会把长段切到约 1.4 格，节点更密没有意义。 */
    public static final int MAIN_POINTS = 41;
    /** 分叉节点数：17 → 13。 */
    public static final int BRANCH_POINTS = 13;

    private SurfaceArcPath() {}

    public record Frame(long strike, double age, double duration, double progress, double flash) {}
    public record Point(double x, double y, double z) {}

    /**
     * 某一组电弧在 seconds 时刻的状态；null 表示此刻不活跃。
     * 每个放电窗口内，触发时刻在窗口里随机挑，寿命在 0.6~1.0 倍上限之间随机掷，
     * 所以同一组的两次放电既不严格等距，长短也每次不同。
     */
    public static Frame frame(CloudTypeSpec.ArcSpec arcs, long seed, int bolt, double seconds) {
        double interval = Math.max(0.5, Math.min(30.0, arcs.intervalSeconds));
        double life = arcs.lifeSeconds();
        int groups = Math.max(1, arcs.clampedCount());
        // 每组一个固定的随机相位（不再是等间隔排队），避免全场像节拍器一样轮流通电
        double offset = chance(seed ^ 0x632BE59BD9B4E019L ^ ((bolt + 1L) * 0x9E3779B97F4A7C15L)) * interval;
        double clock = seconds + offset;
        long cycle = (long) Math.floor(clock / interval);
        double age = clock - cycle * interval;
        long base = mix(seed ^ (cycle * 0x9E3779B97F4A7C15L) ^ ((bolt + 1L) * 0x632BE59BD9B4E019L));
        double duration = Math.min(life * (0.6 + 0.4 * unitAt(base, 2)), interval * 0.88);
        double start = unitAt(base, 1) * (interval - duration);
        double t = age - start;
        if (t < 0.0 || t >= duration) return null;
        double u = t / duration;
        double progress = Math.min(1.0, u / 0.63);
        double flashWidth = Math.max(0.04, Math.min(0.24, arcs.flashSeconds / duration * 0.46));
        double flash = Math.max(0.0, 1.0 - Math.abs(u - 0.67) / flashWidth)
                + Math.max(0.0, 1.0 - Math.abs(u - 0.86) / (flashWidth * 0.55)) * 0.65;
        return new Frame(cycle, t, duration, progress, flash);
    }

    /** splitmix64 哈希到 [0,1)：给"这条电弧要不要也爬上云顶"这类一次性裁决用。 */
    public static double chance(long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 同一组随机数按序号取用，互不影响；省掉每帧新建 Random 的分配压力。 */
    private static double unitAt(long base, long index) {
        return (mix(base ^ (index * 0x9E3779B97F4A7C15L)) >>> 11) * 0x1.0p-53;
    }

    /** 41 个连续节点（旧版 65），粗、中、细三档扰动叠加；方向、半径、起点每次放电独立随机。 */
    public static Point[] main(CloudTypeSpec spec, SpiralCloudFlow flow, long seed, int bolt,
                               long strike, double seconds, int side) {
        long base = mix(seed ^ (strike * 0x9E3779B97F4A7C15L)
                ^ ((bolt + side * 31L) * 0x632BE59BD9B4E019L));
        double start = unitAt(base, 1) * SpiralCloudFlow.TAU;
        // 蔓延方向独立掷硬币：有时顺时针、有时逆时针，不再全体同向像转圈
        double spin = unitAt(base, 2) < 0.5 ? -1.0 : 1.0;
        double sweep = spin * (0.85 + unitAt(base, 3) * 0.85)
                * Math.max(0.35, Math.min(1.6, spec.arcs.lengthScale));
        double radial = flow.radius() * (0.36 + unitAt(base, 4) * 0.38);
        double drift = flow.radius() * (unitAt(base, 5) - 0.5) * 0.30;
        double[] displacement = new double[MAIN_POINTS];
        subdivide(displacement, 0, MAIN_POINTS - 1, flow.radius() * 0.085, base, 0);
        Point[] points = new Point[MAIN_POINTS];
        for (int i = 0; i < points.length; i++) {
            double t = i / (double) (MAIN_POINTS - 1);
            double a = start + t * sweep;
            double r = radial + drift * t + displacement[i];
            points[i] = project(flow, Math.cos(a) * r, Math.sin(a) * r, seed, seconds, side);
        }
        return conform(flow, points, seed, seconds, side);
    }

    public static Point[] branch(SpiralCloudFlow flow, Point root, long seed, int branch,
                                 long strike, double seconds, int side) {
        long base = mix(seed ^ (strike * 137L)
                ^ ((branch + side * 7L + 1L) * 0x632BE59BD9B4E019L));
        // 分叉朝左朝右也各掷一次，别按序号固定出手方向
        double turn = (unitAt(base, 1) < 0.5 ? -1.0 : 1.0) * (0.42 + unitAt(base, 2) * 0.85);
        double direction = Math.atan2(root.z, root.x) + turn;
        double length = flow.radius() * (0.10 + unitAt(base, 3) * 0.15);
        double[] displacement = new double[BRANCH_POINTS];
        subdivide(displacement, 0, BRANCH_POINTS - 1, flow.radius() * 0.024, base, 0);
        Point[] points = new Point[BRANCH_POINTS];
        points[0] = root;
        for (int i = 1; i < points.length; i++) {
            double t = i / (double) (BRANCH_POINTS - 1);
            double x = root.x + Math.cos(direction) * length * t - Math.sin(direction) * displacement[i];
            double z = root.z + Math.sin(direction) * length * t + Math.cos(direction) * displacement[i];
            points[i] = project(flow, x, z, seed, seconds, side);
        }
        return conform(flow, points, seed, seconds, side);
    }

    /**
     * 长段按云场细节尺度补点，避免首尾都在云面外而中段切进凸起。
     * 先数一遍补点总数、一次分配到精确长度的数组（替代 ArrayList 边加边扩容）。
     */
    private static Point[] conform(SpiralCloudFlow flow, Point[] source, long seed, double seconds, int side) {
        // 补点弦长从 半径/160（默认 64 格云 → 0.4 格）放宽到 半径/80（0.8 格）：
        // 每补一个点都要重投影回云面（surfaceY = 5 次 noise），而这条线最终是 0.2~0.8 格粗的发光弧，
        // 0.8 格步长在 64 格云的曲面上肉眼看不出差别。单条主弧的节点与投影开销因此直接减半。
        double step = flow.radius() / 45.0;
        int total = 1;
        for (int i = 0; i < source.length - 1; i++) {
            total += pieces(source[i], source[i + 1], step);
        }
        Point[] result = new Point[total];
        result[0] = source[0];
        int index = 1;
        for (int i = 0; i < source.length - 1; i++) {
            Point a = source[i], b = source[i + 1];
            int pieces = pieces(a, b, step);
            for (int part = 1; part <= pieces; part++) {
                double t = part / (double) pieces;
                result[index++] = project(flow, a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t, seed, seconds, side);
            }
        }
        return result;
    }

    private static int pieces(Point a, Point b, double step) {
        return Math.max(1, (int) Math.ceil(Math.hypot(b.x - a.x, b.z - a.z) / step));
    }

    /** 中点位移折线：每个节点用 (层深+位置) 哈希取随机量，递归序固定、结果可复现。 */
    private static void subdivide(double[] values, int from, int to, double amplitude, long base, int depth) {
        if (to - from < 2) return;
        int mid = (from + to) / 2;
        values[mid] = (values[from] + values[to]) * 0.5
                + (unitAt(base, depth * 128L + mid) * 2.0 - 1.0) * amplitude;
        subdivide(values, from, mid, amplitude * 0.52, base, depth + 1);
        subdivide(values, mid, to, amplitude * 0.52, base, depth + 1);
    }

    public static Point project(SpiralCloudFlow flow, double x, double z, long seed, double seconds, int side) {
        double r = Math.hypot(x, z);
        double limit = flow.solidRadius(Math.atan2(z, x) + SpiralCloudFlow.seedAngle(seed)
                - flow.rotation(seconds)) * 0.94;
        if (r > limit) { x *= limit / r; z *= limit / r; }
        double clearance = Math.max(0.12, flow.radius() * 0.006);
        return new Point(x, flow.surfaceY(x, z, seed, seconds, side) + side * clearance, z);
    }
}

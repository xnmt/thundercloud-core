package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;

/** 缓慢平流的连续螺旋云场：宽旋臂、细云纹和内凹但封闭的漩涡中心共用一个曲面。 */
public final class SpiralCloudFlow {
    public static final double TAU = Math.PI * 2.0;
    private final CloudTypeSpec spec;
    private final double radius;
    private final double inner;

    public SpiralCloudFlow(CloudTypeSpec spec) {
        this.spec = spec;
        radius = spec.clampedRadius();
        inner = radius * spec.clampedEyeOpening();
    }

    public double radius() { return radius; }
    public double inner() { return inner; }

    public double angularSpeed(double r) {
        double f = clamp((r - inner) / (radius - inner));
        return TAU / (spec.corePeriod() + (spec.edgePeriod() - spec.corePeriod()) * f);
    }

    /** 整体真实旋转的相位；所有云纹、材质与边缘使用同一方向，取模避免长存档精度损失。 */
    public double rotation(double seconds) {
        return (seconds % spec.rotationPeriod()) / spec.rotationPeriod() * TAU;
    }

    public double materialAngle(double r, double angle, double seconds) {
        return angle - rotation(seconds) + winding(r / radius);
    }

    private double winding(double fraction) {
        return spec.clampedSwirlTurns() * TAU * Math.log1p(Math.max(0.0, fraction) * 4.0) / Math.log(5.0);
    }

    /** 宽阔、边缘受云噪声扰动的旋臂；不是等距径向圆环，也不在谷底挖透明裂缝。 */
    public double ridge(double r, double angle, double seconds) {
        double f = r / radius;
        double a = materialAngle(r, angle, seconds);
        double x = Math.cos(a) * f * 8.0, z = Math.sin(a) * f * 8.0;
        double wx = noise(x * 0.52 + 13.4, z * 0.52 - 7.1) - 0.5;
        double wz = noise(x * 0.52 - 5.7, z * 0.52 + 19.3) - 0.5;
        x += wx * 1.2; z += wz * 1.2;
        double broad = noise(x, z);
        double medium = noise(x * 2.17 + 31.7, z * 2.17 - 11.3);
        // 随云体转动的宽旋臂给出清晰轮廓；内部细纹再以不同速率缓慢流动。
        double arms = 0.5 + 0.5 * Math.cos(spec.clampedSwirlArms() * a + wx * 0.70);
        arms = smooth(0.12, 0.92, arms);
        double detailAngle = a - (seconds % (spec.corePeriod() * 32.0))
                * TAU / (spec.corePeriod() * 32.0);
        double detailScale = spec.clampedVortexBands() * 1.7;
        double fine = noise(Math.cos(detailAngle) * f * detailScale + 9.3,
                Math.sin(detailAngle) * f * detailScale - 23.1);
        double armWeight = smooth(inner * 0.35, inner * 1.4, r);
        return clamp(0.16 + 0.40 * arms * armWeight + 0.24 * broad + 0.12 * medium + 0.08 * fine);
    }

    /** 同一个云场同时驱动颜色和厚度。核心是封闭水平云底，不是透天的洞。 */
    public double halfHeight(double r, double angle, double seconds) {
        return heightFromDensity(r, angle - rotation(seconds), ridge(r, angle, seconds));
    }

    public double heightFromDensity(double r, double angle, double density) {
        // 凹陷延伸到更宽的中心区域；缓坡仍有实体厚度，不会挖穿成天空洞。
        double eyeBlend = smooth(0.0, inner * 2.30, r);
        double cavity = 1.0 - smooth(inner * 0.06, inner * 2.20, r);
        double rim = Math.exp(-Math.pow((r - inner * 1.90) / (inner * 0.82), 2.0));
        double edge = edgeRadius(angle);
        double roundEdge = Math.sqrt(Math.max(0.0, 1.0 - Math.pow(clamp(r / edge), 14.0)));
        double bulk = 0.34 + 0.43 * density * eyeBlend + 0.07 * rim;
        // 中心比周围薄：上下两侧看都是向内部收凹，绝非向下拉长的龙卷风漏斗。
        return spec.clampedThickness() * bulk * (1.0 - spec.clampedEyeDepression() * cavity)
                * roundEdge + 0.06;
    }

    /** 不随视角变化的参差轮廓，始终不超出配置半径。 */
    public double edgeRadius(double angle) {
        double wobble = 0.50 * Math.sin(angle * 5.0 + Math.sin(angle * 2.0) * 1.6)
                + 0.32 * Math.sin(angle * 11.0 - 1.3) + 0.18 * Math.sin(angle * 23.0 + 2.6);
        return radius * (0.957 + 0.043 * (0.5 + 0.5 * wobble));
    }

    /** 不透明主体的位置；最外约两格留给柔软薄雾，不在云内部挖透明裂缝。 */
    public double solidRadius(double angle) { return edgeRadius(angle) * 0.968; }

    public double surfaceY(double x, double z, long seed, double seconds, int side) {
        double r = Math.hypot(x, z);
        double angle = Math.atan2(z, x) + seedAngle(seed);
        return side * halfHeight(r, angle, seconds);
    }

    public static double seedAngle(long seed) { return (seed & 65535L) / 65536.0 * TAU; }

    private static double noise(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
        fz = fz * fz * fz * (fz * (fz * 6.0 - 15.0) + 10.0);
        double a = lattice(ix, iz), b = lattice(ix + 1, iz);
        double c = lattice(ix, iz + 1), d = lattice(ix + 1, iz + 1);
        return (a + (b - a) * fx) * (1.0 - fz) + (c + (d - c) * fx) * fz;
    }

    private static double lattice(int x, int z) {
        return unit(x * 0x632BE59BD9B4E019L ^ z * 0x9E3779B97F4A7C15L);
    }

    private static double unit(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return ((z ^ (z >>> 31)) >>> 11) * 0x1.0p-53;
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static double smooth(double a, double b, double value) {
        double t = clamp((value - a) / (b - a));
        return t * t * (3.0 - 2.0 * t);
    }
}

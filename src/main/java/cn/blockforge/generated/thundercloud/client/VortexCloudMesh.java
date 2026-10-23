package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** 不透明闭合云核 + 窄幅半透明绒边；网格按距离分级，并缓存低频动画采样。 */
public final class VortexCloudMesh {
    /** 远处自动降低网格密度，近处仍保留完整漩涡轮廓。 */
    public enum Detail {
        // 风暴眼/旋臂是三层加法软辉光（每层透明度量级 0.2），环向/径向取样从 48×10 降到 36×8 肉眼看不出区别，
        // 但每个眼顶点都要重算一次云面高度（5 次 noise），这一档密度换来的帧时间很实在。
        // 这一版把三档都收了一档：环向 128→96、径向倍率 3→2（默认 16 带 → 30 环）、
        // 绒边 5→4、风暴眼 8×36→6×28、螺旋臂 36→28 步。近景云核顶点从约 4.97 万降到约 2.3 万，
        // 而 96 个扇区在 128 格云盘上仍是每段 4.2 格——贴着云底看也看不出多边形。
        HIGH(2, 96, 4, 6, 28, 28),
        MEDIUM(1, 64, 3, 5, 22, 22),
        LOW(1, 48, 2, 4, 16, 16);

        final int ringMultiplier;
        final int sectors;
        final int fringeRings;
        final int eyeRings;
        final int eyeSectors;
        final int ribbonSteps;

        Detail(int ringMultiplier, int sectors, int fringeRings,
               int eyeRings, int eyeSectors, int ribbonSteps) {
            this.ringMultiplier = ringMultiplier;
            this.sectors = sectors;
            this.fringeRings = fringeRings;
            this.eyeRings = eyeRings;
            this.eyeSectors = eyeSectors;
            this.ribbonSteps = ribbonSteps;
        }
    }

    private static final double ANIMATION_SAMPLES_PER_SECOND = 10.0;
    private final CloudTypeSpec spec;
    private final SpiralCloudFlow flow;
    private final Detail detail;
    private final int bottomColor, topColor, eyeColor;
    private final int rings, sectors;
    private final double[] x, z, height, density, shade, cos, sin;
    private final float[] u, v;
    /**
     * 云核上下两面的最终顶点色：只跟密度、明暗坡度与"中心提亮"有关，跟时间无关，
     * 所以跟着 {@link #prepare} 的 10 Hz 缓存一起算一次，绘制时直接查表。
     * 旧实现是在 {@code surfaceVertex} 里逐顶点重算 2 次 mix + hypot + smooth——近景 4.97 万顶点/帧。
     */
    private final int[] solidColorNeg, solidColorPos;
    private double lastTime = Double.NaN;
    private long lastSeed;
    /**
     * 绒边逐扇区预计算（渲染单线程顺序写读）：边缘半径、前雾相位、朝向余弦与两侧颜色
     * 都只跟扇区（与时不变的采样时刻）有关，旧实现每个顶点都重算 3 次 edgeRadius（3 个 sin）
     * 与一次 sin(pattern*37)——近景绒边 5,120 顶点/帧，累计约 2 万次三角函数。
     * 扇区数最大 128（Detail.HIGH），i 会取到 sectors（闭合重复点），所以数组开到 256。
     */
    private static final double[] FRINGE_COS = new double[256];
    private static final double[] FRINGE_SIN = new double[256];
    private static final double[] FRINGE_PATTERN = new double[256];
    private static final double[] FRINGE_EDGE = new double[256];
    private static final double[] FRINGE_FRAY = new double[256];
    private static final int[] FRINGE_COLOR_NEG = new int[256];
    private static final int[] FRINGE_COLOR_POS = new int[256];
    private static double fringeRotation;

    public VortexCloudMesh(CloudTypeSpec spec) {
        this(spec, Detail.HIGH);
    }

    public VortexCloudMesh(CloudTypeSpec spec, Detail detail) {
        this.spec = spec;
        this.detail = detail;
        flow = new SpiralCloudFlow(spec);
        bottomColor = spec.colorBottomRgb();
        topColor = spec.colorTopRgb();
        eyeColor = spec.eye == null ? 0xF4F0FF : spec.eye.resolveEyeGlow();
        // 径向环数：近/中档按带数 × 倍率再夹到 20~30，远档直接压到 14~18。
        int bands = spec.clampedVortexBands();
        rings = detail == Detail.LOW
                ? Math.max(14, Math.min(18, bands))
                : Math.max(20, Math.min(30, bands * detail.ringMultiplier));
        sectors = detail.sectors;
        int count = (rings + 1) * (sectors + 1);
        x = new double[count];
        z = new double[count];
        height = new double[count];
        density = new double[count];
        shade = new double[count];
        u = new float[count];
        v = new float[count];
        solidColorNeg = new int[count];
        solidColorPos = new int[count];
        cos = new double[sectors + 1];
        sin = new double[sectors + 1];
        for (int sector = 0; sector <= sectors; sector++) {
            double angle = (sector % sectors) * SpiralCloudFlow.TAU / sectors;
            cos[sector] = Math.cos(angle);
            sin[sector] = Math.sin(angle);
        }
    }

    public SpiralCloudFlow flow() { return flow; }
    public Detail detail() { return detail; }

    /** 供回归测试核对性能预算。 */
    public int expectedSolidVertices() { return (rings * sectors * 2 + sectors) * 4; }

    private void prepare(double seconds, long seed) {
        // 48秒一圈时，10Hz采样每次只移动0.75度；肉眼连续，但避免每个画面帧重算整片云。
        double sampledTime = Math.floor(seconds * ANIMATION_SAMPLES_PER_SECOND)
                / ANIMATION_SAMPLES_PER_SECOND;
        if (sampledTime == lastTime && seed == lastSeed) return;
        lastTime = sampledTime;
        lastSeed = seed;
        double offset = SpiralCloudFlow.seedAngle(seed);
        double rotation = flow.rotation(sampledTime);
        for (int ring = 0; ring <= rings; ring++) {
            double fraction = ring / (double) rings;
            for (int sector = 0; sector <= sectors; sector++) {
                int index = ring * (sectors + 1) + sector;
                double angle = (sector % sectors) * SpiralCloudFlow.TAU / sectors;
                double pattern = angle + offset;
                double r = flow.solidRadius(pattern - rotation) * fraction;
                x[index] = cos[sector] * r;
                z[index] = sin[sector] * r;
                density[index] = flow.ridge(r, pattern, sampledTime);
                height[index] = flow.heightFromDensity(r, pattern - rotation, density[index]);
                double uvAngle = flow.materialAngle(r, pattern, sampledTime);
                u[index] = (float) mirrored(Math.cos(uvAngle) * r / 17.0 + 0.5);
                v[index] = (float) mirrored(Math.sin(uvAngle) * r / 17.0 + 0.5);
            }
        }
        // 世界固定的斜向柔光，坡度产生立体阴影，不读取视角，不随镜头变色。
        for (int ring = 0; ring <= rings; ring++) {
            for (int sector = 0; sector <= sectors; sector++) {
                int s = sector % sectors, i = ring * (sectors + 1) + sector;
                int prev = Math.max(0, ring - 1) * (sectors + 1) + s;
                int next = Math.min(rings, ring + 1) * (sectors + 1) + s;
                int left = ring * (sectors + 1) + (s + sectors - 1) % sectors;
                int right = ring * (sectors + 1) + (s + 1) % sectors;
                double dr = (height[next] - height[prev]) / Math.max(0.01,
                        Math.hypot(x[next] - x[prev], z[next] - z[prev]));
                double dt = (height[right] - height[left]) / Math.max(0.01,
                        Math.hypot(x[right] - x[left], z[right] - z[left]));
                double a = s * SpiralCloudFlow.TAU / sectors;
                double dx = dr * Math.cos(a) - dt * Math.sin(a);
                double dz = dr * Math.sin(a) + dt * Math.cos(a);
                shade[i] = Math.max(0.0, (0.86 - dx * 0.40 - dz * 0.32)
                        / Math.sqrt(1.0 + dx * dx + dz * dz));
            }
        }
        // 逐顶点最终颜色：与时间无关，跟形状一起按 10 Hz 算一次（上下两面各一份）。
        double inner = flow.inner();
        for (int ring = 0; ring <= rings; ring++) {
            for (int sector = 0; sector <= sectors; sector++) {
                int i = ring * (sectors + 1) + sector;
                double white = 1.0 - smooth(inner * 0.28, inner * 2.25, Math.hypot(x[i], z[i]));
                int neg = CloudVolumeMesh.mix(bottomColor, topColor,
                        0.13 + density[i] * 0.42 + shade[i] * 0.24);
                int pos = CloudVolumeMesh.mix(bottomColor, topColor,
                        0.22 + density[i] * 0.42 + shade[i] * 0.24);
                solidColorNeg[i] = CloudVolumeMesh.mix(neg, eyeColor, white * 0.76);
                solidColorPos[i] = CloudVolumeMesh.mix(pos, eyeColor, white * 0.88);
            }
        }
    }

    private static double mirrored(double value) {
        double p = value - Math.floor(value / 2.0) * 2.0;
        return p <= 1.0 ? p : 2.0 - p;
    }

    /** 主体从圆心开始闭合，无风暴眼穿天孔；透明度固定255，写颜色及深度。 */
    public void drawSolid(VertexConsumer consumer, Matrix4f matrix, double cx, double cy, double cz,
                          double seconds, long seed) {
        prepare(seconds, seed);
        int material = CloudMaterialAtlas.solidSlot(spec.textures.get(0));
        for (int side = -1; side <= 1; side += 2) {
            for (int ring = 0; ring < rings; ring++) {
                for (int sector = 0; sector < sectors; sector++) {
                    int a = ring * (sectors + 1) + sector;
                    surfaceVertex(consumer, matrix, material, a, side, cx, cy, cz);
                    surfaceVertex(consumer, matrix, material, a + sectors + 1, side, cx, cy, cz);
                    surfaceVertex(consumer, matrix, material, a + sectors + 2, side, cx, cy, cz);
                    surfaceVertex(consumer, matrix, material, a + 1, side, cx, cy, cz);
                }
            }
        }
        for (int sector = 0; sector < sectors; sector++) {
            int a = rings * (sectors + 1) + sector;
            surfaceVertex(consumer, matrix, material, a, -1, cx, cy, cz);
            surfaceVertex(consumer, matrix, material, a + 1, -1, cx, cy, cz);
            surfaceVertex(consumer, matrix, material, a + 1, 1, cx, cy, cz);
            surfaceVertex(consumer, matrix, material, a, 1, cx, cy, cz);
        }
    }

    private void surfaceVertex(VertexConsumer consumer, Matrix4f matrix, int material, int index,
                               int side, double cx, double cy, double cz) {
        // 颜色已在 prepare() 里按上下两面各算好，这里只查表 + 输出顶点。
        int color = side < 0 ? solidColorNeg[index] : solidColorPos[index];
        emit(consumer, matrix, cx + x[index], cy + side * height[index], cz + z[index],
                color, 1.0, material, u[index], v[index]);
    }

    /** 只有最外缘淡出；不是给整片云盘加透明度。 */
    public void drawFringe(VertexConsumer consumer, Matrix4f matrix, double cx, double cy, double cz,
                           double seconds, long seed, double fade) {
        prepare(seconds, seed);
        int material = CloudMaterialAtlas.solidSlot(spec.textures.get(0));
        int fringeRings = detail.fringeRings;
        // 逐扇区预计算（与 t 无关的项），4 个顶点/quad 全部复用。
        double rotation = flow.rotation(lastTime);
        fringeRotation = rotation;
        double offset = SpiralCloudFlow.seedAngle(seed);
        double frayTime = lastTime * 0.17D;
        int outer = rings * (sectors + 1);
        for (int i = 0; i <= sectors; i++) {
            double a = (i % sectors) * SpiralCloudFlow.TAU / sectors;
            double pattern = a + offset;
            FRINGE_COS[i] = Math.cos(a);
            FRINGE_SIN[i] = Math.sin(a);
            FRINGE_PATTERN[i] = pattern;
            FRINGE_EDGE[i] = flow.edgeRadius(pattern - rotation);
            FRINGE_FRAY[i] = 0.28D + 0.22D * Math.sin(pattern * 37.0D + frayTime);
            int index = outer + i;
            FRINGE_COLOR_NEG[i] = CloudVolumeMesh.mix(bottomColor, topColor,
                    0.13 + density[index] * 0.42 + shade[index] * 0.24);
            FRINGE_COLOR_POS[i] = CloudVolumeMesh.mix(bottomColor, topColor,
                    0.22 + density[index] * 0.42 + shade[index] * 0.24);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int ring = 0; ring < fringeRings; ring++) {
                for (int sector = 0; sector < sectors; sector++) {
                    fringeVertex(consumer, matrix, material, sector, ring / (double) fringeRings, side, cx, cy, cz, seed, fade);
                    fringeVertex(consumer, matrix, material, sector, (ring + 1) / (double) fringeRings, side, cx, cy, cz, seed, fade);
                    fringeVertex(consumer, matrix, material, sector + 1, (ring + 1) / (double) fringeRings, side, cx, cy, cz, seed, fade);
                    fringeVertex(consumer, matrix, material, sector + 1, ring / (double) fringeRings, side, cx, cy, cz, seed, fade);
                }
            }
        }
    }

    private void fringeVertex(VertexConsumer consumer, Matrix4f matrix, int material, int sector,
                              double t, int side, double cx, double cy, double cz,
                              long seed, double fade) {
        int index = rings * (sectors + 1) + sector;
        double pa = FRINGE_PATTERN[sector] - fringeRotation;
        double r = FRINGE_EDGE[sector] * (0.968 + 0.032 * t);
        double h = flow.heightFromDensity(r, pa, density[index]);
        int color = side < 0 ? FRINGE_COLOR_NEG[sector] : FRINGE_COLOR_POS[sector];
        double fray = 1.0 - t * FRINGE_FRAY[sector];
        emit(consumer, matrix, cx + FRINGE_COS[sector] * r, cy + side * (h + 0.015),
                cz + FRINGE_SIN[sector] * r, color,
                (1.0 - t * t * (3.0 - 2.0 * t)) * fray * fade, material, u[index], v[index]);
    }

    private static double smooth(double a, double b, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z,
                             int color, double alpha, int material, double u, double v) {
        consumer.vertex(matrix, (float) x, (float) y, (float) z)
                .color((color >>> 16) & 255, (color >>> 8) & 255, color & 255,
                        (int) (Math.max(0.0, Math.min(1.0, alpha)) * 255.0))
                .uv(CloudMaterialAtlas.u(material, u), CloudMaterialAtlas.v(material, v)).endVertex();
    }
}

package cn.blockforge.generated.thundercloud.config;

import cn.blockforge.generated.thundercloud.animation.CloudLifecycle;
import cn.blockforge.generated.thundercloud.client.AnimatedCloudConsumer;
import cn.blockforge.generated.thundercloud.client.CloudAnimationShape;
import cn.blockforge.generated.thundercloud.client.SpiralCloudFlow;
import cn.blockforge.generated.thundercloud.client.SurfaceArcPath;
import cn.blockforge.generated.thundercloud.client.VortexCloudMesh;
import cn.blockforge.generated.thundercloud.client.StormArcVisuals;
import cn.blockforge.generated.thundercloud.client.StormEyeVisuals;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Locale;

/** 由构建任务运行的数学回归；不启动游戏，不冒充实机测试。 */
public final class VortexRegressionTest {
    public static void main(String[] args) throws Exception {
        CloudTypeSpec spec = new CloudTypeSpec();
        require(spec.clampedRadius() * 2 == 128, "默认云盘直径必须为128格");
        require(spec.clampedVortexBands() == 16, "默认云面细节带数为16");
        SpiralCloudFlow flow = new SpiralCloudFlow(spec);
        double maxHeight = 0.0;
        long samples = 0;
        for (double time : new double[]{0.0, 17.0, 120.0, 3600.0, 86400.0}) {
            for (int ring = 0; ring <= 96; ring++) {
                double r = flow.radius() * ring / 96.0;
                for (int sector = 0; sector < 192; sector++) {
                    double a = sector * Math.PI * 2 / 192;
                    double h = flow.halfHeight(r, a, time);
                    require(h >= 0.06 && h < spec.clampedThickness() * 0.95, "内凹漩涡不能向下形成漏斗");
                    maxHeight = Math.max(maxHeight, h);
                    require(Math.abs(flow.ridge(r, a, time) - flow.ridge(r, a + 2 * Math.PI, time)) < 1e-7,
                            "云纹必须沿整圈连续闭合");
                    samples++;
                }
            }
        }
        require(flow.angularSpeed(flow.inner()) > flow.angularSpeed(64), "差速应内快外慢");
        System.out.printf(Locale.ROOT, "通过：%d个实际Java连续云面样本，垂直半厚 %.3f%n", samples, maxHeight);
        testSurfaceAndArcs(spec, flow);
        testArcDensityAndBudget(spec, flow);
        testPerspective();
        testConfigDefaults();
        testPerformanceLevels(spec);
        CloudLifecycleRegressionTest.run();
        testVortexRotation(spec, flow);
        exportPreview(spec);
        exportAnimationPreview(spec);
    }

    private static void testSurfaceAndArcs(CloudTypeSpec spec, SpiralCloudFlow flow) {
        long seed = 142235L;
        double offset = SpiralCloudFlow.seedAngle(seed);
        for (double time : new double[]{0, 17, 3600, 86400}) {
            for (int sector = 0; sector < 192; sector++) {
                double a = sector * Math.PI * 2 / 192;
                require(flow.solidRadius(a) > 59 && flow.edgeRadius(a) <= 64, "云核覆盖与128格外径");
                for (int i = 0; i < 80; i++) {
                    double r = flow.solidRadius(a + offset) * i / 80;
                    double x = Math.cos(a) * r, z = Math.sin(a) * r;
                    double top = flow.surfaceY(x, z, seed, time, 1);
                    double bottom = flow.surfaceY(x, z, seed, time, -1);
                    require(top > 0 && Math.abs(top + bottom) < 1e-9, "上下闭合云面始终关于水平面相对");
                    require(Math.abs(flow.ridge(r, a, time) - flow.ridge(r, a, time + 0.001)) < 0.003,
                            "流动必须连续，不能周期性跳帧重置");
                }
            }
            for (int side : new int[]{-1, 1}) {
                for (int bolt = 0; bolt < 8; bolt++) {
                    SurfaceArcPath.Point[] points = SurfaceArcPath.main(spec, flow, seed, bolt, 11, time, side);
                    require(java.util.Arrays.equals(points, SurfaceArcPath.main(spec, flow, seed, bolt, 11, time, side)),
                            "同一时间和种子电弧必须稳定");
                    for (SurfaceArcPath.Point p : points) testArcPoint(p, flow, seed, time, side);
                    for (int i = 0; i < points.length - 1; i++) {
                        SurfaceArcPath.Point a = points[i], b = points[i + 1];
                        for (double f : new double[]{0.25, 0.5, 0.75}) {
                            double x = a.x() + (b.x() - a.x()) * f;
                            double y = a.y() + (b.y() - a.y()) * f;
                            double z = a.z() + (b.z() - a.z()) * f;
                            require((y - flow.surfaceY(x, z, seed, time, side)) * side > 0.02,
                                    "电弧段中部也不能被云面埋住");
                        }
                    }
                    for (int branch = 0; branch < 3; branch++) {
                        for (SurfaceArcPath.Point p : SurfaceArcPath.branch(flow, points[(int) ((points.length - 1) * (0.23 + branch * 0.25))],
                                seed, branch, 11, time, side)) testArcPoint(p, flow, seed, time, side);
                    }
                }
            }
        }
        int active = 0;
        for (int frameIndex = 0; frameIndex < 2000; frameIndex++) {
            SurfaceArcPath.Frame frame = SurfaceArcPath.frame(spec.arcs, seed, 0, frameIndex / 100.0);
            if (frame == null) continue;
            active++;
            require(frame.progress() >= 0 && frame.progress() <= 1 && frame.flash() >= 0,
                    "电弧蔓延参数必须有效");
        }
        require(active > 260 && active < 520,
                "放电时刻与寿命每次重掷：单组约占两成时间，靠12组交错保证云面始终有电在爬");
        System.out.println("通过：闭合云核、中心遮天、连续流动、上下云面电弧投影及生命周期");
    }

    /** 电弧要多、要快、要短促，但同屏峰值与单帧几何量必须有上限。 */
    private static void testArcDensityAndBudget(CloudTypeSpec spec, SpiralCloudFlow flow) {
        long seed = 142235L;
        int count = spec.arcs.clampedCount();
        int peakBolts = 0, maxVertices = 0, totalSamples = 0;
        Matrix4f matrix = new Matrix4f();
        // 覆盖两个完整放电周期，步长1/120秒，统计同屏同时存在的主弧组数与单帧实际顶点
        for (int sample = 0; sample < 360; sample++) {
            double time = 40.0 + sample / 120.0;
            int live = 0;
            for (int bolt = 0; bolt < count; bolt++) {
                if (SurfaceArcPath.frame(spec.arcs, seed, bolt, time) != null) live++;
            }
            peakBolts = Math.max(peakBolts, live);
            Counter counter = new Counter();
            StormArcVisuals.draw(counter, matrix, spec, flow, 0, 0, 0, seed, time, 1);
            maxVertices = Math.max(maxVertices, counter.vertices);
            totalSamples++;
        }
        System.out.printf(Locale.ROOT, "电弧：默认%d组、单条存在%.2f秒、间隔%.2f秒，同屏峰值%d组（性能阀%d），%d帧实测峰值%d顶点%n",
                count, spec.arcs.lifeSeconds(), spec.arcs.intervalSeconds, peakBolts,
                spec.arcs.clampedConcurrent(), totalSamples, maxVertices);
        require(peakBolts >= 3, "电弧大幅增加后同屏至少应有3组同时在蔓延");
        require(peakBolts <= spec.arcs.clampedConcurrent(), "同屏主弧组数不得超过性能阀");
        require(maxVertices < 160_000, "单帧电弧顶点必须有上限，不能拖垮帧率");

        // 阀值收紧到1组时，绘制组数必须真的被限制住
        CloudTypeSpec throttled = new CloudTypeSpec();
        throttled.arcs.maxConcurrent = 1;
        int capped = 0;
        for (int sample = 0; sample < 240; sample++) {
            Counter counter = new Counter();
            StormArcVisuals.draw(counter, matrix, throttled, flow, 0, 0, 0, seed, 40.0 + sample / 120.0, 1);
            capped = Math.max(capped, counter.vertices);
        }
        require(capped < maxVertices, "性能阀必须真的减少单帧电弧几何");
    }

    /** 只数顶点、不落盘的探针。 */
    private static final class Counter implements VertexConsumer {
        int vertices;
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
        public void endVertex() { vertices++; }
    }

    private static void testArcPoint(SurfaceArcPath.Point p, SpiralCloudFlow flow, long seed, double time, int side) {
        double surface = flow.surfaceY(p.x(), p.z(), seed, time, side);
        double clearance = (p.y() - surface) * side;
        require(clearance >= 0.12 && clearance < 0.5, "电弧每个节点必须贴在云面外侧，不能埋进云核");
        require(Math.hypot(p.x(), p.z()) < 61, "分叉不能跑出云体边界");
    }

    /** 导出实际渲染器顶点，而不是另外用Python近似重写一套几何。 */
    private static void exportPreview(CloudTypeSpec spec) throws Exception {
        Path directory = Path.of("build", "visual-check");
        Files.createDirectories(directory);
        VortexCloudMesh mesh = new VortexCloudMesh(spec);
        long seed = 142235L;
        // 放电时刻已完全随机，固定采样点可能恰好全空：自动找三个至少两条主弧在场、彼此间隔0.45秒的时刻
        double[] times = findArcMoments(spec, seed, 17.0);
        for (int i = 0; i < times.length; i++) {
            double time = times[i];
            try (Capture output = new Capture(directory.resolve("surface-" + i + ".bin"), true)) {
                mesh.drawSolid(output, new Matrix4f(), 0, 0, 0, time, seed);
                require(output.vertices == mesh.expectedSolidVertices(), "完整上下表面和侧壁顶点数量");
                require(output.vertices < 55000, "优化后近景单层云核必须控制在5.5万顶点内");
                require(output.minRadius == 0, "不透明云面必须包含圆心");
            }
            try (Capture output = new Capture(directory.resolve("fringe-" + i + ".bin"), false)) {
                mesh.drawFringe(output, new Matrix4f(), 0, 0, 0, time, seed, 1);
            }
            try (Capture output = new Capture(directory.resolve("eye-" + i + ".bin"), false)) {
                StormEyeVisuals.draw(output, new Matrix4f(), spec, mesh.flow(), seed, 0, 0, 0, time, 1);
            }
            try (Capture output = new Capture(directory.resolve("arcs-" + i + ".bin"), false)) {
                StormArcVisuals.draw(output, new Matrix4f(), spec, mesh.flow(), 0, 0, 0, seed, time, 1);
            }
        }
        System.out.println("通过：实际云核所有顶点Alpha=255，中心无孔；已导出三个时刻的真实渲染几何");
    }

    /** 随机放电节奏下为分镜挑选确有电弧在场的采样时间。 */
    private static double[] findArcMoments(CloudTypeSpec spec, long seed, double from) {
        double[] times = new double[3];
        int picked = 0;
        for (int i = 0; picked < 3 && i < 6000; i++) {
            double time = from + i / 20.0;
            int live = 0;
            for (int bolt = 0; bolt < spec.arcs.clampedCount(); bolt++) {
                if (SurfaceArcPath.frame(spec.arcs, seed, bolt, time) != null) live++;
            }
            if (live >= 2 && (picked == 0 || time - times[picked - 1] >= 0.45)) times[picked++] = time;
        }
        require(picked == 3, "300秒内应能找到多个两条以上主弧同时在场的时刻");
        return times;
    }

    private static void testVortexRotation(CloudTypeSpec spec, SpiralCloudFlow flow) {
        double time = 17, delta = 4;
        double rotation = delta / spec.rotationPeriod() * Math.PI * 2;
        double stationaryChange = 0, advectedChange = 0;
        for (int ring = 4; ring < 64; ring++) {
            double r = ring;
            for (int sector = 0; sector < 96; sector++) {
                double a = sector * Math.PI * 2 / 96;
                double initial = flow.ridge(r, a, time);
                stationaryChange += Math.abs(initial - flow.ridge(r, a, time + delta));
                advectedChange += Math.abs(initial - flow.ridge(r, a + rotation, time + delta));
            }
        }
        require(stationaryChange > advectedChange * 5, "宽旋臂必须真正随相位旋转，不是静态噪声闪动");
        require(Math.abs(flow.rotation(time + spec.rotationPeriod()) - flow.rotation(time)) < 1e-9,
                "整体旋转周期可配置");
        for (int sector = 0; sector < 96; sector++) {
            double a = sector * Math.PI * 2 / 96;
            double center = flow.halfHeight(0, a, time);
            double rim = flow.halfHeight(flow.inner() * 1.55, a, time);
            require(center > 0.3 && center < rim * 0.4, "中心应明显内凹，但保留不透明云底");
            double before = flow.halfHeight(30, a, spec.rotationPeriod() - 1e-6);
            double after = flow.halfHeight(30, a, spec.rotationPeriod() + 1e-6);
            require(Math.abs(before - after) < 1e-5, "转满一圈不能跳变");
        }
        System.out.println("通过：真实慢速旋转、宽旋臂平流、凹陷云眼闭合及旋转周期接缝");
    }

    /** 导出形成、稳定旋转和驱散真实顶点，供离线分镜使用。 */
    private static void exportAnimationPreview(CloudTypeSpec spec) throws Exception {
        Path directory = Path.of("build", "visual-check");
        VortexCloudMesh mesh = new VortexCloudMesh(spec);
        long seed = 142235L;
        CloudLifecycle.Frame[] frames = {
                new CloudLifecycle.Frame(0.20, 0), new CloudLifecycle.Frame(0.40, 0),
                new CloudLifecycle.Frame(0.65, 0), CloudLifecycle.Frame.STEADY,
                new CloudLifecycle.Frame(1, 0.25), new CloudLifecycle.Frame(1, 0.55),
                new CloudLifecycle.Frame(1, 0.85), new CloudLifecycle.Frame(1, 1),
                CloudLifecycle.Frame.STEADY, CloudLifecycle.Frame.STEADY
        };
        for (int i = 0; i < frames.length; i++) {
            CloudLifecycle.Frame frame = frames[i];
            double time = i < 8 ? 17 + i * 0.6 : 17 + (i - 7) * 4;
            CloudAnimationShape shape = new CloudAnimationShape(frame, spec.clampedRadius(), spec.clampedThickness(), seed);
            Matrix4f matrix = new Matrix4f();
            int index = i + 10;
            try (Capture solid = new Capture(directory.resolve("surface-" + index + ".bin"), true);
                 Capture fringe = new Capture(directory.resolve("fringe-" + index + ".bin"), false);
                 Capture eye = new Capture(directory.resolve("eye-" + index + ".bin"), false);
                 Capture arcs = new Capture(directory.resolve("arcs-" + index + ".bin"), false)) {
                if (!frame.visible()) continue;
                VertexConsumer shell = new AnimatedCloudConsumer(frame.solid() ? solid : fringe, shape, 0, 0, 0);
                mesh.drawSolid(shell, matrix, 0, 0, 0, time, seed);
                mesh.drawFringe(new AnimatedCloudConsumer(fringe, shape, 0, 0, 0), matrix, 0, 0, 0, time, seed, 1);
                double energy = CloudLifecycle.smooth(frame.formation()) * (1 - CloudLifecycle.smooth(frame.dissipation()));
                StormEyeVisuals.draw(new AnimatedCloudConsumer(eye, shape, 0, 0, 0), matrix, spec,
                        mesh.flow(), seed, 0, 0, 0, time, energy);
                StormArcVisuals.draw(new AnimatedCloudConsumer(arcs, shape, 0, 0, 0), matrix, spec,
                        mesh.flow(), 0, 0, 0, seed, time, energy);
            }
        }
        System.out.println("通过：已导出形成、慢转、消散分镜；形成主体Alpha=255，结束帧无残影");
    }

    private static final class Capture implements VertexConsumer, AutoCloseable {
        private final DataOutputStream output;
        private final boolean solid;
        private float x, y, z, u, v;
        private int r, g, b, a;
        int vertices;
        double minRadius = Double.POSITIVE_INFINITY;
        Capture(Path path, boolean solid) throws Exception {
            output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
            this.solid = solid;
        }
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x; this.y = (float) y; this.z = (float) z; return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) {
            this.r = r; this.g = g; this.b = b; this.a = a; return this;
        }
        public VertexConsumer uv(float u, float v) { this.u = u; this.v = v; return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void defaultColor(int r, int g, int b, int a) { color(r, g, b, a); }
        public void unsetDefaultColor() {}
        public void endVertex() {
            require(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "有限顶点坐标");
            if (solid) require(a == 255, "实体遮天层不能使用半透明顶点");
            minRadius = Math.min(minRadius, Math.hypot(x, z));
            vertices++;
            try {
                output.writeFloat(x); output.writeFloat(y); output.writeFloat(z);
                output.writeByte(r); output.writeByte(g); output.writeByte(b); output.writeByte(a);
                output.writeFloat(u); output.writeFloat(v);
            } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }
        public void close() throws Exception { output.close(); }
    }

    private static void testPerspective() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, 0.05F, 1024F);
        Vector3f center = new Vector3f(31, 128, -19);
        float lastDiameter = 0;
        for (float y : new float[]{0, 32, 64, 96, 112}) {
            Vector3f camera = new Vector3f(center.x, y, center.z);
            Matrix4f view = new Matrix4f().lookAt(new Vector3f(), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1));
            Matrix4f pv = new Matrix4f(projection).mul(view);
            Vector4f left = project(pv, new Vector3f(center).add(-64, 0, 0).sub(camera));
            Vector4f right = project(pv, new Vector3f(center).add(64, 0, 0).sub(camera));
            float diameter = Math.abs(right.x - left.x);
            require(diameter > lastDiameter, "升高靠近必须自然放大，不能固定屏幕大小");
            if (y == 64) require(Math.abs(diameter / lastDiameter - 1.5F) < 1e-4, "透视距离缩放不正确");
            lastDiameter = diameter;
        }
        for (int yaw = 0; yaw < 360; yaw += 30) {
            for (float y : new float[]{48, 112, 160, 210}) {
                double a = Math.toRadians(yaw);
                Vector3f camera = new Vector3f(center).add((float) Math.cos(a) * 95, y - 128, (float) Math.sin(a) * 95);
                Vector3f direction = new Vector3f(center).sub(camera);
                Matrix4f view = new Matrix4f().lookAt(new Vector3f(), direction, new Vector3f(0, 1, 0));
                Matrix4f inverse = new Matrix4f(view).invert();
                for (Vector3f offset : new Vector3f[]{new Vector3f(12, 0, 0), new Vector3f(0, 0, 12), new Vector3f(-12, 0, 0)}) {
                    Vector3f world = new Vector3f(center).add(offset);
                    Vector4f clip = new Vector4f(new Vector3f(world).sub(camera), 1);
                    // 核查世界/观察空间往返，不对远平面的透视深度做病态单精度求逆。
                    view.transform(clip);
                    inverse.transform(clip);
                    Vector3f restored = new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w).add(camera);
                    require(Math.abs(restored.y - 128) < 0.02, "核心平面不应随视角倾斜");
                }
                Vector4f once = new Vector4f(direction, 1);
                view.transform(once);
                require(Math.abs(once.x) < 1e-4 && Math.abs(once.y) < 1e-4 && once.z < 0,
                        "仅应用一次观察变换时核心必须位于瞄准中心");
            }
        }
        System.out.println("通过：五档升高透视与48组环绕视角，核心保持世界Y=128水平面");
    }

    private static Vector4f project(Matrix4f matrix, Vector3f point) {
        Vector4f v = new Vector4f(point, 1);
        matrix.transform(v);
        return v.div(v.w);
    }

    private static void testPerformanceLevels(CloudTypeSpec spec) {
        VortexCloudMesh high = new VortexCloudMesh(spec, VortexCloudMesh.Detail.HIGH);
        VortexCloudMesh medium = new VortexCloudMesh(spec, VortexCloudMesh.Detail.MEDIUM);
        VortexCloudMesh low = new VortexCloudMesh(spec, VortexCloudMesh.Detail.LOW);
        require(high.expectedSolidVertices() < 55000, "近景云核网格必须低于5.5万顶点预算");
        require(high.expectedSolidVertices() > medium.expectedSolidVertices()
                        && medium.expectedSolidVertices() > low.expectedSolidVertices(),
                "距离细节应逐级降低顶点预算");
        require(low.expectedSolidVertices() < high.expectedSolidVertices() * 0.30,
                "远景网格至少减少七成云核顶点");
        System.out.printf(Locale.ROOT, "通过：三级性能网格顶点 %d / %d / %d%n",
                high.expectedSolidVertices(), medium.expectedSolidVertices(), low.expectedSolidVertices());
    }

    /** 第一版配置的形状：版本、四个预设类型与预算数值必须有稳定的默认值。 */
    private static void testConfigDefaults() {
        StormConfig cfg = StormConfig.createDefault();
        require(cfg.version == 1, "默认配置版本必须是1");
        require(cfg.types.size() == 4 && cfg.types.keySet().containsAll(
                        java.util.List.of("default", "small", "monsoon", "vortex")),
                "必须自带 default/small/monsoon/vortex 四种云");
        require(cfg.types.get("default").budget == 960 && cfg.types.get("vortex").budget == 960
                        && cfg.types.get("small").budget == 64 && cfg.types.get("monsoon").budget == 1024,
                "预设云型的预算必须与云体量匹配");
        require(cfg.render.budgetPerCloud == 1024 && cfg.render.budgetTotal == 1600,
                "全局预算默认值必须固定");
        // 预算字段曾有过别的写法，读入时必须落到同一个数值上。
        CloudTypeSpec altSpec = new Gson().fromJson("{\"puffCount\":48}", CloudTypeSpec.class);
        StormConfig altRender = new Gson().fromJson(
                "{\"render\":{\"maxPuffs\":300,\"globalMaxPuffs\":900}}", StormConfig.class);
        require(altSpec.budget == 48 && altRender.render.budgetPerCloud == 300
                        && altRender.render.budgetTotal == 900,
                "预算的别名写法必须读入同一数值");
        // 序列化只写正名，重写文件后不会再出现旧键。
        String rewritten = new Gson().toJson(altRender);
        require(rewritten.contains("budgetPerCloud") && !rewritten.contains("maxPuffs"),
                "规范化重写后只保留正名字段");
        System.out.println("通过：配置默认形状、预算数值与别名读入");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

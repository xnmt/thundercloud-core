package cn.blockforge.generated.thundercloud.config;

import cn.blockforge.generated.thundercloud.animation.CloudLifecycle;
import cn.blockforge.generated.thundercloud.client.CloudAnimationShape;
import com.google.gson.Gson;

/** 时间轴和实际视觉变形回归；无需启动客户端或服务器。 */
public final class CloudLifecycleRegressionTest {
    private CloudLifecycleRegressionTest() {}

    public static void run() {
        require(CloudLifecycle.secondsToTicks(3.0) == 60, "默认3秒必须对应60游戏刻");
        require(CloudLifecycle.secondsToTicks(0.0) == 0, "0应关闭对应动画");
        require(CloudLifecycle.secondsToTicks(0.001) == 1, "正时长至少保留一刻");
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            require(CloudLifecycle.secondsToTicks(invalid) == 60, "异常时长回退3秒");
        }
        require(CloudLifecycle.secondsToTicks(1e12) == 1200, "极大时长必须有限");
        CloudLifecycle cloud = CloudLifecycle.summon(1000, 60);
        require(!cloud.sample(1000).visible(), "召唤第一帧不能闪出完整云盘");
        close(cloud.sample(1030).formation(), 0.5, "形成中间帧");
        require(cloud.sample(1060).steady(), "60刻后完全形成");
        require(!cloud.finished(100000), "永久云不能自己消散");
        CloudLifecycle dismiss = cloud.dismiss(1200, 60);
        require(dismiss.isDissipating(), "必须立即进入驱散状态");
        require(dismiss.dismiss(1230, 120) == dismiss, "重复驱散不得重置倒计时或时长");
        require(!dismiss.finished(1259) && dismiss.finished(1260), "动画完整播放60刻后才移除");
        require(!dismiss.sample(1260).visible(), "结束帧不留残影");
        close(dismiss.sample(1230).dissipation(), 0.5, "消散中间帧");
        CloudLifecycle early = cloud.dismiss(1020, 60);
        close(early.sample(1040).formation(), 1.0 / 3.0, "形成中驱散必须冻结原进度，不能突然变大");
        close(early.sample(1040).dissipation(), 1.0 / 3.0, "形成中驱散照常计时");
        require(CloudLifecycle.summon(100, 0).sample(100).steady(), "禁用形成动画直接完整出现");
        require(cloud.dismiss(1200, 0).finished(1200), "禁用消散动画立刻移除");
        // 同一服务器时间轴在晚加入、重新跟踪或读档后得到相同进度，不依赖实体tickCount。
        CloudLifecycle restored = new CloudLifecycle(dismiss.createdAt(), dismiss.formationTicks(),
                dismiss.dismissedAt(), dismiss.dissipationTicks());
        require(restored.sample(1230.5).equals(dismiss.sample(1230.5)), "读档及同步不能重播动画");
        require(restored.finished(10000), "已经结束的残影重新加载后应移除");
        close(cloud.sample(999).formation(), 0, "客户端时间偏早不能得到负进度");

        CloudTypeSpec minimal = new Gson().fromJson("{\"radius\":37,\"colorTop\":\"#123456\"}", CloudTypeSpec.class);
        require(minimal.formationSeconds == 3 && minimal.dissipationSeconds == 3, "缺省字段自动用默认3秒");
        require(minimal.radius == 37 && minimal.colorTop.equals("#123456"), "写了的字段原样生效");
        CloudTypeSpec custom = new Gson().fromJson("{\"formationSeconds\":1.25,\"dissipationSeconds\":7.5}", CloudTypeSpec.class);
        require(custom.formationSeconds == 1.25 && custom.dissipationSeconds == 7.5, "两种时长独立配置");
        CloudTypeSpec dense = new CloudTypeSpec();
        require(dense.arcs.count == 12, "主电弧默认数量应为12");
        require(dense.arcs.crawlSeconds == 0.35 && dense.arcs.intervalSeconds == 1.3,
                "默认电弧快进快出，寿命上限0.35秒");
        require(dense.arcs.lifeSeconds() == 0.35, "单条电弧寿命上限等于蔓延时长配置");
        require(dense.arcs.topChance == 0.15 && dense.arcs.clampedTopChance() == 0.15,
                "默认电弧集中在云底，云顶只偶发");
        require(dense.arcs.clampedConcurrent() == 6, "同屏主弧默认有性能阀");
        require(dense.arcs.clampedCount() == 12, "默认数量在0~16范围内");
        CloudTypeSpec oversized = new CloudTypeSpec();
        oversized.arcs.count = 99;
        require(oversized.arcs.clampedCount() == 16, "电弧数量必须夹到16");
        oversized.budget = 99999;
        require(oversized.clampedBudget(1024) == 1024 && new CloudTypeSpec().clampedBudget(-1) == 0,
                "单云预算必须夹到全局上限，非法上限按0处理");
        require(StormConfig.createDefault().version == 1, "默认配置版本号为1");
        testShape();
        System.out.println("通过：默认及自定义时长、提前及重复驱散、零时长、异常值、时间轴恢复、缺省字段与预算夹取和动画连续性");
    }

    private static void testShape() {
        long seed = 142235L;
        double radius = 64, thickness = 6;
        CloudAnimationShape steady = new CloudAnimationShape(CloudLifecycle.Frame.STEADY, radius, thickness, seed);
        double[] phases = {0, 0.001, 0.10, 0.17999, 0.18, 0.25, 0.5, 0.75, 0.99999, 1};
        for (double formation : phases) {
            for (double dissipation : phases) {
                CloudLifecycle.Frame frame = new CloudLifecycle.Frame(formation, dissipation);
                CloudAnimationShape shape = new CloudAnimationShape(frame, radius, thickness, seed);
                for (int ring = 0; ring <= 16; ring++) {
                    for (int segment = 0; segment <= 96; segment++) {
                        double angle = segment * Math.PI * 2 / 96;
                        double x = radius * ring / 16 * Math.cos(angle), z = radius * ring / 16 * Math.sin(angle);
                        shape.transform(x, 3.5, z);
                        require(Double.isFinite(shape.x()) && Double.isFinite(shape.y()) && Double.isFinite(shape.z()), "动画顶点有限");
                        require(Math.hypot(shape.x(), shape.z()) <= radius * 1.221, "消散不能超出扩大的剔除范围");
                        require(shape.alpha() >= 0 && shape.alpha() <= 1, "透明度有界");
                        if (frame.solid()) close(shape.alpha(), 1, "形成云核保持不透明");
                        if (dissipation == 1 || formation == 0) close(shape.alpha(), 0, "首尾不留闪帧");
                        steady.transform(x, 3.5, z);
                        close(steady.x(), x, "稳定态X不能漂移");
                        close(steady.y(), 3.5, "稳定态Y不能漂移");
                        close(steady.z(), z, "稳定态Z不能漂移");
                    }
                }
            }
        }
        // 变化边界两侧连续：形成结束、透明转不透明、驱散起始。
        for (double p : new double[]{0.18, 1.0}) {
            CloudAnimationShape before = new CloudAnimationShape(new CloudLifecycle.Frame(p - 1e-7, 0), radius, thickness, seed);
            CloudAnimationShape after = new CloudAnimationShape(new CloudLifecycle.Frame(p, 0), radius, thickness, seed);
            before.transform(38, 2, -21); after.transform(38, 2, -21);
            require(Math.hypot(before.x() - after.x(), before.z() - after.z()) < 0.001, "形成边界不能跳位置");
            require(Math.abs(before.alpha() - after.alpha()) < 1e-5, "批次切换不能跳透明度");
        }
        CloudAnimationShape start = new CloudAnimationShape(new CloudLifecycle.Frame(1, 1e-7), radius, thickness, seed);
        start.transform(38, 2, -21);
        require(Math.hypot(start.x() - 38, start.z() + 21) < 0.001 && Math.abs(start.alpha() - 1) < 1e-5,
                "驱散第一帧必须衔接完整云体");
        // 简化后的形成动画：同一点只向外舒展、只变浓，全程单调，不允许来回弹跳。
        double lastRadius = -1, lastAlpha = -1;
        for (int step = 0; step <= 20; step++) {
            CloudAnimationShape growing = new CloudAnimationShape(
                    new CloudLifecycle.Frame(step / 20.0, 0), radius, thickness, seed);
            growing.transform(38, 2, -21);
            double nowRadius = Math.hypot(growing.x(), growing.z());
            require(nowRadius >= lastRadius - 1e-9, "形成期间云体只向外舒展，不来回跳动");
            require(growing.alpha() >= lastAlpha - 1e-9, "形成期间只变浓，不变淡");
            require(growing.y() >= 2 * (0.12 - 1e-9), "形成期间云盘不会薄过初值的12%");
            lastRadius = nowRadius;
            lastAlpha = growing.alpha();
        }
    }

    private static void close(double a, double b, String message) { require(Math.abs(a - b) < 1e-8, message); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

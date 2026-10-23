package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.animation.CloudLifecycle;
import cn.blockforge.generated.thundercloud.animation.CloudLifecycle.Frame;

/**
 * 对连续厚云作同一个世界空间变形。形成已简化为单一螺旋收拢：
 * 云盘从小盘旋开、摊薄再舒展合拢，不再有额外的三臂摆动；
 * 每个顶点只做一次开方、一对 sin/cos 和几次乘法（原先约八次超越函数）。
 * 云体、风暴眼与电弧共用，避免形成时出现悬空电弧；完整云体的坐标与透明度完全不变。
 */
public final class CloudAnimationShape {
    private static final double TAU = Math.PI * 2.0;
    private final Frame frame;
    private final double radius, thickness;
    private final double unravel, heightScale, opacity;
    // 顶点的扭转角随自身半径线性变化：twist = twistBase + twistSlope * r，
    // 所以构造时算好两个系数，形成期顶点里不再需要 atan2、pow 或按角度查瓣。
    private final double twistBase, twistSlope, spread;
    private final double seedAngle;
    private double x, y, z, alpha;

    public CloudAnimationShape(Frame frame, double radius, double thickness, long seed) {
        this.frame = frame;
        this.radius = radius;
        this.thickness = thickness;
        seedAngle = SpiralCloudFlow.seedAngle(seed);
        double growth = CloudLifecycle.smooth(frame.formation());
        unravel = CloudLifecycle.smooth(frame.dissipation());
        double turn = (1.0 - growth) * TAU;
        double radialK = 0.045 + 0.955 * growth;
        twistBase = turn * 1.44 + unravel * TAU * 0.08;
        twistSlope = (unravel * TAU * 0.16 - turn * 1.12 * radialK) / radius;
        spread = radialK * (1.0 + 0.22 * unravel);
        heightScale = (0.12 + 0.88 * growth) * (1.0 - 0.65 * unravel);
        opacity = CloudLifecycle.smooth(frame.formation() / 0.18);
    }

    /** 复用结果槽，不为每个顶点分配对象。输入相对于云中心。 */
    public void transform(double px, double py, double pz) {
        if (frame.steady()) {
            x = px; y = py; z = pz; alpha = 1.0;
            return;
        }
        double r = Math.sqrt(px * px + pz * pz);
        double twist = twistBase + twistSlope * r;
        double c = Math.cos(twist), s = Math.sin(twist);
        // 直接对原坐标做“随半径变角的旋转 + 径向缩放”，与逐点重建极坐标结果一致但省掉 atan2。
        x = (px * c - pz * s) * spread;
        z = (pz * c + px * s) * spread;
        y = py * heightScale + thickness * 0.65 * unravel;
        double dissolve = 0.0;
        if (frame.dissipation() > 0.0) {
            // 不均匀退散只在驱散期计算：外缘先散、内部云丝稍后消隐，首尾都连续，不突然删掉整盘。
            double angle = Math.atan2(pz, px);
            double wisps = 0.5 + 0.5 * Math.cos(3.0 * (angle + seedAngle) + r / radius * 13.0);
            double delay = 0.20 * (1.0 - Math.min(1.0, r / radius)) + 0.14 * wisps;
            dissolve = CloudLifecycle.smooth((frame.dissipation() - delay) / (1.0 - delay));
        }
        alpha = opacity * (1.0 - dissolve);
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double alpha() { return alpha; }
}

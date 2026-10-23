package cn.blockforge.generated.thundercloud.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 一种雷云的全部可调参数，对应 clouds.json 里 types.&lt;类型名&gt; 这一段。
 * 没写的字段自动使用这里的默认值；每项的含义与取值范围见字段注释和 README。
 *
 * <p>颜色完全由配置决定：渲染端只在构建云网格时取一次色，
 * 不做任何随视角、时间、状态的动态染色。</p>
 */
public final class CloudTypeSpec {

    // ---- 云体外形 ----

    /** 云盘半径（方块），1~96，默认64（即直径128格）；渲染尺寸和范围查询都以它为准 */
    public double radius = 64.0;
    /** 云体蓬松厚度（方块），0.3~32，默认7；中心因内凹会明显更薄 */
    public double thickness = 7.0;
    /** 云面细节带数，8~32，默认16：越大云纹越细、网格越密、开销越高 */
    public int vortexBands = 16;
    /** 风暴眼开口占半径的比例，0.08~0.45，默认0.16；开口内仍是不透明云底，不会挖穿 */
    public double eyeOpening = 0.16;
    /** 上下云面中心内凹强度，0~0.9，默认0.86：越大凹得越深 */
    public double eyeDepression = 0.86;

    // ---- 运动 ----

    /** 整片云转一圈的秒数，8~240，默认48；数值越大转得越慢 */
    public double rotationPeriodSeconds = 48.0;
    /** 大旋臂数量，2~5，默认3；是连续云面上的隆起，不是分离云团 */
    public int swirlArms = 3;
    /** 从中心到边缘旋臂卷曲的圈数，0.2~1.2，默认0.65 */
    public double swirlTurns = 0.65;
    /** 内侧细云纹流动的速度尺度（秒），默认3；与外侧不同速，形成差速滚动 */
    public double corePeriodSeconds = 3.0;
    /** 外侧大团云的流动速度尺度（秒），默认12；不小于内侧周期 */
    public double edgePeriodSeconds = 12.0;

    // ---- 颜色（#RRGGBB）----

    /** 云底与外缘暗部的颜色 */
    public String colorBottom = "#10091D";
    /** 云顶亮部的颜色，表面按高度与云底混色 */
    public String colorTop = "#665080";

    // ---- 性能与生命周期 ----

    /** 一朵云占用的渲染预算，0~1536，默认960：不影响外观，只决定同屏能显示几朵；0=不渲染该类型 */
    @SerializedName(value = "budget", alternate = "puffCount")
    public int budget = 960;
    /** 从召唤到开始驱散的秒数（含形成动画），默认0=永久存在 */
    public double durationSeconds = 0.0;
    /** 螺旋形成秒数，0~60，默认3；0=直接完整出现 */
    public double formationSeconds = 3.0;
    /** 缓慢消散秒数，0~60，默认3；0=立刻移除 */
    public double dissipationSeconds = 3.0;

    /** 云贴图（相对 assets/thundercloud/textures/ 的路径）；第一张用作不透明主体 */
    public List<String> textures = new ArrayList<>(List.of(
            "cloud/cloud_core_a.png",
            "cloud/cloud_core_b.png",
            "cloud/cloud_edge.png"));

    /** 风暴眼发光层 */
    public EyeSpec eye = new EyeSpec();
    /** 云面电弧，纯视觉：不打雷、不伤人、不起火 */
    public ArcSpec arcs = new ArcSpec();

    /** 云面电弧：沿云面蔓延、分叉、回闪的紫白电流；触发时刻、方向、寿命每回都随机 */
    public static final class ArcSpec {
        /** 总开关；false 时电弧与局部电光全部关闭 */
        public boolean enabled = true;
        /** 主弧组数，0~16，默认12；每组自带3条分叉，触发时刻与方向各组独立随机 */
        public int count = 12;
        /** 同组两次放电的最长间隔秒数，0.5~30，默认1.3；触发时刻在间隔内随机挑 */
        public double intervalSeconds = 1.3;
        /** 单次闪光的持续秒数，默认0.18；内部包含多次短脉冲 */
        public double flashSeconds = 0.18;
        /** 单条电弧寿命上限，默认0.35；每回随机掷出0.6~1.0倍，之后沿原路径回闪 */
        public double crawlSeconds = 0.35;
        /** 同屏同时绘制的主弧组数上限，1~16，默认6；0=不限制。超出上限的电弧连路径都不生成 */
        public int maxConcurrent = 6;
        /** 同一次放电也爬上云顶面的概率，0~1，默认0.15；电弧集中在云底，云顶偶发几条即可 */
        public double topChance = 0.15;
        /** 电弧长度倍率，0.35~1.6，默认1.0；默认主弧跨越约35~75格云层 */
        public double lengthScale = 1.0;
        /** 云盘直径128格时的亮芯宽度（方块），默认0.18 */
        public double width = 0.18;
        /** 电弧余光颜色 #RRGGBB */
        public String color = "#CFB0FF";
        /** 电弧亮芯颜色 #RRGGBB */
        public String coreColor = "#FAF3FF";

        /** 主弧组数：0~16 */
        public int clampedCount() {
            return Math.max(0, Math.min(16, count));
        }

        /** 同屏主弧上限：0表示不限制，其余夹到1~16 */
        public int clampedConcurrent() {
            return count <= 0 ? 0 : Math.max(0, Math.min(16, maxConcurrent));
        }

        /** 云顶出现概率：0~1，非法值按默认0.15 */
        public double clampedTopChance() {
            return Double.isFinite(topChance) ? Math.max(0.0, Math.min(1.0, topChance)) : 0.15;
        }

        /** 单条电弧寿命上限：不超过放电间隔的88%，最短0.24秒 */
        public double lifeSeconds() {
            double interval = Math.max(0.5, Math.min(30.0, intervalSeconds));
            return Math.min(interval * 0.88, Math.max(0.24, crawlSeconds));
        }
    }

    /** 风暴眼：贴云底的圆形渐变发光层与螺旋纹理（加法混合） */
    public static final class EyeSpec {
        /** 总开关；false 时不画风暴眼辉光 */
        public boolean enabled = true;
        /** 叠层片数，1~6，默认3；越多辉光越厚、开销越高 */
        public int layers = 3;
        /** 核心辉光相对开口的尺寸倍率，默认3 */
        public double sizeScale = 3.0;
        /** 核心相对整片云的转速倍率，0.1~4，默认1.4 */
        public double speedScale = 1.4;
        /** 辉光不透明度，0~1，默认0.55 */
        public double alpha = 0.55;
        /** 辉光颜色 #RRGGBB */
        public String colorEyeGlow = "#F4F0FF";
        /** 螺旋纹理（相对 assets/thundercloud/textures/ 的路径） */
        public String texture = "cloud/cloud_eye_swirl.png";

        public int resolveEyeGlow() {
            return CloudTypeSpec.parseColor(colorEyeGlow, 0xF4F0FF);
        }
    }

    public double clampedRadius() {
        return Math.max(1.0, Math.min(96.0, radius));
    }

    public double clampedThickness() {
        return Math.max(0.3, Math.min(32.0, thickness));
    }

    public double clampedEyeOpening() {
        return Math.max(0.08, Math.min(0.45, eyeOpening));
    }

    public double clampedEyeDepression() {
        return Double.isFinite(eyeDepression) ? Math.max(0.0, Math.min(0.9, eyeDepression)) : 0.86;
    }

    public int clampedVortexBands() {
        return Math.max(8, Math.min(32, vortexBands));
    }

    public int clampedSwirlArms() {
        return Math.max(2, Math.min(5, swirlArms));
    }

    public double clampedSwirlTurns() {
        return Double.isFinite(swirlTurns) ? Math.max(0.2, Math.min(1.2, swirlTurns)) : 0.65;
    }

    public double rotationPeriod() {
        return Double.isFinite(rotationPeriodSeconds) ? Math.max(8.0, Math.min(240.0, rotationPeriodSeconds)) : 48.0;
    }

    /** 周期下限保护，避免配置成 0 导致除零 */
    public double corePeriod() {
        return Math.max(0.25, corePeriodSeconds);
    }

    public double edgePeriod() {
        return Math.max(corePeriod(), edgePeriodSeconds);
    }

    /** 单云预算：不超过全局单云上限，硬顶1536 */
    public int clampedBudget(int perCloudCap) {
        return Math.max(0, Math.min(Math.min(budget, perCloudCap), 1536));
    }

    public int colorBottomRgb() {
        return parseColor(colorBottom, 0x10091D);
    }

    public int colorTopRgb() {
        return parseColor(colorTop, 0x665080);
    }

    /** "#RRGGBB" → 0xRRGGBB，解析失败返回 fallback */
    public static int parseColor(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        String t = s.startsWith("#") ? s.substring(1) : s;
        try {
            return (int) (Long.parseLong(t, 16) & 0xFFFFFFL);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

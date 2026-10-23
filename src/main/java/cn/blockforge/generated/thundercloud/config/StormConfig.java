package cn.blockforge.generated.thundercloud.config;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * clouds.json 的根结构：版本号 + 渲染全局参数 + 云类型表。
 * types 里的每个名字都可以用于 /thundercloud spawn &lt;名字&gt; 或 API 的 typeId。
 */
public final class StormConfig {

    /** 配置格式版本，程序自动维护；文件与规范形式不一致时载入后会被自动重写整齐 */
    public int version = 1;
    /** 指定类型不存在时兜底使用的类型名 */
    public String defaultType = "default";
    /** 渲染全局参数（性能与贴图处理） */
    public RenderOptions render = new RenderOptions();
    /** 云类型表 */
    public Map<String, CloudTypeSpec> types = new LinkedHashMap<>();

    /** 渲染全局参数 */
    public static final class RenderOptions {
        /** 加载贴图时把亮度烘焙成透明度（白实黑透）；false 则完全按贴图自带 Alpha 渲染 */
        public boolean bakeAlpha = true;
        /** 云贴图边长上限（自动取 2 的幂，16~1024）；过大或非 2 的幂的贴图会重采样到该尺寸 */
        public int textureTargetSize = 512;
        /** 单朵云的预算上限，默认1024；云类型的 budget 超过它时按它计 */
        @SerializedName(value = "budgetPerCloud", alternate = "maxPuffs")
        public int budgetPerCloud = 1024;
        /** 同屏所有云的预算总额（硬顶2048），默认1600；预算耗尽后更远的云自动省略 */
        @SerializedName(value = "budgetTotal", alternate = "globalMaxPuffs")
        public int budgetTotal = 1600;
        /** 云的最大渲染距离（方块），32~512，默认256；超过就整朵剔除 */
        public double maxRenderDistance = 256.0;
        /** 贴图径向柔边起点，0~0.98，默认0.52：距中心该比例内保持完整，向外渐隐到全透明 */
        public double radialFalloff = 0.52;
        /** 噪点底限（0~255），默认12：比这更暗的像素直接全透明，用来压掉黑底灰噪 */
        public int noiseFloor = 12;
    }

    public static StormConfig createDefault() {
        StormConfig c = new StormConfig();
        c.types.put("default", new CloudTypeSpec());

        CloudTypeSpec small = new CloudTypeSpec();
        small.radius = 6.0;
        small.thickness = 1.9;
        small.vortexBands = 8;
        small.budget = 64;
        small.durationSeconds = 30.0;
        c.types.put("small", small);

        CloudTypeSpec monsoon = new CloudTypeSpec();
        monsoon.radius = 64.0;
        monsoon.thickness = 8.0;
        monsoon.budget = 1024;
        monsoon.colorBottom = "#0C0716";
        monsoon.colorTop = "#59416F";
        monsoon.corePeriodSeconds = 4.0;
        monsoon.edgePeriodSeconds = 16.0;
        c.types.put("monsoon", monsoon);

        // 参考图风格预设，与 default 参数一致，单独占一个类型名方便命令与 API 引用
        c.types.put("vortex", new CloudTypeSpec());

        return c;
    }
}

package cn.blockforge.generated.thundercloud.client;

import net.minecraft.client.renderer.RenderStateShard;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;

/**
 * 原版 RenderStateShard 的管线常量大多是 protected 的，
 * 这里通过继承合法取出来用（不改任何原版行为），供自定义 RenderType 组装。
 */
public final class StormRenderShards extends RenderStateShard {

    /** 无光照、无 overlay 的精简着色器：颜色 × 纹理，角度不参与 */
    public static final ShaderStateShard SHADER_POSITION_COLOR_TEX = POSITION_COLOR_TEX_SHADER;
    public static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
    // 亮度受顶点Alpha控制；不能用ONE/ONE让渐隐的云面辉光也变成实心白片。
    public static final TransparencyStateShard ADDITIVE = new TransparencyStateShard("storm_alpha_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });
    public static final TransparencyStateShard OPAQUE = NO_TRANSPARENCY;
    public static final WriteMaskStateShard SOLID_WRITE = COLOR_DEPTH_WRITE;
    public static final OutputStateShard MAIN_OUTPUT = MAIN_TARGET;
    public static final CullStateShard NO_CULL = RenderStateShard.NO_CULL;
    /** 开深度测试：山体、建筑正常遮挡云 */
    public static final DepthTestStateShard DEPTH_TEST = LEQUAL_DEPTH_TEST;
    /** 只写颜色不写深度：半透明面片互相叠加不乱序遮挡，不出现黑框 */
    public static final WriteMaskStateShard COLOR_WRITE_ONLY = COLOR_WRITE;
    public static final LightmapStateShard NO_LIGHTMAP = RenderStateShard.NO_LIGHTMAP;
    public static final OverlayStateShard NO_OVERLAY = RenderStateShard.NO_OVERLAY;
    public static final LayeringStateShard NO_LAYERING = RenderStateShard.NO_LAYERING;
    // AFTER_WEATHER 尚未合成天气缓冲；Fabulous 模式也必须写入对应的天气目标。
    public static final OutputStateShard WEATHER_OUTPUT = RenderStateShard.WEATHER_TARGET;
    public static final TexturingStateShard DEFAULT_TEXTURING = RenderStateShard.DEFAULT_TEXTURING;
    public static final LineStateShard DEFAULT_LINE = RenderStateShard.DEFAULT_LINE;
    public static final ColorLogicStateShard NO_COLOR_LOGIC = RenderStateShard.NO_COLOR_LOGIC;

    private StormRenderShards() {
        super("thundercloud_shards", () -> {}, () -> {});
    }
}

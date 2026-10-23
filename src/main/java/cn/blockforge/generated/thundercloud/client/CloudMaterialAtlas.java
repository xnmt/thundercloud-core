package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.ThunderCloudCoreMod;
import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import cn.blockforge.generated.thundercloud.config.StormConfig;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将所有云质纹理装入同一图集，透明曲面可以跨云、跨素材统一排序。
 * 图集必须先注册，再允许创建使用它的 RenderType。RGB 保持灰度，颜色只来自配置。
 */
public final class CloudMaterialAtlas {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(
            ThunderCloudCoreMod.MOD_ID, "dynamic/cloud_volume_atlas");
    private static final int TILE = 252;
    private static final int STRIDE = TILE + 2; // 一圈复制边缘，避免线性过滤串到相邻素材
    private static final int COLUMNS = 8;
    private static final int SIZE = 2048;
    private static final int MAX_MATERIALS = 64;
    private static final Map<String, Integer> SLOTS = new LinkedHashMap<>();
    private static final String FALLBACK = "cloud/cloud_core_a.png";
    private static StormConfig loadedConfig;
    private static volatile boolean dirty = true;
    private static RenderType volumeType;
    private static RenderType eyeType;
    private static RenderType solidType;

    private CloudMaterialAtlas() {}

    /** 重载监听只置脏标记；上传发生在下一次渲染线程调用，避免跨线程操作显卡。 */
    public static void invalidate(ResourceManager ignored) {
        dirty = true;
    }

    public static void prepare() {
        StormConfig config = StormConfigManager.get();
        if (!dirty && loadedConfig == config) return;
        Minecraft mc = Minecraft.getInstance();
        ResourceManager resources = mc.getResourceManager();
        SLOTS.clear();
        add("__arc_white__"); // 电弧使用独立发光批次，并接受实体云核的深度遮挡
        add(FALLBACK);
        add("opaque:" + FALLBACK);
        for (CloudTypeSpec spec : config.types.values()) {
            add("opaque:" + spec.textures.get(0));
            for (String path : spec.textures) add(path);
            if (spec.eye != null) add(spec.eye.texture);
        }
        NativeImage atlas = new NativeImage(SIZE, SIZE, true);
        try {
            for (Map.Entry<String, Integer> entry : SLOTS.entrySet()) {
                fillTile(atlas, entry.getKey(), entry.getValue(), resources, config.render);
            }
            // DynamicTexture 接管 NativeImage；注册会释放同地址的上一张纹理。
            DynamicTexture texture = new DynamicTexture(atlas);
            mc.getTextureManager().register(LOCATION, texture);
            texture.setBlurMipmap(true, false);
        } catch (RuntimeException failure) {
            atlas.close();
            throw failure;
        }
        volumeType = StormRenderTypes.layer(LOCATION, false);
        eyeType = StormRenderTypes.layer(LOCATION, true);
        solidType = StormRenderTypes.solid(LOCATION);
        loadedConfig = config;
        dirty = false;
    }

    private static void add(String path) {
        if (path != null && !path.isBlank() && SLOTS.size() < MAX_MATERIALS) {
            SLOTS.computeIfAbsent(path, key -> SLOTS.size());
        }
    }

    private static void fillTile(NativeImage atlas, String path, int slot,
                                 ResourceManager resources, StormConfig.RenderOptions options) {
        NativeImage source = null;
        boolean white = path.equals("__arc_white__");
        boolean opaque = path.startsWith("opaque:");
        if (opaque) path = path.substring(7);
        if (!white) {
            try (InputStream input = resources.open(CloudTextures.resolve(path))) {
                source = NativeImage.read(input);
            } catch (Exception ex) {
                // 兜底仍然是灰色云质，绝不使用 missing texture 棋盘或其他实体纹理。
                LOGGER.warn("[ThunderCloudCore] 云质素材 {} 不可用，使用中性密度", path);
            }
        }
        int ox = slot % COLUMNS * STRIDE;
        int oy = slot / COLUMNS * STRIDE;
        double floor = Math.max(0.0, Math.min(254.0, options.noiseFloor)) / 255.0;
        try {
            for (int y = -1; y <= TILE; y++) {
                for (int x = -1; x <= TILE; x++) {
                    int sx = Math.max(0, Math.min(TILE - 1, x));
                    int sy = Math.max(0, Math.min(TILE - 1, y));
                    double lum = white ? 1.0 : 0.78;
                    double originalAlpha = 1.0;
                    if (source != null) {
                        int px = Math.min(source.getWidth() - 1, sx * source.getWidth() / TILE);
                        int py = Math.min(source.getHeight() - 1, sy * source.getHeight() / TILE);
                        int p = source.getPixelRGBA(px, py);
                        lum = ((p & 255) + ((p >>> 8) & 255) + ((p >>> 16) & 255)) / 765.0;
                        originalAlpha = (p >>> 24) / 255.0;
                    }
                    double density = options.bakeAlpha ? Math.max(0.0, (lum - floor) / (1.0 - floor)) : 1.0;
                    // 云核纹理只贡献明暗，不允许贴图黑底或透明像素在遮天层打洞。
                    int alpha = opaque || white ? 255 : (int) Math.round(density * originalAlpha * 255.0);
                    // 保留纹理明暗，但不重复把黑底乘进 RGB，避免乌云变成一团漆黑。
                    int gray = (int) Math.round(255.0 * (0.72 + 0.28 * lum));
                    atlas.setPixelRGBA(ox + x + 1, oy + y + 1,
                            gray | gray << 8 | gray << 16 | alpha << 24);
                }
            }
        } finally {
            if (source != null) source.close();
        }
    }

    public static int slot(String path) {
        return SLOTS.getOrDefault(path, 1);
    }

    public static float u(int slot, double u) {
        return (float) ((slot % COLUMNS * STRIDE + 1.5 + u * (TILE - 1)) / SIZE);
    }

    public static float v(int slot, double v) {
        return (float) ((slot / COLUMNS * STRIDE + 1.5 + v * (TILE - 1)) / SIZE);
    }

    public static int solidSlot(String path) {
        return SLOTS.getOrDefault("opaque:" + path, 2);
    }

    public static RenderType solidType() { return solidType; }

    public static RenderType type(boolean eye) {
        return eye ? eyeType : volumeType;
    }
}

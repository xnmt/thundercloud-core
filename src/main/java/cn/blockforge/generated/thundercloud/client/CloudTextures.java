package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.ThunderCloudCoreMod;
import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 云贴图注册表：烘焙纹理、TextureManager 注册、RenderType 缓存。
 *
 * <p>纹理先用 {@link TextureManager#register(ResourceLocation, AbstractTexture)}
 * 正式注册并拿到 ResourceLocation，之后一切 RenderType 都通过
 * {@code TextureStateShard(ResourceLocation)} 显式绑定它——
 * 不存在"采样到上一次绑定的贴图"（猪皮肤 / 树叶图集）的可能。</p>
 *
 * <p>通过 RegisterClientReloadListenersEvent 挂进原版资源重载管线：
 * 换资源包、F3+T 之后纹理会重新加载、重新烘焙并上传。</p>
 */
public final class CloudTextures {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** GL 各向异性常量（EXT_texture_filter_anisotropic） */
    private static final int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
    private static final int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;
    private static final float WANTED_ANISOTROPY = 8.0F;

    private CloudTextures() {}

    /** 贴图路径（相对 textures/）→ 烘焙纹理 */
    private static final Map<String, AbstractTexture> TEXTURES = new HashMap<>();
    /** 贴图路径 → 半透明云絮 RenderType */
    private static final Map<String, RenderType> LAYER_TYPES = new HashMap<>();
    /** 贴图路径 → 加法混合风暴眼 RenderType */
    private static final Map<String, RenderType> EYE_TYPES = new HashMap<>();

    public static ResourceLocation resolve(String texturePath) {
        return ResourceLocation.fromNamespaceAndPath(
                ThunderCloudCoreMod.MOD_ID, "textures/" + texturePath);
    }

    /** 资源重载入口：按当前配置重建全部纹理并重新注册上传 */
    public static void reloadAll(ResourceManager resourceManager) {
        TEXTURES.clear();
        LAYER_TYPES.clear();
        EYE_TYPES.clear();
        for (CloudTypeSpec spec : StormConfigManager.get().types.values()) {
            if (spec == null) continue;
            for (String path : spec.textures) {
                ensure(path, resourceManager);
            }
            if (spec.eye != null && spec.eye.texture != null) {
                ensure(spec.eye.texture, resourceManager);
            }
        }
        // 硬编码默认贴图也保证可用（配置异常时回落用）
        CloudTypeSpec def = new CloudTypeSpec();
        for (String path : def.textures) ensure(path, resourceManager);
        if (def.eye != null && def.eye.texture != null) ensure(def.eye.texture, resourceManager);
    }

    /** 渲染时懒加载兜底（首次进入世界、reload 事件尚未跑过时） */
    public static AbstractTexture get(String texturePath) {
        return ensure(texturePath, Minecraft.getInstance().getResourceManager());
    }

    public static RenderType layerType(String texturePath) {
        return LAYER_TYPES.computeIfAbsent(texturePath,
                p -> StormRenderTypes.layer(resolve(p), false));
    }

    public static RenderType eyeType(String texturePath) {
        return EYE_TYPES.computeIfAbsent(texturePath,
                p -> StormRenderTypes.layer(resolve(p), true));
    }

    private static AbstractTexture ensure(String texturePath, ResourceManager rm) {
        AbstractTexture tex = TEXTURES.get(texturePath);
        if (tex == null) {
            ResourceLocation loc = resolve(texturePath);
            BakedAlphaTexture baked = new BakedAlphaTexture(loc);
            try {
                baked.load(rm);   // 加载时即完成 尺寸归一 + 亮度转 Alpha 烘焙
            } catch (IOException e) {
                LOGGER.warn("[ThunderCloudCore] 云贴图加载失败: {}", texturePath);
            }
            // 关键修复：正式注册进 TextureManager，RenderType 才能按位置绑到它
            Minecraft.getInstance().getTextureManager().register(loc, baked);
            applySoftFilter(baked);
            tex = baked;
            TEXTURES.put(texturePath, tex);
        }
        return tex;
    }

    /** 线性过滤（模糊）+ 各向异性：软边面片远处不闪烁、斜视角不发糊 */
    private static void applySoftFilter(AbstractTexture tex) {
        tex.setBlurMipmap(true, false);
        try {
            int id = tex.getId();
            if (id == AbstractTexture.NOT_ASSIGNED) return;
            GlStateManager._bindTexture(id);
            float max = GL11.glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            float want = Math.min(WANTED_ANISOTROPY, Math.max(1.0F, max));
            if (want > 1.0F) {
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, want);
            }
        } catch (Throwable ignored) {
            // 驱动不支持各向异性就只保留线性过滤，不影响正确性
        }
    }
}

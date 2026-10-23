package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.config.StormConfig;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

/**
 * 在「读文件 → 上传 GPU」之间插入亮度转 Alpha 烘焙的纹理。
 *
 * <p>原版 SimpleTexture 的加载流程会先调用 getTextureImage(ResourceManager) 拿到
 * NativeImage，再上传；这里覆写该钩子，在上传前就地完成两步：</p>
 * <ol>
 *   <li>统一尺寸：按实际读取到的宽高判断，超过目标边长（默认 512）或非 2 的幂时
 *       双线性重采样到目标尺寸——遍历永远用 getWidth()/getHeight()，不存在尺寸假设；</li>
 *   <li>亮度转 Alpha：逐像素 (r+g+b)/3 写入 A 通道（白→不透明，黑→全透明）。</li>
 * </ol>
 */
public class BakedAlphaTexture extends SimpleTexture {

    private final boolean bake;
    private final int targetSize;

    public BakedAlphaTexture(ResourceLocation location) {
        super(location);
        StormConfig.RenderOptions ro = StormConfigManager.get().render;
        this.bake = ro == null || ro.bakeAlpha;
        this.targetSize = normalizeTarget(ro != null ? ro.textureTargetSize : 512);
    }

    /** 目标边长必须是 2 的幂，范围 [16, 1024] */
    private static int normalizeTarget(int requested) {
        int t = 16;
        while (t < requested && t < 1024) t <<= 1;
        return t;
    }

    @Override
    protected TextureImage getTextureImage(ResourceManager resourceManager) {
        TextureImage image = super.getTextureImage(resourceManager);
        try {
            NativeImage pixels = image.getImage();
            int w = pixels.getWidth();   // 实际尺寸，绝不硬编码
            int h = pixels.getHeight();

            if (this.bake) {
                StormConfig.RenderOptions ro = StormConfigManager.get().render;
                int floor = ro != null ? ro.noiseFloor : 12;
                double inner = ro != null ? ro.radialFalloff : 0.52;
                AlphaBaker.bake(pixels, floor, inner);
            }

            int potW = nextPowerOfTwo(w);
            int potH = nextPowerOfTwo(h);
            int tw = Math.min(this.targetSize, Math.max(16, potW));
            int th = Math.min(this.targetSize, Math.max(16, potH));
            if (tw != w || th != h) {
                NativeImage resized = resample(pixels, tw, th);
                pixels.close();
                return new TextureImage(image.getTextureMetadata(), resized);
            }
            return image;
        } catch (IOException | RuntimeException e) {
            // 任何一步失败都按原样上传，宁可朴素也不花屏
            return image;
        }
    }

    private static int nextPowerOfTwo(int v) {
        int p = 1;
        while (p < v) p <<= 1;
        return p;
    }

    /** 双线性重采样到 w×h */
    private static NativeImage resample(NativeImage src, int w, int h) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            double sy = Math.min(sh - 1.001, (y + 0.5) * sh / (double) h - 0.5);
            int y0 = (int) sy;
            double fy = sy - y0;
            int y1 = Math.min(sh - 1, y0 + 1);
            for (int x = 0; x < w; x++) {
                double sx = Math.min(sw - 1.001, (x + 0.5) * sw / (double) w - 0.5);
                int x0 = (int) sx;
                double fx = sx - x0;
                int x1 = Math.min(sw - 1, x0 + 1);

                int p00 = src.getPixelRGBA(x0, y0);
                int p10 = src.getPixelRGBA(x1, y0);
                int p01 = src.getPixelRGBA(x0, y1);
                int p11 = src.getPixelRGBA(x1, y1);

                int r = lerp(lerp(p00 & 0xFF, p10 & 0xFF, fx), lerp(p01 & 0xFF, p11 & 0xFF, fx), fy);
                int g = lerp(lerp((p00 >> 8) & 0xFF, (p10 >> 8) & 0xFF, fx), lerp((p01 >> 8) & 0xFF, (p11 >> 8) & 0xFF, fx), fy);
                int b = lerp(lerp((p00 >> 16) & 0xFF, (p10 >> 16) & 0xFF, fx), lerp((p01 >> 16) & 0xFF, (p11 >> 16) & 0xFF, fx), fy);
                int a = lerp(lerp(p00 >>> 24, p10 >>> 24, fx), lerp(p01 >>> 24, p11 >>> 24, fx), fy);
                out.setPixelRGBA(x, y, r | (g << 8) | (b << 16) | (a << 24));
            }
        }
        return out;
    }

    private static int lerp(int a, int b, double t) {
        return (int) (a + (b - a) * t + 0.5);
    }
}

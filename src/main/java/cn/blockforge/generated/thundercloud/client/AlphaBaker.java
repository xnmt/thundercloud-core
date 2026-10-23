package cn.blockforge.generated.thundercloud.client;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * 亮度转 Alpha 烘焙：贴图加载时用 NativeImage 遍历像素，
 * 采样 RGB 亮度写入 A 通道 —— 白色→A=255 不透明，灰色→半透明，黑色→A=0 完全透明。
 *
 * <p>这样渲染可以直接走原版 RenderType 管线，不需要自定义 ShaderInstance，
 * 从根上避开 Iris / Optifine 光影 mod 的着色器冲突。</p>
 *
 * <p>同时叠加径向渐变蒙版（云絮软边）与噪点底限（压掉 AI 黑底里的灰噪点）。</p>
 */
public final class AlphaBaker {

    private AlphaBaker() {}

    /**
     * 就地修改 image。
     *
     * @param noiseFloor  亮度噪点底限（0~255），低于它的像素直接全透明
     * @param radialInner 径向蒙版起始半径（0~1）：距中心小于该比例的像素保持全亮，向外渐隐到 1.0 处全透
     */
    public static void bake(NativeImage image, int noiseFloor, double radialInner) {
        int w = image.getWidth();
        int h = image.getHeight();
        double cx = w * 0.5;
        double cy = h * 0.5;
        double maxD = Math.max(w, h) * 0.5;
        int floor = Math.max(0, Math.min(254, noiseFloor));
        double inner = Math.max(0.0, Math.min(0.98, radialInner));

        // 注意：w/h 一律取自 image 实际尺寸（上方 getWidth/getHeight），严禁硬编码
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = image.getPixelRGBA(x, y);
                // NativeImage 像素为小端 RGBA：低字节 R，高字节 A
                int r = p & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = (p >> 16) & 0xFF;
                int a = (p >>> 24) & 0xFF;

                // 亮度 = 通道平均：(r+g+b)/3
                int lum = (r + g + b) / 3;
                // 噪点底限：线性重映射 [floor..255] → [0..255]
                if (lum <= floor) {
                    lum = 0;
                } else {
                    lum = (lum - floor) * 255 / (255 - floor);
                }

                // 径向渐变蒙版：中心 1.0，外缘渐隐到 0
                double dx = (x + 0.5 - cx) / maxD;
                double dy = (y + 0.5 - cy) / maxD;
                double d = Math.sqrt(dx * dx + dy * dy);
                double mask = smoothstep(1.0, inner, d);

                int alpha = (int) (lum * mask + 0.5);
                if (a < 255) {
                    // 贴图自带 Alpha 时相乘保留
                    alpha = alpha * a / 255;
                }
                image.setPixelRGBA(x, y, r | (g << 8) | (b << 16) | (alpha << 24));
            }
        }
    }

    /** 通用 smoothstep，支持 edge0 > edge1（反向渐变） */
    public static double smoothstep(double edge0, double edge1, double x) {
        double denom = edge1 - edge0;
        if (Math.abs(denom) < 1.0E-9) {
            return x < edge0 ? 1.0 : 0.0;
        }
        double t = (x - edge0) / denom;
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }
}

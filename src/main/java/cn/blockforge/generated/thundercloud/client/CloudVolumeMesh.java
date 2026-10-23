package cn.blockforge.generated.thundercloud.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 有真实 XYZ 厚度的软边云团，不使用朝向镜头的平面。
 * 两层不规则封闭曲面组成浓密云心和稀薄外雾；世界朝向固定，仅透明边缘随观察位置变化。
 * 所有几何数据按固定种子缓存，转镜头、走动和换帧不会重新随机生成轮廓。
 */
public final class CloudVolumeMesh {
    private static final int VARIANTS = 12;
    private static final CloudVolumeMesh[][] CACHE = new CloudVolumeMesh[3][VARIANTS];
    private final float[] x, y, z, nx, ny, nz, u, v, density;
    private final int[] indices;
    // 仅渲染线程使用的顶点暂存；每个网格点计算一次，而不是被四个邻面重复计算。
    private final float[] drawX, drawY, drawZ;
    private final int[] drawRgb, drawAlpha;

    private CloudVolumeMesh(int longitude, int latitude, int variant) {
        int count = (longitude + 1) * (latitude + 1);
        x = new float[count]; y = new float[count]; z = new float[count];
        nx = new float[count]; ny = new float[count]; nz = new float[count];
        u = new float[count]; v = new float[count]; density = new float[count];
        drawX = new float[count]; drawY = new float[count]; drawZ = new float[count];
        drawRgb = new int[count]; drawAlpha = new int[count];
        double seed = variant * 2.399963229728653;
        for (int iy = 0; iy <= latitude; iy++) {
            double polar = Math.PI * iy / latitude;
            for (int ix = 0; ix <= longitude; ix++) {
                double angle = Math.PI * 2.0 * ix / longitude;
                double dx = Math.sin(polar) * Math.cos(angle);
                double dy = Math.cos(polar);
                double dz = Math.sin(polar) * Math.sin(angle);
                // 三个尺度的连续起伏；两极和经线闭合，没有硬边球块或破裂接缝。
                double billow = Math.sin(dx * 4.3 + seed) * Math.cos(dy * 3.7 - seed)
                        * Math.sin(dz * 4.1 + seed * 0.6);
                double detail = Math.cos(dx * 9.0 - dy * 5.0 + seed)
                        * Math.sin(dz * 8.0 + dy * 4.0 - seed);
                double radius = 1.0 + 0.17 * billow + 0.055 * detail;
                int i = iy * (longitude + 1) + ix;
                x[i] = (float) (dx * radius);
                y[i] = (float) (dy * radius);
                z[i] = (float) (dz * radius);
                nx[i] = (float) dx; ny[i] = (float) dy; nz[i] = (float) dz;
                u[i] = ix / (float) longitude;
                v[i] = iy / (float) latitude;
                density[i] = (float) (0.74 + 0.19 * billow + 0.07 * detail);
            }
        }
        indices = new int[longitude * latitude * 4];
        int q = 0;
        for (int iy = 0; iy < latitude; iy++) {
            for (int ix = 0; ix < longitude; ix++) {
                int a = iy * (longitude + 1) + ix;
                indices[q++] = a;
                indices[q++] = a + 1;
                indices[q++] = a + longitude + 2;
                indices[q++] = a + longitude + 1;
            }
        }
    }

    public static CloudVolumeMesh get(int quality, int variant) {
        int lod = Math.max(0, Math.min(2, quality));
        int seed = Math.floorMod(variant, VARIANTS);
        CloudVolumeMesh mesh = CACHE[lod][seed];
        if (mesh == null) {
            int longitude = lod == 2 ? 14 : lod == 1 ? 10 : 8;
            int latitude = lod == 2 ? 8 : lod == 1 ? 6 : 4;
            mesh = new CloudVolumeMesh(longitude, latitude, seed);
            CACHE[lod][seed] = mesh;
        }
        return mesh;
    }

    /** 相对相机的平移只在入口计算一次；不会把相机左右/上下向量混进云团几何。 */
    public void draw(VertexConsumer consumer, Matrix4f matrix, int material,
                     double cx, double cy, double cz, double rx, double ry, double rz,
                     double yaw, int bottom, int top, double alpha) {
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        // 体积内层更厚、外层低密度；不写深度，全部曲面由同一批次远到近排序。
        for (int shell = 0; shell < 2; shell++) {
            double scale = shell == 0 ? 0.83 : 1.12;
            double opacity = alpha * (shell == 0 ? 0.74 : 0.30);
            for (int index = 0; index < x.length; index++) {
                double lx = x[index] * rx * scale;
                double ly = y[index] * ry * scale;
                double lz = z[index] * rz * scale;
                double px = cx + lx * cos - lz * sin;
                double py = cy + ly;
                double pz = cz + lx * sin + lz * cos;
                double normalX = nx[index] / rx;
                double normalY = ny[index] / ry;
                double normalZ = nz[index] / rz;
                double wx = normalX * cos - normalZ * sin;
                double wz = normalX * sin + normalZ * cos;
                double denom = Math.sqrt((wx * wx + normalY * normalY + wz * wz)
                        * (px * px + py * py + pz * pz));
                double facing = Math.abs((wx * px + normalY * py + wz * pz) / Math.max(0.00001, denom));
                // 切线处渐隐：不改变顶点位置，不跟着视角转，也不出现实心球的锐利轮廓。
                double edge = AlphaBaker.smoothstep(0.0, shell == 0 ? 0.52 : 0.72, facing);
                double a = opacity * edge * density[index];
                // 只在两种配置色之间按固定表面高度插值，不依赖时间和镜头朝向。
                double shade = Math.max(0.0, Math.min(1.0,
                        0.18 + 0.72 * (ny[index] * 0.5 + 0.5) + 0.10 * density[index]));
                drawRgb[index] = mix(bottom, top, shade);
                drawAlpha[index] = (int) Math.round(Math.min(1.0, a) * 255.0);
                drawX[index] = (float) px;
                drawY[index] = (float) py;
                drawZ[index] = (float) pz;
            }
            for (int index : indices) {
                int rgb = drawRgb[index];
                consumer.vertex(matrix, drawX[index], drawY[index], drawZ[index])
                        .color((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, drawAlpha[index])
                        .uv(CloudMaterialAtlas.u(material, u[index]), CloudMaterialAtlas.v(material, v[index]))
                        .endVertex();
            }
        }
    }

    public static int mix(int a, int b, double t) {
        double f = Math.max(0.0, Math.min(1.0, t));
        int r = (int) (((a >>> 16) & 255) * (1.0 - f) + ((b >>> 16) & 255) * f);
        int g = (int) (((a >>> 8) & 255) * (1.0 - f) + ((b >>> 8) & 255) * f);
        int blue = (int) ((a & 255) * (1.0 - f) + (b & 255) * f);
        return r << 16 | g << 8 | blue;
    }
}

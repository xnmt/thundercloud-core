package cn.blockforge.generated.thundercloud.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** 对所有视觉层应用相同的云中心变形，仍使用原来的素材、颜色和独立批次。 */
public final class AnimatedCloudConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final CloudAnimationShape shape;
    private final double cx, cy, cz;

    public AnimatedCloudConsumer(VertexConsumer delegate, CloudAnimationShape shape,
                                 double cx, double cy, double cz) {
        this.delegate = delegate;
        this.shape = shape;
        this.cx = cx; this.cy = cy; this.cz = cz;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        shape.transform(x - cx, y - cy, z - cz);
        delegate.vertex(cx + shape.x(), cy + shape.y(), cz + shape.z());
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, (int) Math.round(alpha * shape.alpha()));
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
    @Override
    public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
    @Override
    public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
    @Override
    public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    @Override
    public void endVertex() { delegate.endVertex(); }
    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(red, green, blue, alpha);
    }
    @Override
    public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
}

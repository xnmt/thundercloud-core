package cn.blockforge.generated.thundercloud.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** 实体云核写主深度；绒边和电光只写颜色，但仍接受深度遮挡。 */
public final class StormRenderTypes {
    private StormRenderTypes() {}

    public static RenderType solid(ResourceLocation texture) {
        return create(texture, true, false);
    }

    public static RenderType layer(ResourceLocation texture, boolean additive) {
        return create(texture, false, additive);
    }

    private static RenderType create(ResourceLocation texture, boolean solid, boolean additive) {
        return RenderType.create("thundercloud_" + (solid ? "opaque_body" : additive ? "electric_glow" : "soft_edge"),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS,
                RenderType.MEDIUM_BUFFER_SIZE, false, !solid && !additive,
                RenderType.CompositeState.builder()
                        .setShaderState(StormRenderShards.SHADER_POSITION_COLOR_TEX)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(solid ? StormRenderShards.OPAQUE
                                : additive ? StormRenderShards.ADDITIVE : StormRenderShards.TRANSLUCENT)
                        .setDepthTestState(StormRenderShards.DEPTH_TEST)
                        .setCullState(StormRenderShards.NO_CULL)
                        .setLightmapState(StormRenderShards.NO_LIGHTMAP)
                        .setOverlayState(StormRenderShards.NO_OVERLAY)
                        .setLayeringState(StormRenderShards.NO_LAYERING)
                        .setOutputState(solid ? StormRenderShards.MAIN_OUTPUT : StormRenderShards.WEATHER_OUTPUT)
                        .setTexturingState(StormRenderShards.DEFAULT_TEXTURING)
                        .setWriteMaskState(solid ? StormRenderShards.SOLID_WRITE : StormRenderShards.COLOR_WRITE_ONLY)
                        .setLineState(StormRenderShards.DEFAULT_LINE)
                        .setColorLogicState(StormRenderShards.NO_COLOR_LOGIC)
                        .createCompositeState(false));
    }
}

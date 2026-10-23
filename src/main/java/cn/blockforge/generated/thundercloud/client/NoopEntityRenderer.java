package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.entity.ThunderCloudCoreEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 雷云逻辑实体的空渲染器：一个像素都不画。
 *
 * <p>必须显式注册（哪怕什么都不做）——若不给实体类型注册渲染器，
 * 引擎会 fallback 到默认模型（这就是之前"满屏粉色猪"的来源之一）。
 * 注册空渲染器 + 实体 {@code shouldRender=false} 双重保险，
 * 实体的可见输出被彻底封死；所有视觉内容改由
 * {@link StormCloudVisuals} 在关卡阶段事件里绘制，服务端逻辑与客户端视觉完全分离。</p>
 */
public class NoopEntityRenderer extends EntityRenderer<ThunderCloudCoreEntity> {

    public NoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public boolean shouldRender(ThunderCloudCoreEntity cloud, Frustum camera,
                                double camX, double camY, double camZ) {
        return false; // 永不调度渲染，连 fallback 的机会都不留
    }

    @Override
    public void render(ThunderCloudCoreEntity cloud, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 空实现：不提交任何几何体
    }

    @Override
    public ResourceLocation getTextureLocation(ThunderCloudCoreEntity cloud) {
        return DUMMY_TEXTURE;
    }

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/unknown_server.png");
}

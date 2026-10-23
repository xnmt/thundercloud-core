package cn.blockforge.generated.thundercloud.client;

import cn.blockforge.generated.thundercloud.ThunderCloudCoreMod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * 客户端装配：
 * <ul>
 *   <li>为逻辑实体注册空渲染器（绝不画模型，杜绝 fallback 成猪）；</li>
 *   <li>把云视觉挂到 RenderLevelStageEvent（游戏事件总线）；</li>
 *   <li>资源重载时标记图集失效，在渲染线程重新烘焙上传。</li>
 * </ul>
 */
public final class ClientEvents {

    private ClientEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientEvents::onRegisterRenderers);
        modBus.addListener(ClientEvents::onRegisterReloadListeners);
        // 关卡阶段渲染与客户端粒子是游戏级事件，走 MinecraftForge.EVENT_BUS
        MinecraftForge.EVENT_BUS.addListener(StormCloudVisuals::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(CloudDissipationParticles::onClientTick);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ThunderCloudCoreMod.THUNDER_CLOUD_CORE.get(), NoopEntityRenderer::new);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (ResourceManagerReloadListener) CloudMaterialAtlas::invalidate);
    }
}

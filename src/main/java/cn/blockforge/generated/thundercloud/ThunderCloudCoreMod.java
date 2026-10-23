package cn.blockforge.generated.thundercloud;

import cn.blockforge.generated.thundercloud.command.StormCommands;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import cn.blockforge.generated.thundercloud.entity.ThunderCloudCoreEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 雷云核心 ThunderCloud Core —— 面向法术 mod 的前置库。
 *
 * <p>提供：可配置的不透明水平厚云、连续卷曲云纹和云面游走电弧，
 * 以及球形区域查询 API。云体不是独立云球也不是面向相机的粒子：视觉在关卡阶段
 * 以世界空间网格提交，贴图经 TextureManager 注册后由 RenderType 显式绑定。
 * 不使用自定义 ShaderInstance；第三方光影兼容性仍需实机验证。</p>
 *
 * <p>下游 mod 请调用 {@link cn.blockforge.generated.thundercloud.api.ThunderCloudCoreApi}。</p>
 */
@Mod(ThunderCloudCoreMod.MOD_ID)
public final class ThunderCloudCoreMod {
    public static final String MOD_ID = "thundercloud";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<ThunderCloudCoreEntity>> THUNDER_CLOUD_CORE =
            ENTITY_TYPES.register("thunder_cloud_core", () -> ThunderCloudCoreEntity.createType());

    public ThunderCloudCoreMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITY_TYPES.register(bus);

        // 通用逻辑：注册命令、两侧 tick 时检查 JSON 配置热重载
        StormCommands.register();
        StormConfigManager.registerTickHooks();

        if (FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            cn.blockforge.generated.thundercloud.client.ClientEvents.register(bus);
        }
    }
}

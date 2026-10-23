package cn.blockforge.generated.thundercloud.api;

import cn.blockforge.generated.thundercloud.ThunderCloudCoreMod;
import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import cn.blockforge.generated.thundercloud.entity.ThunderCloudCoreEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 雷云核心对外总入口（供法术 mod 调用）。
 *
 * <p>典型用法（施法时生成一片风暴云，随后按球形范围查询命中目标）：</p>
 * <pre>{@code
 * // 服务端：施法
 * ThunderCloudCoreEntity cloud =
 *         ThunderCloudCoreApi.spawnCloud(serverLevel, castPos, "monsoon");
 *
 * // 服务端：周期性区域查询（例如每 tick 判定落雷目标）
 * List<Entity> targets = ThunderCloudCoreApi.queryEntitiesInSphere(
 *         serverLevel, cloud.getStormCenter(), cloud.getStormRadius(),
 *         e -> e instanceof LivingEntity && !(e instanceof Player));
 * }</pre>
 *
 * <p>本库只负责视觉与查询，不产生任何伤害/推动等玩法副作用；
 * 云不碰撞、不受伤、不可选中，不会与其他 mod 的玩法冲突。</p>
 */
public final class ThunderCloudCoreApi {

    /** 查询云覆盖时额外搜索的最大云半径（方块） */
    public static final double MAX_CLOUD_SEARCH = 128.0;

    private ThunderCloudCoreApi() {}

    // ------------------------------------------------------------------
    // 生成
    // ------------------------------------------------------------------

    /**
     * 在服务端生成一朵雷云。typeId 对应 clouds.json 中 types 的键；
     * 传 null 或未知 id 时使用 defaultType。
     */
    @Nullable
    public static ThunderCloudCoreEntity spawnCloud(ServerLevel level, Vec3 center, @Nullable String typeId) {
        EntityType<ThunderCloudCoreEntity> type = ThunderCloudCoreMod.THUNDER_CLOUD_CORE.get();
        if (type == null) return null;
        ThunderCloudCoreEntity cloud = new ThunderCloudCoreEntity(type, level);
        cloud.setPos(center.x, center.y, center.z);
        cloud.setTypeId(typeId);
        return level.addFreshEntity(cloud) ? cloud : null;
    }

    /** 驱散雷云：自动播放消散动画，结束后移除。请在服务端线程调用。 */
    public static void dismissCloud(ThunderCloudCoreEntity cloud) {
        cloud.dismiss();
    }

    /** 已进入消散阶段；法术可据此立即停止自己的伤害等逻辑。 */
    public static boolean isCloudDissipating(ThunderCloudCoreEntity cloud) {
        return cloud.isDissipating();
    }

    /** 切换云的类型（同步到客户端，下一帧生效；不重播形成动画） */
    public static void setCloudType(ThunderCloudCoreEntity cloud, String typeId) {
        cloud.setTypeId(typeId);
    }

    /** 云当前生效半径（方块） */
    public static double getCloudRadius(ThunderCloudCoreEntity cloud) {
        return cloud.getStormRadius();
    }

    /** 云当前生效的参数（只读，勿修改） */
    public static CloudTypeSpec getCloudSpec(ThunderCloudCoreEntity cloud) {
        return cloud.getSpec();
    }

    // ------------------------------------------------------------------
    // 球形区域查询
    // ------------------------------------------------------------------

    /**
     * 球形范围查询：返回中心在球内、存活、且通过 filter 的实体（不含雷云自身）。
     * filter 可为 null 表示不过滤。请在服务端线程调用。
     */
    public static List<Entity> queryEntitiesInSphere(Level level, Vec3 center, double radius,
                                                     @Nullable Predicate<Entity> filter) {
        double r = Math.max(0.5, radius);
        AABB box = new AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r);
        List<Entity> candidates = level.getEntities((Entity) null, box,
                e -> e.isAlive() && !(e instanceof ThunderCloudCoreEntity));
        List<Entity> out = new ArrayList<>();
        double r2 = r * r;
        for (Entity e : candidates) {
            if (e.distanceToSqr(center) <= r2 && (filter == null || filter.test(e))) {
                out.add(e);
            }
        }
        return out;
    }

    /** 查询与给定点重叠的有效雷云（不含消散残影；云中心距不超过 云半径+查询半径）。 */
    public static List<ThunderCloudCoreEntity> queryCloudsInSphere(Level level, Vec3 center, double radius) {
        double r = Math.max(0.0, radius);
        AABB box = new AABB(center, center).inflate(r + MAX_CLOUD_SEARCH);
        List<ThunderCloudCoreEntity> clouds =
                level.getEntitiesOfClass(ThunderCloudCoreEntity.class, box);
        List<ThunderCloudCoreEntity> out = new ArrayList<>();
        for (ThunderCloudCoreEntity cloud : clouds) {
            if (cloud.isRemoved() || cloud.isDissipating()) continue;
            double reach = r + cloud.getStormRadius();
            if (cloud.getStormCenter().distanceToSqr(center) <= reach * reach) {
                out.add(cloud);
            }
        }
        return out;
    }

    /** 该点是否落在任意雷云范围内。 */
    public static boolean isPositionInStorm(Level level, Vec3 pos) {
        return stormDepthAt(level, pos) > 0.0;
    }

    /** 该点离最近云边缘有多深（方块）；不在任何云内返回 0。可用于按深度加权伤害/闪电概率。 */
    public static double stormDepthAt(Level level, Vec3 pos) {
        double best = 0.0;
        for (ThunderCloudCoreEntity cloud : queryCloudsInSphere(level, pos, 0.0)) {
            double depth = cloud.getStormRadius() - cloud.getStormCenter().distanceTo(pos);
            if (depth > best) best = depth;
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 配置访问
    // ------------------------------------------------------------------

    /** 强制立即重读 clouds.json（热重载），返回是否成功 */
    public static boolean reloadConfig() {
        return StormConfigManager.reloadNow();
    }
}

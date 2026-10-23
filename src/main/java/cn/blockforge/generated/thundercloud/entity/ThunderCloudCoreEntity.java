package cn.blockforge.generated.thundercloud.entity;

import cn.blockforge.generated.thundercloud.animation.CloudLifecycle;
import cn.blockforge.generated.thundercloud.config.CloudTypeSpec;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 雷云核心实体：纯逻辑锚点，渲染上一个像素都不出。
 *
 * <p>它承载范围查询、生命周期、持续时间与配置同步（云类型 id），
 * 但绝不负责视觉：为它注册的是空渲染器 {@link cn.blockforge.generated.thundercloud.client.NoopEntityRenderer}，
 * 并且 {@link #shouldRender} 恒为 false——不渲染任何可见内容，也不给引擎
 * fallback 到默认模型（猪）留任何空间。服务端逻辑与客户端视觉彻底分离，
 * 云的全部可见效果由 {@link cn.blockforge.generated.thundercloud.client.StormCloudVisuals}
 * 在 RenderLevelStageEvent 中以软边立体云团和水平漩涡绘制。</p>
 */
public class ThunderCloudCoreEntity extends Entity {

    private static final EntityDataAccessor<String> DATA_TYPE_ID =
            SynchedEntityData.defineId(ThunderCloudCoreEntity.class, EntityDataSerializers.STRING);

    private static final String TAG_TYPE_ID = "CloudTypeId";
    // 一个复合数据项原子同步整个时间轴，避免到达顺序不同导致客户端进度跳变。
    private static final EntityDataAccessor<CompoundTag> DATA_LIFECYCLE =
            SynchedEntityData.defineId(ThunderCloudCoreEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final String TAG_LIFECYCLE = "CloudLifecycle";
    private CloudLifecycle lifecycle;
    private RemovalReason pendingRemoval = RemovalReason.DISCARDED;

    public ThunderCloudCoreEntity(EntityType<? extends ThunderCloudCoreEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
        // 不阻挡玩家准星、不参与碰撞
        this.setInvulnerable(true);
    }

    public static EntityType<ThunderCloudCoreEntity> createType() {
        return EntityType.Builder.<ThunderCloudCoreEntity>of(ThunderCloudCoreEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(24)
                .updateInterval(20)
                .fireImmune()
                .noSummon()
                .build("thunder_cloud_core");
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE_ID, "default");
        this.entityData.define(DATA_LIFECYCLE, new CompoundTag());
    }

    public String getTypeId() {
        return this.entityData.get(DATA_TYPE_ID);
    }

    public void setTypeId(String typeId) {
        this.entityData.set(DATA_TYPE_ID, typeId == null ? "default" : typeId);
    }

    /** 该云当前生效的参数（来自热重载配置） */
    public CloudTypeSpec getSpec() {
        return StormConfigManager.specFor(getTypeId());
    }

    /** 云盘半径（方块），即区域查询半径 */
    public double getStormRadius() {
        return getSpec().clampedRadius();
    }

    /** 云盘中心（实体位置），视觉层以此为旋转与分布的锚点 */
    public Vec3 getStormCenter() {
        return this.position();
    }

    /** 新实体入场即启动；读档实体已有时间轴，不重播形成。 */
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) ensureLifecycle();
    }

    private void ensureLifecycle() {
        if (lifecycle == null) {
            setLifecycle(CloudLifecycle.summon(level().getGameTime(),
                    CloudLifecycle.secondsToTicks(getSpec().formationSeconds)));
        }
    }

    private void setLifecycle(CloudLifecycle state) {
        lifecycle = state;
        CompoundTag data = new CompoundTag();
        data.putLong("CreatedAt", state.createdAt());
        data.putInt("FormationTicks", state.formationTicks());
        data.putLong("DismissedAt", state.dismissedAt());
        data.putInt("DissipationTicks", state.dissipationTicks());
        entityData.set(DATA_LIFECYCLE, data);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LIFECYCLE.equals(key)) {
            CompoundTag data = entityData.get(DATA_LIFECYCLE);
            if (data.contains("CreatedAt", 4)) {
                lifecycle = new CloudLifecycle(data.getLong("CreatedAt"), data.getInt("FormationTicks"),
                        data.contains("DismissedAt", 4) ? data.getLong("DismissedAt") : -1L,
                        data.getInt("DissipationTicks"));
            }
        }
    }

    /** 尚未收到时间轴时不画完整云盘，避免生成首帧闪现。 */
    public CloudLifecycle.Frame getAnimationFrame(float partialTick) {
        return lifecycle == null ? new CloudLifecycle.Frame(0.0, 0.0)
                : lifecycle.sample(level().getGameTime() + partialTick);
    }

    public boolean isDissipating() {
        return lifecycle != null && lifecycle.isDissipating();
    }

    /** 服务端开始驱散；重复调用不会把消散倒计时重置。 */
    public void dismiss() {
        remove(RemovalReason.DISCARDED);
    }

    /**
     * discard()是父类final方法，但它与kill()都会经过remove()。
     * 只延迟主动销毁；区块卸载、换维度和客户端收到的移除包照常处理。
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && !isRemoved()
                && (reason == RemovalReason.DISCARDED || reason == RemovalReason.KILLED)) {
            ensureLifecycle();
            if (!lifecycle.isDissipating()) {
                pendingRemoval = reason;
                setLifecycle(lifecycle.dismiss(level().getGameTime(),
                        CloudLifecycle.secondsToTicks(getSpec().dissipationSeconds)));
            }
            if (lifecycle.finished(level().getGameTime())) super.remove(pendingRemoval);
            return;
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) return;
        ensureLifecycle();
        long now = level().getGameTime();
        if (lifecycle.isDissipating()) {
            if (lifecycle.finished(now)) super.remove(pendingRemoval);
        } else {
            double duration = getSpec().durationSeconds;
            if (Double.isFinite(duration) && duration > 0.0
                    && (now - lifecycle.createdAt()) / 20.0 >= duration) dismiss();
        }
    }

    // ---- 纯视觉锚点：拒绝一切玩法干扰 ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 逻辑锚点：渲染侧永远不可见（视觉由关卡阶段事件统一绘制） */
    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return false;
    }

    // ---- 序列化 ----

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_TYPE_ID, 8)) {
            this.setTypeId(tag.getString(TAG_TYPE_ID));
        }
        CompoundTag saved = tag.getCompound(TAG_LIFECYCLE);
        if (saved.contains("CreatedAt", 4)) {
            setLifecycle(new CloudLifecycle(saved.getLong("CreatedAt"), saved.getInt("FormationTicks"),
                    saved.contains("DismissedAt", 4) ? saved.getLong("DismissedAt") : -1L,
                    saved.getInt("DissipationTicks")));
        } else {
            // 老存档里的云视为已经形成；不能把重新进入世界当作再次召唤。
            setLifecycle(CloudLifecycle.summon(level().getGameTime(), 0));
        }
        pendingRemoval = "KILLED".equals(tag.getString("CloudRemovalReason"))
                ? RemovalReason.KILLED : RemovalReason.DISCARDED;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(TAG_TYPE_ID, this.getTypeId());
        tag.put(TAG_LIFECYCLE, entityData.get(DATA_LIFECYCLE).copy());
        tag.putString("CloudRemovalReason", pendingRemoval.name());
    }
}

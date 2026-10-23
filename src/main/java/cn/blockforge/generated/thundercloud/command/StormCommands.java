package cn.blockforge.generated.thundercloud.command;

import cn.blockforge.generated.thundercloud.api.ThunderCloudCoreApi;
import cn.blockforge.generated.thundercloud.config.StormConfig;
import cn.blockforge.generated.thundercloud.config.StormConfigManager;
import cn.blockforge.generated.thundercloud.entity.ThunderCloudCoreEntity;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * 调试命令（OP 权限）：
 * /thundercloud spawn [类型]   在当前位置的世界高度 Y=128 生成一朵雷云
 * /thundercloud query <半径>   以自身为中心做球形区域查询并汇报数量
 * /thundercloud list           列出配置里的云类型
 * /thundercloud reload         强制热重载 clouds.json
 * /thundercloud dismiss        驱散当前维度全部已加载雷云，先播放消散动画
 */
public final class StormCommands {

    private StormCommands() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StormCommands::onRegister);
    }

    private static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thundercloud")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(ctx -> doSpawn(ctx, "default"))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> doSpawn(ctx, StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("query")
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.5F, 128.0F))
                                .executes(StormCommands::doQuery)))
                .then(Commands.literal("dismiss").executes(StormCommands::doDismiss))
                .then(Commands.literal("list").executes(StormCommands::doList))
                .then(Commands.literal("reload").executes(StormCommands::doReload)));
    }

    private static int doSpawn(CommandContext<CommandSourceStack> ctx, String typeId) {
        CommandSourceStack source = ctx.getSource();
        // 世界绝对高度，不是玩家头顶再加128格；只沿用命令位置的X、Z。
        Vec3 position = source.getPosition();
        ThunderCloudCoreEntity cloud = ThunderCloudCoreApi.spawnCloud(
                source.getLevel(), new Vec3(position.x, 128.0, position.z), typeId);
        if (cloud == null) {
            source.sendFailure(Component.literal("雷云实体类型尚未注册"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "已在 " + cloud.blockPosition().toShortString() + " 生成雷云，类型=" + typeId
                        + "，直径=" + String.format("%.1f", cloud.getStormRadius() * 2.0) + " 格"
                        + "，中心固定在世界高度 Y=128"), true);
        return 1;
    }

    /** 与旧/kill选择器一致，仅影响当前维度已加载的雷云。 */
    private static int doDismiss(CommandContext<CommandSourceStack> ctx) {
        List<ThunderCloudCoreEntity> clouds = new java.util.ArrayList<>();
        for (Entity entity : ctx.getSource().getLevel().getAllEntities()) {
            if (entity instanceof ThunderCloudCoreEntity cloud
                    && !cloud.isRemoved() && !cloud.isDissipating()) clouds.add(cloud);
        }
        // 时长为0时会立刻移除，先复制集合，避免遍历期间修改原实体列表。
        clouds.forEach(ThunderCloudCoreApi::dismissCloud);
        int affected = clouds.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已开始驱散当前维度的 " + affected + " 朵雷云，消散结束后移除"), true);
        return affected;
    }

    private static int doQuery(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double radius = FloatArgumentType.getFloat(ctx, "radius");
        List<Entity> targets = ThunderCloudCoreApi.queryEntitiesInSphere(
                source.getLevel(), source.getPosition(), radius, null);
        List<ThunderCloudCoreEntity> clouds = ThunderCloudCoreApi.queryCloudsInSphere(
                source.getLevel(), source.getPosition(), radius);
        long mobs = targets.stream().filter(e -> e instanceof LivingEntity).count();
        source.sendSuccess(() -> Component.literal(
                "球形查询 r=" + String.format("%.1f", radius) + "："
                        + targets.size() + " 个实体（含 " + mobs + " 个生物），"
                        + clouds.size() + " 朵雷云覆盖"), false);
        return targets.size();
    }

    private static int doList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StormConfig cfg = StormConfigManager.get();
        StringBuilder sb = new StringBuilder("云类型：");
        cfg.types.keySet().forEach(k -> sb.append(k).append("  "));
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return cfg.types.size();
    }

    private static int doReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean ok = ThunderCloudCoreApi.reloadConfig();
        source.sendSuccess(() -> Component.literal(
                ok ? "clouds.json 已热重载" : "重载失败，沿用上一份有效配置（看日志）"), false);
        return ok ? 1 : 0;
    }
}

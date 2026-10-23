package cn.blockforge.generated.thundercloud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * clouds.json 的加载与热重载。
 *
 * <p>文件位置：&lt;游戏目录&gt;/config/thundercloud/clouds.json。
 * 首次运行自动生成默认配置；保存后约 1 秒内自动生效，也可执行 /thundercloud reload
 * 立即重载（客户端与服务端各自热重载，无需重启）。解析失败时保留上一份有效配置并打日志。</p>
 *
 * <p>规范化：每次载入后把文件内容与程序写出的规范形式比对，不一致就当场重写一次
 * （补齐缺省字段、移除无效字段、统一缩进与字段顺序），玩家写过的数值原样保留；
 * 已经是规范形式的文件不会被反复改写。</p>
 */
public final class StormConfigManager {
    /** 当前配置格式版本 */
    private static final int CONFIG_VERSION = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long CHECK_INTERVAL_TICKS = 20L;

    private StormConfigManager() {}

    private static volatile StormConfig current = StormConfig.createDefault();
    private static long tickCounter = 0L;
    private static long lastModified = -1L;
    private static boolean initialized = false;

    public static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("thundercloud").resolve("clouds.json");
    }

    public static StormConfig get() {
        return current;
    }

    /** 按类型名取参数；未知类型回落到 defaultType，再回落到表内第一项，最后硬编码默认。 */
    public static CloudTypeSpec specFor(String typeId) {
        StormConfig cfg = current;
        if (typeId != null) {
            CloudTypeSpec s = cfg.types.get(typeId);
            if (s != null) return s;
        }
        CloudTypeSpec fallback = cfg.types.get(cfg.defaultType);
        if (fallback != null) return fallback;
        return cfg.types.isEmpty() ? new CloudTypeSpec() : cfg.types.values().iterator().next();
    }

    /** 挂到游戏事件总线：客户端与服务端 tick 定期查文件是否被修改 */
    public static void registerTickHooks() {
        MinecraftForge.EVENT_BUS.register(StormConfigManager.class);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) tick();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) tick();
    }

    private static void tick() {
        if (!initialized) {
            initialized = true;
            reloadNow();
            return;
        }
        if (++tickCounter % CHECK_INTERVAL_TICKS != 0L) return;
        try {
            Path file = configFile();
            if (Files.exists(file)) {
                long mtime = Files.getLastModifiedTime(file).toMillis();
                if (mtime != lastModified) {
                    loadFrom(file);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[ThunderCloudCore] 配置文件检查失败: {}", e.getMessage());
        }
    }

    /** 强制立即重载（命令 /thundercloud reload 也走这里）；返回是否成功 */
    public static boolean reloadNow() {
        try {
            Path file = configFile();
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(StormConfig.createDefault()), StandardCharsets.UTF_8);
                LOGGER.info("[ThunderCloudCore] 已生成默认配置 {}", file);
            }
            loadFrom(file);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[ThunderCloudCore] 配置加载失败，沿用上一份有效配置: {}", e.toString());
            return false;
        }
    }

    private static void loadFrom(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        StormConfig parsed = GSON.fromJson(json, StormConfig.class);
        if (parsed == null || parsed.types == null || parsed.types.isEmpty()) {
            throw new IllegalStateException("clouds.json 缺少 types 段");
        }
        if (parsed.render == null) parsed.render = new StormConfig.RenderOptions();
        for (CloudTypeSpec spec : parsed.types.values()) {
            if (spec == null) throw new IllegalStateException("types 中存在 null 条目");
            if (spec.eye == null) spec.eye = new CloudTypeSpec.EyeSpec();
            if (spec.arcs == null) spec.arcs = new CloudTypeSpec.ArcSpec();
            // 0.45 是电弧寿命的过期默认值：只要文件里还写着它，就随规范化统一换成 0.35
            if (spec.arcs.crawlSeconds == 0.45) spec.arcs.crawlSeconds = 0.35;
            if (spec.textures == null || spec.textures.isEmpty()) {
                spec.textures = new CloudTypeSpec().textures;
            }
        }
        if (parsed.version != CONFIG_VERSION) parsed.version = CONFIG_VERSION;
        current = parsed;
        // 内容与规范形式不一致（缺字段、多僵尸字段、旧默认值、排版漂移）就重写一次；
        // 一致的普通情况只做一次字符串比较，不产生任何磁盘写入。
        String canonical = GSON.toJson(parsed);
        if (!canonical.equals(json)) {
            Files.writeString(file, canonical, StandardCharsets.UTF_8);
            LOGGER.info("[ThunderCloudCore] 配置文件已规范化重写：{}", file);
        }
        lastModified = Files.getLastModifiedTime(file).toMillis();
        LOGGER.info("[ThunderCloudCore] 配置已重载：{} 种云类型", parsed.types.size());
    }
}

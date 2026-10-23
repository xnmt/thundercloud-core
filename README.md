# 雷云核心 ThunderCloud Core

适用：Minecraft Java 版 **1.20.1 + Forge**，modid：`thundercloud`，无需任何前置模组。

这是一个**纯视觉**的雷云前置模组：召唤一朵遮天的紫黑色漩涡雷云，云面有电弧窜动，供其他模组或地图做"雷暴氛围"。它不打雷、不伤害、不起火，玩法全部由接入方自己写。

## 功能一览

- **持续慢转的漩涡外形**：宽旋臂、表面云纹和参差外缘一起旋转，默认48秒一圈；内部细纹与外部大团云差速流动，不是静止圆盘。
- **内凹的漩涡中心**：中心明显凹陷，云质大部分呈近白色，紫白辉光贴着凹壁旋入；凹底仍是不透明云层，不会挖穿露出蓝天。
- **封闭的立体云体**：云顶、云底和侧壁连成一体的厚云，站在云下不会透天；只有外围约两格宽的薄雾渐隐。
- **云面电弧**：紫白亮芯、紫色余光，逐段蔓延、分叉、回闪；默认12组主弧，触发时刻、蔓延方向（顺逆时针随机）和寿命（0.35秒以内、每回长短不一）全部每次放电现场重掷，不会有规律转圈或轮流动电的节奏；电弧绝大多数趴在云底——玩家抬头那面，云顶只有偶发几条；放电附近的云皮会短暂亮起。
- **螺旋形成、缓慢消散**：新云默认用3秒从细小卷云铺开成完整厚云；驱散时颜色变淡、崩碎成紫色星光，默认再用3秒消失，动画结束才真正移除。
- **性能自适应**：近、中、远三档网格按观看距离自动切换（1.0.3 起三档各收一档：环向 128→96、径向环数 48→30），云面动画按10次/秒采样缓存；同屏云数和电弧组数都有预算上限；风暴眼与云面电弧只画镜头这一侧，候选云查询按 tick 缓存——1.0.3 把每帧顶点从约 12 万降到约 5 万（−60%）。
- **空间约定**：云中心固定在世界高度 **Y=128**，常规预设最大直径 **128 格**；云盘平行地面，不跟随视角转向，靠近会自然放大。

## 快速上手

1. 装好 **Minecraft 1.20.1 + Forge**（47.3.5 及以上），把 `thundercloud-1.0.3.jar` 放进 `mods` 文件夹（只放一份即可）。
2. 进入允许作弊的世界，在开阔处执行：

   ```mcfunction
   /thundercloud spawn vortex
   ```

   云生成在执行位置的 X、Z 上方，**Y=128**，3秒螺旋铺开，之后永久存在（玩家高于128时云在下方）。
3. 走到云下仰头看：云体遮天、中心发白凹陷、电弧在云面窜动；也可用创造模式从 Y≈64 升到 Y≈96 体会云明显放大、斜看中心凹陷。
4. 执行 `/thundercloud dismiss`，看3秒紫色星光消散；也可以刚召唤就驱散，小卷云会平滑消失，不会跳成大云。

## 命令一览

| 命令 | 作用 |
| --- | --- |
| `/thundercloud spawn [类型]` | 在执行位置上方生成一朵云；不写类型用 `default` |
| `/thundercloud dismiss` | 驱散当前维度全部已加载的云（先播消散动画再移除） |
| `/thundercloud list` | 列出所有云类型名 |
| `/thundercloud query <半径>` | 统计被云罩住的实体数 |
| `/thundercloud reload` | 立即重读配置文件 |

自带类型：`default` / `vortex`（128格直径主力）、`monsoon`（紫黑）、`small`（直径12格，第30秒自动开始消散，测试用）。

也可以用原版选择器清除：`/kill @e[type=thundercloud:thunder_cloud_core]`（同样先播消散动画）。云实体是不可见的逻辑锚点，被 `noSummon` 保护——请用命令或下面的 API 生成，不要用原版 `/summon`。

## 配置文件：`config/thundercloud/clouds.json`

第一次进游戏自动生成；**改完保存约1秒内生效，不用重启游戏**（多人时客户端、服务端各自热重载，建议两侧内容一致，否则客户端按自己的配置画云）。写坏了会沿用上一份有效配置并在日志里提示，游戏不会崩。文件由程序自动保持整齐：载入后与规范形式比对，缺字段、多无效字段或排版漂移就当场重写一次，你写过的数值原样保留；`version` 字段不用管。

默认配置长这样（`render` 是全局参数，`types` 下每种云一段）：

```json
{
  "version": 1,
  "defaultType": "default",
  "render": {
    "bakeAlpha": true,
    "textureTargetSize": 512,
    "budgetPerCloud": 1024,
    "budgetTotal": 1600,
    "maxRenderDistance": 256.0,
    "radialFalloff": 0.52,
    "noiseFloor": 12
  },
  "types": {
    "default": {
      "radius": 64.0,
      "thickness": 7.0,
      "vortexBands": 16,
      "eyeOpening": 0.16,
      "eyeDepression": 0.86,
      "rotationPeriodSeconds": 48.0,
      "swirlArms": 3,
      "swirlTurns": 0.65,
      "corePeriodSeconds": 3.0,
      "edgePeriodSeconds": 12.0,
      "colorBottom": "#10091D",
      "colorTop": "#665080",
      "budget": 960,
      "durationSeconds": 0.0,
      "formationSeconds": 3.0,
      "dissipationSeconds": 3.0,
      "textures": [ "cloud/cloud_core_a.png", "cloud/cloud_core_b.png", "cloud/cloud_edge.png" ],
      "eye": {
        "enabled": true,
        "layers": 3,
        "sizeScale": 3.0,
        "speedScale": 1.4,
        "alpha": 0.55,
        "colorEyeGlow": "#F4F0FF",
        "texture": "cloud/cloud_eye_swirl.png"
      },
      "arcs": {
        "enabled": true,
        "count": 12,
        "intervalSeconds": 1.3,
        "flashSeconds": 0.18,
        "crawlSeconds": 0.35,
        "maxConcurrent": 6,
        "topChance": 0.15,
        "lengthScale": 1.0,
        "width": 0.18,
        "color": "#CFB0FF",
        "coreColor": "#FAF3FF"
      }
    }
  }
}
```

实际生成的文件里有 `default`、`small`、`monsoon`、`vortex` 四段，结构与上面完全相同，只是数值不同：`small` 的 `radius` 6、`budget` 64、`vortexBands` 8、`durationSeconds` 30；`monsoon` 的 `thickness` 8、`budget` 1024、更暗的配色、`corePeriodSeconds` 4 / `edgePeriodSeconds` 16；`vortex` 与 `default` 数值一致，只是多占一个类型名方便命令调用。

想加自己的云类型？照抄一段 `default` 改名（比如 `mythstorm`）再调数值，`/thundercloud spawn mythstorm` 立刻能用。已存在的云位置不会因改配置而移动；`radius` 等外形参数保存后，已召唤的云下一帧就会变。

### 字段说明

**云体外形（`types.<名字>` 下）**

| 字段 | 含义 | 范围 / 默认 |
| --- | --- | --- |
| `radius` | 云盘半径（方块），同时是范围查询半径 | 1~96 / 64 |
| `thickness` | 云体蓬松厚度，中心因内凹自动更薄 | 0.3~32 / 7 |
| `vortexBands` | 云面细节带数：越大云纹越细、网格越密、开销越高 | 8~32 / 16 |
| `eyeOpening` | 风暴眼开口占半径比例，开口内仍是不透明云底 | 0.08~0.45 / 0.16 |
| `eyeDepression` | 中心内凹强度，不会挖穿成洞 | 0~0.9 / 0.86 |

**运动**

| 字段 | 含义 | 范围 / 默认 |
| --- | --- | --- |
| `rotationPeriodSeconds` | 整片云转一圈的秒数，越大越慢 | 8~240 / 48 |
| `swirlArms` | 大旋臂数量（连续云面上的隆起，不是分离云团） | 2~5 / 3 |
| `swirlTurns` | 旋臂从内到外的卷曲圈数 | 0.2~1.2 / 0.65 |
| `corePeriodSeconds` / `edgePeriodSeconds` | 内侧细云纹 / 外侧大团云的流动速度尺度，两者不同速形成差速滚动 | ≥0.25 / 3 和 12 |

**颜色与动画时长**

| 字段 | 含义 | 默认 |
| --- | --- | --- |
| `colorBottom` / `colorTop` | 云底暗部 / 云顶亮部，#RRGGBB，表面按高度混色，不随视角或时间染色 | `#10091D` / `#665080` |
| `formationSeconds` / `dissipationSeconds` | 螺旋形成 / 缓慢消散秒数，0=关闭该动画，最多60秒；多人时由服务端同步。形成时长在新云召唤时确定，消散时长在开始驱散时确定，已开始的动画不受热重载打断 | 各 3 |
| `durationSeconds` | 从召唤到开始驱散的秒数（含形成阶段），0=永久 | 0 |
| `textures` | 云贴图路径（相对 `assets/thundercloud/textures/`）；第一张用作不透明主体 | 自带3张 |

**风暴眼 `eye`**

| 字段 | 含义 | 默认 |
| --- | --- | --- |
| `enabled` | 风暴眼辉光总开关 | true |
| `layers` | 叠层片数，越多辉光越厚、开销越高 | 3（1~6） |
| `sizeScale` | 核心相对开口的尺寸倍率 | 3.0 |
| `speedScale` | 核心相对整片云的转速倍率 | 1.4 |
| `alpha` | 辉光不透明度（0~1） | 0.55 |
| `colorEyeGlow` | 辉光颜色 | `#F4F0FF` |
| `texture` | 螺旋纹理路径 | `cloud/cloud_eye_swirl.png` |

**云面电弧 `arcs`（纯视觉，不伤人、不起火）**

| 字段 | 含义 | 默认 |
| --- | --- | --- |
| `enabled` | 电弧与局部电光总开关 | true |
| `count` | 主弧组数，每组自带3条分叉；触发时刻与方向各组独立随机 | 12（0~16） |
| `intervalSeconds` | 同组两次放电的最长间隔，触发时刻在间隔内随机挑 | 1.3（0.5~30） |
| `crawlSeconds` | 单条电弧寿命上限，每回随机掷出0.6~1.0倍；越小闪得越脆，之后沿原路径回闪 | 0.35 |
| `flashSeconds` | 单次闪光持续秒数（内部含多次短脉冲） | 0.18 |
| `maxConcurrent` | 同屏同时绘制的主弧组数上限（性能阀），0=不限制；超出的电弧连路径都不生成 | 6 |
| `topChance` | 同一次放电也爬上云顶面的概率；调到0=云底独享，1=上下各画一套 | 0.15（0~1） |
| `lengthScale` | 电弧长度倍率，默认主弧跨越约35~75格云层 | 1.0（0.35~1.6） |
| `width` | 直径128格云上的亮芯宽度（方块） | 0.18 |
| `color` / `coreColor` | 余光 / 亮芯颜色 | `#CFB0FF` / `#FAF3FF` |

**全局参数 `render`**

| 字段 | 含义 | 默认 |
| --- | --- | --- |
| `budgetTotal` | 同屏所有云的预算总额（硬顶2048），耗尽后更远的云省略 | 1600 |
| `budgetPerCloud` | 单朵云的预算上限 | 1024 |
| `budget`（写在各类型里） | 一朵这种云的开销值：不影响外观，只决定同屏能显示几朵；0=不渲染该类型 | 960 |
| `maxRenderDistance` | 云的最大渲染距离（方块），超过整朵剔除 | 256（32~512） |
| `bakeAlpha` | 加载贴图时把亮度烘焙成透明度（白实黑透）；false=按贴图自带Alpha渲染 | true |
| `textureTargetSize` | 云贴图边长上限（自动取2的幂，16~1024），过大的贴图重采样到该尺寸 | 512 |
| `radialFalloff` | 贴图径向柔边起点（0~0.98）：距中心该比例内保持完整，向外渐隐到全透明 | 0.52 |
| `noiseFloor` | 噪点底限（0~255），比这更暗的像素直接全透明，用来压掉黑底灰噪 | 12 |

### 常见调法

- **电弧更猛**：`arcs.count` 提到 16、`intervalSeconds` 降到 0.8；嫌太亮可把 `width` 调小；想让云顶也常见电光，把 `topChance` 提到 0.6~1.0。
- **电弧更安静**：`count` 降到 6、`intervalSeconds` 提到 3。
- **完全不要电光**（对闪烁敏感）：`arcs.enabled: false`；想连中心辉光也关掉，再设 `eye.enabled: false`。
- **云更厚 / 中心更凹**：`thickness` 提到 9~12、`eyeDepression` 提到 0.9。
- **游戏掉帧**：1.0.3 已经把每帧顶点砍掉约 60%（云核 −53%、电弧 −70%、风暴眼 −70%、查询改按 tick）。如果仍然掉帧：先把 `arcs.maxConcurrent` 降到 3、`arcs.count` 降到 8，再降 `vortexBands` 到 10~12，最后降 `budgetTotal`。
- **测试用小云**：`/thundercloud spawn small`。

### 重新进世界会发生什么

重新进入世界、走出再走回实体加载范围：已有云按已形成处理，不会重播形成动画；旧存档雷云直接完整出现。区块卸载或切换维度不算驱散，不为它们强行保留实体。

## 作为前置模组接入你的 mod（给开发者）

### 关键信息一览

| 项目 | 值 |
| --- | --- |
| modid | `thundercloud` |
| 游戏与加载器 | Minecraft Java **1.20.1 + Forge 47.x**（玩家装 **47.3.5 及以上**即可；构建自 47.4.0，兼容整个 47.x 线） |
| 当前版本 | `1.0.3`（由 `gradle.properties` 的 `modVersion` 统一管理）；依赖范围写 `[1.0,)`，通吃所有 1.x 更新 |
| 前置 jar | `thundercloud-1.0.3.jar`（服务端、客户端两侧都要装） |
| API 入口类 | `cn.blockforge.generated.thundercloud.api.ThunderCloudCoreApi` |
| 云实体注册名 | `thundercloud:thunder_cloud_core`（不可见逻辑锚点；请走 API 或命令生成） |
| 配置文件 | `config/thundercloud/clouds.json`（改完自动热重载） |
| 自带云类型 | `default` / `vortex` / `monsoon` / `small` |

### 你的 mods.toml 里声明依赖

```toml
[[dependencies.你的modid]]
modId="thundercloud"
mandatory=true
versionRange="[1.0,)"
ordering="NONE"
side="BOTH"
```

### 开发环境接上这个 jar

把 `thundercloud-1.0.3.jar` 放进你工程的 `libs/` 目录，`build.gradle` 里加：

```gradle
dependencies {
    compileOnly files('libs/thundercloud-1.0.3.jar')  // 编译时能引用 API
    runtimeOnly files('libs/thundercloud-1.0.3.jar')  // runClient 时真正加载
}
```

本模组用 official 映射编译，类名即发布名，`files(...)` 直接可用，不需要 `fg.deobf`，也不需要 maven 仓库。

### 代码接口一览（全部在服务端线程调用）

```java
import cn.blockforge.generated.thundercloud.api.ThunderCloudCoreApi;
import cn.blockforge.generated.thundercloud.entity.ThunderCloudCoreEntity;

// 1) 施法：在指定位置生成一朵雷云（typeId 未知或 null 时回落 default；失败返回 null）
ThunderCloudCoreEntity cloud =
        ThunderCloudCoreApi.spawnCloud(serverLevel, new Vec3(x, 128.0, z), "vortex");

// 2) 范围查询：云覆盖内的实体（不含云自身；filter 可为 null）
List<Entity> targets = ThunderCloudCoreApi.queryEntitiesInSphere(
        serverLevel, cloud.getStormCenter(), cloud.getStormRadius(),
        e -> e instanceof LivingEntity && !(e instanceof Player));

// 3) 判定某点是否被雷云罩住；以及“离云边缘多深”（可按深度加权落雷概率/伤害）
boolean covered = ThunderCloudCoreApi.isPositionInStorm(level, pos);
double depth    = ThunderCloudCoreApi.stormDepthAt(level, pos);

// 4) 找与某点重叠的所有云（例如“我头顶是哪片云”）
List<ThunderCloudCoreEntity> overhead =
        ThunderCloudCoreApi.queryCloudsInSphere(level, playerPos, 0.0);

// 5) 生命周期与外观控制
ThunderCloudCoreApi.setCloudType(cloud, "monsoon");   // 换类型，下一帧生效
double radius = ThunderCloudCoreApi.getCloudRadius(cloud); // 当前生效半径（方块）
CloudTypeSpec spec = ThunderCloudCoreApi.getCloudSpec(cloud); // 只读参数，勿修改
ThunderCloudCoreApi.dismissCloud(cloud);             // 播放消散动画，结束后移除
boolean fading = ThunderCloudCoreApi.isCloudDissipating(cloud); // 法术可据此停止自己的伤害逻辑
cloud.discard();                                     // 同样先播消散动画再删除；kill()、/kill 也是
ThunderCloudCoreApi.reloadConfig();                  // 强制热重载 clouds.json
```

### 使用规则

- **只管视觉与查询**：本模组不打雷、不伤害、不推动、不起火。要落雷请自己处理 `queryEntitiesInSphere` 的结果（原版 `LightningBolt` 或你的法术逻辑）。云实体本身免疫伤害、不可选中、不参与碰撞，不会与其他玩法冲突。
- **视觉自动出现**：调用 API 后，厚云遮天与云面电弧由本模组的客户端自动绘制，你不需要写任何渲染代码，也不要自己给云实体注册渲染器。
- **持续时间**由配置 `durationSeconds` 决定（默认0=永久），从召唤起计时、含形成阶段；到时自动开始驱散，消散动画时长另加（例如10秒持续+3秒消散≈第13秒真正移除）。
- **驱散一律先动画后移除**：`dismissCloud`、`discard`、`kill`、`/kill` 都走消散动画；动画结束才 `isRemoved()`。驱散一开始，范围查询（`queryCloudsInSphere`、`stormDepthAt`、`isPositionInStorm`）就立刻不再把它算作有效云；手上持有实体引用的法术，请用 `isCloudDissipating` 判断并停掉自己的伤害逻辑，不要只等 `isRemoved()`。请勿通过底层 `setRemoved` 绕过生命周期。
- **动画与时间轴**：形成时长在召唤时确定、消散时长在开始驱散时确定，均由服务端同步；读档恢复相同进度，不重播；换类型不重播；重复驱散不会续期或重置；游戏暂停或低 TPS 时动画与游戏时间一起暂停或变慢。
- **自定义云类型**：在 `clouds.json` 的 `types` 里加一段即可，`spawnCloud` 传你的类型名就能用，改完约1秒内生效。
- **坐标**：通过 API 生成时采用调用方传入的世界坐标（推荐 Y=128），不强制改高度。
- **多人**：服务端与客户端都装相同版本。
- **分发**：请让玩家单独下载前置 jar，不要把本模组 jar 塞进你自己的 jar（jar-in-jar）。
- **免代码用法**：命令方块 / 数据包里直接执行 `/thundercloud spawn vortex`（需要 OP2）。

## 版本号从哪来（集中管理）

版本全部在 **`gradle.properties`** 里统一维护，改完重新构建即生效：

```properties
# gradle.properties
modVersion=1.0.3            # 模组版本（打进 jar 名与 mods.toml）
minecraftVersion=1.20.1     # 游戏版本
forgeVersion=47.3.5         # 玩家侧最低 Forge 版本（写进 mods.toml 依赖范围 [47.3.5,)）
forgeBuildVersion=47.4.0    # 编译容器固定提供的 Forge 版本（勿改）
```

`build.gradle` 与 `META-INF/mods.toml` 都从这几行读取，本工程不需要再手改其他文件。

## 参考与验证范围

- [公开参考与许可说明](docs/visual-references.md)：区分 Apex Arcana、Lodestone 与电弧算法示例；没有复制参考模组的代码或资源。
- [云面与旋转回归测试](src/test/java/cn/blockforge/generated/thundercloud/config/VortexRegressionTest.java)：连续云面厚度、内凹中心封闭、旋臂真实旋转与周期接缝、每个实体顶点完全不透明、云面电弧投影、顶点与电弧性能预算、配置默认形状，随构建自动运行。
- [生命周期回归测试](src/test/java/cn/blockforge/generated/thundercloud/config/CloudLifecycleRegressionTest.java)：默认/自定义/零/异常时长、形成途中驱散、重复驱散、时间轴恢复、缺省字段回落与预算夹取、动画首尾和批次切换连续性。
- 静态检查与离线预览脚本（`tools/*.py`）**未包含在本仓库**中；上面的预览图由这些脚本离线渲染，不是游戏截图，也不是近似的第二套几何。
- 预览图：[形成与消散分镜](docs/previews/cloud-lifecycle.png)、[不同时间的旋转对比](docs/previews/cloud-rotation.png)、[远近与上下侧面](docs/previews/cloud-shape.png)。棋盘仅是用于检查透天的测试背景，不是游戏素材。
- **尚未启动游戏实测。** 离线预览不包含原版雾、地形与后处理；实际观感、多人同屏性能、Fabulous模式及第三方光影兼容仍需在游戏内确认。

## 开源协议

- 本模组的 Java 源码、纹理、语言文件与文档均以 **MIT License** 授权，见 [LICENSE](LICENSE)。
- 不包含 Minecraft / Forge / 其他模组的代码或资源；运行时依赖由玩家自行安装。
- 欢迎转载、修改、二次分发，保留版权声明即可。

## 构建

需要 **JDK 17** 与 **Gradle 8.x**；首次构建要联网下载 ForgeGradle 与 Minecraft 依赖。

```bash
gradle build
```

产物：`build/libs/thundercloud-<version>.jar`。

> 仓库暂未附带 Gradle Wrapper。拉取后在项目根目录执行一次 `gradle wrapper` 生成
> `gradlew` / `gradle/wrapper/`，之后统一用 `./gradlew build`（Windows：`gradlew.bat build`）。

## 仓库结构

```
src/main/java      模组源码：实体、命令、配置、客户端渲染、对外 API
src/main/resources 资源：mods.toml、语言文件、云纹理、pack.mcmeta
src/test/java      离线回归测试（不启动游戏）
docs/              预览图与参考资料说明
dist/              宣传/展示截图
```

## 参与贡献

欢迎提交 Issue 与 PR。提交前请确认：

```bash
gradle test
gradle vortexRegressionTest
```

渲染相关改动请附离线预览或游戏内截图；涉及公开 API 的改动请在 PR 描述里写清兼容性影响。

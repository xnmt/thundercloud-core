# 雷云重构的公开参考与取舍

## Apex Arcana

- [Apex Arcana — Iron's Spells 'n Spellbooks Addon 项目页](https://www.curseforge.com/minecraft/mc-mods/apex-arcana-irons-spells-n-spellbooks-addon)
- [MC百科介绍](https://www.mcmod.cn/class/30645.html)

本轮检索没有找到可确认属于该附属模组的公开 GitHub 源码仓库，也没有取得它的 MIT LICENSE 原文。用户提供的 MIT 信息暂未独立证实；不能把 Iron's Spells 'n Spellbooks 本体仓库当作这个附属的源码。本版没有复制 Apex Arcana 的代码、贴图、模型或法术资源，也不声称复现了它的实际渲染实现。

## 实际读取的 Minecraft 特效参考

[LodestarMC/Lodestone](https://github.com/LodestarMC/Lodestone)，查阅分支 `1.21`：仅研究效果组织，不照搬该版本 API；本工程仍固定 Minecraft 1.20.1 + Forge。

- [LodestoneRenderTypes.java](https://github.com/LodestarMC/Lodestone/blob/1.21/src/main/java/team/lodestar/lodestone/registry/client/LodestoneRenderTypes.java)：透明与加法发光分别设定；发光采用源 Alpha 控制强度，透明/发光层使用仅颜色写入。
- [WorldVFXBuilder.java](https://github.com/LodestarMC/Lodestone/blob/1.21/src/main/java/team/lodestar/lodestone/systems/rendering/builder/WorldVFXBuilder.java)：光束与轨迹可以沿长度改变宽度和效果参数；其 `renderBeam` 使用朝向观察者的法线。本项目有固定世界朝向要求，因此没有使用该光片做法，继续独立绘制六边形管状电弧。
- [LICENSE.txt](https://github.com/LodestarMC/Lodestone/blob/1.21/LICENSE.txt)：LGPL-3.0，不是 MIT。本项目没有复制该库实现，也未新增依赖。

## 电弧算法参考

- [three-stdlib 固定版本 LightningStrike.js](https://cdn.jsdelivr.net/npm/three-stdlib@2.36.1/geometries/LightningStrike.js)
- [对应 MIT 许可](https://cdn.jsdelivr.net/npm/three-stdlib@2.36.1/LICENSE)

这是 JavaScript 图形库示例，不是 Minecraft 模组，也不是 Apex Arcana 源码。研究要点是多尺度线段细分、分支生命周期和几何约束；本项目的 `SurfaceArcPath` 是独立编写的 Java 中点扰动算法，没有移植该示例的 4D 噪声实现。

## 本版自己的实现

1. 云核是闭合曲面，中心也有云底，全部顶点及云核材质 Alpha 固定为255。只在外缘约两格范围过渡到半透明。
2. 原有云图仅为主体贡献明暗，不再把黑底转换成云核上的透明孔。
3. 三个尺度的扭曲连续噪声同时驱动厚度和明暗；取消等距径向云纹，取消云盘内部独立绕圈的云球。
4. 不透明主体在半透明场景之前写入主深度。柔边与发光分开绘制，仍接受深度遮挡。
5. 电弧每个节点按当前云面高度投影，沿路径逐段出现、产生分叉并回闪；触发时刻、蔓延方向与寿命每次放电由哈希随机重掷，云底为绝对主力、云顶按 `topChance` 概率偶发，局部辉光不穿过实体云核。

以上是源码及离线几何检查结果，不等于已经启动游戏完成观感或第三方光影兼容测试。

# PortInt — Portable Interface

CurseForge: https://www.curseforge.com/minecraft/mc-mods/portint

AE2 附属模组，为 NeoForge 1.21.1 添加便携接口方块。通过绑定卡（Binding Card）无线连接远程容器，实现跨维度的物品、流体、化学品自动传输。

## 功能

- **绑定卡** — 潜行右键方块绑定坐标与面，放入接口列槽位建立连接
- **9 列独立配置** — 每列 1 个绑定卡槽 + 27 个标记槽，支持白名单/黑名单 + 输入/输出模式
- **物品/流体/化学品传输** — 兼容 AE2 网络 + Mekanism 化学品（气体/灌注/颜料/浆液），支持反射调用无硬依赖
- **升级槽** — 加速卡、范围卡、维度卡、long 卡（无限速率），long 卡右键直接插入
- **绑定实时更新** — 每 20 tick 轮询一列，容器替换后自动更新显示名
- **高亮渲染** — 潜行指向方块时贴合表面法线半透明填充 + 线框高亮
- **JEI 集成** — 支持 JEI 拖拽标记
- **双语支持** — 中/英文完整翻译

## 依赖

| 模组 | 版本 |
|---|---|
| NeoForge | 1.21.1 |
| Applied Energistics 2 |  1.21.1 |
| Mekanism | 10.7.x（可选，反射调用无硬依赖） |
| JEI | 1.21.1（可选） |

## 构建

```bash
# JDK 21
gradlew jar -x test
```

产物在 `build/libs/portint-1.0.0.jar`。

## 使用

1. 放置便携接口，接入 AE2 网络
2. 手持绑定卡潜行右键目标容器（可跨维度）
3. 将绑定卡放入接口列槽位
4. 右键接口打开配置界面，在对应列设置标记物品/流体/化学品
5. 选择输入/输出模式，接口开始自动传输

## 许可

本模组代码以 MIT License 发布。

`assets/ae2/` 目录下的屏幕布局文件（`screens/interface.json`）源自 Applied Energistics 2，遵循 LGPLv3 许可。
Copyright (c) 2013 - 2024 AlgorithmX2 et al.
AE2 源码仓库：https://github.com/AppliedEnergistics/Applied-Energistics-2

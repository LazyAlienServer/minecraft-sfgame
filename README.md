# SFGame

SFGame 是一个服务端权威的 Forge 1.20.1 枪战模式模组，使用 TACZ 提供枪械和弹药。当前版本实现团队竞技与占点模式、动态规则、原版队伍绑定、职业配装、重生、计分、菜单及 HUD。

## 环境与构建

- Minecraft 1.20.1
- Forge 47.4.20
- Java 17
- TACZ 1.1.8-hotfix（客户端和服务端均必需）

Windows 构建命令：

```powershell
.\gradlew.bat build
```

输出文件为 `build/libs/sfgame-0.1.0.jar`。开发运行使用 `runClient` 或 `runServer`；首次启动本地测试服务端时需由操作者自行阅读并接受 Mojang EULA。

> 开发环境注意：TACZ 已由 `build.gradle` 的 Gradle 依赖自动加入 `runClient` 和 `runServer`。不要再把 TACZ 发布版 JAR 放入工程的 `run/mods`，否则同一个模组会以开发映射版和生产版重复加载，并导致 TACZ Mixin 启动失败。正式整合包或独立服务器的 `mods` 目录仍需正常安装 TACZ。

## 首次配置

1. 将 SFGame 与 TACZ 1.1.8-hotfix 放入客户端和服务端的 `mods` 目录。
2. 进入服务器后，系统会创建并默认绑定 `sfgame_red`、`sfgame_blue`、`sfgame_yellow`、`sfgame_green` 四个原版队伍。
3. 分别站在大厅及需要启用的队伍出生点执行；至少配置两个队伍，黄队和绿队可选：

```text
/sfgame spawn set lobby
/sfgame spawn set red
/sfgame spawn set blue
/sfgame spawn set yellow
/sfgame spawn set green

/sfgame spawn list <red|blue|yellow|green>
/sfgame spawn remove <red|blue|yellow|green> <序号>
/sfgame spawn clear <red|blue|yellow|green>
```

4. 使用 `/team join` 或 `/sfgame team set <玩家> <red|blue|random>` 分配队伍。
5. 玩家按 `M`（或执行 `/sfgame menu`）选择职业并加入。
6. 执行 `/sfgame status` 检查开赛条件，然后执行 `/sfgame start`。

未手动选择职业的玩家会在加入大厅或开赛校验时自动选择职业 JSON 中的第一个有效职业（默认是 `assault`）。玩家从旁观状态在大厅点击“加入比赛”后，会立即切换为冒险模式并传送回大厅。

Tab 玩家列表沿用原版队伍的颜色、前缀和后缀，并在其后显示 SFGame 击杀/死亡。可使用 `/team modify sfgame_red color red` 等原版命令修改显示样式。
SFGame 新建默认队伍时会自动将红方设为原版 `red`、蓝方设为原版 `blue`；已存在队伍的管理员样式不会被覆盖。

管理员命令要求原版权限等级 2。

## 常用命令

```text
/sfgame menu
/sfgame status
/sfgame start
/sfgame stop
/sfgame reset
/sfgame reload
/sfgame joinnow <玩家>

/sfgame team status
/sfgame team bind <red|blue|yellow|green> <原版队伍>
/sfgame team set <玩家选择器> <red|blue|yellow|green|random>
/sfgame team remove <玩家>

/sfgame mode list
/sfgame mode status
/sfgame mode select <模式ID>

/sfgame map list
/sfgame map status
/sfgame map create <地图ID>
/sfgame map select <地图ID>
/sfgame map remove <地图ID>

/sfgame rules list
/sfgame rules get <规则>
/sfgame rules set <规则> <整数>
/sfgame rules reset

/sfgame class list
/sfgame class validate
/sfgame class reload
/sfgame class set <玩家> <职业ID>
```

规则名称为 `maxPlayers`、`scoreLimit`、`timeLimitSeconds`、`startCountdownSeconds`、`respawnSeconds`、`respawnProtectionSeconds` 和 `resultSeconds`。规则及地图坐标保存在世界 SavedData 中，重启后保留。

`/sfgame team set` 和 `/sfgame team remove` 使用多玩家实体参数，支持玩家名以及 `@a`、`@p`、`@r` 等原版选择器。例如：`/sfgame team set @a random`。

地图按照“模式 → 地图”保存。当前内置模式 ID 为 `tdm` 和 `domination`，每个模式可以拥有多张地图。每张地图可启用红、蓝、黄、绿中的 2～4 个阵营：某阵营只要至少配置一个出生点，就视为该地图启用该阵营；地图至少需要两个启用阵营。每次执行 `/sfgame spawn set <队伍>` 都会追加出生点，玩家部署时从本队坐标中随机选择。随机分队只会使用当前地图已启用阵营，并优先分配人数最少的阵营。使用 `/sfgame spawn list` 查看带序号坐标，通过 `remove` 或 `clear` 管理。旧版单个红蓝出生点会自动迁移为对应列表中的第 1 个点。比赛进行时禁止修改地图与出生点。

## 职业配置

首次运行会生成 `config/sfgame/classes.json`。内置默认文件位于 `src/main/resources/defaults/classes.json`，包含：

- `assault`：HK416、180 发备用弹药、105% 移速（TACZ 1.1.8-hotfix 资源 ID 为 `tacz:hk416d`）。
- `sniper`：M107、30 发备用弹药、95% 移速。

修改 JSON 后执行 `/sfgame class reload`。SFGame 会通过 TACZ API 校验枪械、弹药和附件资源；发现无效资源时保留上一份有效配置，并阻止比赛在配装无效时开始。重载不会替换存活玩家的装备，新配置在下一次部署时生效。

通用配置文件 `config/sfgame-common.toml` 中的 `globalHungerLock` 默认为 `true`。启用后，SFGame 模式运行期间所有在线玩家的饥饿值和饱和度均固定为 20。

## 已实现的比赛规则

- 原版 `/team` 是队伍唯一事实来源，比赛内换边会无死亡、无得分地满配装重新部署。
- 未开赛时退出比赛只移除 SFGame 队伍，保持当前位置和当前游戏模式；只有对局开始后退出才进入旁观模式。未开赛时加入队伍不会自动传送大厅。
- 等待配置、大厅和结算返回大厅倒计时期间，所有在线玩家获得抗性提升 V；进入开场倒计时或正式比赛时移除该阶段保护。
- 删除绑定队伍会无结果终止当前比赛；离开绑定队伍会转旁观并排入下一局。
- 中途玩家默认旁观并进入下一局；管理员可用 `joinnow` 允许当前局参赛。
- 友军伤害与友军击退被取消；出生保护同时阻止造成和受到伤害，首次使用 TACZ 枪械开火会提前解除。
- 有归属的敌方击杀计分；自杀、环境或无归属死亡不计团队分。
- 比赛玩家为冒险模式，禁止破坏、放置和丢弃受保护的职业装备。
- 比赛异常关闭后不恢复半场，下一次启动清理战斗状态并回到大厅。
- 开场、重生和结算使用原版 Title、Action Bar 与提示音；结算期间 Action Bar 会显示返回大厅倒计时。

## 占点模式

使用 `/sfgame mode select domination` 选择占点模式。每张地图支持 1～16 个互不重叠的点位：

```text
/sfgame point pos1
/sfgame point pos2
/sfgame point add box <点位ID>
/sfgame point add square <点位ID> <半径>
/sfgame point set box <点位ID>
/sfgame point set center <点位ID>
/sfgame point set radius <点位ID> <半径>
/sfgame point set height <点位ID> full
/sfgame point set height <点位ID> <minY> <maxY>
/sfgame point set order <点位ID> <序号>
/sfgame point strategy <async|sync>
/sfgame point list
/sfgame point status <点位ID>
/sfgame point remove <点位ID>
/sfgame point clear
```

`async` 同时开放所有点；`sync` 按顺序逐个开放，并在一轮完成后结算。长方形使用两个角点，正方形使用管理员脚下中心与半径；未设置高度时覆盖全部高度。

规则按模式独立保存。占点模式额外支持：

```text
/sfgame rules set captureTimeSeconds <秒>
/sfgame rules set captureUsePlayerDifference <true|false>
/sfgame rules set captureDifferenceCoefficient <小数>
/sfgame rules set captureMaxMultiplier <倍率>
/sfgame rules set scoreIntervalSeconds <秒>
/sfgame rules set scorePerPoint <分数>
/sfgame rules set syncHoldSeconds <秒>
```

人数差计算开启时，占领速度为基础速度乘以第一名与第二名人数差，再乘以系数 `k`，最终受最大倍率限制。比赛中使用原版 Bossbar 显示点位进度。

## 验证

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

自动测试覆盖动态规则的边界、重置与 SavedData 序列化。完整的队伍切换、TACZ 射击归属和双客户端流程仍应在实际 Forge 测试服中按验收清单做联机验证。

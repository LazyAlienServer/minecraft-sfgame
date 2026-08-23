# SFGame

[![Build](https://github.com/LazyAlienServer/minecraft-sfgame/actions/workflows/build.yml/badge.svg)](https://github.com/LazyAlienServer/minecraft-sfgame/actions/workflows/build.yml)

SFGame 是一个 Forge 1.20.1 的枪战游戏框架 Mod，依赖 TACZ 提供枪械和弹药。当前版本实现团队竞技、占点、突破模式和夺旗（CTF）模式，以及动态规则、原版队伍绑定、职业配装、重生、计分、菜单及 HUD。

## 环境与构建

- Minecraft 1.20.1
- Forge 47.4.20
- Java 17
- TACZ 1.1.8-hotfix

Windows 构建命令：

```powershell
.\gradlew.bat build
```

输出文件为 `build/libs/sfgame-0.1.1.jar`。开发运行使用 `runClient` 或 `runServer`；首次启动本地测试服务端时需由操作者自行阅读并接受 Mojang EULA。

> 开发环境注意：TACZ 已由 `build.gradle` 的 Gradle 依赖自动加入 `runClient` 和 `runServer`。不要再把 TACZ 发布版 JAR 放入工程的 `run/mods`，否则同一个模组会以开发映射版和生产版重复加载，并导致 TACZ Mixin 启动失败。正式整合包或独立服务器的 `mods` 目录仍需正常安装 TACZ。

## 首次配置

1. 将 SFGame 与 TACZ 1.1.8-hotfix 放入客户端和服务端的 `mods` 目录。
2. 进入服务器后，系统会创建并默认绑定 `sfgame_red`、`sfgame_blue`、`sfgame_yellow`、`sfgame_green` 四个原版队伍。
3. 分别站在大厅及需要启用的队伍出生点执行；至少配置两个队伍，黄队和绿队可选：

```text
/sfgame spawn set lobby
/sfgame spawn setdefault lobby
/sfgame spawn set red
/sfgame spawn set blue
/sfgame spawn set yellow
/sfgame spawn set green

/sfgame spawn list <red|blue|yellow|green>
/sfgame spawn remove <red|blue|yellow|green> <序号>
/sfgame spawn clear <red|blue|yellow|green>
/sfgame spawn clear lobby
```

4. 使用 `/team join` 或 `/sfgame team set <玩家> <red|blue|random>` 分配队伍。
5. 玩家按 `M`（或执行 `/sfgame menu`）选择职业并加入。菜单使用深色按钮；职业以配置文件 `icon` 指定物品的正方形卡片显示，职业过多时将鼠标放在卡片区域并滚动滚轮即可左右浏览。
6. 执行 `/sfgame status` 检查开赛条件，然后执行 `/sfgame start`。

未手动选择职业的玩家会在加入大厅或开赛校验时自动选择职业 JSON 中的第一个有效职业（默认是 `assault`）。玩家从旁观状态在大厅点击“加入游戏”后，会立即切换为冒险模式并传送回大厅。

Tab 玩家列表沿用原版队伍的颜色、前缀和后缀，并在其后显示 SFGame 击杀/死亡。可使用 `/team modify sfgame_red color red` 等原版命令修改显示样式。
SFGame 新建默认队伍时会自动将红方设为原版 `red`、蓝方设为原版 `blue`；已存在队伍的管理员样式不会被覆盖。

管理员命令要求原版权限等级 2。

### 游戏内管理面板

权限等级 2 的管理员打开 `M` 菜单后，右上角会出现“管理”按钮。管理面板顶部第一行选择模式，第二行选择该模式的地图；比赛进行期间两行都会锁定，防止误切换正在运行的地图。面板包含两个独立选项卡：

- “状态”页显示比赛阶段、当前模式/地图、在线/参赛/排队人数、比分、剩余时间、地图配置状态、规则继承来源及地图重载进度。地图重载时该页每秒刷新一次，不需要手动关闭重开。
- “规则”页只列出当前模式可用的规则。支持对局内实时修改的参数排列在上方并标记为“实时”；只能在大厅修改的参数排列在下方并标记为“下局”，比赛期间控件自动禁用。数值规则会显示允许范围，输入后点击“应用”或按回车；布尔规则直接点击开关；枚举规则点击按钮会在全部合法选项间循环。规则页不会自动刷新，避免清除管理员正在输入的内容，可使用右上角“刷新”主动重新读取。

所有按钮操作都会重新在服务端校验管理员权限、当前模式、当前地图、参数类型和范围；修改仍写入对应模式的地图规则 JSON，与 `/sfgame rule set` 使用同一套规则系统。

## 常用命令

```text
/sfgame menu
/sfgame leave
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

/sfgame pos1
/sfgame pos2

/sfgame rule list
/sfgame rule get <规则>
/sfgame rule set <规则> <值>
/sfgame rule inherit <base|地图ID>
/sfgame rule reset

/sfgame dev

/sfgame class list
/sfgame class validate
/sfgame class reload
/sfgame class set <玩家> <职业ID>
```

规则统一通过 `/sfgame rule` 管理，并按“模式目录 → 地图目录”保存。每张地图的规则都写在该地图目录的 `map.json` 中；`parent: "base"` 使用模式内置基线，`parent: "<地图ID>"` 继承同模式另一张地图的 `map.json`。`list`、`get` 和命令补全只显示当前模式适用的规则，其他模式的专属规则会被隐藏并拒绝执行。`maxPlayers`、`scoreLimit`、`timeLimitSeconds`、`startCountdownSeconds`、`respawnSeconds`、`respawnProtectionSeconds` 和 `resultSeconds` 是通用规则。`resultSeconds` 默认是 20 秒，即比赛结算后等待 20 秒返回大厅。

### 规则参数完整说明

管理员权限要求为 2。`<规则>` 必须使用下表中的精确拼写；`<值>` 不要带单位。修改会写入当前世界。标记为实时的规则会在当前比赛下一 Tick（或下一阶段）生效；模式变体、开放策略、攻守方和赛段数属于下局规则，只能在大厅修改。

| 规则 | 默认值 | 允许范围 | 适用模式 | 含义与用法 |
| --- | ---: | ---: | --- | --- |
| `maxPlayers` | 10 | 2～128 | 全部 | 当前局总参赛人数上限。降低上限不会踢出已参赛玩家，只会阻止新人加入。例：`/sfgame rule set maxPlayers 16`。 |
| `scoreLimit` | TDM 50、占点 100、CTF 3 | 1～10000 | TDM、占点、CTF | TDM 为击杀目标，占点为团队分数，CTF 为成功夺旗次数；突破不使用团队分数。 |
| `timeLimitSeconds` | 600 | 30～86400 | 全部 | TDM/占点/CTF 为整局时限；突破为每个 sector 的时限。例：`/sfgame rule set timeLimitSeconds 900`。 |
| `startCountdownSeconds` | 5 | 0～60 | 全部 | 开局倒计时；设为 0 表示跳过倒计时。 |
| `respawnSeconds` | 5（突破 10） | 0～60 | 全部 | 死亡后等待重生的秒数。突破倒计时结束后会打开出生点/占领点选择。 |
| `respawnProtectionSeconds` | 3 | 0～30 | 全部 | 重生保护时间；首次使用 TACZ 武器开火会提前解除。 |
| `resultSeconds` | 20 | 1～60 | 全部 | 结算展示和无敌等待时间，结束后返回大厅。 |
| `captureTimeSeconds` | 10 | 1～300 | 占点、突破、CTF territory | 单人、基础速度完成一个占领/中立化阶段所需秒数。 |
| `captureUsePlayerDifference` | true | true/false | 占点、突破、CTF territory | 是否按点内双方人数/权重差加速。关闭后仍按人数最多的一方判定，但速度固定。例：`/sfgame rule set captureUsePlayerDifference false`。 |
| `captureDifferenceCoefficient` | 1.0 | 0.1～10.0 | 占点、突破、CTF territory | 人数/权重差速度系数 `k`。速度为基础速度 × 差值 × `k`，再受最大倍率限制。 |
| `captureMaxMultiplier` | 4 | 1～64 | 占点、突破、CTF territory | 占领速度最大倍率。 |
| `scoreIntervalSeconds` | 1 | 1～300 | 占点 | 已归属活动点产生分数的周期。默认每秒结算一次。 |
| `scorePerPoint` | 1 | 1～1000 | 占点 | 每个已归属活动点每个周期产生的团队分数。 |
| `syncHoldSeconds` | 45 | 1～3600 | 占点 `sync` | `sync` 点位保持归属达到该秒数后切换到下一个点；无人争夺时暂停累计。 |
| `dominationStrategy` | async | async/sync | 占点 | 点位开放策略。`async` 同时开放全部点，`sync` 每局随机轮换一个开放点。下局规则。例：`/sfgame rule set dominationStrategy sync`。 |
| `breakthroughVariant` | normal | normal/captain | 突破 | `normal` 为普通突破，`captain` 启用进攻方队长选举。下局规则。 |
| `breakthroughLegs` | 1 | 1～2 | 突破 | 完整攻防赛段数；2 表示第一赛段后交换攻守再进行一次。下局规则。 |
| `breakthroughAttacker` | red | red/blue/yellow/green | 突破 | 第一赛段进攻阵营。必须与防守方不同且具有地图出生点。下局规则。 |
| `breakthroughDefender` | blue | red/blue/yellow/green | 突破 | 第一赛段防守阵营。双赛段会自动与进攻方互换。下局规则。 |
| `attackerTickets` | 100 | 1～10000 | 突破、CTF assault | 突破为进攻方死亡票数；CTF assault 为进攻方兵力票。设定后当前阶段/回合立即改为该值。 |
| `sectorTransitionSeconds` | 10 | 0～60 | 突破 | 攻陷一个 sector 后的整备和安全部署时间。 |
| `captainVoteSeconds` | 15 | 1～120 | 突破 captain | 首次队长投票时长。 |
| `captainReplacementVoteSeconds` | 10 | 1～120 | 突破 captain | 队长掉线、离队或换队后的补选时长。 |
| `attackerCaptainGlowing` | true | true/false | 突破 captain | 是否让进攻方队长使用发光轮廓；队长不占用头盔栏。 |
| `mapBlockBreaking` | false | true/false | 全部 | 是否启用当前地图的白名单方块破坏与放置。默认关闭；启用后仅 build box 内、allowlist 中的方块可编辑，爆炸、TACZ 枪械、卓越前线枪械与载具同样受此规则限制。 |
| `mapBlockAllowlist` | `[]` | JSON 字符串数组 | 全部 | 当前模式/地图允许改变的方块选择器。支持方块 ID（如 `minecraft:glass`）和方块标签（如 `#minecraft:logs`），支持地图规则继承。使用 `rule build allow/disallow/allowlist` 管理，或直接编辑规则 JSON。 |
| `mapSnapshotMode` | allowlist | allowlist/full | 全部 | 地图快照的保存和还原方式。`allowlist` 只保存、清除和还原选区内白名单方块；`full` 保存并还原完整选区。该规则只能在大厅修改，修改后必须重新保存快照。 |
| `attackerCaptainCaptureWeight` | 2.0 | 1.0～10.0 | 突破 captain | 进攻队长在点内的占领权重。 |
| `defenderCaptureWeight` | 1.4 | 0.1～10.0 | 突破 captain | 每名防守玩家在点内的占领权重；防守方不选举队长。 |
| `ctfFlagReturnSeconds` | 30 | 5～600 | CTF | 掉落旗帜无人回收时自动返回旗座的秒数。 |
| `ctfHomeCaptureTimeSeconds` | 15 | 1～600 | CTF territory | territory 家旗独立占领/中立化阶段的基础时间。 |
| `ctfVariant` | classic | classic/assault/territory | CTF | 夺旗子模式。下局规则。 |
| `ctfAttacker` | red | red/blue/yellow/green | CTF assault | 单向攻防夺旗的进攻方。下局规则。 |
| `ctfDefender` | blue | red/blue/yellow/green | CTF assault | 单向攻防夺旗的防守方。下局规则。 |
| `ctfCarrierRestriction` | normal | normal/movement_limited/no_weapons | CTF | 持旗限制：无限制、限制特殊移动、禁止枪械与近战。下局规则。 |

查询或修改示例：

```text
/sfgame rule list
/sfgame rule get scoreLimit
/sfgame rule set scoreLimit 75
/sfgame rule set mapSnapshotMode allowlist
/sfgame rule set dominationStrategy sync
/sfgame rule set breakthroughVariant captain
/sfgame rule set breakthroughLegs 2
/sfgame rule set ctfVariant territory
/sfgame rule inherit base
/sfgame rule reset
```

`rule set` 修改当前选中模式和地图的覆盖值并立即写入该地图目录的 `map.json`。`rule reset` 只清除当前地图的规则覆盖并继续使用 parent；`rule inherit <parent>` 可切换 parent。

配置按“模式目录 → base/地图目录”组织：

```text
<存档>/serverconfig/sfgame/maps/tdm/base/map.json
<存档>/serverconfig/sfgame/maps/tdm/base/classes.json
<存档>/serverconfig/sfgame/maps/tdm/default/map.json
<存档>/serverconfig/sfgame/maps/tdm/default/classes.json

<存档>/serverconfig/sfgame/maps/domination/base/map.json
<存档>/serverconfig/sfgame/maps/domination/base/classes.json
<存档>/serverconfig/sfgame/maps/domination/desert/map.json
<存档>/serverconfig/sfgame/maps/domination/desert/classes.json
```

`base/` 是每个模式的基线配置目录，不会出现在 `/sfgame map list`，也不能作为普通地图选择。新建普通地图时，两个文件都只生成一个明确的 parent：

```json
{
  "parent": "tdm/base"
}
```

parent 支持以下格式：

```text
base             当前模式的 base
default          当前模式的 default 地图
tdm/base         指定模式的 base
tdm/default      指定模式的普通地图
```

保存到 JSON 时统一规范为 `<模式ID>/<地图ID>`。因此同模式和跨模式继承使用同一套循环、缺失引用校验。跨模式规则只继承子模式也支持的同名规则；例如占点地图继承 TDM 时会继承 `maxPlayers`、`scoreLimit` 等通用规则，但不会导入目标模式不支持的专属规则。职业配置则完整继承 parent 职业池。

管理员修改地图坐标、出生点、点位、旗帜或规则后，相应字段才写入当前地图的 `map.json`；修改职业时才在当前地图 `classes.json` 中增加覆盖字段。地图的读取和修改只使用当前地图文件夹、parent 链和最终的 `base/` 文件，不读取模式级 `rules/<mode>.json`、`classes/<mode>.json` 或 `defaults.json`。
单人开发测试使用全局命令 `/sfgame dev`。每次执行会切换开启/关闭状态；状态写入当前世界，切换模式或地图后仍然保持。开启后允许只有一名参赛玩家开局，但地图、出生点、队伍绑定、职业/TACZ 资源和模式配置仍必须有效：

```text
/sfgame dev
/sfgame start
```

测试结束后再次执行同一命令即可关闭，恢复正式的双方（或多方）参赛人数检查：

```text
/sfgame dev
```

`rule list`、`rule get`、`rule set` 会根据当前 `/sfgame mode select <模式ID>` 隐藏并拒绝其他模式专属参数。例如当前是 TDM 时，`scoreIntervalSeconds`、`attackerTickets` 和 `ctfFlagReturnSeconds` 都不能使用。

`/sfgame team set` 和 `/sfgame team remove` 使用多玩家实体参数，支持玩家名以及 `@a`、`@p`、`@r` 等原版选择器。例如：`/sfgame team set @a random`。

### 全局命令参数

| 命令 | 参数说明与效果 |
| --- | --- |
| `/sfgame menu` | 为执行命令的玩家打开菜单；默认按键为 `M`。 |
| `/sfgame leave` | 退出当前比赛。比赛未开始时只离开 SFGame 参赛队伍并留在原地；比赛进行中会进入旁观并排入下一局。 |
| `/sfgame status` | 查看当前模式、地图、阶段、队伍人数、出生点和开赛校验错误。 |
| `/sfgame dev` | 全局切换开发模式。开启后允许一名玩家开始测试局；再次执行关闭。权限等级要求为 2。 |
| `/sfgame start` | 手动开始当前地图。会依次校验队伍、出生点、职业、点位/旗帜配置和人数。 |
| `/sfgame stop` | 安全停止当前比赛、清理 HUD/实体并返回大厅。 |
| `/sfgame reset` | 清空当前地图的比赛运行状态和比分；不会删除地图点位或职业文件。 |
| `/sfgame reload` | 热重载职业、当前各模式/地图规则 JSON 和 CTF 商店。规则立即影响当前局；职业重载不会强制替换存活玩家装备。 |
| `/sfgame joinnow <玩家>` | 管理员允许一名中途玩家立即加入当前局。当前人数达到 `maxPlayers` 或玩家未选有效职业时拒绝。 |
| `/sfgame pos1`、`/sfgame pos2` | 记录准星指向的方块，供所有 box 区域命令使用；最大选择距离为 128 格。两点的维度必须相同；`pos1`/`pos2` 是全局临时选择，不是地图坐标。 |
| `/sfgame mode list` | 列出 `tdm`、`domination`、`breakthrough`、`ctf` 等可用模式。 |
| `/sfgame mode select <模式ID>` | 选择当前世界后续编辑和开赛使用的模式；比赛进行中禁止切换。 |
| `/sfgame map list` | 列出当前模式的所有地图及启用状态。 |
| `/sfgame map create <地图ID>` | 创建一个模式专属地图。地图 ID 支持字母、数字和下划线，且可为单个字母/数字。 |
| `/sfgame map select <地图ID>` | 选择当前模式的活动地图；后续 `spawn`、`point`、`sector`、`ctf` 编辑都写入该地图。 |
| `/sfgame map status` | 查看活动地图 ID、模式配置和校验摘要。 |
| `/sfgame map remove <地图ID>` | 删除当前模式下指定地图的全部持久化配置；比赛中不可用，执行前请确认 ID。 |

`<玩家>` 使用单个在线玩家参数，`<玩家选择器>` 使用原版多实体选择器，因此 `/sfgame team set @a random` 会一次处理所有在线玩家；`@a`、`@p`、`@r` 的筛选规则与原版 `/team` 相同。管理员命令统一要求权限等级 2。

地图按照“模式 → 地图”保存。当前内置模式 ID 为 `tdm`、`domination`、`breakthrough` 和 `ctf`，每个模式可以拥有多张地图。`/sfgame spawn setdefault lobby` 将管理员当前位置保存为全局默认大厅；世界首次加载时若尚未配置，会自动使用主世界原版出生点。`/sfgame spawn set lobby` 为当前地图设置覆盖点，`/sfgame spawn clear lobby` 清除覆盖并恢复使用全局默认大厅。长方形区域统一将准星对准两个角方块，依次执行根命令 `/sfgame pos1`、`/sfgame pos2`，再执行对应模式的 `set box` 或 `add box` 命令；两个端点方块都会完整包含在区域内。团队竞技和占点地图可启用红、蓝、黄、绿中的 2～4 个阵营：某阵营只要至少配置一个出生点，就视为该地图启用该阵营；地图至少需要两个启用阵营。每次执行 `/sfgame spawn set <队伍>` 都会追加出生点，玩家部署时从本队坐标中随机选择。随机分队只会使用当前地图已启用阵营，并优先分配人数最少的阵营；也支持 `/sfgame team set random @a` 的随机参数顺序。使用 `/sfgame spawn list` 查看带序号坐标，通过 `remove` 或 `clear` 管理。突破模式地图改用每个 sector 的攻守角色出生点，配置方法见下文。旧版单个红蓝出生点会自动迁移为对应列表中的第 1 个点。比赛进行时禁止修改地图与出生点。

SFGame 自有 ID（地图、模式、sector、点位、职业及职业配置继承 ID）可以字母或数字开头，支持单个字母和单个数字，例如 `a`、`1`、`a1`、`1a`。后续字符可使用字母、数字和下划线；输入的大写字母会统一按小写 ID 处理。枪械、物品和附件等 Minecraft/TACZ 资源 ID 仍遵循其原有的命名空间格式。

### 大厅、出生点和原版队伍参数

`<阵营>` 只能是 `red`、`blue`、`yellow`、`green`。它代表 SFGame 阵营，不是颜色文字本身；SFGame 会把它绑定到一个原版 `/team` 名称。
地图按照“模式目录 → 地图目录”保存，主文档路径为 `<存档>/serverconfig/sfgame/maps/<模式>/<地图>/map.json`。当前内置模式 ID 为 `tdm`、`domination`、`breakthrough` 和 `ctf`，每个模式可以拥有多张地图。`/sfgame spawn setdefault lobby` 将管理员当前位置保存为全局默认大厅；世界首次加载时若尚未配置，会自动使用主世界原版出生点。`/sfgame spawn set lobby` 为当前地图设置覆盖点，`/sfgame spawn clear lobby` 清除覆盖并恢复使用全局默认大厅。长方形区域统一将准星对准两个角方块，依次执行根命令 `/sfgame pos1`、`/sfgame pos2`，再执行对应模式的 `set box` 或 `add box` 命令；两个端点方块都会完整包含在区域内。团队竞技和占点地图可启用红、蓝、黄、绿中的 2～4 个阵营：某阵营只要至少配置一个出生点，就视为该地图启用该阵营；地图至少需要两个启用阵营。每次执行 `/sfgame spawn set <队伍>` 都会追加出生点，玩家部署时从本队坐标中随机选择。随机分队只会使用当前地图已启用阵营，并优先分配人数最少的阵营；也支持 `/sfgame team set random @a` 的随机参数顺序。使用 `/sfgame spawn list` 查看带序号坐标，通过 `remove` 或 `clear` 管理。突破模式地图改用每个 sector 的攻守角色出生点，配置方法见下文。旧版单个红蓝出生点会自动迁移为对应列表中的第 1 个点。比赛进行时禁止修改地图与出生点。
```text
/sfgame spawn setdefault lobby
/sfgame spawn set lobby
/sfgame spawn clear lobby
/sfgame spawn set <阵营>
/sfgame spawn list <阵营>
/sfgame spawn remove <阵营> <序号>
/sfgame spawn clear <阵营>

/sfgame team status
/sfgame team bind <阵营> <原版队伍名>
/sfgame team set <玩家选择器> <阵营|random>
/sfgame team set random <玩家选择器>
/sfgame team remove <玩家选择器>
```

- `spawn setdefault lobby` 保存全局默认大厅；只有没有当前地图大厅覆盖时才使用它。
- `spawn set lobby` 保存当前模式/地图的大厅覆盖；`spawn clear lobby` 删除覆盖并回退到默认大厅。
- `spawn set <阵营>` 每执行一次就追加一个出生点，坐标包含维度、位置和朝向；`list` 输出的序号从 1 开始。
- `spawn remove` 删除指定序号，`spawn clear` 删除该阵营全部出生点。出生点列表为空的阵营不会被视为该地图启用阵营。
- `team bind` 只保存绑定关系，不改原版队伍的名称、前缀、颜色等显示规则；默认队伍创建时会设置红/蓝颜色，管理员随后可继续用 `/team modify` 调整。
- `team set` 会把玩家加入绑定的原版队伍；`random` 按当前地图启用阵营中人数最少者分配，人数相同时随机。`team remove` 让玩家离开原版队伍；比赛中下一 Tick 会转为旁观并排入下一局。

## 职业配置

首次加载存档时会为每个模式生成一个完整 base 职业文件，并为普通地图生成只包含 parent 的职业文件：

```text
maps/tdm/base/classes.json
maps/tdm/default/classes.json        # {"parent":"tdm/base"}
maps/domination/base/classes.json
maps/domination/default/classes.json # {"parent":"domination/base"}
```

`base/classes.json` 保存模式内置普通职业、突破队长职业和基础队伍覆盖。普通地图 `classes.json` 只保存 parent 以及该地图真正需要覆盖的字段：

```json
{
  "parent": "tdm/base",
  "classes": [
    {
      "id": "assault",
      "displayName": "沙漠突击兵",
      "description": "当前地图覆盖的突击职业",
      "icon": "minecraft:iron_sword",
      "gunId": "tacz:hk416d",
      "ammoId": "tacz:556x45",
      "initialMagazine": 30,
      "reserveAmmo": 180
    }
  ],
  "teams": {
    "blue": {
      "classes": [
        {
          "id": "sniper",
          "displayName": "蓝队狙击手",
          "gunId": "tacz:m107",
          "ammoId": "tacz:50bmg"
        }
      ]
    }
  }
}
```

相同 ID 的职业对象整体替换 parent 池中的职业，不做逐字段合并；不同 ID 会追加。parent 可写当前模式地图或 `<模式>/<地图>` 跨模式引用。`teams.<阵营>.parent` 只处理当前最终职业池中的队伍 scope 继承。职业选择、`/sfgame class set` 和开赛校验都会按当前地图完整 parent 链读取；链上缺失或循环会使重载失败并保留上一份有效配置。

物品选择器统一支持 `资源ID{SNBT}` 内联 NBT 写法，例如 `"tacz:modern_kinetic_gun{GunId:\"tacz:hk416d\"}"` 可让图标直接显示指定枪械模型。JSON 中内部引号需要转义；NBT 键名区分大小写，资源 ID 部分会自动转为小写。

职业 JSON 字段含义如下：

| 字段 | 类型/示例 | 作用 |
| --- | --- | --- |
| `id` | `"assault"` | 配置和命令使用的职业 ID；同一模式中必须唯一。 |
| `displayName` | `"突击兵"` | 菜单、HUD 和提示中显示的人类可读名称。 |
| `description` | `"中近距离持续作战"` | 职业卡片 Tooltip 的说明文字。 |
| `icon` | `"tacz:modern_kinetic_gun{GunId:\"tacz:hk416d\"}"` | 职业卡片图标物品；支持 `资源ID{NBT}` 内联 NBT，无效时显示屏障图标。 |
| `maxHealth` | `20.0` | 部署时的最大生命值，范围由服务端限制为 1～2048。 |
| `movementSpeedMultiplier` | `1.05` | 移动速度倍率；`1.0` 为正常速度，`0.95` 为慢 5%。 |
| `gunId` | `"tacz:hk416d"` | TACZ 主武器资源 ID；必须能被 `TimelessAPI` 索引。 |
| `ammoId` | `"tacz:556x45"` | TACZ 弹药资源 ID；必须与枪械兼容。 |
| `initialMagazine` | `30` | 主武器初始弹匣装弹量。 |
| `reserveAmmo` | `180` | 备用弹药数量；服务端装入一个 TACZ 钻石级弹药箱，放在槽位 9。 |
| `fireMode` | `"AUTO"` | TACZ 射击模式，例如 `AUTO`、`SEMI`；无效值会阻止开赛。 |
| `attachments` | `{ "SCOPE": "tacz:..." }` | TACZ 附件类型到资源 ID 的映射；附件类型和资源都必须有效。 |
| `inventory` | `[ {"item":"minecraft:...", "count":1} ]` | 普通物品栏配装；`item` 支持内联 NBT（如 `"superbwarfare:ammo_box{...}"`），也可写可选 `nbt` 字段，两者同时存在时内联优先。 |
| `armor` | `{ "head": {...}, "chest": {...} }` | `head`、`chest`、`legs`、`feet` 四个盔甲槽的物品定义；`item` 同样支持内联 NBT。 |
| `offhand` | `{ "item":"minecraft:..." }` | 副手物品；未填写则为空。 |
| `effects` | `[ {"id":"minecraft:...", "durationTicks":200, "amplifier":0} ]` | 部署时添加的药水效果；`visible:false` 可隐藏状态图标。 |
| `allowDrop` | `false` | 是否允许玩家丢弃该职业配装；比赛内通常保持 `false`。 |

`classes.json` 的根对象只描述当前目录；`classes` 是普通职业池，`captainClasses` 是突破队长职业池，`teams` 中可以按阵营覆盖。根部 `parent` 可引用同模式或跨模式地图；模式级职业文件不参与运行时继承。

修改 JSON 后执行 `/sfgame class reload`。SFGame 会通过 TACZ API 校验枪械、弹药和附件资源；发现无效资源时保留上一份有效配置，并阻止比赛在配装无效时开始。重载不会替换存活玩家的装备，新配置在下一次部署时生效。

```text
/sfgame class list
/sfgame class list normal
/sfgame class list captain
/sfgame class validate
/sfgame class reload
/sfgame class set <玩家> <职业ID>
/sfgame class setcaptain <玩家> <队长职业ID>
```

`class list` 的 `normal`/`captain` 参数分别查看当前地图按阵营展开的普通职业池和突破队长职业池；不带参数时查看普通池。`class set` 修改目标玩家当前地图/阵营的普通职业，`class setcaptain` 只在突破 captain 变体中生效；玩家存活期间为“待切换”，下一次死亡重生或管理员重新部署时应用。

职业配置中的 `reserveAmmo` 会装入一个 TACZ 钻石级弹药箱，不再发放散装弹药；弹药箱固定放在背包三行区域左上角（物品栏槽位 `9`）。

存档服务端配置文件 `<存档>/serverconfig/sfgame-server.toml` 中的 `globalHungerLock` 默认为 `true`。启用后，SFGame 模式运行期间所有在线玩家的饥饿值和饱和度均固定为 20。

## 已实现的比赛规则

- 原版 `/team` 是队伍唯一事实来源，比赛内换边会无死亡、无得分地满配装重新部署。
- 未开赛时点击“观战”只移除 SFGame 队伍，保持当前位置和当前游戏模式；只有对局开始后使用 `/sfgame leave` 才会进入旁观模式。未开赛时点击“加入游戏”不会自动传送大厅。
- 等待配置、大厅和结算返回大厅倒计时期间，所有在线玩家获得抗性提升 V；进入开场倒计时或正式比赛时移除该阶段保护。
- 删除绑定队伍会无结果终止当前比赛；离开绑定队伍会转旁观并排入下一局。
- 中途玩家默认旁观并进入下一局；管理员可用 `joinnow` 允许当前局参赛。
- 对局开始后，参赛玩家菜单不显示加入或退出按钮；玩家需要主动退出时使用 `/sfgame leave`。队长选拔期间，进攻方菜单只保留投票与职业功能，服务端也会拒绝过期或伪造的菜单加入/退出请求。
- 友军伤害与友军击退被取消；出生保护同时阻止造成和受到伤害，首次使用 TACZ 枪械开火会提前解除。
- 有归属的敌方击杀计分；自杀、环境或无归属死亡不计团队分。
- 比赛玩家为冒险模式，禁止破坏、放置和丢弃受保护的职业装备。
- 比赛异常关闭后不恢复半场，下一次启动清理战斗状态并回到大厅。
- 开场、重生和结算使用原版 Title、Action Bar 与提示音；结算期间 Action Bar 会显示返回大厅倒计时。

## 占点模式

使用 `/sfgame mode select domination` 选择占点模式。每张地图支持 1～16 个互不重叠的点位：

```text
/sfgame pos1
/sfgame pos2
/sfgame point add box <点位ID>
/sfgame point add square <点位ID> <半径>
/sfgame point set box <点位ID>
/sfgame point set center <点位ID>
/sfgame point set radius <点位ID> <半径>
/sfgame point set height <点位ID> full
/sfgame point set height <点位ID> <minY> <maxY>
/sfgame point set order <点位ID> <序号>
/sfgame point list
/sfgame point status <点位ID>
/sfgame point remove <点位ID>
/sfgame point clear
```

`async` 同时开放所有点；`sync` 每局随机排列点位，一次只开放并显示一个点，切换时会提示“点位 A 已沦陷，请前往占领点位 B”并播放提示音，完成本轮全部点位后结算。点位 ID 支持单个字母或单个数字，输入的大写字母会按同一小写 ID 保存。长方形使用两个角点，正方形使用管理员脚下中心与半径；未设置高度时覆盖全部高度。占点默认每 1 秒为每个已归属活动点增加 1 分。

| 点位参数 | 说明 |
| --- | --- |
| `<点位ID>` | 当前地图内唯一的小写资源式 ID，支持 `a`、`1`、`alpha_1` 等。最多 16 个点位。 |
| `box` | 使用最近一次 `/sfgame pos1` 与 `/sfgame pos2` 作为两个角点，形成长方体；两点维度和坐标必须有效。 |
| `square <半径>` | 以执行命令者当前脚部位置为中心，形成 `|ΔX|≤半径`、`|ΔZ|≤半径` 的正方形；半径 1～256。 |
| `set center` | 将已有 square 点的中心移动到执行者当前位置。 |
| `set radius` | 修改已有 square 点半径，范围 1～256；与其他点重叠时拒绝。 |
| `set height full` | 让点位覆盖当前维度所有高度。 |
| `set height <minY> <maxY>` | 只允许脚部 Y 坐标在闭区间内的玩家占点，范围 -2048～2048。 |
| `set order <序号>` | 仅影响 `sync` 的候选顺序，序号 1～16；实际每局会在这些点中随机选取下一个未完成点。 |
| `dominationStrategy=async` | 所有点同时扫描、同时显示 Bossbar，并分别产分。使用 `/sfgame rule set dominationStrategy async`。 |
| `dominationStrategy=sync` | 只扫描和显示当前开放点；当前点完成保持时间后才切到随机下一个点。使用 `/sfgame rule set dominationStrategy sync`。 |
| `list` / `status <点位ID>` | 查看区域类型、维度、高度、归属、推进者、进度和当前是否开放。 |

点内没有唯一人数最多的阵营时进度暂停；唯一领先方推进，中立点更换推进方会清除原挑战进度。已有归属点被争夺后先中立化，再由新阵营占领。占点人员必须是存活且参赛玩家，旁观、排队和重生倒计时玩家不计入。

规则按模式文件和地图独立保存。占点模式额外支持：

```text
/sfgame rule set captureTimeSeconds <秒>
/sfgame rule set dominationStrategy <async|sync>
/sfgame rule set captureUsePlayerDifference <true|false>
/sfgame rule set captureDifferenceCoefficient <小数>
/sfgame rule set captureMaxMultiplier <倍率>
/sfgame rule set scoreIntervalSeconds <秒>
/sfgame rule set scorePerPoint <分数>
/sfgame rule set syncHoldSeconds <秒>
```

人数差计算开启时，占领速度为基础速度乘以第一名与第二名人数差，再乘以系数 `k`，最终受最大倍率限制。比赛中使用原版 Bossbar 显示点位进度。

占点模式会在每个当前开放点的中心上方显示发光悬浮字母。`async` 显示全部活动点，`sync` 只显示当前随机开放点；切点和比赛结束时会自动清理。

## 突破模式

使用 `/sfgame mode select breakthrough` 选择突破模式。该模式严格使用两个阵营：管理员指定一个进攻阵营和一个防守阵营。地图由 1～16 个有序 sector 构成，每个 sector 包含 1～16 个同时开放的点位，并分别保存进攻方和防守方出生点。

进攻方必须同时控制当前 sector 的全部点位才能推进。点位初始归防守方，进攻方需要先中立化再占领；在整个 sector 完成前，防守方可以修复和夺回点位。推进后旧 sector 锁定，经过默认 10 秒整备期，全员无死亡记录地部署到下一个 sector。当前开放点会显示 Bossbar 和中心上方的发光悬浮字母。

### 最小建图流程

下面的示例创建一张红队进攻、蓝队防守、包含两个 sector 的普通突破地图：

```text
/sfgame mode select breakthrough
/sfgame map create example
/sfgame spawn set lobby

/sfgame rule set breakthroughVariant normal
/sfgame rule set breakthroughLegs 1
/sfgame rule set breakthroughAttacker red
/sfgame rule set breakthroughDefender blue

/sfgame sector add first
/sfgame pos1
/sfgame pos2
/sfgame sector point add box first a
/sfgame sector spawn add first attacker
/sfgame sector spawn add first defender

/sfgame sector add second
/sfgame sector point add square second b 6
/sfgame sector spawn add second attacker
/sfgame sector spawn add second defender

/sfgame team set <进攻玩家> red
/sfgame team set <防守玩家> blue
/sfgame class validate
/sfgame status
/sfgame start
```

每条 `sector spawn add` 都使用管理员当前站立位置，并可重复执行以添加多个随机出生点。方形点使用管理员当前位置作为中心；长方形点将准星依次对准两个角方块并执行 `/sfgame pos1` 和 `/sfgame pos2`。未设置高度时点位覆盖该维度全部高度。

### 模式与 sector 指令

```text
/sfgame rule set breakthroughVariant <normal|captain>
/sfgame rule set breakthroughLegs <1|2>
/sfgame rule set breakthroughAttacker <red|blue|yellow|green>
/sfgame rule set breakthroughDefender <red|blue|yellow|green>
/sfgame rule breakthrough status

/sfgame sector add <sectorID>
/sfgame sector set order <sectorID> <1-16>
/sfgame sector list
/sfgame sector status <sectorID>
/sfgame sector remove <sectorID>
/sfgame sector clear
```

`legs` 表示整场比赛进行几次完整的攻防赛段，不是 sector 数量：

- `breakthroughLegs=1`：只进行一次攻防。`breakthroughAttacker` 指定的进攻方依次进攻全部 sector；攻陷最后一个 sector 时进攻方获胜，任一 sector 超时或进攻票数归零时防守方获胜。比赛结束后不会交换攻守。
- `breakthroughLegs=2`：双方各进攻一次。第一赛段结束后自动交换攻守并从第一个 sector 重新开始；第二赛段结束后比较双方推进的 sector 数、当前 sector 已占点数、达到该进度的用时和剩余票数，完全相同则平局。

例如 `breakthroughAttacker=red`、`breakthroughDefender=blue` 配合 `breakthroughLegs=1` 表示整局固定由红队进攻、蓝队防守；配合 `breakthroughLegs=2` 则第一赛段红攻蓝守，第二赛段蓝攻红守。

攻守规则可使用 `red`、`blue`、`yellow` 或 `green`，代表对应的 SFGame 阵营及其绑定的原版队伍。双方必须不同。sector 的数量由 `/sfgame sector add` 决定，与 `breakthroughLegs` 无关。

突破模式参数速查：

| 参数 | 可选值/范围 | 说明 |
| --- | --- | --- |
| `breakthroughVariant` | `normal` / `captain` | `normal` 没有队长选举；`captain` 只为当前进攻方选举队长，防守方不选举。 |
| `breakthroughLegs` | `1` / `2` | `1` 只进行指定攻守方向；`2` 第一赛段结束后交换攻守，各进行一次完整进攻。 |
| `breakthroughAttacker` / `breakthroughDefender` | 两个不同阵营 | 设置第一赛段的初始攻守；双赛段会自动互换。 |
| `<sectorID>` | 资源式 ID | 一个有序攻防阶段，最多 16 个；`sector set order` 的序号决定基础顺序。 |
| `<pointID>` | 资源式 ID | sector 内的占领点；同一 sector 内不得重叠，不同 sector 可以复用区域。 |
| `attacker` / `defender` | 角色关键字 | sector 出生点按角色保存，不直接写阵营；双赛段换边时角色坐标自动换用。 |
| `inside` / `nearby` | 重生位置类型 | `inside` 是点内安全重生坐标；`nearby` 是点争夺或不安全时使用的点外附近坐标。 |

`sector point set respawn` 只记录管理员当前位置，不会自动寻找安全位置；编辑地图时必须分别站在点内和点外安全位置各执行一次。删除 sector 或点位会同时删除其阶段归属、Bossbar 和重生配置，比赛进行中禁止修改。

### Sector 点位指令

```text
/sfgame pos1
/sfgame pos2
/sfgame sector point add box <sectorID> <点位ID>
/sfgame sector point add square <sectorID> <点位ID> <半径>
/sfgame sector point set box <sectorID> <点位ID>
/sfgame sector point set radius <sectorID> <点位ID> <半径>
/sfgame sector point set height <sectorID> <点位ID> full
/sfgame sector point set height <sectorID> <点位ID> <minY> <maxY>
/sfgame sector point set respawn <sectorID> <点位ID> inside
/sfgame sector point set respawn <sectorID> <点位ID> nearby
/sfgame sector point remove <sectorID> <点位ID>
```

同一个 sector 内的点位不可重叠；不同 sector 可以复用区域和点位 ID。sector 与点位 ID 均支持单个字母或单个数字；字母显示时会自动转为大写，数字保持不变。每个突破点位都必须配置两个重生坐标：站在点位区域内部执行 `inside`，站在该点附近的安全位置执行 `nearby`。两者必须和点位处于同一维度，缺少任意一个都会阻止开赛。

### Sector 出生点指令

```text
/sfgame sector spawn add <sectorID> <attacker|defender>
/sfgame sector spawn list <sectorID> <attacker|defender>
/sfgame sector spawn remove <sectorID> <attacker|defender> <序号>
/sfgame sector spawn clear <sectorID> <attacker|defender>
```

每个 sector 必须至少有一个进攻出生点和一个防守出生点。双赛段交换的是原版队伍的攻守身份，地图中的 `attacker` 和 `defender` 坐标仍表示当前赛段的角色位置，无需重新配置。

### 突破模式载具

突破模式支持按“载具槽位”生成外部载具模组注册的实体。SFGame 不硬编码卓越前线的实体类，而是读取实体资源 ID（例如 `superbwarfare:<实体名>`）；开赛校验会检查该实体是否已在当前服务器注册。缺少对应载具模组或实体 ID 写错会阻止开赛。每个槽位最多同时存在一个实体，实体被摧毁或移除后才开始该槽位的重生计时，计时结束才会生成下一辆，不会因为 Tick 重复生成。

```text
/sfgame rule breakthrough vehicle add <槽位ID> <实体资源ID> <attacker|defender> <重生秒数>
/sfgame rule breakthrough vehicle set <槽位ID>
/sfgame rule breakthrough vehicle set entity <槽位ID> <实体资源ID>
/sfgame rule breakthrough vehicle set role <槽位ID> <attacker|defender>
/sfgame rule breakthrough vehicle set interval <槽位ID> <重生秒数>
/sfgame rule breakthrough vehicle set offset <槽位ID> <Y轴偏移>
/sfgame rule breakthrough vehicle set energy <槽位ID> <0-100>
/sfgame rule breakthrough vehicle set ammo <槽位ID> <弹药物品ID> <数量>
/sfgame rule breakthrough vehicle set ammo <槽位ID> none
/sfgame rule breakthrough vehicle ammo add <槽位ID> <弹药物品ID> <数量>
/sfgame rule breakthrough vehicle ammo remove <槽位ID> <弹药物品ID>
/sfgame rule breakthrough vehicle ammo clear <槽位ID>
/sfgame rule breakthrough vehicle ammo list <槽位ID>
/sfgame rule breakthrough vehicle list
/sfgame rule breakthrough vehicle status <槽位ID>
/sfgame rule breakthrough vehicle remove <槽位ID>
/sfgame rule breakthrough vehicle clear
```

`vehicle add` 使用管理员当前位置作为基准生成点；`vehicle set` 将已有槽位移动到当前位置。载具只继承管理员的水平朝向（yaw），俯仰角始终固定为 `0°`，因此低头执行设置指令也不会让载具垂直生成。生成时还会应用槽位的 Y 轴偏移，默认 `+0.2` 格，可用 `set offset` 在 `-64～64` 之间修改。重生秒数范围为 1～3600，地图最多配置 16 个槽位。`attacker`/`defender` 用于标记该槽位服务的当前角色，双赛段交换攻守时角色含义随赛段变化；生成位置不会自动移动。

每个新槽位默认携带 `1` 个 `superbwarfare:creative_ammo_box`，即卓越前线载具可使用无限弹药。`set ammo` 会用一种弹药物品替换整个出生弹药配置；`none` 或 `ammo clear` 会禁用自动装填；`ammo add` 可为同时使用多种弹药的载具追加或更新物品，单项数量范围为 `1～4096`，每个槽位最多 16 项。弹药通过 Forge 实体物品栏能力放入载具自身仓库，错误或未安装的物品 ID 会阻止开赛。

载具能源使用 Forge Energy 兼容层处理，因此卓越前线中表现为燃油或电量的载具都适用。新槽位默认以 `100%` 能源生成，`set energy` 可配置为 `0～100%`；不具有能源能力的实体会忽略此配置。阶段推进和赛段切换不会因为计时而重复生成或清理仍存活的载具。卓越前线载具在一阶段爆炸并进入 `isWreck` 状态的下一个服务端 Tick 会被立即清除，不会继续显示二阶段报废车；此时才开始该槽位的重生计时。停止比赛和返回大厅时会清理全部载具。

### 票数、时间与胜负

- 每个 sector 默认有 100 张进攻票，任意进攻方有效死亡扣除 1；防守方死亡不扣票。
- 进入下一 sector 时，进攻票数重新补满。
- `timeLimitSeconds` 在突破模式中表示每个 sector 的独立时限，推进时重置。
- 单赛段中，攻陷最后 sector 则进攻方获胜；票数归零或超时则防守方获胜。
- 双赛段结束后依次比较已攻陷 sector 数、当前 sector 已占点数、达到进度所用时间和剩余票数，完全相同则平局。
- 阶段整备期间全员无敌，不能通过开火提前解除保护。
- `mapBlockBreaking` 默认关闭；管理员启用后，实际只能编辑地图 build box 内的白名单方块。突破模式的投票、倒计时和 sector 整备期间仍暂停编辑。规则可在比赛中修改并立即生效。
- 突破模式默认死亡重生倒计时为 10 秒。倒计时结束后自动打开位置选择，可选择当前 sector 的队伍出生点，或当前由本队占领的点位；安全点位使用 `inside` 坐标，人数并列或敌方正在中立化/夺取时使用 `nearby` 坐标。按钮点击时会再次校验归属，已经失守的点不能重生。

突破模式规则：

```text
/sfgame rule set attackerTickets <1-10000>
/sfgame rule set breakthroughVariant <normal|captain>
/sfgame rule set breakthroughLegs <1|2>
/sfgame rule set breakthroughAttacker <阵营>
/sfgame rule set breakthroughDefender <阵营>
/sfgame rule set timeLimitSeconds <秒>
/sfgame rule set sectorTransitionSeconds <秒>
/sfgame rule set captureTimeSeconds <秒>
/sfgame rule set captureUsePlayerDifference <true|false>
/sfgame rule set captureDifferenceCoefficient <小数>
/sfgame rule set captureMaxMultiplier <倍率>
/sfgame rule set captainVoteSeconds <秒>
/sfgame rule set captainReplacementVoteSeconds <秒>
/sfgame rule set attackerCaptainGlowing <true|false>
/sfgame rule set mapBlockBreaking <true|false>
/sfgame rule set attackerCaptainCaptureWeight <小数>
/sfgame rule set defenderCaptureWeight <小数>
```

比赛中修改 `attackerTickets` 会立即把当前 sector 的剩余票数设置为新值，后续 sector 也按新值补满。降低时限至本 sector 已用时间以下，会在下一 Tick 判定防守成功。

### 队长变体用法

执行 `/sfgame rule set breakthroughVariant captain` 启用队长变体。只有当前进攻方选举队长，防守方不选举队长、没有旗帜、也不使用队长职业。

- `/sfgame start` 后先进入默认 15 秒投票阶段，进攻玩家会自动打开投票菜单。
- 玩家可以投给任意在线进攻队友、自投或弃权；未投票按弃权处理。
- 队长只使用可配置的发光轮廓辨识，不占用头盔栏；队长职业使用 `captainClasses` 中的专属职业。
- 进攻方队长默认对全服玩家显示发光轮廓，可用 `attackerCaptainGlowing` 热规则关闭。
- 普通进攻玩家占点权重为 1.0，进攻队长默认 2.0；每名防守玩家默认 1.4。
- 队长死亡后保留身份，重生期间清除发光；重新部署后恢复。掉线、离队或换队会触发默认 10 秒补选，比赛结束时也会清理 SFGame 发光状态。
- 补选期间普通进攻玩家仍可占点，但没有队长加成。
- 双赛段换边时，原队长恢复普通职业，新进攻方重新选举队长。

玩家与管理员接口：

```text
/sfgame captain vote <玩家>
/sfgame captain vote abstain
/sfgame captain status
/sfgame captain set <进攻阵营> <玩家>
/sfgame captain reelect <进攻阵营>

/sfgame class list normal
/sfgame class list captain
/sfgame class set <玩家> <普通职业ID>
/sfgame class setcaptain <玩家> <队长职业ID>
```

管理员指定或补选出新队长时，系统会无死亡记录地重新部署该玩家并应用队长职业。队长可以在菜单中预选其他队长职业，新配装在下一次部署时生效。

## 夺旗（CTF）模式

使用 `/sfgame mode select ctf` 选择夺旗模式。CTF 包含三个子模式：`classic`（多队经典夺旗）、`assault`（固定进攻/防守的一攻一守）和 `territory`（前线旗先占点解锁，再夺旗）。CTF 地图沿用当前地图目录中的大厅、队伍出生点和原版 `/team` 绑定；职业覆盖保存在 `<存档>/serverconfig/sfgame/maps/ctf/<地图>/classes.json`，未填写覆盖时使用 TDM 内置职业池。

### 模式与队伍

```text
/sfgame rule set ctfVariant <classic|assault|territory>
/sfgame rule set ctfAttacker <red|blue|yellow|green>
/sfgame rule set ctfDefender <red|blue|yellow|green>
/sfgame rule set ctfCarrierRestriction <normal|movement_limited|no_weapons>
/sfgame rule ctf status
```

`classic` 和 `territory` 支持当前地图启用的 2～4 个阵营；`assault` 必须恰好启用两个阵营，并用 `ctfAttacker` 与 `ctfDefender` 指定攻守。`normal` 允许完整装备，`movement_limited` 禁止冲刺，`no_weapons` 禁止枪械和近战攻击但保留投掷物。

CTF 模式参数说明：

| 参数 | 可选值/范围 | 说明 |
| --- | --- | --- |
| `ctfVariant=classic` | 2～4 个启用阵营 | 每队都有家旗；夺取敌旗并带回己方交旗区域得分。己方家旗不在旗座时不能交旗得分。 |
| `ctfVariant=assault` | 恰好 2 个阵营 | 只允许规则指定的进攻方夺取防守方目标旗；防守方不能得分，进攻票归零时防守方获胜。 |
| `ctfVariant=territory` | 2～4 个启用阵营 | 先按占点规则解锁前线旗，再把旗带到己方 depot；原归属队伍可回收并插回原点。 |
| `ctfAttacker` / `ctfDefender` | 两个不同阵营 | 只在 assault 中使用；classic/territory 不读取攻守方规则。 |
| `ctfCarrierRestriction=normal` | — | 持旗者可正常使用职业装备、武器和冲刺。 |
| `ctfCarrierRestriction=movement_limited` | — | 持旗者不能冲刺及使用特殊移动，但仍可使用武器。 |
| `ctfCarrierRestriction=no_weapons` | — | 持旗者禁止 TACZ 枪械和近战攻击，只保留投掷物（待对应 TACZ 枪包提供后启用）。 |
| `<队伍>` | `red` / `blue` / `yellow` / `green` | 家旗配置使用的 SFGame 阵营；该阵营必须是当前地图启用阵营。 |
| `<旗帜ID>` | 字母、数字、下划线 | 前线旗的唯一 ID；允许单个字母或单个数字。 |
| `<半径>` | 1～256 | square 家旗交旗区或前线旗占点区半径。 |
| `flag` | 一个位置 | 家旗展示/插旗位置；站在地面目标位置执行。 |
| `capture box/square` | 区域 | 交旗区域；box 使用根命令的 `pos1`/`pos2`，square 使用当前站立中心和半径。 |
| `depot` | 一个位置 | 敌方旗带回、存放和原队伍回收旗帜的位置。 |
| `forward ... stand` | 一个位置 | 前线旗的初始插旗/恢复位置。 |

CTF 旗帜状态是 `STAND`（旗座）、`CARRIED`（玩家头盔栏持有）、`DROPPED`（掉落）或 `DEPOT`（存放点）。持旗者会高亮，旗帜交付、死亡、离队、掉线或自动返回时恢复原头盔装备。`ctfFlagReturnSeconds` 控制掉落旗自动返回；`scoreLimit` 控制夺旗胜利次数。

### 家旗与前线旗

管理员站在目标位置执行：

```text
/sfgame pos1
/sfgame pos2
/sfgame rule ctf home set <red|blue|yellow|green> flag
/sfgame rule ctf home set <red|blue|yellow|green> capture box
/sfgame rule ctf home set <red|blue|yellow|green> capture square <半径>
/sfgame rule ctf home set <red|blue|yellow|green> depot
/sfgame rule ctf home clear <队伍> <flag|capture|depot>
/sfgame rule ctf home list

/sfgame rule ctf forward add box <归属阵营> <旗帜ID>
/sfgame rule ctf forward add square <归属阵营> <旗帜ID> <半径>
/sfgame rule ctf forward set box <旗帜ID>
/sfgame rule ctf forward set center <旗帜ID>
/sfgame rule ctf forward set radius <旗帜ID> <半径>
/sfgame rule ctf forward set height <旗帜ID> full
/sfgame rule ctf forward set height <旗帜ID> <minY> <maxY>
/sfgame rule ctf forward set stand <旗帜ID>
/sfgame rule ctf forward list
/sfgame rule ctf forward status <旗帜ID>
/sfgame rule ctf forward remove <旗帜ID>
/sfgame rule ctf forward clear
```

`classic` 中每队需要家旗、交旗区域和 depot；己方家旗离开旗座时不能交旗得分。旗帜会在死亡、掉线、离队或换队时掉落，默认 30 秒后自动返回。`territory` 的前线旗初始锁定，敌方先在其点位完成占领才可拾取；带到己方 depot 得 1 分，原归属队伍可在该 depot 回收并插回原点再得 1 分。旗帜在旗座、掉落点和 depot 使用服务端不可见盔甲架承载并直接放置在配置坐标的地面位置，不显示盔甲架轮廓；玩家持旗时旗帜改为装备在头盔栏并使持旗者高亮，交付或掉落后恢复原头盔装备。

### 所有模式共用的地图复原与方块白名单

```text
/sfgame pos1
/sfgame pos2
/sfgame rule build setbox
/sfgame rule build status
/sfgame rule build clear snapshot
/sfgame rule build clear setbox
/sfgame rule build clear all
/sfgame rule build allow <方块ID|#方块标签>
/sfgame rule build disallow <方块ID|#方块标签>
/sfgame rule build allowlist
/sfgame rule build snapshot save
/sfgame rule build snapshot restore
```

地图复原属于地图本身，不再属于 CTF，因此 TDM、占点、突破和夺旗都使用同一套配置。快照保存到世界 `data/sfgame/maps/<模式ID>/<地图ID>/`。系统会按 X/Z 方向自动拆成 16×16 的分区 NBT；大型地图不会构造单个超大结构 NBT。`allowlist` 模式使用稀疏分区，只保存和排队还原实际包含白名单母版方块的分区；空分区不会产生文件、进度或等待时间。例如 500×500 地图中只有一个白名单方块时，快照通常只有一个分区。只有 `mapBlockBreaking=true` 时才会在开赛前自动恢复，结算和返回大厅期间不会再次恢复。

将准星分别对准两个对角方块并执行 `/sfgame pos1` 与 `/sfgame pos2`，再执行 `rule build setbox`。`allow`/`disallow` 接受完整方块资源 ID（例如 `minecraft:white_wool`）或以 `#` 开头的方块标签（例如 `#minecraft:logs`），并提供原版资源/标签补全。所有模式默认 `mapBlockBreaking=false`，因此未主动配置的地图不会恢复或开放破坏；需要地图破坏时，先设置 build box、白名单并保存快照，再将规则设为 `true`。启用后，匹配的方块才可由玩家挖掘/放置，也可被原版爆炸、TACZ 枪械、卓越前线枪械或载具破坏。白名单写在当前模式的规则 JSON 中，而不是地图 SavedData NBT。`snapshot save` 保存母版，`restore` 按当前快照模式恢复选区，`build status` 查看 setbox 两个边界坐标、快照有效性与实际分区数。规则开启但未设置 build box 或未保存兼容母版时，`/sfgame start` 会拒绝开赛。

#### `setbox` 是什么

`/sfgame rule build setbox` 用最近一次 `/sfgame pos1` 和 `/sfgame pos2` 选择当前地图的 build box，也就是允许方块变化并参与母版保存、清空和复原的地图范围。两个位置必须位于同一维度；X/Z 使用两个位置形成的长方形边界，目前 Y 轴默认覆盖该维度从最低建筑高度到最高建筑高度的全部高度。

`setbox` 只记录区域，不会立即保存、复制、清空或修改任何方块。设置后还必须执行 `/sfgame rule build snapshot save` 才会生成母版。build box 按当前地图 ID 独立保存，因此切换地图后需要为新地图单独设置。

完整操作：

```text
# 站在地图范围的第一个水平对角
/sfgame pos1

# 站在另一个水平对角
/sfgame pos2

# 把两个位置之间的区域设为当前地图的 build box
/sfgame rule build setbox

# 保存该区域目前的方块状态
/sfgame rule build snapshot save
```

相关命令的区别：

| 命令 | 是否修改世界方块 | 作用 |
| --- | --- | --- |
| `/sfgame rule build setbox` | 否 | 设置或替换当前地图的复原区域。替换区域后旧母版会失效。 |
| `/sfgame rule build status` | 否 | 显示 setbox 是否存在、两个边界坐标、母版是否有效、有效分区数量及状态详情。全高度 setbox 的 Y 坐标显示为 `full-height`。 |
| `/sfgame rule build clear snapshot` | 否 | 删除当前地图的母版文件，但保留 setbox 和白名单。 |
| `/sfgame rule build clear setbox` | 否 | 清除当前地图的 build box 配置，但保留母版文件和白名单。它不负责恢复地图。 |
| `/sfgame rule build clear all` | 否 | 同时删除当前地图的母版文件并清除 setbox；白名单仍然保留。 |
| `/sfgame rule build snapshot save` | 否 | 按 `mapSnapshotMode` 读取 build box 内的方块并保存成母版。 |
| `/sfgame rule build snapshot restore` | 是 | 按 `mapSnapshotMode` 清除并还原 build box 内对应的方块。 |

#### `snapshot` 是什么

`/sfgame rule build snapshot` 是地图“母版快照”命令组，本身不能单独执行，后面必须添加 `save` 或 `restore`。母版快照记录比赛开始前地图应有的方块状态及方块实体数据，例如箱子内容；它不保存玩家、载具、掉落物或其他普通实体。状态查询已经统一为 `/sfgame rule build status`，删除母版使用 `/sfgame rule build clear snapshot`。实际保存范围由规则 `mapSnapshotMode` 决定：

- `allowlist`（默认）：只保存和还原 build box 内由方块 ID 或 `#方块标签` 匹配的母版方块。系统只为实际含有这些方块的 16×16×16 区域创建稀疏分区；没有匹配方块的区域不会进入还原队列。还原时清除这些分区内当前仍匹配该白名单的方块，再写回母版；选区内其他地形方块和选区外所有方块都不会被修改。
- `full`：保存和还原 build box 内的全部方块，包含快照中的空气位置；手动还原时会把整个选区恢复到母版状态。

设置命令为 `/sfgame rule set mapSnapshotMode <allowlist|full>`。这是按模式、地图保存且支持继承的下局规则，比赛期间不能修改。切换模式，或在 `allowlist` 模式下增删白名单后，已有快照会失效，必须重新执行 `snapshot save`。

| 命令 | 作用 |
| --- | --- |
| `/sfgame rule build snapshot save` | 把 build box 当前状态保存为母版。建好地图并清理临时方块、载具和测试痕迹后执行。再次执行会覆盖当前地图原有母版。 |
| `/sfgame rule build snapshot restore` | 按当前 `mapSnapshotMode` 立即恢复所有分区。用于管理员手动测试复原结果。 |
| `/sfgame rule build status` | 查看当前地图的 setbox 两个边界坐标、母版是否匹配、实际有效分区数和错误详情。 |
| `/sfgame rule build clear snapshot` | 删除当前地图的母版文件，不会立即删除或修改世界中的方块。删除后必须重新 `save` 才能在 `mapBlockBreaking=true` 时开赛。 |

推荐配置顺序：

```text
# 1. 选择需要保存的地图范围
/sfgame pos1
/sfgame pos2
/sfgame rule build setbox

# 2. 配置允许比赛期间改变的方块
/sfgame rule build allow minecraft:grass_block
/sfgame rule build allow minecraft:glass
/sfgame rule build allow #minecraft:logs

# 默认只保存和还原上述白名单方块；如需完整选区快照可改为 full
/sfgame rule set mapSnapshotMode allowlist

# 3. 确认地图处于正确的初始状态，然后保存母版
/sfgame rule build snapshot save

# 4. 检查配置；需要时可以手动测试恢复
/sfgame rule build status
/sfgame rule build snapshot restore
```

保存母版后，如果重新执行 `build setbox` 修改区域、切换 `mapSnapshotMode`，或在 `allowlist` 模式下修改白名单，原母版会被判定为不匹配，必须再次执行 `snapshot save`。启用方块破坏时，比赛只会在开赛前自动恢复一次母版；比赛结束后不会再次加载地图。

### 规则与商店

```text
/sfgame rule set scoreLimit <夺旗次数>
/sfgame rule set timeLimitSeconds <秒>
/sfgame rule set ctfFlagReturnSeconds <秒>
/sfgame rule set ctfHomeCaptureTimeSeconds <秒>
/sfgame rule set captureTimeSeconds <秒>
/sfgame rule set captureUsePlayerDifference <true|false>
/sfgame rule set captureDifferenceCoefficient <小数>
/sfgame rule set captureMaxMultiplier <倍率>

/sfgame shop list
/sfgame shop buy <商品ID>
/sfgame shop reload
```

击杀默认奖励 25，带旗回家 100，前线旗插回 50，前线点首次解锁 10；货币只在本局有效。商店配置位于 `<存档>/serverconfig/sfgame/shop/ctf.json`，商品会在服务端检查参赛状态、死亡状态、价格、物品有效性和背包空间。

```text
/sfgame shop list
/sfgame shop buy <商品ID>
/sfgame shop reload
```

`shop buy` 只能由当前参赛且不在死亡倒计时的玩家执行；`<商品ID>` 必须来自当前 `ctf.json`。商品字段为 `id`（唯一 ID）、`name`（菜单名称）、`icon`（菜单图标资源）、`price`（货币价格）、`item`（实际发放物品）、`count`（数量）和可选 `nbt`（物品 NBT 字符串）。`icon` 与 `item` 均支持 `资源ID{NBT}` 内联 NBT 写法，例如 `"tacz:modern_kinetic_gun{GunId:\"tacz:hk416d\"}"`；内联与 `nbt` 字段同时存在时内联优先。每局开始货币归零，死亡后购买物品不会保留。

## 验证

IDEA 开发客户端通过 `installTaczForRuns` 自动把 ForgeGradle 生成的 TACZ 1.1.8-hotfix 开发映射 JAR 放入 `run/mods/`。不要把官网下载的生产版 TACZ JAR 直接放入该开发目录，否则其 Mixin 字段名无法匹配 Forge 的命名开发环境。IDEA 的 `runClient` 会在启动前执行 `prepareRunClientCompile`，并自动完成安装。生成或修复 IDEA 启动配置可执行：

```powershell
.\gradlew.bat genIntellijRuns
```

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

自动测试覆盖动态规则的边界、重置与 SavedData 序列化。完整的队伍切换、TACZ 射击归属和双客户端流程仍应在实际 Forge 测试服中按验收清单做联机验证。

## 自动构建与发布

推送到 `main` 或向 `main` 提交 Pull Request 时，GitHub Actions 会使用 Java 17 自动执行测试和完整构建，并保存构建产物。每次向 `main` 推送新提交还会自动创建一个可追溯到提交哈希的预发布版本；本次 v0.1.1 快照会形如 `v0.1.1-build.<运行号>.<提交短哈希>`。创建并推送语义化版本标签则会创建或覆盖对应的正式 GitHub Release：

```powershell
git tag v0.1.0
git push origin v0.1.0
```

标签 `v1.0.0` 会构建 `sfgame-1.0.0.jar`；包含 `alpha`、`beta` 或 `rc` 的版本会自动作为预发布版本。

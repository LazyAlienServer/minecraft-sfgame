# SFGame

[![Build](https://github.com/LazyAlienServer/minecraft-sfgame/actions/workflows/build.yml/badge.svg)](https://github.com/LazyAlienServer/minecraft-sfgame/actions/workflows/build.yml)

SFGame 是一个 Forge 1.20.1 的枪战游戏框架 Mod，依赖 TACZ 提供枪械和弹药。当前版本实现团队竞技、占点、突破攻防和夺旗（CTF）模式，以及动态规则、原版队伍绑定、职业配装、重生、计分、菜单及 HUD。

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
/sfgame rule reset

/sfgame class list
/sfgame class validate
/sfgame class reload
/sfgame class set <玩家> <职业ID>
```

规则统一通过 `/sfgame rule` 管理。`list`、`get` 和命令补全只显示当前模式适用的规则；其他模式的专属规则会被隐藏并拒绝执行。`maxPlayers`、`scoreLimit`、`timeLimitSeconds`、`startCountdownSeconds`、`respawnSeconds`、`respawnProtectionSeconds` 和 `resultSeconds` 是通用规则。`resultSeconds` 默认是 20 秒，即比赛结算后等待 20 秒返回大厅。规则及地图坐标保存在世界 SavedData 中，重启后保留。旧版 `/sfgame rules` 仍作为兼容别名保留。

### 规则参数完整说明

管理员权限要求为 2。`<规则>` 必须使用下表中的精确拼写；`<值>` 不要带单位。修改会写入当前世界，并在当前比赛的下一次服务端 Tick（或下一阶段）生效。

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
| `attackerTickets` | 100 | 1～10000 | 突破、CTF assault | 突破为进攻方死亡票数；CTF assault 为进攻方兵力票。设定后当前阶段/回合立即改为该值。 |
| `sectorTransitionSeconds` | 10 | 0～60 | 突破 | 攻陷一个 sector 后的整备和安全部署时间。 |
| `captainVoteSeconds` | 15 | 1～120 | 突破 captain | 首次队长投票时长。 |
| `captainReplacementVoteSeconds` | 10 | 1～120 | 突破 captain | 队长掉线、离队或换队后的补选时长。 |
| `attackerCaptainGlowing` | true | true/false | 突破 captain | 是否让进攻方队长使用发光轮廓；队长不占用头盔栏。 |
| `attackerCaptainCaptureWeight` | 2.0 | 1.0～10.0 | 突破 captain | 进攻队长在点内的占领权重。 |
| `defenderCaptureWeight` | 1.4 | 0.1～10.0 | 突破 captain | 每名防守玩家在点内的占领权重；防守方不选举队长。 |
| `ctfFlagReturnSeconds` | 30 | 5～600 | CTF | 掉落旗帜无人回收时自动返回旗座的秒数。 |
| `ctfHomeCaptureTimeSeconds` | 15 | 1～600 | CTF territory | territory 家旗独立占领/中立化阶段的基础时间。 |

查询或修改示例：

```text
/sfgame rule list
/sfgame rule get scoreLimit
/sfgame rule set scoreLimit 75
/sfgame rule reset
```

`rule list`、`rule get`、`rule set` 会根据当前 `/sfgame mode select <模式ID>` 隐藏并拒绝其他模式专属参数。例如当前是 TDM 时，`scoreIntervalSeconds`、`attackerTickets` 和 `ctfFlagReturnSeconds` 都不能使用。

`/sfgame team set` 和 `/sfgame team remove` 使用多玩家实体参数，支持玩家名以及 `@a`、`@p`、`@r` 等原版选择器。例如：`/sfgame team set @a random`。

### 全局命令参数

| 命令 | 参数说明与效果 |
| --- | --- |
| `/sfgame menu` | 为执行命令的玩家打开菜单；默认按键为 `M`。 |
| `/sfgame leave` | 退出当前比赛。比赛未开始时只离开 SFGame 参赛队伍并留在原地；比赛进行中会进入旁观并排入下一局。 |
| `/sfgame status` | 查看当前模式、地图、阶段、队伍人数、出生点和开赛校验错误。 |
| `/sfgame start` | 手动开始当前地图。会依次校验队伍、出生点、职业、点位/旗帜配置和人数。 |
| `/sfgame stop` | 安全停止当前比赛、清理 HUD/实体并返回大厅。 |
| `/sfgame reset` | 清空当前地图的比赛运行状态和比分；不会删除地图点位或职业文件。 |
| `/sfgame reload` | 重新载入 SFGame 世界数据、职业配置和 CTF 商店；正在进行的比赛不会被强制替换装备。 |
| `/sfgame joinnow <玩家>` | 管理员允许一名中途玩家立即加入当前局。当前人数达到 `maxPlayers` 或玩家未选有效职业时拒绝。 |
| `/sfgame pos1`、`/sfgame pos2` | 记录执行者当前维度和坐标，供所有 box 区域命令使用。两点的维度必须相同；`pos1`/`pos2` 是全局临时选择，不是地图坐标。 |
| `/sfgame mode list` | 列出 `tdm`、`domination`、`breakthrough`、`ctf` 等可用模式。 |
| `/sfgame mode select <模式ID>` | 选择当前世界后续编辑和开赛使用的模式；比赛进行中禁止切换。 |
| `/sfgame map list` | 列出当前模式的所有地图及启用状态。 |
| `/sfgame map create <地图ID>` | 创建一个模式专属地图。地图 ID 支持字母、数字和下划线，且可为单个字母/数字。 |
| `/sfgame map select <地图ID>` | 选择当前模式的活动地图；后续 `spawn`、`point`、`sector`、`ctf` 编辑都写入该地图。 |
| `/sfgame map status` | 查看活动地图 ID、模式配置和校验摘要。 |
| `/sfgame map remove <地图ID>` | 删除当前模式下指定地图的全部持久化配置；比赛中不可用，执行前请确认 ID。 |

`<玩家>` 使用单个在线玩家参数，`<玩家选择器>` 使用原版多实体选择器，因此 `/sfgame team set @a random` 会一次处理所有在线玩家；`@a`、`@p`、`@r` 的筛选规则与原版 `/team` 相同。管理员命令统一要求权限等级 2。

地图按照“模式 → 地图”保存。当前内置模式 ID 为 `tdm`、`domination`、`breakthrough` 和 `ctf`，每个模式可以拥有多张地图。`/sfgame spawn setdefault lobby` 将管理员当前位置保存为全局默认大厅；世界首次加载时若尚未配置，会自动使用主世界原版出生点。`/sfgame spawn set lobby` 为当前地图设置覆盖点，`/sfgame spawn clear lobby` 清除覆盖并恢复使用全局默认大厅。长方形区域统一先执行根命令 `/sfgame pos1`、`/sfgame pos2` 记录两个角点，再执行对应模式的 `set box` 或 `add box` 命令。团队竞技和占点地图可启用红、蓝、黄、绿中的 2～4 个阵营：某阵营只要至少配置一个出生点，就视为该地图启用该阵营；地图至少需要两个启用阵营。每次执行 `/sfgame spawn set <队伍>` 都会追加出生点，玩家部署时从本队坐标中随机选择。随机分队只会使用当前地图已启用阵营，并优先分配人数最少的阵营；也支持 `/sfgame team set random @a` 的随机参数顺序。使用 `/sfgame spawn list` 查看带序号坐标，通过 `remove` 或 `clear` 管理。突破攻防地图改用每个 sector 的攻守角色出生点，配置方法见下文。旧版单个红蓝出生点会自动迁移为对应列表中的第 1 个点。比赛进行时禁止修改地图与出生点。

SFGame 自有 ID（地图、模式、sector、点位、职业及职业配置继承 ID）可以字母或数字开头，支持单个字母和单个数字，例如 `a`、`1`、`a1`、`1a`。后续字符可使用字母、数字和下划线；输入的大写字母会统一按小写 ID 处理。枪械、物品和附件等 Minecraft/TACZ 资源 ID 仍遵循其原有的命名空间格式。

### 大厅、出生点和原版队伍参数

`<阵营>` 只能是 `red`、`blue`、`yellow`、`green`。它代表 SFGame 阵营，不是颜色文字本身；SFGame 会把它绑定到一个原版 `/team` 名称。

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

首次运行会保留旧的 `config/sfgame/classes.json`，并为每个模式生成独立职业配置：

```text
config/sfgame/classes/tdm.json
config/sfgame/classes/domination.json
config/sfgame/classes/breakthrough.json
config/sfgame/classes/ctf.json
```

第一次迁移会为三个模式建立独立文件，不默认建立继承关系。TDM 与占点的内置来源位于 `src/main/resources/defaults/classes.json`；突破模式的完整默认来源位于 `src/main/resources/defaults/classes/breakthrough.json`。已有的突破配置会执行一次增量升级：保留管理员已填写的字段，补入缺失的内置职业，并为内置职业补齐为空的 `inventory` 与 `armor`。

TDM 与占点默认包含：

- `assault`：HK416、180 发备用弹药、105% 移速（TACZ 1.1.8-hotfix 资源 ID 为 `tacz:hk416d`）。
- `sniper`：M107、30 发备用弹药、95% 移速。

每个模式文件支持可选的 `parent`、普通 `classes` 和队长 `captainClasses`：

```json
{
  "parent": "tdm",
  "classes": [],
  "captainClasses": []
}
```

子配置中相同 ID 会覆盖父配置，缺失父配置和循环继承会被拒绝。突破模式默认普通职业包括 `assault`、`sniper`、`medic`（医疗兵）、`tank`（坦克）和 `smg_assault`（冲锋手）；队长职业包括 `heavy_captain`（重装队长）与 `captain_tank`（坦克）。当前 TACZ 1.1.8-hotfix 默认包只提供枪械、枪械近战动作和弹药，没有独立的手雷、烟雾弹、闪光弹或近战物品；因此职业暂时使用配置中的普通物品占位，待提供包含这些资源的枪包后再接入四选一投掷物和 TACZ 近战物品。护甲和其他物品仍可通过 `inventory` 与 `armor` 修改。

职业 JSON 字段含义如下：

| 字段 | 类型/示例 | 作用 |
| --- | --- | --- |
| `id` | `"assault"` | 配置和命令使用的职业 ID；同一模式中必须唯一。 |
| `displayName` | `"突击兵"` | 菜单、HUD 和提示中显示的人类可读名称。 |
| `description` | `"中近距离持续作战"` | 职业卡片 Tooltip 的说明文字。 |
| `icon` | `"minecraft:iron_sword"` | 职业卡片图标物品资源 ID。 |
| `maxHealth` | `20.0` | 部署时的最大生命值，范围由服务端限制为 1～2048。 |
| `movementSpeedMultiplier` | `1.05` | 移动速度倍率；`1.0` 为正常速度，`0.95` 为慢 5%。 |
| `gunId` | `"tacz:hk416d"` | TACZ 主武器资源 ID；必须能被 `TimelessAPI` 索引。 |
| `ammoId` | `"tacz:556x45"` | TACZ 弹药资源 ID；必须与枪械兼容。 |
| `initialMagazine` | `30` | 主武器初始弹匣装弹量。 |
| `reserveAmmo` | `180` | 备用弹药数量；服务端装入一个 TACZ 钻石级弹药箱，放在槽位 9。 |
| `fireMode` | `"AUTO"` | TACZ 射击模式，例如 `AUTO`、`SEMI`；无效值会阻止开赛。 |
| `attachments` | `{ "SCOPE": "tacz:..." }` | TACZ 附件类型到资源 ID 的映射；附件类型和资源都必须有效。 |
| `inventory` | `[ {"item":"minecraft:...", "count":1} ]` | 普通物品栏配装；每项可写 `item`、`count`、可选 `nbt`。 |
| `armor` | `{ "head": {...}, "chest": {...} }` | `head`、`chest`、`legs`、`feet` 四个盔甲槽的物品定义。 |
| `offhand` | `{ "item":"minecraft:..." }` | 副手物品；未填写则为空。 |
| `effects` | `[ {"id":"minecraft:...", "durationTicks":200, "amplifier":0} ]` | 部署时添加的药水效果；`visible:false` 可隐藏状态图标。 |
| `allowDrop` | `false` | 是否允许玩家丢弃该职业配装；比赛内通常保持 `false`。 |

模式文件根对象的 `parent` 只能引用同目录的另一个配置 ID；`classes` 是普通职业池，`captainClasses` 是突破队长职业池。子文件中相同 `id` 会覆盖父文件定义，未覆盖的职业继续继承。玩家在不同模式的选择相互隔离。

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

`class list` 的 `normal`/`captain` 参数分别查看当前模式普通职业池和突破队长职业池；不带参数时查看普通池。`class set` 修改普通职业，`class setcaptain` 只在突破 captain 变体中生效；玩家存活期间为“待切换”，下一次死亡重生或管理员重新部署时应用。

职业配置中的 `reserveAmmo` 会装入一个 TACZ 钻石级弹药箱，不再发放散装弹药；弹药箱固定放在背包三行区域左上角（物品栏槽位 `9`）。

通用配置文件 `config/sfgame-common.toml` 中的 `globalHungerLock` 默认为 `true`。启用后，SFGame 模式运行期间所有在线玩家的饥饿值和饱和度均固定为 20。

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
/sfgame point strategy <async|sync>
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
| `strategy async` | 所有点同时扫描、同时显示 Bossbar，并分别产分。 |
| `strategy sync` | 只扫描和显示当前开放点；当前点完成保持时间后才切到随机下一个点。 |
| `list` / `status <点位ID>` | 查看区域类型、维度、高度、归属、推进者、进度和当前是否开放。 |

点内没有唯一人数最多的阵营时进度暂停；唯一领先方推进，中立点更换推进方会清除原挑战进度。已有归属点被争夺后先中立化，再由新阵营占领。占点人员必须是存活且参赛玩家，旁观、排队和重生倒计时玩家不计入。

规则按模式独立保存。占点模式额外支持：

```text
/sfgame rule set captureTimeSeconds <秒>
/sfgame rule set captureUsePlayerDifference <true|false>
/sfgame rule set captureDifferenceCoefficient <小数>
/sfgame rule set captureMaxMultiplier <倍率>
/sfgame rule set scoreIntervalSeconds <秒>
/sfgame rule set scorePerPoint <分数>
/sfgame rule set syncHoldSeconds <秒>
```

人数差计算开启时，占领速度为基础速度乘以第一名与第二名人数差，再乘以系数 `k`，最终受最大倍率限制。比赛中使用原版 Bossbar 显示点位进度。

占点模式会在每个当前开放点的中心上方显示发光悬浮字母。`async` 显示全部活动点，`sync` 只显示当前随机开放点；切点和比赛结束时会自动清理。

## 突破攻防模式

使用 `/sfgame mode select breakthrough` 选择突破攻防。该模式严格使用两个阵营：管理员指定一个进攻阵营和一个防守阵营。地图由 1～16 个有序 sector 构成，每个 sector 包含 1～16 个同时开放的点位，并分别保存进攻方和防守方出生点。

进攻方必须同时控制当前 sector 的全部点位才能推进。点位初始归防守方，进攻方需要先中立化再占领；在整个 sector 完成前，防守方可以修复和夺回点位。推进后旧 sector 锁定，经过默认 10 秒整备期，全员无死亡记录地部署到下一个 sector。当前开放点会显示 Bossbar 和中心上方的发光悬浮字母。

### 最小建图流程

下面的示例创建一张红队进攻、蓝队防守、包含两个 sector 的普通突破地图：

```text
/sfgame mode select breakthrough
/sfgame map create example
/sfgame spawn set lobby

/sfgame breakthrough variant normal
/sfgame breakthrough legs 1
/sfgame breakthrough roles red blue

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

每条 `sector spawn add` 都使用管理员当前站立位置，并可重复执行以添加多个随机出生点。方形点使用管理员当前位置作为中心；长方形点先在两个角执行 `point pos1` 和 `point pos2`。未设置高度时点位覆盖该维度全部高度。

### 模式与 sector 指令

```text
/sfgame breakthrough variant <normal|captain>
/sfgame breakthrough legs <1|2>
/sfgame breakthrough roles <进攻阵营> <防守阵营>
/sfgame breakthrough status

/sfgame sector add <sectorID>
/sfgame sector set order <sectorID> <1-16>
/sfgame sector list
/sfgame sector status <sectorID>
/sfgame sector remove <sectorID>
/sfgame sector clear
```

`legs` 表示整场比赛进行几次完整的攻防赛段，不是 sector 数量：

- `/sfgame breakthrough legs 1`：只进行一次攻防。`roles` 指定的进攻方依次进攻全部 sector；攻陷最后一个 sector 时进攻方获胜，任一 sector 超时或进攻票数归零时防守方获胜。比赛结束后不会交换攻守。
- `/sfgame breakthrough legs 2`：双方各进攻一次。第一赛段结束后自动交换攻守并从第一个 sector 重新开始；第二赛段结束后比较双方推进的 sector 数、当前 sector 已占点数、达到该进度的用时和剩余票数，完全相同则平局。

例如 `roles red blue` 配合 `legs 1` 表示整局固定由红队进攻、蓝队防守；配合 `legs 2` 则第一赛段红攻蓝守，第二赛段蓝攻红守。

`roles` 中可使用 `red`、`blue`、`yellow` 或 `green`，代表对应的 SFGame 阵营及其绑定的原版队伍。双方必须不同。sector 的数量由 `/sfgame sector add` 决定，与 `legs` 无关。

突破模式参数速查：

| 参数 | 可选值/范围 | 说明 |
| --- | --- | --- |
| `variant` | `normal` / `captain` | `normal` 没有队长选举；`captain` 只为当前进攻方选举队长，防守方不选举。 |
| `legs` | `1` / `2` | `1` 只进行指定攻守方向；`2` 第一赛段结束后交换攻守，各进行一次完整进攻。 |
| `roles` | 两个不同阵营 | 第一个参数是初始进攻方，第二个是初始防守方；双赛段会自动互换。 |
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

### 票数、时间与胜负

- 每个 sector 默认有 100 张进攻票，任意进攻方有效死亡扣除 1；防守方死亡不扣票。
- 进入下一 sector 时，进攻票数重新补满。
- `timeLimitSeconds` 在突破模式中表示每个 sector 的独立时限，推进时重置。
- 单赛段中，攻陷最后 sector 则进攻方获胜；票数归零或超时则防守方获胜。
- 双赛段结束后依次比较已攻陷 sector 数、当前 sector 已占点数、达到进度所用时间和剩余票数，完全相同则平局。
- 阶段整备期间全员无敌，不能通过开火提前解除保护。
- 突破模式默认死亡重生倒计时为 10 秒。倒计时结束后自动打开位置选择，可选择当前 sector 的队伍出生点，或当前由本队占领的点位；安全点位使用 `inside` 坐标，人数并列或敌方正在中立化/夺取时使用 `nearby` 坐标。按钮点击时会再次校验归属，已经失守的点不能重生。

突破模式规则：

```text
/sfgame rule set attackerTickets <1-10000>
/sfgame rule set timeLimitSeconds <秒>
/sfgame rule set sectorTransitionSeconds <秒>
/sfgame rule set captureTimeSeconds <秒>
/sfgame rule set captureUsePlayerDifference <true|false>
/sfgame rule set captureDifferenceCoefficient <小数>
/sfgame rule set captureMaxMultiplier <倍率>
/sfgame rule set captainVoteSeconds <秒>
/sfgame rule set captainReplacementVoteSeconds <秒>
/sfgame rule set attackerCaptainGlowing <true|false>
/sfgame rule set attackerCaptainCaptureWeight <小数>
/sfgame rule set defenderCaptureWeight <小数>
```

比赛中修改 `attackerTickets` 会立即把当前 sector 的剩余票数设置为新值，后续 sector 也按新值补满。降低时限至本 sector 已用时间以下，会在下一 Tick 判定防守成功。

### 队长变体用法

执行 `/sfgame breakthrough variant captain` 启用队长变体。只有当前进攻方选举队长，防守方不选举队长、没有旗帜、也不使用队长职业。

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

使用 `/sfgame mode select ctf` 选择夺旗模式。CTF 包含三个子模式：`classic`（多队经典夺旗）、`assault`（固定进攻/防守的一攻一守）和 `territory`（前线旗先占点解锁，再夺旗）。CTF 地图沿用当前地图的大厅、队伍出生点和原版 `/team` 绑定；职业配置独立保存在 `config/sfgame/classes/ctf.json`，默认继承 TDM 配置。

### 模式与队伍

```text
/sfgame ctf variant <classic|assault|territory>
/sfgame ctf roles <进攻阵营> <防守阵营>
/sfgame ctf carrier <normal|movement_limited|no_weapons>
/sfgame ctf status
```

`classic` 和 `territory` 支持当前地图启用的 2～4 个阵营；`assault` 必须恰好启用两个阵营，并用 `roles` 指定进攻方与防守方。`normal` 允许完整装备，`movement_limited` 禁止冲刺，`no_weapons` 禁止枪械和近战攻击但保留投掷物。

CTF 模式参数说明：

| 参数 | 可选值/范围 | 说明 |
| --- | --- | --- |
| `variant classic` | 2～4 个启用阵营 | 每队都有家旗；夺取敌旗并带回己方交旗区域得分。己方家旗不在旗座时不能交旗得分。 |
| `variant assault` | 恰好 2 个阵营 | 只允许 `roles` 指定的进攻方夺取防守方目标旗；防守方不能得分，进攻票归零时防守方获胜。 |
| `variant territory` | 2～4 个启用阵营 | 先按占点规则解锁前线旗，再把旗带到己方 depot；原归属队伍可回收并插回原点。 |
| `roles <进攻> <防守>` | 两个不同阵营 | 只在 assault 中使用；classic/territory 不需要设置。 |
| `carrier normal` | — | 持旗者可正常使用职业装备、武器和冲刺。 |
| `carrier movement_limited` | — | 持旗者不能冲刺及使用特殊移动，但仍可使用武器。 |
| `carrier no_weapons` | — | 持旗者禁止 TACZ 枪械和近战攻击，只保留投掷物（待对应 TACZ 枪包提供后启用）。 |
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
/sfgame ctf home set <red|blue|yellow|green> flag
/sfgame ctf home set <red|blue|yellow|green> capture box
/sfgame ctf home set <red|blue|yellow|green> capture square <半径>
/sfgame ctf home set <red|blue|yellow|green> depot
/sfgame ctf home clear <队伍> <flag|capture|depot>
/sfgame ctf home list

/sfgame ctf forward add box <归属阵营> <旗帜ID>
/sfgame ctf forward add square <归属阵营> <旗帜ID> <半径>
/sfgame ctf forward set box <旗帜ID>
/sfgame ctf forward set center <旗帜ID>
/sfgame ctf forward set radius <旗帜ID> <半径>
/sfgame ctf forward set height <旗帜ID> full
/sfgame ctf forward set height <旗帜ID> <minY> <maxY>
/sfgame ctf forward set stand <旗帜ID>
/sfgame ctf forward list
/sfgame ctf forward status <旗帜ID>
/sfgame ctf forward remove <旗帜ID>
/sfgame ctf forward clear
```

`classic` 中每队需要家旗、交旗区域和 depot；己方家旗离开旗座时不能交旗得分。旗帜会在死亡、掉线、离队或换队时掉落，默认 30 秒后自动返回。`territory` 的前线旗初始锁定，敌方先在其点位完成占领才可拾取；带到己方 depot 得 1 分，原归属队伍可在该 depot 回收并插回原点再得 1 分。旗帜在旗座、掉落点和 depot 使用服务端不可见盔甲架承载并直接放置在配置坐标的地面位置，不显示盔甲架轮廓；玩家持旗时旗帜改为装备在头盔栏并使持旗者高亮，交付或掉落后恢复原头盔装备。

### 地图复原与方块白名单

```text
/sfgame pos1
/sfgame pos2
/sfgame ctf build setbox
/sfgame ctf build clear
/sfgame ctf build allow <方块ID>
/sfgame ctf build disallow <方块ID>
/sfgame ctf build allowlist
/sfgame ctf build snapshot save
/sfgame ctf build snapshot restore
/sfgame ctf build snapshot status
/sfgame ctf build snapshot clear
```

快照以 NBT 保存到世界 `data/sfgame/ctf/<地图ID>.nbt`，开赛前、结算和异常停止时自动恢复。只有 build box 内且在 allowlist 中的方块允许比赛中破坏或放置；默认白名单为空。

`ctf build setbox` 使用最近一次 `pos1`/`pos2` 设置复原区域；`allow <方块ID>` 和 `disallow <方块ID>` 使用完整资源 ID（例如 `minecraft:stone`），只影响比赛内该 box 中的方块。`snapshot save` 保存母版，`restore` 立即清空 box 并写回母版，`status` 查看是否存在母版，`clear` 删除母版。启用了可破坏方块但没有母版时，`/sfgame start` 会拒绝开赛。

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

击杀默认奖励 25，带旗回家 100，前线旗插回 50，前线点首次解锁 10；货币只在本局有效。商店配置位于 `config/sfgame/shop/ctf.json`，商品会在服务端检查参赛状态、死亡状态、价格、物品有效性和背包空间。

```text
/sfgame shop list
/sfgame shop buy <商品ID>
/sfgame shop reload
```

`shop buy` 只能由当前参赛且不在死亡倒计时的玩家执行；`<商品ID>` 必须来自当前 `ctf.json`。商品字段为 `id`（唯一 ID）、`name`（菜单名称）、`icon`（菜单图标资源）、`price`（货币价格）、`item`（实际发放物品）、`count`（数量）和可选 `nbt`（物品 NBT 字符串）。每局开始货币归零，死亡后购买物品不会保留。

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

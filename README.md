# SFGame

[![Build](https://github.com/LazyAlienServer/minecraft-sfgame/actions/workflows/build.yml/badge.svg)](https://github.com/LazyAlienServer/minecraft-sfgame/actions/workflows/build.yml)

SFGame 是一个 Forge 1.20.1 的枪战游戏框架 Mod，依赖 TACZ 提供枪械和弹药。当前版本实现团队竞技、占点和突破攻防模式，以及动态规则、原版队伍绑定、职业配装、重生、计分、菜单及 HUD。

## 环境与构建

- Minecraft 1.20.1
- Forge 47.4.20
- Java 17
- TACZ 1.1.8-hotfix

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

未手动选择职业的玩家会在加入大厅或开赛校验时自动选择职业 JSON 中的第一个有效职业（默认是 `assault`）。玩家从旁观状态在大厅点击“加入比赛”后，会立即切换为冒险模式并传送回大厅。

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

/sfgame rules list
/sfgame rules get <规则>
/sfgame rules set <规则> <整数>
/sfgame rules reset

/sfgame class list
/sfgame class validate
/sfgame class reload
/sfgame class set <玩家> <职业ID>
```

规则名称为 `maxPlayers`、`scoreLimit`、`timeLimitSeconds`、`startCountdownSeconds`、`respawnSeconds`、`respawnProtectionSeconds` 和 `resultSeconds`。`resultSeconds` 默认是 20 秒，即比赛结算后等待 20 秒返回大厅。规则及地图坐标保存在世界 SavedData 中，重启后保留。

`/sfgame team set` 和 `/sfgame team remove` 使用多玩家实体参数，支持玩家名以及 `@a`、`@p`、`@r` 等原版选择器。例如：`/sfgame team set @a random`。

地图按照“模式 → 地图”保存。当前内置模式 ID 为 `tdm`、`domination` 和 `breakthrough`，每个模式可以拥有多张地图。`/sfgame spawn setdefault lobby` 将管理员当前位置保存为全局默认大厅；世界首次加载时若尚未配置，会自动使用主世界原版出生点。`/sfgame spawn set lobby` 为当前地图设置覆盖点，`/sfgame spawn clear lobby` 清除覆盖并恢复使用全局默认大厅。团队竞技和占点地图可启用红、蓝、黄、绿中的 2～4 个阵营：某阵营只要至少配置一个出生点，就视为该地图启用该阵营；地图至少需要两个启用阵营。每次执行 `/sfgame spawn set <队伍>` 都会追加出生点，玩家部署时从本队坐标中随机选择。随机分队只会使用当前地图已启用阵营，并优先分配人数最少的阵营。使用 `/sfgame spawn list` 查看带序号坐标，通过 `remove` 或 `clear` 管理。突破攻防地图改用每个 sector 的攻守角色出生点，配置方法见下文。旧版单个红蓝出生点会自动迁移为对应列表中的第 1 个点。比赛进行时禁止修改地图与出生点。

SFGame 自有 ID（地图、模式、sector、点位、职业及职业配置继承 ID）可以字母或数字开头，支持单个字母和单个数字，例如 `a`、`1`、`a1`、`1a`。后续字符可使用字母、数字和下划线；输入的大写字母会统一按小写 ID 处理。枪械、物品和附件等 Minecraft/TACZ 资源 ID 仍遵循其原有的命名空间格式。

## 职业配置

首次运行会保留旧的 `config/sfgame/classes.json`，并为每个模式生成独立职业配置：

```text
config/sfgame/classes/tdm.json
config/sfgame/classes/domination.json
config/sfgame/classes/breakthrough.json
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

子配置中相同 ID 会覆盖父配置，缺失父配置和循环继承会被拒绝。突破模式默认普通职业包括 `assault`、`sniper`、`medic`（医疗兵）、`tank`（坦克）和 `smg_assault`（冲锋枪突击手）；队长职业包括 `heavy_captain` 与 `captain_tank`（坦克·队长加强版）。每套突破职业均配置 TACZ 主枪、近战武器、投掷物和差异化护甲，具体物品可以继续通过 `inventory` 与 `armor` 修改。队长旗帜占用头盔栏，因此队长职业的默认重甲只配置胸甲、护腿和靴子。

修改 JSON 后执行 `/sfgame class reload`。SFGame 会通过 TACZ API 校验枪械、弹药和附件资源；发现无效资源时保留上一份有效配置，并阻止比赛在配装无效时开始。重载不会替换存活玩家的装备，新配置在下一次部署时生效。

职业配置中的 `reserveAmmo` 会装入一个 TACZ 钻石级弹药箱，不再发放散装弹药；弹药箱固定放在背包三行区域左上角（物品栏槽位 `9`）。

通用配置文件 `config/sfgame-common.toml` 中的 `globalHungerLock` 默认为 `true`。启用后，SFGame 模式运行期间所有在线玩家的饥饿值和饱和度均固定为 20。

## 已实现的比赛规则

- 原版 `/team` 是队伍唯一事实来源，比赛内换边会无死亡、无得分地满配装重新部署。
- 未开赛时退出比赛只移除 SFGame 队伍，保持当前位置和当前游戏模式；只有对局开始后退出才进入旁观模式。未开赛时加入队伍不会自动传送大厅。
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

`async` 同时开放所有点；`sync` 每局随机排列点位，一次只开放并显示一个点，切换时会提示“点位 A 已沦陷，请前往占领点位 B”并播放提示音，完成本轮全部点位后结算。点位 ID 支持单个字母或单个数字，输入的大写字母会按同一小写 ID 保存。长方形使用两个角点，正方形使用管理员脚下中心与半径；未设置高度时覆盖全部高度。占点默认每 1 秒为每个已归属活动点增加 1 分。

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
/sfgame point pos1
/sfgame point pos2
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

### Sector 点位指令

```text
/sfgame point pos1
/sfgame point pos2
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
/sfgame rules set attackerTickets <1-10000>
/sfgame rules set timeLimitSeconds <秒>
/sfgame rules set sectorTransitionSeconds <秒>
/sfgame rules set captureTimeSeconds <秒>
/sfgame rules set captureUsePlayerDifference <true|false>
/sfgame rules set captureDifferenceCoefficient <小数>
/sfgame rules set captureMaxMultiplier <倍率>
/sfgame rules set captainVoteSeconds <秒>
/sfgame rules set captainReplacementVoteSeconds <秒>
/sfgame rules set attackerCaptainGlowing <true|false>
/sfgame rules set attackerCaptainCaptureWeight <小数>
/sfgame rules set defenderCaptureWeight <小数>
```

比赛中修改 `attackerTickets` 会立即把当前 sector 的剩余票数设置为新值，后续 sector 也按新值补满。降低时限至本 sector 已用时间以下，会在下一 Tick 判定防守成功。

### 队长变体用法

执行 `/sfgame breakthrough variant captain` 启用队长变体。只有当前进攻方选举队长，防守方不选举队长、没有旗帜、也不使用队长职业。

- `/sfgame start` 后先进入默认 15 秒投票阶段，进攻玩家会自动打开投票菜单。
- 玩家可以投给任意在线进攻队友、自投或弃权；未投票按弃权处理。
- 队长的头盔栏固定装备本方颜色旗帜，并使用 `captainClasses` 中的专属职业；旗帜会覆盖队长职业 JSON 中的头盔配置。
- 进攻方队长默认对全服玩家显示发光轮廓，可用 `attackerCaptainGlowing` 热规则关闭。
- 普通进攻玩家占点权重为 1.0，进攻队长默认 2.0；每名防守玩家默认 1.4。
- 队长死亡后保留身份，重生期间隐藏旗帜并清除发光；重新部署后恢复。掉线、离队或换队会触发默认 10 秒补选，比赛结束时也会清理旗帜与 SFGame 发光状态。
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

推送到 `main` 或向 `main` 提交 Pull Request 时，GitHub Actions 会使用 Java 17 自动执行测试和完整构建，并保存构建产物。每次向 `main` 推送新提交还会自动创建一个可追溯到提交哈希的预发布版本，例如 `v0.1.0-build.3.486d722`。创建并推送语义化版本标签则会创建或覆盖对应的正式 GitHub Release：

```powershell
git tag v0.1.0
git push origin v0.1.0
```

标签 `v1.0.0` 会构建 `sfgame-1.0.0.jar`；包含 `alpha`、`beta` 或 `rc` 的版本会自动作为预发布版本。

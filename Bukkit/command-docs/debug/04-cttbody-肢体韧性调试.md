# 肢体韧性调试指令（chotentech-body）

> **根指令**：`/chotentech-body`（别名：`/cttbody`、`/bodydebug`）
> **权限**：`chotentech.command.bodydebug`（默认：OP）
> **源码**：`Bukkit/src/main/kotlin/io/github/zzzyyylllty/chotentech/command/ChoTenBodyDebugCommand.kt`

## 功能概述

调试《肢体韧性》规格引入的运行时数据：各部位当前/最大韧性、部位状态、肢体效果、水分、
黑色滤镜数值、倒地/休克阶段，以及这些数据汇总出来的血量上限与移动速度惩罚。

写操作的语义是「直接改内存里的档案数据」：

- 改动后**立刻**调用 `syncMaxima` + `syncPenalties`，血上限与移速修饰符当场刷新，
  不需要等下一次 5 tick 心跳。
- 目标为**在线玩家当前档案**（`PlayerData.bodyHealth`）。离线玩家无法调试。
- 所有写操作都会触发一次 `normalize()`，缺失的部位会被补齐。

输出走只读快照 `BodyHealthManager.computePenalties(body)`——
它和真正生效的 `syncPenalties` 共用同一份计算，但**不调用**会推进计时的
`tickDestructionPenalty`，所以反复执行 `show` 不会改变头部/身体的惩罚进度。

## 指令总览

| 完整指令 | 说明 |
|---|---|
| `/cttbody` | 打印指令用法树（`createModernHelper()`） |
| `/cttbody show [target]` | 总览：7 个部位的韧性/状态/效果 + 结算惩罚 |
| `/cttbody toughness set <part> <value>` | 强制设定当前韧性（夹在 `0..max`） |
| `/cttbody toughness damage <part> <value>` | 施加**只扣韧性**的伤害，含溢出扩散 |
| `/cttbody toughness heal <part> <value>` | 恢复韧性（损毁部位会拒绝） |
| `/cttbody toughness get <part>` | 单部位详情 |
| `/cttbody effect add <part> <type> [level] [seconds]` | 施加肢体效果，省略时长=永久 |
| `/cttbody effect remove <part> <type>` | 移除肢体效果 |
| `/cttbody effect clear [part]` | 清空全部部位效果，或只清指定部位 |
| `/cttbody water [target]` | 查看水分 / 连续脱水计时 / 滤镜 |
| `/cttbody water set <value>` | 覆盖水分（夹在 `0..maxWater`） |
| `/cttbody water add <value>` | 增减水分 |
| `/cttbody stage down [target]` | 强制进入倒地阶段 |
| `/cttbody stage shock [target]` | 强制进入休克阶段 |
| `/cttbody stage revive [target]` | 跳过救援进度直接拉起 |
| `/cttbody stage kill [target]` | 强制致死（走正常死亡流程） |
| `/cttbody reset [target]` | 复位韧性与水分、清空全部伤势 |
| `/cttbody reference` | 部位 / 效果 ID 参考表 |

> 省略 `[target]` 时作用于执行者自己；**控制台执行必须显式指定目标**，否则提示后中止。

## 参数

### `part` 部位

`HEAD` `BODY` `STOMACH` `LARM` `RARM` `LLEG` `RLEG`

解析顺序（`partOf`）：

1. 部位 ID，**大小写不敏感**（`head` / `HEAD` / `Head` 等价）；
2. 中文名精确匹配（`头部`、`左腿`…）；
3. ID 唯一前缀（`sto` → `STOMACH`）。

无法确定时**不猜**：直接列出可用值并中止，避免调试指令静默作用到错误部位。

### `type` 效果

取自 `STATUS_EFFECT_TYPES`：

`fracture` `pain` `pain_relief` `penetration` `dizziness` `shock_sequela`
`minor_bleed` `major_bleed` `wound` `tinnitus` `blackout` `tremor`
`dehydration` `severe_dehydration` `flash` `sanity_regen` `sanity_drain`
`stress_resistance` `stress_immunity` `muscle_weakness` `hound_virus`
`immunity` `stamina_regen`

被规则拒绝时会打印具体原因（`rejectReason`），而不是静默失败：

| 情形 | 提示 |
|---|---|
| 四肢以外加 `fracture` | 骨折只允许四肢 |
| 头/身体/胃以外加 `penetration` | 贯穿只允许头部/身体/胃部 |
| 已有大出血再加 `minor_bleed` | 该部位已是大出血，不能被较轻的小出血覆盖 |
| 其他出血类冲突 | 出血/创口类效果被该部位现有状态拒绝 |
| 非头部加头部专属效果 | 该效果只允许出现在头部，请改用 HEAD |

### `value` / `level` / `seconds`

- `value` 为 `decimal`，`toughness set` 与 `water set` 都会被夹到合法区间并在输出里注明「已夹到」。
- `seconds` 省略（或只给 `level`）时写入 `Long.MAX_VALUE`，即**永久**，与死亡/治疗前不会自然消失。
- 给 `seconds` 时按 `seconds × 1000` 毫秒写入，心跳每 5 tick 减 250 ms。

## 指令详解

### `/cttbody show [target]`

一次性打印四行：部位明细、结算惩罚、手臂惩罚、头/身体损毁进度与水/阶段。

部位行格式：`名称 ID 当前/最大 百分比% [状态] | 效果列表`
状态由颜色区分：`<#8FD4A6>`NORMAL / `<#EFDF8F>`DAMAGED / `<#E08A98>`DESTROYED。

效果列表里永久效果不显示时长，限时效果显示剩余秒数。

结算行给出：

- **血量上限**：`头部损毁惩罚 + 身体损毁惩罚 + 贯穿总惩罚 + 休克后遗症 20%`
- **移速**：两条腿的惩罚相加（损毁 20% / 骨折 15% / 疼痛取配置值），止痛会屏蔽骨折与疼痛
- **禁止奔跑**：`BodyHealthManager.sprintBlocked(body)`
- **当前血上限**：`MAX_HEALTH` 属性的实际值，可直接验证上限惩罚是否真的生效
- **手臂**：瞄准 / 精准 / 搜索 / 近战，来自 `armPenalties()`
- **损毁惩罚进度**：头部与身体各自的 `destructionPenalty` 与已累计计时（秒）

示例：

```
/cttbody show
/cttbody show Steve
```

### `/cttbody toughness set <part> <value>`

把当前韧性直接写成 `value`（夹在 `0..maxToughness`）。

- 写 `0` → 状态变 `DESTROYED`，并触发**首次损毁惩罚**（头部/身体立即 -10% 上限）。
- 写 `max` → 状态变 `NORMAL`；若原本是损毁状态，会触发**恢复返还**
  （头部立即返还 15%，身体 10%）。
- 这是**唯一**能把损毁部位复位的手段——`toughness heal` 会像常规治疗一样拒绝损毁部位。

```
/cttbody toughness set STOMACH 20
/cttbody toughness set HEAD 0
```

### `/cttbody toughness damage <part> <value>`

对指定部位施加 `value` 点**韧性伤害**，走完整的 `damageToughness` 扩散逻辑：

- 只扣韧性，**不扣血量**——用于单独观察打穿与扩散规则。
- 超出剩余韧性的部分按**来源部位倍率**扩散（头 2× / 胃 1.4× / 身体·双臂 1× / 双腿 0.6×），
  均分给所有未损毁部位；接收方也被打穿时继续按接收方倍率扩散。
- 输出会逐条列出**实际发生变化**的部位及其前后值与新状态；所有部位都已损毁时明确提示
  「所有部位均已损毁，本次伤害无处可去」。

```
/cttbody toughness damage HEAD 10      # 头部只剩 5 时，溢出 5×2=10 均分给其余 6 个部位
/cttbody toughness damage LLEG 100     # 观察双腿 0.6× 的传播衰减
```

### `/cttbody toughness heal <part> <value>`

按 `BodyPart.heal()` 恢复韧性，上限为 `maxToughness`。

损毁部位会被拒绝并提示改用 `toughness set`——这与规格「损毁部位不能接受常规治疗」一致，
调试指令不偷偷绕过损毁规则。

### `/cttbody toughness get <part>`

单部位详情：韧性、状态、**扩散倍率**，以及每条效果的等级与剩余时长。

### `/cttbody effect add <part> <type> [level] [seconds]`

四种调用形态：

```
/cttbody effect add HEAD dizziness              # Lv.1，永久
/cttbody effect add LARM fracture 2             # Lv.2，永久
/cttbody effect add BODY major_bleed 1 300      # Lv.1，300 秒
/cttbody effect add HEAD flash 1 60 Steve
```

同部位同类效果不会新增重复条目：`applyEffect` 取 `max(旧等级, 新等级)` 与
`max(旧剩余, 新时长)`，即**只会变强或变长，不会被较弱的效果覆盖**。

### `/cttbody effect remove <part> <type>`

移除指定部位的效果并刷新惩罚（例如移除 `penetration` 会让贯穿的血上限惩罚立刻下降）。

### `/cttbody effect clear [part]`

不带参数清空**全部 7 个部位**的效果，输出被清掉的条数；带 `part` 只清该部位。

移除 `pain_relief` 会让骨折/疼痛限制重新生效；清除 `shock_sequela` 会返还 20% 血量上限。

### `/cttbody water [target]` / `water set` / `water add`

`water` 打印当前水分、最大水分、百分比、**连续脱水计时**（秒）与黑色滤镜数值。

`water set` 覆盖、`water add` 增减，都会：

- 夹到 `0..maxWater`；
- 结果为正数时**立刻**清掉 `dehydration` / `severe_dehydration` 并重置 `dehydrationTicks`
  （与 `ChoTenDataHelper.setWater` 行为一致）。

```
/cttbody water set 0        # 立即开始脱水计时：2 点真实伤害 / 2 秒
/cttbody water set 0 ; 等 >180 秒 ; /cttbody water   # 观察是否已转严重脱水
```

### `/cttbody stage down|shock|revive|kill [target]`

| 子指令 | 实际调用 | 说明 |
|---|---|---|
| `down` | `BleedoutManager.enterBleedout` | 已处于倒地/休克时直接提示，不重复进入 |
| `shock` | `BleedoutManager.debugEnterShock` | 未倒地时先补一次 `enterBleedout` 再转休克 |
| `revive` | `BleedoutManager.reviveDowned` | 跳过 5 秒救援进度，从休克被拉起会获得 `shock_sequela` |
| `kill` | `BleedoutManager.forceDeath` | 走正常 `PlayerDeathEvent`，伤势与水分会被重置 |

`stage down` 在 `config.yml` 的 `bleedout.enabled: false` 时会失败并提示检查配置。

> `debugEnterShock` 是专为调试新增的入口：`enterShock` 原本是私有的，直接复制一份
> 状态切换逻辑会与 tick 循环里的计时器脱节，所以改为在 `BleedoutManager` 内部暴露一个
> 复用同一状态机的函数。

### `/cttbody reset [target]`

`BodyHealthManager.reset(player)`：重建 `PlayerBodyHealth`（韧性/水分回到各最大值）、
清空全身效果、移除血量与移速修饰符、还原假光源。等价于「死亡后的状态」，但玩家不会真的死。

执行完会自动接一次 `show`，方便确认复位结果。

### `/cttbody reference`

打印部位表（默认上限 / 扩散倍率 / 治疗优先级）与全部效果 ID，省去翻源码或 yml。

## 与其他指令的关系

- `/cttdebug`（`chotentech-debug`）：通用数据倾倒，本指令的 `show` 是它的肢体专项替代。
- `/atb`（`chotenattribute-debug`）：查各部位**最大韧性属性**（`toughness_head_max` 等）。
  注意 `syncMaxima` 每 5 tick 会把 `maxToughness` 同步成属性值，所以想让最大韧性长期变化
  应改属性，而不是反复用 `toughness set`。
- `/cttadmin`：`revive` 子指令与本指令的 `stage revive` 等价。

## 已知限制

| 项 | 说明 |
|---|---|
| 离线玩家 | 无法调试：所有路径都通过 `Bukkit.getPlayerExact` 取在线玩家 |
| 最大韧性覆盖 | `toughness set` 只改当前值；下一次 `syncMaxima` 不会覆盖它（同步的是 max），但 `maxToughness` 本身由属性驱动 |
| 晕眩假光源 | 依赖心跳 `visuals()`，改动后最多 5 tick（约 250 ms）体现 |
| 黑色滤镜 | 只读展示；实际数值由 `visuals()` 按 `body-health.yml` 的 `filter.*` 曲线计算，无法直接设定 |
| 疼痛自动触发 | 规格留待后续配置，当前只能由本指令手动施加 |
| 单元测试覆盖 | 本指令的覆盖范围是 `computePenalties` 纯计算部分；指令本体需进服验证 |

## 验证

```powershell
./gradlew.bat test -x taboolibMainTask --offline --console=plain --no-daemon '-Dorg.gradle.jvmargs=-Xmx3g -XX:+UseParallelGC -XX:-UseJVMCICompiler'
```

> 本机若出现 `Connection to the Kotlin daemon has been unexpectedly lost`，加 `--no-daemon`
> 并放宽堆内存即可（GraalVM 25.0.2 + JVMCI 的本地编译器问题）。

`BodyHealthTest` 里针对本指令依赖的纯计算新增了两个用例：
`penalty preview` 系列覆盖「损毁 + 贯穿总惩罚 + 后遗症」相加，以及「预览不推进计时器」。

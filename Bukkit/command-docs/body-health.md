# 肢体韧性与医疗

`PlayerData.bodyHealth: PlayerBodyHealth` 实现 `BodyHealth`，部位存储为 `Map<String, BodyPart>`。
部位 ID 为 `HEAD/BODY/STOMACH/LARM/RARM/LLEG/RLEG`，状态 getter 返回字符串 `NORMAL/DAMAGED/DESTROYED`。
默认最大韧性为 45/75/40/50/50/50/50，总计 360。零韧性即损毁。

## 伤害与效果

受击先结算护甲和现有减伤，再使用命中前的韧性百分比抵扣血量，最高 75%；吸收血量在韧性之后消耗。
部位承受未抵扣的伤害数值作为韧性消耗。第一次打穿的溢出也会立即扩散。
扩散按来源部位倍率均分给未损毁部位，接收部位再被打穿时继续扩散；只扣韧性，不重复扣玩家血量。
所有部位损毁时停止分摊剩余韧性伤害，玩家仍按血量和倒地流程处理。
枪械使用弹道命中位置，近战使用攻击者视线与目标包围盒交点。
环境伤害权重由 `body-health.yml` 的 `damage-distribution` 指定并归一化；未指定时伤害分配给身体。
`bypass-causes` 配置绕过韧性的原因。溺水默认全到头部，摔落到双腿，爆炸均分七部位。

请在 Bukkit 主线程调用接口：

```kotlin
val helper = ChoTenDataHelper(player)
helper.addBodyStatusEffect("LARM", FRACTURE, 1, 60_000)
helper.addBodyStatusEffect("BODY", MAJOR_BLEED) // 默认永久，直到治疗/死亡
helper.removeBodyStatusEffect("LARM", FRACTURE)
helper.bandageBodyPart("BODY") // 大出血→240秒创口；小出血→移除
helper.addWater(25.0)
helper.setWater(100.0)
```

运维/调试可在游戏内用 `/cttbody`（`chotentech-body`）直接查看与改写这些数据，
详见 [`debug/04-cttbody-肢体韧性调试.md`](debug/04-cttbody-肢体韧性调试.md)。

同部位同类效果不重复。骨折仅接受四肢；贯穿仅接受头、身体、胃；其余限制见 `HEAD_ONLY_EFFECTS`。
旧版全身效果在加载后转到头部，旧版全身骨折映射为双腿骨折。
小出血、大出血、创口互斥；大出血不会被较轻的小出血覆盖。
出血每 5 秒只扣携带该效果的部位，绝不向健康部位补加出血效果；部位溢出的部分才按扩散倍率分摊。
创口在部位直接受击超过 10 点时重新变成大出血，跳跃/疾跑可能变成小出血。
三种现有绷带按出血严重程度及部位优先级选择一个未损毁部位包扎。

本次不自动产生随机伤势，除水分产生的脱水、休克救援后遗症和已定义的创口转化。
头/身体损毁、肢体效果、水分、倒地阶段及剩余计时按档案保存；离线暂停计时。
死亡时清空全部伤势，恢复各部位最大韧性和最大水分；属性修饰会在下一次同步应用。
手术可以恢复损毁部位（见下节），常规医疗仍然不会。

## 手术

配置文件 `medkit/surgery.yml`，按物品 ID 索引；物品带上 `sertraline_data.durability` 后右键即进入手术流程。
手术是**唯一**能把 `DESTROYED` 部位拉回来的手段，一次只能处理一个部位。

```yaml
knife_surgical:
  durabilitycost: 1        # 每次有效治疗的固定耐久
  cost: {food: 10, san: 0, water: 10}
  time: {pre: 3, per: 10, post: 1.5}
  cancellable: false       # per 阶段开始后不可取消
  moveable: false          # per 阶段不可移动
  jumpable: false          # per 阶段不可跳跃
  toughness: 10            # 也可以是 "10%"，按该部位最大韧性折算
  removedebuff: {}         # 移除对应 debuff 的独立耐久
```

每轮结算顺序：① 移除该部位可移除的 debuff（各扣自己的独立耐久）→ ② 恢复韧性
（`toughness` 绝对量或最大韧性的百分比，损毁部位直接赋值而非调用 `heal()`）→
③ 本轮确有工作时扣一次 `durabilitycost` 与 `cost.food/san/water`。

`pre` 结束后**立即**结算第一轮，之后每 `per` 秒一轮（与医疗包一致），
因此一轮 `pre + (轮数-1) × per`。损毁部位韧性归零但 `maxToughness` 保留，
所以 `toughness: 10` 的刀要把 50 上限的腿拉回来需要 5 轮。

`per` 阶段由 `cancellable/moveable/jumpable` 三项分别控制取消、水平位移与跳跃；
受击只有在 `cancellable: true` 时才会中止手术。切换物品在手术全程被拦截。
`removedebuff` 里负担不起的条目会让手术中止，**不会**绕过它继续修韧性。
`toughness` 与 `removedebuff` 同时为空时配置视为无内容，右键直接提示。

## 已知未实现项（对照规格）

以下规格条目在当前实现中**没有**对应代码，需后续补齐：

| 规格条目 | 现状 |
| --- | --- |
| 运行期创口 → 出血转化的概率判定 | 跳跃/疾跑时判定存在；但仅在 `second` 周期内跑，且 `MAX_VALUE` 永久创口不会到期 |
| 手臂/腿部**疼痛**造成的禁止奔跑与减速 | `sprintBlocked` 与移动速度计算均已包含，但疼痛**不会被任何代码自动施加**（规格留待后续配置） |
| 头部/身体/胃部疼痛的额外影响 | 未实现（规格留待后续规则） |
| 肢体效果随时间衰减 | 仅按 `remainingDuration` 倒计时；`Long.MAX_VALUE` 即永久 |
| 黑色滤镜的外部绘制 | 本插件只产出 `%chotentech_blackfilter%`，由外部插件绘制 |
| 假光源/滤镜/Dialog/救援的在线验证 | 单元测试不覆盖，仍需进服验证 |
| `bleedout.crawl-radius` | 配置项存在但代码未读取，倒地移动半径不受限制 |
| 手术的在线验证 | 位移锁定、跳跃拦截、Dialog 排版需进服验证 |

## 属性

各部位最大韧性属性：`toughness_head_max`、`toughness_body_max`、`toughness_stomach_max`、
`toughness_larm_max`、`toughness_rarm_max`、`toughness_lleg_max`、`toughness_rleg_max`。
最大水分属性为 `water_max`。最大值变化保持当前百分比；损毁部位仍为零。
内伤/外伤字段、属性、命令、药品效果及占位符已经移除，旧存档的对应 JSON 字段被忽略。
头部损毁立即扣 10% 血量上限，每 5 秒增加 1%，总计最多 30%。恢复时立即返还 15%，剩余每 4 秒返还 1%。
身体损毁立即扣 10%，每 20 秒增加 1%，400 秒达到 30%。恢复时立即返还 10%，剩余每 10 秒返还 1%。
头部、身体、贯穿和休克后遗症的血量上限惩罚相加。
止痛免疫骨折效果和腿伤/疼痛的疾跑限制，损毁的结构惩罚与运动造成的韧性伤害仍然生效。
未详细指定的疼痛参数采用可配置默认值：腿部减速 10% 并限制疾跑，手臂额外瞄准消耗和精准度惩罚各 10%。

## 医疗包

```yaml
akacandy_medkit:
  maxheal: 200
  healspeed: 25
  healrate: 1
  hpleast: 85
  time: {pre: 3, per: 1, post: 1.5}
  cost: {food: 0, san: 0}
  removedebuff:
    minor_bleed: 50
    major_bleed: 180
    fracture: 50
```

右键打开 Dialog。每个部位有两个按钮，外加快速治疗与仅回血：

- `头部 45/45 正常` —— 只做 ①②：移除伤势、恢复韧性。
- `头部 45/45 正常 ＋血量` —— ①②③ 连做：该部位韧性回满后，用当轮剩余额度继续恢复血量。
- `快速治疗受损部位` —— 按分数选一个未损毁部位，只做 ①②。
- `快速治疗，随后恢复血量` —— 同上，再执行 ③。
- `仅恢复血量（需全身韧性>85%）` —— 跳过 ①②，只做 ③。

`＋血量` 与「仅恢复血量」按钮只在 `hpleast >= 0` 时出现；不满足门槛时按下会提示具体原因
（未过门槛 / 血量已满 / 该包不支持回血）。

快捷部位评分为缺失韧性百分比×部位优先级，排除损毁部位。
每轮先按配置顺序移除所选部位 debuff，耐久不足时停止；移除 debuff 独立扣耐久，不占 `maxheal/healspeed`。
随后韧性和血量共同使用 `healspeed` 每轮额度、`maxheal` 单次额度，恢复每点消耗 `healrate` 耐久。
血量治疗必须主动选择，并在韧性治疗后满足全身韧性百分比严格大于 `hpleast`；`-1` 禁止治疗血量。
韧性优先恢复所选部位，满后才恢复血量。小数耐久精确保留。
`maxheal` 是「韧性与血量总量」：`BodyTreatment.Result.treated` 已经包含血量部分，
累加额度和判断上限时不要再叠加 `hpHealed`。

## 水分、休克和外部显示

水分默认 100，每 5 秒判断：未移动不扣，走路扣 0.5，疾跑扣 1。胃部损毁额外每 5 秒扣 2.5 饱食度、1 水分。
水分为零时每 2 秒对血量造成 2 点真实伤害；持续超过 180 秒后改为每 5 秒 4 点。
严重脱水且头/身体损毁时直接死亡。水分回正立即重置脱水计时。
饮品可使用 consumable 的 `effect: water`，支持 `add/set/percent`，数值由物品配置指定。

倒地默认 60 秒，可缓慢移动、可受击，每次受击默认扣 10 秒；结束后进入 120 秒休克。
休克禁止位移，免疫玩家、怪物、环境及持续伤害。救援默认 5 秒，救起恢复 20% 血量。
救援期间暂停倒计时但不重置；两个阶段都可按住 Shift 放弃。休克获救后头部获得永久后遗症，降低 20% 血量上限。
后遗症通过效果移除接口或死亡清除。主配置 `bleedout.shock-duration` 控制休克时长。

| 占位符 | 内容 |
| --- | --- |
| `%chotentech_toughness%` | 当前总韧性÷最大总韧性×100，getter 计算 |
| `%chotentech_toughness_HEAD%` | 对应部位当前韧性，部位 ID 不区分大小写 |
| `%chotentech_toughness_HEAD_max%` | 对应部位最大韧性 |
| `%chotentech_water%` | 当前水分 |
| `%chotentech_water_max%` | 最大水分 |
| `%chotentech_water_percent%` | 水分百分比 |
| `%chotentech_blackfilter%` | 0–50 的黑色滤镜内部值 |
| `%chotentech_bodyeffects_HEAD%` | 对应部位效果；其他原版效果显示在头部 |

黑色滤镜按余弦曲线从 0→50→0，随机间隔后再次出现；本插件不绘制滤镜。
晕眩使用仅发给受影响玩家的 LIGHT 方块更新，亮度值 15；动态刷新并还原真实方块，不修改世界。
耳鸣音效、滤镜周期、颤动和创口概率均在 `body-health.yml` 配置。
LithiumCarbon 搜刮耗时通过 `ItemSearchStartEvent.searchTime` 直接调整，双臂惩罚相加。
编译依赖 `libs/LithiumCarbon-common-1.5.6-api.jar`，源自本地 LithiumCarbon common 模块的 API 构建产物；不打包进插件。

## 验证

```powershell
./gradlew.bat test -x taboolibMainTask --offline --console=plain '-Dorg.gradle.jvmargs=-Xmx2g -XX:+UseParallelGC -XX:-UseJVMCICompiler'
```

禁用 JVMCI 只用于避开本机 GraalVM 25.0.2 的本地编译器崩溃；未修改项目 JVM 配置。
单元测试覆盖韧性抵扣、首次打穿与递归扩散、损毁拒绝治疗、属性百分比、效果限制、双臂叠加、头身体惩罚恢复、医疗额度和 `hpleast`。
客户端假光源、滤镜对接、Dialog 与在线救援仍需进服验证。

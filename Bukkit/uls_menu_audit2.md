# 菜单插件（服务端）审计报告

审计对象：`E:\ChoTenTechbk\Bukkit\src\main\kotlin\io\github\zzzyyylllty\chotentech\client\menu\`
及相关调用方 `client\ULSClientManager.kt`、`ChoTenTech.kt`、`client\menu\ULSMenuCommand.kt`。
审计时间：本次会话。审计者：子代理 `e2593412-64d3-429b-94ab-322198bc0728`。

**说明**：本报告在审计期间代码被实时修改，行号以报告落盘时的文件为准。

---

## FINDING A（P1，可见故障）—— Kotlin 隐式接收者泄漏，嵌套容器内声明的子元素全部挂到根上

**位置**：`client\menu\ULSMenuElement.kt` 的 `Builder`（原 `:121-200`）、`client\menu\ULSMenus.kt` 的
`ULSMenuRootScope`（`:130-189`）、调用点 `client\menu\ULSDemoMenu.kt:174-178` 与 `:189-195`。

**机制**：`ULSMenuRootScope.container(...)` / `scrollArea(...)` 的 `block` 参数类型是
`ULSMenuElement.Builder.() -> Unit`。`Builder` 上**没有** `button` / `divider` / `progress` 成员，
于是 Kotlin 的隐式接收者链继续向外查找，命中 `ULSMenuRootScope.button(...)` —— 而那个函数体写死了
`rootBuilder.child(b)`。

**后果**：在 `scrollArea { button("行 1", ...) }` 里写的按钮**进了根，滚动容器一个子元素都没有**。
编译通过、无错误、无警告。运行时表现为：滚动容器空着（`layoutNested` 对 `children.isEmpty()`
直接 return），元素出现在菜单底部，滚动条没内容可滚。

**更隐蔽的变体**：`Builder.text(value)` 存在（写 `"text"` 属性），所以 `text("A")` 不外泄，
而是被解释成「给容器设 text 属性」—— 同样不报错，同样不是调用者想要的。

**根因**：嵌套 DSL 的接收者泄漏。签名完全合法，IDE 不会提示。

**修复**：给 `Builder` 加同名成员（成员优先于外层隐式接收者）：
`text(value, width?, height?)`、`button(label, actionId, width=120)`、
`divider(width=240)`、`progress(variable, width=160)`、`container(type, columns?, width?, height?, block)`。
原属性设置器 `text(value)` 改名 `rawText(value)`，避免新成员与它互相递归
（`selfText` 里若写 `of(TEXT).text(value)`，单参数会命中新成员 → 无限递归）。
同时修正三处构造器调用点：`ULSMenus.kt:131`、`ULSMenuElement.kt` companion 的 `text(...)`
与 `button(...)` —— 它们原先是 `.text(value)`，重构后会变成「给自己塞一个自己」。

**验收**：`.\gradlew.bat --no-daemon compileKotlin` → `BUILD SUCCESSFUL in 19s`，
仅剩两条警告：`ULSMenuElement.kt:372 This extension is shadowed by a member`（预期，见代码注释）、
`ULSDemoMenu.kt:129 'var maxHealth: Double' is deprecated`（Paper API 弃用，无害）。

---

## FINDING B（P2）—— `ULSMenuManager.open` 编码失败时留下陈旧 `openMenus` 条目

**位置**：`client\menu\ULSMenuManager.kt` 的 `open(...)`。

**机制**：先执行 `openMenus[player.uniqueId] = menu.menuId`，再调用 `ULSMenuCodec.encodeOpen(menu)`。
而 `encodeOpen` 对超过 `ULSProtocol.MAX_MENU_ELEMENTS`(512) 个元素或超过
`ULSProtocol.MAX_MENU_DEPTH`(16) 层深度的定义会抛 `IllegalArgumentException`。异常未被捕获。

**后果**：玩家什么都收不到（连经典轨降级都没有，因为降级判定在编码之后），
但 `openMenus` 里留下了陈旧条目。之后对该玩家的 `updateData` 会通过校验并
把 `menu_data` 推给一个从未打开过菜单的客户端，且返回 `true`。管理员看到的诊断是健康的。

---

## FINDING C（P1，可见故障）—— `ULSMenuManager.close` 对原版玩家不关界面却清空了 `openMenus`

**位置**：`client\menu\ULSMenuManager.kt` 的 `close(...)`：
`val menuId = openMenus.remove(player.uniqueId) ?: return` 之后
`if (!ULSClientManager.isUsingMod(player)) return`。

**机制**：`openMenus` 已被 `remove` 清空，而经典轨点击要经过
`executeAction` 里的 `openMenus` 校验（`ULSMenuManager.kt:174` 起）。

**后果**：原版玩家的箱子界面**不会被关掉**，但它之后点任何格子都被静默丢弃
（拒绝路径全走 `devLog`，生产默认关闭，管理员看不到任何东西）。
触发场景：`demo.close` 动作。

---

## FINDING D（P2）—— 两条轨的判定集不一致，注释与实现不符

**位置**：`client\menu\ULSMenuClassicRail.kt` 的 `handleClick(...)` 与
`client\menu\ULSMenuManager.kt` 的 `executeAction(...)`。

**机制**：经典轨 `handleClick` 只查动作白名单，不查 `openMenus`；增强轨两者都查。
旁边的注释声称「与增强轨走完全相同的执行路径」，与实现不符。

**后果**：目前无直接可利用面（经典轨只可能在玩家开着该界面时被点击），
但注释会误导后来者把安全判定加错地方。

---

## 判定 CLEAN（勿重复怀疑）

- **`ULSMenuActionRegistry` 全部访问器 `@Synchronized`**：动作注册/注销/查找在同一个
  单例监视器上串行，Netty 线程与主线程并发访问安全。
- **`ULSMenuClassicRail` 的扁平化遍历不会丢嵌套内容**：它用 `menu.flatten()`（前序 DFS），
  GRID/SCROLL/ROOT 作为结构被跳过，但它们的子元素仍在扁平列表里靠后出现，
  `index` 与 `setItem` 在同一非空判定下对齐，**嵌套内容不会丢失、不会错位**。
- **无刷物品 / 无物品移动**：点击在任何 early return 之前被无条件取消，拖拽同样被取消。
- **`rawSlot` 正确且有界**：按引用身份排除玩家自己的背包，超出 54 的 rawSlot 早退。
- **协议编码与模组解码一致**：NOT 条件插件写 `count=1`、模组读 count 取 `children.get(0)`；
  ATOM 字段顺序与宽度一致；flag 位 1/2/4 一致；未知 typeId 两侧都读完整结构。
- **`sendToClient` 确实切主线程**（`Bukkit.getServer().isPrimaryThread` 判定 + `runTask`），
  `Player#sendPluginMessage` 不在 Netty 线程上被调用。
- **所有攻击者可控长度在分配前有界**：`readBody`、`drainBytes`、`modSize`、`readString`、
  `readVarInt`（超 35 位抛）。

---

## 本报告的局限

- 审计期间 `ULSMenuCodec.kt` / `ULSMenuManager.kt` / `ULSMenuClassicRail.kt` 被改动过，
  部分结论按改动后的版本复核，行号可能漂移。
- 未做运行时验证（无实机联调），全部结论来自静态阅读。
- FINDING A 的修复由父代理实施；FINDING B/C/D 未修。

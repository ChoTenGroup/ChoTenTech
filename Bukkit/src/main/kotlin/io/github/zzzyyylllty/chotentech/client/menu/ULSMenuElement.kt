package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSProtocol

/**
 * 菜单元素的类型。
 *
 * **必须与模组侧 `cn.gtemc.ulshelper.menu.MenuElementType` 的 `id` 逐字一致。**
 * 任何一侧增删都会让另一侧的解析错位——客户端遇到不认识的 typeId 会
 * 跳过该元素（连同子树），这是一次静默的功能缺失，不会报错。
 */
enum class ULSMenuElementType(val id: String, val capability: String?) {
    ROOT("root", null),
    TEXT("text", ULSProtocol.CAP_TEXT),
    SHAPE("shape", ULSProtocol.CAP_SHAPE),
    BUTTON("button", ULSProtocol.CAP_BUTTON),
    IMAGE("image", ULSProtocol.CAP_IMAGE),
    GRID("grid", ULSProtocol.CAP_GRID),
    CHECKBOX("checkbox", ULSProtocol.CAP_CHECKBOX),
    TEXTFIELD("textfield", ULSProtocol.CAP_TEXTFIELD),
    SLOT("slot", ULSProtocol.CAP_SLOT),
    SCROLL("scroll", ULSProtocol.CAP_SCROLL),
    PROGRESS("progress", ULSProtocol.CAP_PROGRESS),

    /**
     * 未知类型占位。
     *
     * 客户端解析时遇到不认识的 typeId 会落到这里：**结构照常读完**
     * （字节流是顺序的，读错位置后面全错），渲染时跳过它连同子树。
     * 这样服务端新增组件后，老客户端只是缺那一块，而不是整个菜单打不开。
     *
     * 这个值**永远不应该被下发**——它只是解析侧的兜底。
     */
    UNKNOWN("__unknown", null);

    companion object {
        private val BY_ID: Map<String, ULSMenuElementType> =
            entries.filter { it != UNKNOWN }.associateBy { it.id }

        /**
         * 按协议 typeId 查找。
         *
         * 未知返回 [UNKNOWN] 而不是 null——调用方想跳过就必须显式判断，
         * 而不是不小心把 null 当成「没有子元素」继续往下走。
         */
        fun byId(id: String): ULSMenuElementType = BY_ID[id] ?: UNKNOWN
    }
}

/**
 * 条件运算符。与模组侧 `MenuCondition.Op` 的符号一一对应。
 */
enum class ULSMenuOp(val symbol: String) {
    EQ("=="),
    NE("!="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<=");
}

/**
 * 一个菜单条件。
 *
 * 服务端构造它只是为了让**客户端**知道怎么显示；服务端自己该不该允许
 * 某次操作，必须由服务端在处理 [ULSActionPacket] 时独立重新判定。
 * 见 `plan.md` 的信任边界一节。
 */
sealed class ULSMenuCondition {

    /** 单个比较：variable op value。 */
    data class Atom(
        val variable: String,
        val op: ULSMenuOp,
        val value: String,
        /** value 是变量名而不是字面量。 */
        val valueIsVariable: Boolean = false,
    ) : ULSMenuCondition()

    /** 所有子条件都成立。空列表视为成立。 */
    data class All(val children: List<ULSMenuCondition>) : ULSMenuCondition()

    /** 任一子条件成立。空列表视为不成立。 */
    data class Any(val children: List<ULSMenuCondition>) : ULSMenuCondition()

    /** 取反。 */
    data class Not(val child: ULSMenuCondition) : ULSMenuCondition()

    companion object {
        fun eq(variable: String, value: String) = Atom(variable, ULSMenuOp.EQ, value)
        fun ne(variable: String, value: String) = Atom(variable, ULSMenuOp.NE, value)
        fun gt(variable: String, value: String) = Atom(variable, ULSMenuOp.GT, value)
        fun gte(variable: String, value: String) = Atom(variable, ULSMenuOp.GTE, value)
        fun lt(variable: String, value: String) = Atom(variable, ULSMenuOp.LT, value)
        fun lte(variable: String, value: String) = Atom(variable, ULSMenuOp.LTE, value)
        fun all(vararg children: ULSMenuCondition) = All(children.toList())
        fun any(vararg children: ULSMenuCondition) = Any(children.toList())
        fun not(child: ULSMenuCondition) = Not(child)
    }
}

/**
 * 一个菜单元素。
 *
 * 用属性表（[props]）而不是每种组件一个 class，理由与客户端侧相同：
 * 组件会持续增加，而属性表让「服务端下发了一个客户端还不认识的属性」
 * 变成无害的忽略，而不是解析失败。
 *
 * **所有属性值都是字符串**，客户端在需要时自行解析。这样协议层不必
 * 关心类型，服务端也不用手写类型标签。
 */
class ULSMenuElement private constructor(
    val type: ULSMenuElementType,
    val props: Map<String, String>,
    val children: List<ULSMenuElement>,
    val actionId: String?,
    val visibleWhen: ULSMenuCondition?,
    val enabledWhen: ULSMenuCondition?,
) {

    class Builder internal constructor(private val type: ULSMenuElementType) {

        private val props = linkedMapOf<String, String>()
        private val children = mutableListOf<ULSMenuElement>()
        private var actionId: String? = null
        private var visibleWhen: ULSMenuCondition? = null
        private var enabledWhen: ULSMenuCondition? = null

        /** 写入一个属性。超长会被裁剪到协议上限。 */
        fun prop(key: String, value: Any?): Builder = apply {
            if (key.isEmpty() || key.length > ULSProtocol.MAX_PROPERTY_LEN) return@apply
            if (props.size >= ULSProtocol.MAX_ELEMENT_PROPERTIES && !props.containsKey(key)) {
                return@apply
            }
            val text = value?.toString() ?: ""
            props[key] = text.take(ULSProtocol.MAX_TEXT_LEN)
        }

        fun props(vararg pairs: Pair<String, Any?>): Builder = apply {
            for ((k, v) in pairs) prop(k, v)
        }

        /**
         * 设置本元素自己的显示文本（**属性**，不是子元素）。
         *
         * 注意别和下面那个同名的 `text(value, width, height)`（加一个
         * TEXT 子元素）搞混——那是本次改动特意引入的区分：
         * 单参数调用现在加子元素，因为嵌套容器里「加一个文本」远比
         * 「给容器设 text 属性」常见，而后者写的人本来就知道自己在做什么。
         *
         * 内部需要无条件地设属性，所以留了这个名字明确的入口；
         * 构造器里一律走它，避免重载解析回头命中自己。
         */
        fun rawText(value: String): Builder = prop("text", value)

        /** 元素宽度（逻辑像素）。 */
        fun width(value: Int): Builder = prop("width", value)

        /** 元素高度（逻辑像素）。 */
        fun height(value: Int): Builder = prop("height", value)

        /** 是否在此元素之前换行（仅对同层兄弟有效）。 */
        fun newline(value: Boolean = true): Builder = prop("newline", value)

        fun background(color: String): Builder = prop("background", color)

        fun color(color: String): Builder = prop("color", color)

        fun tooltip(vararg lines: String): Builder = prop("tooltip", lines.joinToString("\n"))

        /**
         * 绑定一个动作 ID。
         *
         * 这个字符串会原样回到服务端，因此**必须**在
         * [ULSMenuActionRegistry] 里登记，否则服务端会拒绝这次操作。
         */
        fun action(id: String): Builder = apply {
            require(id.length <= ULSProtocol.MAX_ACTION_ID_LEN) {
                "动作 ID 过长: $id"
            }
            actionId = id
        }

        /** 显示条件。不成立时客户端不渲染这个元素。 */
        fun visibleWhen(condition: ULSMenuCondition): Builder = apply {
            visibleWhen = condition
        }

        /** 可交互条件。不成立时客户端把按钮画成灰的。 */
        fun enabledWhen(condition: ULSMenuCondition): Builder = apply {
            enabledWhen = condition
        }

        fun child(element: ULSMenuElement): Builder = apply { children.add(element) }

        fun child(builder: Builder): Builder = child(builder.build())

        fun children(vararg elements: ULSMenuElement): Builder = apply {
            elements.forEach { children.add(it) }
        }

        // ------------------------------------------------------------------
        // 在容器内部创建子元素
        // ------------------------------------------------------------------
        //
        // 这几个是 **`Builder` 自己的** 成员，不是 `ULSMenuRootScope` 的。
        //
        // ## 为什么必须在这里也来一份
        //
        // Kotlin 的嵌套 lambda 会把外层的隐式接收者一并带进来。写
        //
        //     scrollArea(width = 240, height = 60) {
        //         button("行 1", "a", 240)     // ← 期待它进滚动容器
        //     }
        //
        // 时，内层 lambda 的接收者是 `Builder`，而 `Builder` 上**没有**
        // `button`，于是 Kotlin 顺着隐式接收者往外找，命中了
        // `ULSMenuRootScope.button` —— 那个函数写死了
        // `rootBuilder.child(b)`。
        //
        // 结果是：**按钮进了根，滚动容器一个子元素都没有**。
        // 编译通过、不报错、不警告，只是那个容器空着、该出现的元素
        // 跑到了菜单底部。这类「接收者泄漏」是嵌套 DSL 最典型的翻车方式，
        // 而且因为签名完全合法，IDE 也不会提示。
        //
        // 修法不是在每个调用点加 `child(...)`（那要靠人记住），
        // 而是让内层接收者**自己有**同名成员：内层优先，永远不会外泄。
        // 顺带一提，`text` 的情况更隐蔽——`Builder.text(value)` 存在
        // （它写的是 "text" 属性），所以 `text("A")` 不会外泄，而是被
        // 解释成「给容器设一个 text 属性」，同样不报错。这里提供
        // `text(value, width, height)` 重载并把 `text(value)` 的行为
        // 明确成「加一个 TEXT 子元素」，两种情况就都有正确答案了。
        //
        // 注意重载解析：两个参数的 `text("A", 100)` 只会命中这里，
        // 因为单参数的 `Builder.text(value)` 不匹配两个实参。
        // 单参数的 `text("A")` 则**优先命中下面这个成员**
        // （同层更具体的成员优于 property 语义的那一个），
        // 于是「加一个文本子元素」成为默认行为。

        /** 加一个文本子元素。 */
        fun text(value: String, width: Int? = null, height: Int? = null): Builder = apply {
            val b = selfText(value)
            if (width != null) b.width(width)
            if (height != null) b.height(height)
            child(b)
        }

        /** 加一个按钮子元素。 */
        fun button(label: String, actionId: String, width: Int = 120): Builder = apply {
            child(selfButton(label, actionId, width))
        }

        /** 加一条分隔线子元素。 */
        fun divider(width: Int = 240): Builder = apply {
            child(selfDivider(width))
        }

        /** 加一个进度条子元素。 */
        fun progress(variable: String, width: Int = 160): Builder = apply {
            child(selfProgress(variable, width))
        }

        /**
         * 加一个子容器，在 [block] 里写它的子元素。
         *
         * 与顶层的 [container] 扩展功能重合，但这里的是**成员**，
         * 所以内层接收者一定优先命中它，不会外泄到根。
         */
        fun container(
            type: ULSMenuElementType,
            columns: Int? = null,
            width: Int? = null,
            height: Int? = null,
            block: Builder.() -> Unit,
        ): Builder = apply {
            val b = of(type)
            if (columns != null) b.prop("columns", columns)
            if (width != null) b.width(width)
            if (height != null) b.height(height)
            b.block()
            child(b)
        }

        /** 同名字面量构造——避免和上面的成员重载打架。 */
        private fun selfText(value: String): Builder = of(ULSMenuElementType.TEXT).rawText(value)

        private fun selfButton(label: String, actionId: String, width: Int): Builder =
            of(ULSMenuElementType.BUTTON).rawText(label).action(actionId).width(width)

        private fun selfDivider(width: Int): Builder =
            of(ULSMenuElementType.SHAPE)
                .prop("style", "rect")
                .color("#3A424B")
                .width(width)
                .height(1)
                .newline()

        private fun selfProgress(variable: String, width: Int): Builder =
            of(ULSMenuElementType.PROGRESS).prop("value", variable).width(width)

        fun build(): ULSMenuElement = ULSMenuElement(
            type, LinkedHashMap(props), children.toList(), actionId, visibleWhen, enabledWhen,
        )
    }

    companion object {
        fun of(type: ULSMenuElementType): Builder = Builder(type)

        fun text(value: String, width: Int? = null, height: Int? = null): Builder =
            Builder(ULSMenuElementType.TEXT).rawText(value).also {
                if (width != null) it.width(width)
                if (height != null) it.height(height)
            }

        fun button(label: String, actionId: String, width: Int = 120): Builder =
            Builder(ULSMenuElementType.BUTTON).rawText(label).action(actionId).width(width)

        fun shape(color: String, width: Int, height: Int): Builder =
            Builder(ULSMenuElementType.SHAPE).color(color).width(width).height(height)

        /** 分隔线（1 像素高的形状）。 */
        fun divider(width: Int): Builder =
            Builder(ULSMenuElementType.SHAPE)
                .prop("style", "rect")
                .color("#3A424B")
                .width(width)
                .height(1)
                .newline()

        fun grid(columns: Int, vararg elements: ULSMenuElement): Builder =
            Builder(ULSMenuElementType.GRID).prop("columns", columns).children(*elements)

        /**
         * 滚动容器。
         *
         * [width]/[height] 必须显式给：滚动容器的裁剪矩形就是它的方框，
         * 没有尺寸就没有裁剪范围，客户端会把内容当成完全不裁剪来画。
         *
         * 高度决定「能看见多少」，内容超出这个高度就可以滚。
         */
        fun scroll(width: Int, height: Int, vararg elements: ULSMenuElement): Builder =
            Builder(ULSMenuElementType.SCROLL)
                .width(width)
                .height(height)
                .children(*elements)

        fun progress(variable: String, width: Int = 160): Builder =
            Builder(ULSMenuElementType.PROGRESS).prop("value", variable).width(width)

        fun progressStatic(ratio: Float, width: Int = 160): Builder =
            Builder(ULSMenuElementType.PROGRESS).prop("ratio", ratio).width(width)
    }
}

/**
 * 往容器里加一个嵌套容器，并在 [block] 里写它的子元素。
 *
 * ## 这个扩展在 `Builder` 的接收者里**不生效**
 *
 * `Builder` 上现在有一个同签名的**成员**函数 `container(...)`，而
 * Kotlin 里成员永远优先于扩展。所以在 `scrollArea { }` / `container { }`
 * 内部写 `container(...)` 命中的是那个成员——这**正是想要的**行为：
 * 成员把子容器挂到当前容器上，扩展则挂到调用者身上，两者在
 * 「当前接收者就是父容器」时结果相同，但成员不会因为外层作用域
 * 而改变目标。
 *
 * 保留这个扩展是因为它可以在**没有** `Builder` 接收者的地方用，
 * 比如对一个已经拿到的 builder 变量显式调用：
 *
 * ```kotlin
 * val root = ULSMenuElement.of(ULSMenuElementType.ROOT)
 * with(root) { this.container(ULSMenuElementType.GRID, columns = 2) { text("A") } }
 * ```
 *
 * 编译器会对 `Builder` 内部遮蔽发出警告（`This extension is shadowed
 * by a member`），这是**预期**的，不是问题。
 *
 * 返回容器自身的构建器，所以还能继续链 `visibleWhen` / `background`。
 */
fun ULSMenuElement.Builder.container(
    type: ULSMenuElementType,
    columns: Int? = null,
    width: Int? = null,
    height: Int? = null,
    block: ULSMenuElement.Builder.() -> Unit,
): ULSMenuElement.Builder {
    val b = ULSMenuElement.of(type)
    if (columns != null) b.prop("columns", columns)
    if (width != null) b.width(width)
    if (height != null) b.height(height)
    b.block()
    child(b)
    return b
}

/** 便捷写法：一个嵌套的滚动容器；见 [ULSMenuElement.Builder.container]。 */
fun ULSMenuElement.Builder.scrollContainer(
    width: Int,
    height: Int,
    block: ULSMenuElement.Builder.() -> Unit,
): ULSMenuElement.Builder =
    container(ULSMenuElementType.SCROLL, width = width, height = height, block = block)

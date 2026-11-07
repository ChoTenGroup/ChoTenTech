package io.github.zzzyyylllty.chotentech.client.menu

/**
 * 菜单构建的便捷入口。
 *
 * ## 为什么还要一层 DSL
 *
 * [ULSMenuElement] / [ULSMenuDefinition] 已经是可用的构建器了，但直接
 * 用它们写一个菜单会是这样：
 *
 * ```kotlin
 * ULSMenuDefinition.builder("warehouse", "仓库")
 *     .size(240, 160)
 *     .root(
 *         ULSMenuElement.of(ULSMenuElementType.ROOT).apply {
 *             width(240); height(160)
 *             child(ULSMenuElement.text("库存"))
 *             child(ULSMenuElement.button("取出", "warehouse.take"))
 *         }.build()
 *     )
 *     .build()
 * ```
 *
 * 每个元素都要 `.build()`，嵌套深了括号配平就成了负担。这个 DSL 用
 * Kotlin 的 receiver lambda 把它压平：
 *
 * ```kotlin
 * ULSMenus.build("warehouse", "仓库") {
 *     size(240, 160)
 *     text("库存")
 *     button("取出", "warehouse.take")
 * }
 * ```
 *
 * ## 它只是语法糖
 *
 * 这里不做任何协议层的事，也不持有状态——所有东西最终都落到
 * [ULSMenuDefinition] 上。这样「怎么构造」和「怎么下发」不会缠在一起，
 * 测试时也可以只构造不下发。
 */
object ULSMenus {

    /**
     * 构建一个菜单。
     *
     * [block] 收到的 receiver 是根元素的构建器，所以 `text(...)` /
     * `button(...)` 这些是「往根里加子元素」。
     */
    fun build(
        menuId: String,
        title: String,
        block: ULSMenuRootScope.() -> Unit,
    ): ULSMenuDefinition {
        val scope = ULSMenuRootScope(menuId, title)
        scope.block()
        return scope.build()
    }

    /**
     * 根作用域。
     *
     * 同时暴露「菜单级」的（尺寸、变量）和「根元素级」的（子元素）操作，
     * 因为它们在实际写菜单时总是混在一起用，分成两层反而要多写一层嵌套。
     */
    class ULSMenuRootScope internal constructor(
        private val menuId: String,
        private val title: String,
    ) {
        private val builder = ULSMenuDefinition.builder(menuId, title)
        private val rootBuilder = ULSMenuElement.of(ULSMenuElementType.ROOT)

        /** 菜单逻辑尺寸。 */
        fun size(width: Int, height: Int) {
            builder.size(width, height)
            rootBuilder.width(width).height(height)
        }

        /** 定义一个初始变量。 */
        fun variable(name: String, value: String) {
            builder.variable(name, value)
        }

        /** 根容器是否可滚动。 */
        fun scrollable(value: Boolean = true) {
            rootBuilder.prop("scrollable", value)
        }

        /**
         * 滚轮一格的像素数（逻辑像素）。
         *
         * 客户端读的是根元素上的 `scrollStep`，缺省 16。数值越大滚得越快；
         * 内容很长时调小、内容略超出时调大，手感差别很明显。
         *
         * 必须为正：0 会让滚轮完全没反应（事件被消费、偏移不变），
         * 负数会让滚动方向反过来。
         */
        fun scrollStep(pixels: Int) {
            require(pixels > 0) { "scrollStep 必须为正数，收到 $pixels" }
            rootBuilder.prop("scrollStep", pixels)
        }

        fun background(color: String) {
            rootBuilder.background(color)
        }

        // ---- 直接往根里加元素 ----

        fun element(element: ULSMenuElement.Builder): ULSMenuElement.Builder {
            rootBuilder.child(element)
            return element
        }

        fun element(element: ULSMenuElement) {
            rootBuilder.child(element)
        }

        /**
         * 文本元素。
         *
         * 返回 [ULSMenuElement.Builder] 而不是 `Unit`，这样可以直接挂
         * `visibleWhen` / `tooltip` 这类属性：
         *
         * ```kotlin
         * text("仅开启时可见").visibleWhen(ULSMenuCondition.eq("toggle", "true"))
         * ```
         *
         * 返回值的「加进根」这个副作用**在调用时就已发生**，所以
         * 丢弃返回值（不链式调用）依然是正确用法。
         */
        fun text(value: String, width: Int? = null, height: Int? = null): ULSMenuElement.Builder {
            // 用 rawText：单参数的 text(value) 现在是「加一个 TEXT 子元素」，
            // 在这里调用它会给自己塞一个自己。
            val b = ULSMenuElement.of(ULSMenuElementType.TEXT).rawText(value)
            if (width != null) b.width(width)
            if (height != null) b.height(height)
            rootBuilder.child(b)
            return b
        }

        fun button(label: String, actionId: String, width: Int = 120): ULSMenuElement.Builder {
            val b = ULSMenuElement.button(label, actionId, width)
            rootBuilder.child(b)
            return b
        }

        /**
         * 往根里加一个**容器**（grid / scroll），在 [block] 里写它的子元素。
         *
         * 返回容器自身的构建器（`block` 已执行完毕），所以还可以继续给它
         * 挂 `visibleWhen` / `background` 等属性。
         *
         * 滚动容器必须给 [width] 和 [height]：它的裁剪矩形就是自己的方框，
         * 没有尺寸就没有裁剪范围。嵌套容器用
         * [io.github.zzzyyylllty.chotentech.client.menu.container]（Builder 上的扩展）。
         */
        fun container(
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
            rootBuilder.child(b)
            return b
        }

        /** 便捷写法：一个可滚动的根级区域。 */
        fun scrollArea(
            width: Int,
            height: Int,
            block: ULSMenuElement.Builder.() -> Unit,
        ): ULSMenuElement.Builder =
            container(ULSMenuElementType.SCROLL, width = width, height = height, block = block)

        /** 一行分隔线，宽度默认取菜单宽度。 */
        fun divider(width: Int = 240): ULSMenuElement.Builder {
            val b = ULSMenuElement.divider(width)
            rootBuilder.child(b)
            return b
        }

        fun progress(value: String, width: Int = 160): ULSMenuElement.Builder {
            val b = ULSMenuElement.progress(value, width)
            rootBuilder.child(b)
            return b
        }

        internal fun build(): ULSMenuDefinition = builder.root(rootBuilder).build()
    }
}

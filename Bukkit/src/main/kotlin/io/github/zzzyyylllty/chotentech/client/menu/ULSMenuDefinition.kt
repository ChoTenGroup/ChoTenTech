package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSProtocol

/**
 * 一个完整的菜单描述：结构 + 初始变量。
 *
 * 不可变。要改变量请用 [withVariables] 生成新实例——理由与客户端侧相同：
 * 下发过程中这个对象可能被多个线程读到（网络线程编码、主线程改数据），
 * 就地改会出现「编码到一半变量变了」的撕裂状态。
 */
class ULSMenuDefinition internal constructor(
    val menuId: String,
    val title: String,
    val width: Int,
    val height: Int,
    val root: ULSMenuElement,
    val variables: Map<String, String>,
) {

    /** 用一组新变量替换，返回新实例。 */
    fun withVariables(newVariables: Map<String, String>): ULSMenuDefinition =
        ULSMenuDefinition(menuId, title, width, height, root, LinkedHashMap(newVariables))

    /**
     * 扁平化所有元素（含自身与所有后代）。
     *
     * 用显式栈而不是递归。元素树完全由本地代码构造（不存在被外部撑爆的
     * 可能），但保持和客户端侧一样的写法，避免两边行为分叉。
     */
    fun flatten(): List<ULSMenuElement> {
        val result = ArrayList<ULSMenuElement>()
        val stack = ArrayDeque<ULSMenuElement>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val element = stack.removeLast()
            result.add(element)
            // 倒序压栈，保证弹出顺序与声明顺序一致
            for (i in element.children.indices.reversed()) {
                stack.addLast(element.children[i])
            }
        }
        return result
    }

    /** 元素总数（含根）。 */
    fun elementCount(): Int = flatten().size

    /**
     * 这个菜单用到的所有能力标识。
     *
     * 服务端在 [ULSMenuManager] 下发前会检查每一项都在该玩家的能力表里；
     * 缺任何一项就应该**整体降级到经典轨**，而不是发一个残缺的增强菜单。
     */
    fun requiredCapabilities(): Set<String> {
        val result = LinkedHashSet<String>()
        for (element in flatten()) {
            element.type.capability?.let { result.add(it) }
        }
        return result
    }

    override fun toString(): String =
        "ULSMenuDefinition($menuId, ${width}x$height, ${elementCount()} 个元素, ${variables.size} 个变量)"

    class Builder internal constructor(
        private val menuId: String,
        private var title: String,
    ) {
        private var width = 240
        private var height = 160
        private var root: ULSMenuElement = ULSMenuElement.of(ULSMenuElementType.ROOT).build()
        private val variables = linkedMapOf<String, String>()

        init {
            require(menuId.isNotEmpty()) { "菜单 ID 不能为空" }
            require(menuId.length <= ULSProtocol.MAX_ID_LEN) {
                "菜单 ID 过长（上限 ${ULSProtocol.MAX_ID_LEN}）: $menuId"
            }
        }

        fun title(value: String): Builder = apply { title = value }

        /**
         * 菜单的逻辑尺寸。
         *
         * 下限 16：客户端按 `可用尺寸 / 菜单尺寸` 算缩放，
         * 0 或负数会让它除零。这里就地挡住，比让客户端去防更省事。
         */
        fun size(w: Int, h: Int): Builder = apply {
            width = w.coerceAtLeast(16)
            height = h.coerceAtLeast(16)
        }

        fun root(element: ULSMenuElement): Builder = apply { root = element }

        fun root(builder: ULSMenuElement.Builder): Builder = root(builder.build())

        fun variable(name: String, value: String): Builder = apply {
            if (name.isEmpty() || name.length > ULSProtocol.MAX_VAR_NAME_LEN) return@apply
            if (variables.size >= ULSProtocol.MAX_MENU_VARIABLES && !variables.containsKey(name)) {
                return@apply
            }
            variables[name] = value.take(ULSProtocol.MAX_TEXT_LEN)
        }

        fun variables(pairs: Map<String, String>): Builder = apply {
            pairs.forEach { (k, v) -> variable(k, v) }
        }

        fun build(): ULSMenuDefinition = ULSMenuDefinition(
            menuId = menuId,
            title = title,
            width = width,
            height = height,
            root = root,
            variables = LinkedHashMap(variables),
        )
    }

    companion object {
        fun builder(menuId: String, title: String): Builder = Builder(menuId, title)
    }
}

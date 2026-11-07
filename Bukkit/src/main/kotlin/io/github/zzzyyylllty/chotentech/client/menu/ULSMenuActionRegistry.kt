package io.github.zzzyyylllty.chotentech.client.menu

import org.bukkit.entity.Player

/**
 * 动作白名单。
 *
 * ## 这是安全边界，不是便利设施
 *
 * 客户端点击一个按钮时，只会回传一个**字符串动作 ID**。这个字符串完全
 * 来自客户端，可以被任意改写——玩家装一个改过的模组，就能发出任何
 * actionId。因此：
 *
 * - **只有在这个注册表里登记过的 actionId 才会被执行。**
 * - 未登记 → 丢弃并记日志，不执行、不报错、不回应。
 * - 动作 ID 是**公开**的（玩家反编译模组或在日志里就能看到），
 *   所以它绝不能编码任何权限信息。「这个按钮只有管理员能点」必须
 *   在 [ULSMenuAction.handler] 里**重新查权限**，而不是靠 ID 里带个
 *   `admin_` 前缀。
 *
 * ## 与菜单描述的关系
 *
 * 服务端构造菜单时会用 [ULSMenuActionRegistry.exists] 校验每个
 * [ULSMenuElement] 的 actionId；写错一个字母的后果是「按钮点了没反应」，
 * 而不是「点了执行了别的动作」——后者才是真正危险的失败模式。
 */
object ULSMenuActionRegistry {

    /**
     * 一个已登记的动作。
     *
     * @param id        动作 ID，必须与菜单元素里写的字符串完全一致。
     * @param owner     归属插件，用于 reload 时清理。为 null 表示内置。
     * @param handler   真正干活的逻辑。**返回 false 表示这次点击不生效**
     *                  （例如条件不满足、权限不足），返回 true 表示已处理。
     */
    class ULSMenuAction internal constructor(
        val id: String,
        val owner: Any?,
        val description: String,
        val handler: (context: ULSMenuActionContext) -> Boolean,
    )

    /**
     * 一次动作调用的上下文。
     *
     * 服务端在处理时**重新读取**它需要的所有状态。刻意不把客户端
     * 报上来的任何东西（除了 payload 文本本身）放进来当作决策依据。
     */
    class ULSMenuActionContext internal constructor(
        /** 触发者。真实玩家对象，不是客户端自称的身份。 */
        val player: Player,
        /** 被点击的按钮所属菜单 ID。 */
        val menuId: String,
        /** 动作 ID。 */
        val actionId: String,
        /**
         * 客户端附带的文本（例如输入框内容）。
         *
         * **完全不可信**：这是玩家可以任意填的字符串。用作显示、
         * 用作搜索关键词都可以；用作文件名、命令拼装、SQL 就一定出事。
         * 长度已被 [ULSMenuCodec] 限制在 1024 字符。
         */
        val payload: String,
    )

    private val actions = linkedMapOf<String, ULSMenuAction>()

    /**
     * 登记一个动作。
     *
     * 重复 ID 会抛异常——这是刻意的：两个插件抢同一个动作 ID 时，
     * 后登记的那个会静默覆盖前一个，表现为「按钮有时干 A 有时干 B」。
     * 开发期就该炸出来。
     */
    @Synchronized
    fun register(
        id: String,
        owner: Any? = null,
        description: String = "",
        handler: (ULSMenuActionContext) -> Boolean,
    ): ULSMenuAction {
        require(id.isNotEmpty()) { "动作 ID 不能为空" }
        require(id.length <= io.github.zzzyyylllty.chotentech.client.ULSProtocol.MAX_ACTION_ID_LEN) {
            "动作 ID 过长: $id"
        }
        require(!actions.containsKey(id)) { "动作 ID 重复登记: $id" }
        val action = ULSMenuAction(id, owner, description, handler)
        actions[id] = action
        return action
    }

    /** 注销一个动作。返回是否真的存在过。 */
    @Synchronized
    fun unregister(id: String): Boolean = actions.remove(id) != null

    /** 注销某个归属的所有动作。用于插件卸载 / reload。 */
    @Synchronized
    fun unregisterOwner(owner: Any): Int {
        val before = actions.size
        actions.entries.removeIf { it.value.owner === owner }
        return before - actions.size
    }

    /** 该动作 ID 是否已登记。 */
    @Synchronized
    fun exists(id: String): Boolean = actions.containsKey(id)

    /** 取一个动作，未登记返回 null。 */
    @Synchronized
    fun get(id: String): ULSMenuAction? = actions[id]

    /** 所有已登记动作的 ID（只读快照）。 */
    @Synchronized
    fun ids(): List<String> = actions.keys.toList()

    /** 清空。仅用于 reload / 测试。 */
    @Synchronized
    fun clear() = actions.clear()
}

package io.github.zzzyyylllty.chotentech.client.menu

import io.github.zzzyyylllty.chotentech.client.ULSProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * 菜单封包的编解码。
 *
 * ## 契约
 *
 * 本文件是模组侧 `cn.gtemc.ulshelper.menu.MenuCodec` 的 **Kotlin 镜像**。
 * 两边的字段顺序、字段宽度、标志位定义必须逐字一致，否则菜单会静默错位
 * （读出来的属性全是乱码，但不抛异常）。
 *
 * ## 线格式
 *
 * ```
 * 元素编码（递归）：
 *   typeId  : String(≤32)
 *   flags   : Byte  bit0=有 actionId, bit1=有 visibleWhen, bit2=有 enabledWhen
 *   [actionId]     : String(≤64)   仅当 bit0
 *   props          : VarInt 个数 + N × (key:String(256), value:String(1024))
 *   [visibleWhen]  : 条件编码      仅当 bit1
 *   [enabledWhen]  : 条件编码      仅当 bit2
 *   children       : VarInt 个数 + N × 元素编码
 *
 * 条件编码：
 *   logic : VarInt  0=ATOM 1=ALL 2=ANY 3=NOT
 *   ATOM  : variable:String(64), opSymbol:String(4), operandIsVar:Byte(0/1), operand:String(64)
 *   其它  : children:VarInt 个数 + N × 条件编码
 *
 * 所有菜单包体在 menuId:String(64) 后紧跟 nonce:Long（大端，有符号），
 * 由服务端为每次增强菜单打开生成；同 menuId 的旧包不能作用于下一次打开。
 * menu_open  体 : menuId, nonce, title:String(256), width:VarInt, height:VarInt,
 *                 variables(VarInt 个数 + N × (name:64, value:1024)), rootElement
 * menu_data  体 : menuId, nonce, variables（**整体替换，不是增量合并**）
 * menu_close 体 : menuId, nonce
 * action     体 : menuId, nonce, actionId:String(64), payload:String(1024) ← C2S
 * menu_dismiss 体 : menuId, nonce ← C2S
 * ```
 *
 * ## 解析的防御前提
 *
 * 服务端**不信任**任何来自客户端的输入；同理，客户端也不该信任服务端的字节流
 * 一定合法（服务端可能版本不同、可能被改）。所以两侧的读取函数都在
 * **分配内存之前**校验长度——VarInt 可以合法地编码出 21 亿，
 * 直接拿来当循环次数或数组长度会让对方 OOM。
 */
object ULSMenuCodec {

    private const val FLAG_ACTION = 1
    private const val FLAG_VISIBLE = 2
    private const val FLAG_ENABLED = 4

    private const val LOGIC_ATOM = 0
    private const val LOGIC_ALL = 1
    private const val LOGIC_ANY = 2
    private const val LOGIC_NOT = 3

    /** 属性名长度上限。 */
    private const val MAX_PROP_KEY_LEN = ULSProtocol.MAX_PROPERTY_LEN

    /** 属性值长度上限。 */
    private const val MAX_PROP_VALUE_LEN = ULSProtocol.MAX_TEXT_LEN

    /** 元素类型 ID 长度上限。 */
    private const val MAX_TYPE_ID_LEN = 32

    /** 菜单标题长度上限。 */
    private const val MAX_TITLE_LEN = 256

    /** 运算符符号长度上限（最长的是 "<=" 。 */
    private const val MAX_OP_SYMBOL_LEN = 4

    // ==================================================================
    // 编码（服务端 → 客户端）
    // ==================================================================

    /** 编码一个 menu_open 载荷体。 */
    fun encodeOpen(menu: ULSMenuDefinition, nonce: Long): ByteArray {
        // 元素总数也要卡。客户端的解码器按 MAX_MENU_ELEMENTS 拒绝，
        // 超了同样是「服务端以为发成功、客户端整张菜单丢掉」，
        // 理由与 writeElement 的深度校验完全一样。
        val count = menu.flatten().size
        if (count > ULSProtocol.MAX_MENU_ELEMENTS) {
            throw IllegalArgumentException(
                "菜单元素过多（$count 个，上限 ${ULSProtocol.MAX_MENU_ELEMENTS}）。" +
                        "客户端会拒绝解析整张菜单。"
            )
        }

        // 客户端共用一份元素+条件计数预算；条件树还各自限制深度。
        val budget = DecodeBudget()
        validateElement(menu.root, 0, budget)
        require(menu.variables.size <= ULSProtocol.MAX_MENU_VARIABLES) { "菜单变量数超限" }

        val buf = buffer()
        val out = buf.out
        writeString(out, menu.menuId, ULSProtocol.MAX_ID_LEN)
        out.writeLong(nonce)
        writeString(out, menu.title, MAX_TITLE_LEN)
        writeVarInt(out, menu.width)
        writeVarInt(out, menu.height)
        writeVariables(out, menu.variables)
        writeElement(out, menu.root, 0)
        return checkedPayload(buf.toByteArray())
    }

    /** 编码一个 menu_data 载荷体（只带变量，不带结构）。 */
    fun encodeData(menuId: String, nonce: Long, variables: Map<String, String>): ByteArray {
        val buf = buffer()
        val out = buf.out
        require(variables.size <= ULSProtocol.MAX_MENU_VARIABLES) { "菜单变量数超限" }
        writeString(out, menuId, ULSProtocol.MAX_ID_LEN)
        out.writeLong(nonce)
        writeVariables(out, variables)
        return checkedPayload(buf.toByteArray())
    }

    /** 编码一个 menu_close 载荷体。 */
    fun encodeClose(menuId: String, nonce: Long): ByteArray {
        val buf = buffer()
        val out = buf.out
        writeString(out, menuId, ULSProtocol.MAX_ID_LEN)
        out.writeLong(nonce)
        return buf.toByteArray()
    }

    /**
     * 编码一个 action 载荷体（客户端 → 服务端）。
     *
     * 这个方法在插件侧只是为了让「服务端自己模拟一次点击」这类测试/内部调用
     * 能复用同一套格式；正常情况下这个方向的封包由模组发出。
     */
    fun encodeAction(menuId: String, nonce: Long, actionId: String, payload: String): ByteArray {
        val buf = buffer()
        val out = buf.out
        writeString(out, menuId, ULSProtocol.MAX_ID_LEN)
        out.writeLong(nonce)
        writeString(out, actionId, ULSProtocol.MAX_ACTION_ID_LEN)
        writeString(out, payload, MAX_PROP_VALUE_LEN)
        return buf.toByteArray()
    }

    // ==================================================================
    // 解码（客户端 → 服务端）—— 只需要 action 这一个方向
    // ==================================================================

    /** 一个入站动作。 */
    data class ActionPayload(
        val menuId: String,
        val nonce: Long,
        val actionId: String,
        val payload: String,
    )

    /**
     * 解析一个 action 载荷体。
     *
     * 解析不出来返回 null，**绝不抛异常**——这个方法的输入完全来自客户端，
     * 任何精心构造的字节都能送到这里。
     */
    fun decodeAction(body: ByteArray): ActionPayload? {
        if (body.isEmpty()) return null
        var input: DataInputStream? = null
        return try {
            input = DataInputStream(ByteArrayInputStream(body))
            val menuId = readString(input, ULSProtocol.MAX_ID_LEN)
            val nonce = input.readLong()
            val actionId = readString(input, ULSProtocol.MAX_ACTION_ID_LEN)
            val payload = readString(input, MAX_PROP_VALUE_LEN)
            if (input.available() != 0 || menuId.isEmpty() || actionId.isEmpty()) null
            else ActionPayload(menuId, nonce, actionId, payload)
        } catch (_: Throwable) {
            null
        } finally {
            try {
                input?.close()
            } catch (_: Throwable) {
                // 关不掉也无所谓，底层是 ByteArrayInputStream
            }
        }
    }

    data class DismissPayload(val menuId: String, val nonce: Long)

    /** C2S menu_dismiss 与 S2C menu_close 共用严格的菜单 ID + nonce 格式。 */
    fun decodeDismiss(body: ByteArray): DismissPayload? = try {
        DataInputStream(ByteArrayInputStream(body)).use { input ->
            val menuId = readString(input, ULSProtocol.MAX_ID_LEN)
            val nonce = input.readLong()
            if (menuId.isNotEmpty() && input.available() == 0) DismissPayload(menuId, nonce) else null
        }
    } catch (_: java.io.IOException) {
        null
    }

    // ==================================================================
    // 内部写入
    // ==================================================================

    private fun checkedPayload(body: ByteArray): ByteArray {
        // 封包头（ID+VarInt 长度）另外占空间；预留 64 字节供最坏情况。
        require(body.size <= ULSProtocol.MAX_PAYLOAD_BYTES - 64) { "菜单载荷超过封包上限" }
        return body
    }

    private class DecodeBudget {
        var remaining = ULSProtocol.MAX_MENU_ELEMENTS
        fun consume() {
            require(--remaining >= 0) { "菜单元素与条件总数超限" }
        }
    }

    private fun validateElement(element: ULSMenuElement, depth: Int, budget: DecodeBudget) {
        require(depth <= ULSProtocol.MAX_MENU_DEPTH) { "菜单元素嵌套过深" }
        budget.consume()
        require(element.props.size <= ULSProtocol.MAX_ELEMENT_PROPERTIES) { "菜单元素属性数超限" }
        element.visibleWhen?.let { validateCondition(it, 0, budget) }
        element.enabledWhen?.let { validateCondition(it, 0, budget) }
        element.children.forEach { validateElement(it, depth + 1, budget) }
    }

    private fun validateCondition(condition: ULSMenuCondition, depth: Int, budget: DecodeBudget) {
        require(depth <= ULSProtocol.MAX_MENU_DEPTH) { "菜单条件嵌套过深" }
        budget.consume()
        when (condition) {
            is ULSMenuCondition.Atom -> Unit
            is ULSMenuCondition.All -> condition.children.forEach { validateCondition(it, depth + 1, budget) }
            is ULSMenuCondition.Any -> condition.children.forEach { validateCondition(it, depth + 1, budget) }
            is ULSMenuCondition.Not -> validateCondition(condition.child, depth + 1, budget)
        }
    }

    /**
     * 取出缓冲区内容。
     *
     * `ByteArrayOutputStream#toByteArray` 用扩展函数取不到，
     * 所以这里持有原引用而不是靠 `DataOutputStream` 反查。
     */
    private class Buffer {
        val stream = ByteArrayOutputStream(256)
        val out = DataOutputStream(stream)
        fun toByteArray(): ByteArray = stream.toByteArray()
    }

    private fun buffer(): Buffer = Buffer()

    /**
     * 编码一个元素。
     *
     * ## 为什么这里必须数深度
     *
     * 客户端的解码器 [MenuCodec] 在读元素时按 `MAX_MENU_DEPTH` 拒绝，
     * 而且用的是**递归**——超过深度的菜单它解不开，会整张丢弃。
     *
     * 如果服务端这边不数，就会产生一个很难查的组合：
     * 服务端认为菜单发成功了（`encodeOpen` 正常返回、
     * `ULSMenuManager.open` 还顺手把 `openMenus` 记上了），
     * 客户端那边却一个字都没收到。`/ulsmenu diagnose` 显示一切健康，
     * 玩家看到的是「什么都没发生」，而两边日志都干净。
     *
     * **失败必须在产生它的那一侧就变成错误**，不能指望对端替我们发现。
     *
     * @param depth 当前深度，根元素为 0
     */
    private fun writeElement(out: DataOutputStream, element: ULSMenuElement, depth: Int) {
        if (depth > ULSProtocol.MAX_MENU_DEPTH) {
            throw IllegalArgumentException(
                "菜单嵌套过深（超过 ${ULSProtocol.MAX_MENU_DEPTH} 层）。" +
                        "客户端会拒绝解析整张菜单——请把层级拆浅一些。"
            )
        }

        writeString(out, element.type.id, MAX_TYPE_ID_LEN)

        var flags = 0
        val actionId = element.actionId
        // 空串不算「有动作」：客户端那边 `MenuElement.interactive()` 判的是
        // actionId 非 null，而空串会被写成一个长度为 0 的 ID，
        // 点下去发一个空 actionId 上来——服务端白名单里没有空 ID，
        // 所以只会得到「未登记的动作」日志。不如根本不置位。
        if (!actionId.isNullOrEmpty()) flags = flags or FLAG_ACTION
        if (element.visibleWhen != null) flags = flags or FLAG_VISIBLE
        if (element.enabledWhen != null) flags = flags or FLAG_ENABLED
        out.writeByte(flags)

        if (flags and FLAG_ACTION != 0) {
            writeString(out, actionId!!, ULSProtocol.MAX_ACTION_ID_LEN)
        }

        writeVarInt(out, element.props.size)
        for ((key, value) in element.props) {
            writeString(out, key, MAX_PROP_KEY_LEN)
            writeString(out, value, MAX_PROP_VALUE_LEN)
        }

        if (flags and FLAG_VISIBLE != 0) writeCondition(out, element.visibleWhen!!)
        if (flags and FLAG_ENABLED != 0) writeCondition(out, element.enabledWhen!!)

        writeVarInt(out, element.children.size)
        for (child in element.children) writeElement(out, child, depth + 1)
    }

    private fun writeCondition(out: DataOutputStream, condition: ULSMenuCondition) {
        when (condition) {
            is ULSMenuCondition.Atom -> {
                writeVarInt(out, LOGIC_ATOM)
                writeString(out, condition.variable, ULSProtocol.MAX_VAR_NAME_LEN)
                writeString(out, condition.op.symbol, MAX_OP_SYMBOL_LEN)
                out.writeByte(if (condition.valueIsVariable) 1 else 0)
                writeString(out, condition.value, ULSProtocol.MAX_VAR_NAME_LEN)
            }

            is ULSMenuCondition.All -> {
                writeVarInt(out, LOGIC_ALL)
                writeVarInt(out, condition.children.size)
                for (child in condition.children) writeCondition(out, child)
            }

            is ULSMenuCondition.Any -> {
                writeVarInt(out, LOGIC_ANY)
                writeVarInt(out, condition.children.size)
                for (child in condition.children) writeCondition(out, child)
            }

            is ULSMenuCondition.Not -> {
                writeVarInt(out, LOGIC_NOT)
                // NOT 也必须写子节点个数，与 All / Any 保持同一种「头部」形状。
                //
                // 这里曾经省略过这个 VarInt，后果很有代表性：两侧都能编译，
                // 但客户端读 NOT 时会把子节点里的 `LOGIC_ATOM`(=0) 当成个数，
                // 于是循环 0 次、NOT 退化成 not(all([])) → 恒为 false，
                // 而且读取位置停在原子条件内部，后面整个元素错位、
                // 最终整张菜单被丢弃。**一个字节的差异，表现是「菜单打不开」。**
                writeVarInt(out, 1)
                writeCondition(out, condition.child)
            }
        }
    }

    private fun writeVariables(out: DataOutputStream, variables: Map<String, String>) {
        writeVarInt(out, variables.size)
        for ((name, value) in variables) {
            writeString(out, name, ULSProtocol.MAX_VAR_NAME_LEN)
            writeString(out, value, MAX_PROP_VALUE_LEN)
        }
    }

    // ==================================================================
    // 底层编解码
    //
    // 与 ULSClientManager 里的那份是同一种格式（MC 的 PacketByteBuf 约定）：
    // 字符串 = VarInt 字节长度 + UTF-8 字节；VarInt = 低 7 位一组，最高位续接。
    // 刻意在这里再写一份，而不是把 ULSClientManager 的私有函数改成 internal——
    // 那样会让「协议格式」这件事散落在两个文件里，而它应该是自包含的。
    // ==================================================================

    private fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.writeByte(v and 0x7F)
    }

    private fun readVarInt(input: DataInputStream): Int {
        var value = 0
        var position = 0
        while (true) {
            val b = input.readByte().toInt()
            value = value or ((b and 0x7F) shl position)
            if (b and 0x80 == 0) return value
            position += 7
            if (position >= 35) throw java.io.IOException("VarInt 过长")
        }
    }

    private fun writeString(out: DataOutputStream, value: String, maxLength: Int) {
        val bytes = value.take(maxLength).toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    /**
     * 读一个字符串。
     *
     * ## 长度判据必须与模组侧一致
     *
     * 模组侧用的是 `PacketByteBuf.readString(maxLength)`，它按**编码后的
     * 字节数**卡上限（MC 内部按 `maxLength * 3` 校验），超了直接抛异常，
     * 且**返回完整字符串、不截断**。
     *
     * 这里以前写的是「放宽到 `maxLength * 4`，然后 `.take(maxLength)`」，
     * 那是一处静默分歧：一个 900 字符的中文串（2700 字节）能过
     * `900 <= 1024*4` 的校验，再被 `.take(1024)` 悄悄砍掉——
     * 而模组侧会在 2700 字节处直接拒绝。同一份数据在两侧得到不同的结果，
     * 排查时看起来会像「服务端发的东西和客户端收到的不是一回事」。
     *
     * 现在改成和模组侧同一种语义：**按字节卡上限，超了报错，不截断**。
     * 上限取 `maxLength * 3`（一个字符最多 3 字节 UTF-8 有效载荷；
     * 4 字节的补充平面字符虽然存在，但用 3 倍已经足够宽松，
     * 且与 MC 的判据同量级）。
     *
     * 分配前必须校验：`length` 是从流里读出来的任意整数，
     * 直接 `ByteArray(length)` 会让一个谎报长度的对端把内存吃光。
     */
    private fun readString(input: DataInputStream, maxLength: Int): String {
        val length = readVarInt(input)
        if (length < 0 || length > maxLength * 3) {
            throw java.io.IOException("字符串长度非法: $length（上限 ${maxLength * 3} 字节）")
        }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val result = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        if (result.length > maxLength) throw java.io.IOException("字符串字符数超限: ${result.length}")
        return result
    }
}

package io.github.zzzyyylllty.chotentech.data

enum class CoolNumberTier(val displayName: String, val minScore: Int, val color: String, val tag: String) {
    NORMAL("普通", 0, "<#A9B4C3>", "NORMAL"),
    PLATINUM("白金", 0, "<#CDAEEF>", "PLATINUM"),
    GOLD("黄金", 99, "<#DFBD7C>", "GOLD"),
    BLACKGOLD("黑金", 189, "<#B46572>", "BLACKGOLD"),
    CRYSTAL("晶钻", 259, "<#84D1CB>", "CRYSTAL");

    companion object {
        fun fromTag(tag: String): CoolNumberTier =
            entries.firstOrNull { it.tag == tag } ?: NORMAL

        fun fromScore(score: Int): CoolNumberTier {
            return entries.lastOrNull { score >= it.minScore } ?: PLATINUM
        }
    }
}

data class CoolNumberInfo(
    val number: String,
    val score: Int,
    val tier: CoolNumberTier,
    val pointPrice: Int,
    val tapePrice: Int
)

enum class CoolNumberCategory(val displayName: String, val icon: String, val desc: String) {
    AAA("AAA", "111", "三连号"),
    AAAA("AAAA", "1111", "四连号"),
    AABB("AABB", "1122", "双对子"),
    AABBCC("AABBCC", "112233", "三连对"),
    LOVE("爱情号", "520", "520/1314/521"),
    CONSECUTIVE("顺子", "123", "顺序数字"),
    ALL_SAME("豹子", "888", "全部相同");
}

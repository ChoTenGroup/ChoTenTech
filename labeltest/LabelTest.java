import com.efm.core.item.EFMItem;
import org.bukkit.ChatColor;

/**
 * Smoke test for the label helpers of EFMItem: sanitizing and colour parsing only,
 * both are pure functions and run without a server.
 */
public class LabelTest {
    private static int failures = 0;

    public static void main(String[] args) {
        // sanitizing
        expect("一号武器箱", EFMItem.SanitizeLabel("一号武器箱"));
        expect("一号 武器箱", EFMItem.SanitizeLabel("  一号 武器箱  "));
        expect("abc", EFMItem.SanitizeLabel("a(b)c"));
        expect("红色", EFMItem.SanitizeLabel("§c红色"));
        expect("红色", EFMItem.SanitizeLabel("§l§c红色"));
        expect(null, EFMItem.SanitizeLabel("   "));
        expect(null, EFMItem.SanitizeLabel("()"));
        expect(null, EFMItem.SanitizeLabel(null));
        expect("1234567890123456", EFMItem.SanitizeLabel("1234567890123456789"));

        // colour parsing
        expectColor(ChatColor.RED, EFMItem.ParseLabelColor("red"));
        expectColor(ChatColor.RED, EFMItem.ParseLabelColor("RED"));
        expectColor(ChatColor.RED, EFMItem.ParseLabelColor("红"));
        expectColor(ChatColor.RED, EFMItem.ParseLabelColor("红色"));
        expectColor(ChatColor.DARK_RED, EFMItem.ParseLabelColor("dark_red"));
        expectColor(ChatColor.DARK_RED, EFMItem.ParseLabelColor("dark-red"));
        expectColor(ChatColor.GOLD, EFMItem.ParseLabelColor("orange"));
        expectColor(ChatColor.GOLD, EFMItem.ParseLabelColor("金"));
        expectColor(ChatColor.LIGHT_PURPLE, EFMItem.ParseLabelColor("紫"));
        expectColor(ChatColor.AQUA, EFMItem.ParseLabelColor("青"));
        expectColor(null, EFMItem.ParseLabelColor("nope"));
        expectColor(null, EFMItem.ParseLabelColor(""));
        expectColor(null, EFMItem.ParseLabelColor(null));

        // the expected item name, built the way GetLabelSuffix() does
        expect(" §l(一号武器箱)", suffix("一号武器箱", EFMItem.ParseLabelColor(null)));
        expect(" §c§l(一号武器箱)", suffix("一号武器箱", EFMItem.ParseLabelColor("red")));
        expect("沈阳牌武器箱 §l(一号武器箱)", "沈阳牌武器箱" + suffix("一号武器箱", null));
        expect("沈阳牌武器箱 §c§l(一号武器箱)", "沈阳牌武器箱" + suffix("一号武器箱", EFMItem.ParseLabelColor("red")));

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Mirrors EFMItem.GetLabelSuffix for a sanitized label. */
    private static String suffix(String label, ChatColor color) {
        return String.format(" %s§l(%s)", color == null ? "" : "§" + color.getChar(), label);
    }

    private static void expect(Object wanted, Object got) {
        if (wanted == null ? got != null : !wanted.equals(got)) {
            failures++;
            System.out.println("FAIL: wanted [" + wanted + "] got [" + got + "]");
        }
    }

    private static void expectColor(ChatColor wanted, ChatColor got) {
        expect(wanted, got);
    }
}

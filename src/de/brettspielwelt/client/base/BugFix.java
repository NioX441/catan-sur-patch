package de.brettspielwelt.client.base;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Vector;

/**
 * Null-/range-safe replacements for single calls in the game, injected by CatanSavePatch.
 * Each method has the same stack signature as the call it replaces.
 */
public final class BugFix {

    private static Graphics scratch;

    private BugFix() {
    }

    /** Vector.elementAt(i), but null instead of ArrayIndexOutOfBoundsException (list changed meanwhile). */
    public static Object elementAtOrNull(Vector v, int i) {
        synchronized (v) {
            return i >= 0 && i < v.size() ? v.elementAt(i) : null;
        }
    }

    /** Vector.size() - 1: "back" in the help needs at least two pages in the history. */
    public static int sizeMinusOne(Vector v) {
        return v.size() - 1;
    }

    /** String.substring(b, e) with indices clamped to the string (layout data was stale). */
    public static String substringSafe(String s, int b, int e) {
        int len = s.length();
        if (b < 0) b = 0;
        if (e > len) e = len;
        if (b >= e) return "";
        return s.substring(b, e);
    }

    /** Graphics.getFontMetrics(Font), also before the component got its Graphics. */
    public static FontMetrics fontMetrics(Graphics g, Font f) {
        if (g == null) {
            synchronized (BugFix.class) {
                if (scratch == null) {
                    scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics();
                }
                g = scratch;
            }
        }
        return g.getFontMetrics(f);
    }

    /** String.replace(a, b) where b may be null (e.g. a player name that is not set yet). */
    public static String replaceSafe(String s, CharSequence a, CharSequence b) {
        return s.replace(a, b == null ? "" : b);
    }
}

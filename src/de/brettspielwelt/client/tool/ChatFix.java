package de.brettspielwelt.client.tool;

import java.awt.Graphics;
import java.awt.Rectangle;

/** Helpers for the patched ChatTool2 / ChatTool2.Bubble. */
public final class ChatFix {

    private ChatFix() {
    }

    /** Used by ChatTool2.paint(): a speech bubble may vanish while it is painted. */
    public static void paintBubble(ChatTool2.Bubble b, Graphics g) {
        if (b != null) b.paint(g);
    }

    /**
     * Used by Bubble.paint() instead of ChatTool2.border. The original area was a fixed
     * 1024x768 rectangle that is never updated, so on bigger screens bubbles of players at
     * the bottom/right were pushed up/left into that area (e.g. bottom right in QHD).
     */
    public static Rectangle border(ChatTool2 t) {
        if (BaseTool.master != null) {
            int w = BaseTool.master.topWidth;
            int h = BaseTool.master.topHeight;
            Rectangle r = t.border;
            if (w > 0 && h > 0 && (r == null || r.x != 0 || r.y != 0 || r.width != w || r.height != h)) {
                t.border = new Rectangle(0, 0, w, h);
            }
        }
        return t.border;
    }
}

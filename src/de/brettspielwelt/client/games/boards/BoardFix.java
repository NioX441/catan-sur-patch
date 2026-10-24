package de.brettspielwelt.client.games.boards;

import java.awt.Graphics;
import java.awt.Point;

/**
 * Replacement body for BaseGameBoard.drawAnim(Graphics), injected by CatanSavePatch.
 * Same logic as the original, but skips entries whose start/target point is not set yet
 * (the original crashed with "this.fpt[i] is null" while a new animation was being set up).
 */
public final class BoardFix {

    private BoardFix() {
    }

    /**
     * Called at the start of closeWindow(). Windows are queued; closeWindow() moves on to the
     * next one, but only the OK button also hid the dialogs. When a window was moved on
     * automatically (e.g. by the production animation right after "Barbarians attack"), the
     * old text dialog stayed visible and the next one (barbarian result) opened on top of it,
     * and the old text stayed in the matrix dialog.
     */
    public static void hideWindows(SiedlerBaseBoard b) {
        if (b.info != null) {
            b.info.setActive(false);
        }
        if (b.infoMatrix != null) {
            b.infoMatrix.setActive(false);
            b.infoMatrix.setText("");
        }
    }

    public static void drawAnim(BaseGameBoard b, Graphics g) {
        if (b.animation != 1 || b.flying == null) return;
        int[] flying = b.flying;
        Point[] fp = b.fp;
        Point[] fpt = b.fpt;
        int[] jiy = b.jiy;
        int len = b.animLength;
        for (int i = 0; i < flying.length; i++) {
            if (fp == null || fpt == null || i >= fp.length || i >= fpt.length) return;
            Point from = fp[i];
            Point to = fpt[i];
            if (from == null || to == null) continue;
            int t = b.jpc - i * b.animDelta;
            if (t > len) t = len;
            if (t < 0 || t > len || jiy == null || t >= jiy.length) continue;
            int x = from.x;
            int y = from.y;
            if (len > 0) {
                x += (to.x - from.x) * t / len;
                y += (to.y - from.y) * t / len;
            }
            b.drawObj(g, flying[i], x, y, jiy[t]);
        }
    }
}

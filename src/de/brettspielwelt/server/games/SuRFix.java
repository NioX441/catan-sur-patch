package de.brettspielwelt.server.games;

/**
 * Replacement bodies for StaedteUndRitterInformer.setHändler() and setFlotte(),
 * injected by CatanSavePatch.
 *
 * Both cards change a trade rate (kurs). The Handelsflotte remembers the old rate in
 * alterKurs and restores it at the end of the turn; the Händler remembers it in haendler[4]
 * and restores it when the merchant moves on. The original code did not know about the
 * other card:
 *  - Flotte + Händler on the same resource in one turn: the merchant stored the fleet's
 *    temporary 2:1 as "old" rate -> 4:1 again at the end of the turn although the merchant
 *    is still there, and a permanent 2:1 once the merchant has moved on.
 *  - Both Flotten on the same resource in one turn: the second stored the first one's 2:1
 *    -> permanent 2:1 after the turn.
 *  - Moving the own merchant away while a fleet is active on that resource: the fleet's
 *    2:1 was lost for the rest of the turn and the merchant's 2:1 stayed for good.
 * Everything else (animation, window, victory point) is unchanged.
 */
public final class SuRFix {

    private SuRFix() {
    }

    /** Index into alterKurs of the saved rate for resource res, or -1 if no fleet is active on it. */
    private static int fleetSlot(StaedteUndRitterInformer s, int wer, int res) {
        if (wer != s.currentPlayer) return -1;
        if (s.alterKurs[0] == res) return 1;
        if (s.alterKurs[2] == res) return 3;
        return -1;
    }

    public static boolean setHaendler(StaedteUndRitterInformer s, int x, int y, int wer) {
        Landschaft la;
        try {
            la = s.sboard.getLand(x, y);
        } catch (RuntimeException e) {
            return false; // click next to the board
        }
        if (la == null || !s.siedlungDran(la, wer) || la.rohstoff <= 2 || la.rohstoff >= 8) {
            return false;
        }

        int[] h = s.haendler;
        boolean ani = false;
        if (h[2] > -1) {
            ani = true;
            s.openpoints[h[2]]--;
            int slot = fleetSlot(s, h[2], h[3]);
            if (slot > 0) {
                s.alterKurs[slot] = h[4]; // fleet keeps 2:1 until turn end, then the real rate
            } else {
                s.kurs[h[2]][h[3]] = h[4];
            }
        }

        int oldPos = h[0] | h[1] << 4;
        int res = la.rohstoff - 3;
        h[0] = x;
        h[1] = y;
        h[2] = wer;
        h[3] = res;
        int slot = fleetSlot(s, wer, res);
        if (slot > 0) {
            h[4] = s.alterKurs[slot]; // real rate underneath the fleet
            s.alterKurs[slot] = 2;    // merchant keeps 2:1 after the fleet ends
        } else {
            h[4] = s.kurs[wer][res];
        }
        s.kurs[wer][res] = 2;

        if (s.stadtausbau[wer][1] >= 3) {
            for (int i = 5; i < 8; i++) {
                s.kurs[wer][i] = 2;
            }
        }

        s.openpoints[wer]++;
        s.sendWindow(42, (long) res);
        if (ani) {
            s.sendAnim(s.appendAnim(new byte[0], wer + 9, 2, oldPos, 2, x | y << 4));
        }
        return true;
    }

    public static int setFlotte(StaedteUndRitterInformer s, int wer, int[] v) {
        int res = v[0];
        if (res > -1 && res < s.RS) {
            // a second fleet on the same resource must not overwrite the saved real rate
            if (s.alterKurs[0] != res && s.alterKurs[2] != res) {
                if (s.alterKurs[0] == -1) {
                    s.alterKurs[0] = res;
                    s.alterKurs[1] = s.kurs[wer][res];
                } else {
                    s.alterKurs[2] = res;
                    s.alterKurs[3] = s.kurs[wer][res];
                }
            }
            s.kurs[wer][res] = 2;

            if (s.stadtausbau[wer][1] >= 3) {
                for (int i = 5; i < 8; i++) {
                    s.kurs[wer][i] = 2;
                }
            }
        }
        return res;
    }
}

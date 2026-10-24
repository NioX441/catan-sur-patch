package de.brettspielwelt.server.games;

/**
 * Called by CatanSavePatch at the start of SiedlerBaseInformer.tausch(anbieter, partner).
 *
 * A player signals "I accept" by posting the mirror image of an offer. If two players do
 * that for the same offer and the trade is made with one of them, the original code only
 * reset the offers of the two trading players. The other interested player's identical
 * offer stayed on the table (and on screen) until the AI withdrew it a moment later.
 */
public final class TradeFix {

    private TradeFix() {
    }

    public static void clearCompetingOffers(SiedlerBaseInformer s, int anbieter, int partner) {
        int n = 2 * s.RS;
        for (int i = 0; i < s.anzMitSpieler; i++) {
            if (i == anbieter || i == partner || !hasOffer(s.angebot[i], n)) continue;
            if (sameOffer(s.angebot[i], s.angebot[anbieter], n) || sameOffer(s.angebot[i], s.angebot[partner], n)) {
                for (int j = 0; j < n; j++) {
                    s.angebot[i][j] = 0;
                }
                for (int j = 0; j < s.status.length; j++) {
                    s.status[i][j] = 0;
                    s.status[j][i] = 0;
                }
            }
        }
    }

    private static boolean hasOffer(int[] a, int n) {
        for (int j = 0; j < n; j++) {
            if (a[j] != 0) return true;
        }
        return false;
    }

    private static boolean sameOffer(int[] a, int[] b, int n) {
        for (int j = 0; j < n; j++) {
            if (a[j] != b[j]) return false;
        }
        return true;
    }
}

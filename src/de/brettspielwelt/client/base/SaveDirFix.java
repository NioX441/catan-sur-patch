package de.brettspielwelt.client.base;

import java.io.File;
import java.util.Vector;

/**
 * Helper injected into the game by CatanSavePatch.
 *
 * The original game expects the folders kiStuff/save, kiStuff/save/ki and
 * KiSaveGames to already exist (they were created by the old installer) and
 * never creates them itself. Without them profiles cannot be saved, File.list()
 * returns null and the game crashes with a NullPointerException at the end of
 * a round (MenuRangliste.recalcRang via GameStore.rateGame).
 */
public final class SaveDirFix {

    private static final String[] DIRS = { "kiStuff/save/ki", "KiSaveGames" };

    private SaveDirFix() {
    }

    /** Creates all folders the game writes into (relative to the Client folder). */
    public static void ensureDirs() {
        for (String d : DIRS) {
            try {
                File f = new File(d);
                if (!f.isDirectory() && !f.mkdirs()) {
                    System.err.println("SaveDirFix: cannot create " + f.getAbsolutePath());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /** Null-safe replacement for File.list(): never returns null. */
    public static String[] list(File f) {
        String[] r = f.list();
        return r == null ? new String[0] : r;
    }

    /** True if row is not a valid index into v (used to guard table clicks). */
    public static boolean badRow(Vector v, int row) {
        return v == null || row < 0 || row >= v.size();
    }
}

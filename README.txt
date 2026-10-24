Catan - Cities & Knights (AI version): Save and Bugfix Patch
============================================================

Patches the encrypted class archive Client\code of "Catan - Staedte und Ritter" so that
profiles and progress are saved on modern Windows, the game no longer hangs at the end of
a round, and a number of further crashes and rule bugs are fixed. See CHANGELOG.txt for
the full list of fixes.


The original problem
--------------------
The game writes profiles to  Client\kiStuff\save\<name>.sav  (AI opponents to
kiStuff\save\ki\) and saved games to  Client\KiSaveGames\<name>\.  These folders used to
be created by the installer; the game itself never creates them (or only with
File.mkdir(), which fails when the parent folder is missing). If they are missing:

  * Creating a profile: the FileNotFoundException is swallowed, nothing is saved.
  * Player selection: File("kiStuff/save").list() returns null -> NullPointerException
    in MenuSpielerWahl.getAllSpieler(), the profile list stays empty.
  * End of a round: GameStore.rateGame() -> MenuRangliste.recalcRang() ->
    NullPointerException in the middle of SiedlerBaseInformer.bestimmeSieger()/weiter()
    -> the game hangs.
  * Every move: GameStore.saveGame() -> FileNotFoundException (KiSaveGames\...\*.que).


Installation / uninstallation
-----------------------------
  patch_anwenden.bat   - apply the patch. Needs a JDK 24 or newer (java and javac on the
                         PATH). Creates the backup Client\code.orig on first run.
  patch_entfernen.bat  - restore the original Client\code from code.orig.
                         Profiles and saved games are kept.

The patcher always builds from the original archive (code.orig once it exists), so
running it again replaces an older patch version instead of patching twice. Before
writing anything it checks that every patch rule matched; with an unknown game version
it aborts without changing the game.


How it works
------------
The game classes in Client\code are obfuscated with a rolling XOR: the key starts at 238,
is increased by 3 (mod 256) before every byte, and each byte is XORed with the key.
Encryption and decryption are the same operation. The loader that does this at runtime is
de.brettspielwelt.client.base.B in ..\bin.

src\CatanSavePatch.java decrypts all classes, rewrites the affected bytecode with the
Java Class-File API (java.lang.classfile, hence JDK 24+), adds the helper classes and
encrypts everything again. The original bytecode version (Java 5) is kept, so the patched
game runs on any Java version the original ran on.

Helper classes added to the game (all in src\):
  SaveDirFix   creates the save folders, null-safe File.list(), table row guard
  BugFix       null-/range-safe replacements for single calls (Vector, String, fonts)
  BoardFix     replacement body for BaseGameBoard.drawAnim(), hides dialogs in closeWindow()
  ChatFix      speech bubble helpers (null-safe painting, real screen size)
  SuRFix       replacement bodies for the merchant and merchant fleet logic
  TradeFix     removes competing "accept" offers after a trade

  java src\CatanSavePatch.java <ClientDir> <helperClassDir>   apply the patch
  java src\CatanSavePatch.java <ClientDir> --dump <outDir>    write decrypted original classes


Java version
------------
Use Java 25 (LTS). Do NOT switch to Java 26 or newer: it removed the Applet API, which the
game uses for music and sound effects (Applet.newAudioClip). Recompiling the game brings
no benefit; the 2011 bytecode runs unchanged and just as fast on current JVMs.


Not changed (harmless log messages)
-----------------------------------
  * "Error in phase=.. curPl=.." - debug output of the AI when one of its moves is rejected.
  * NullPointerException in B.A (class loader) - AppLoader probes class names that do not
    exist; this is correctly handled as a ClassNotFoundException.


Known deviations that are not changed
-------------------------------------
  * Progress cards are always dealt starting with player 1 instead of the player whose
    turn it is. This only matters when a stack is almost empty.
  * The player whose turn it is is never forced to discard down to 4 progress cards.
    The bundled handbook does not cover this rule.
  * If several players tie for the barbarian defence and all three progress card stacks
    are empty, the game waits for a card choice that is impossible (extremely unlikely).
  * Merchant Master (Handelsmeister) against a player with fewer than 3 cards: the cards
    are taken correctly, only the animation is missing.
  * Profile names with characters that Windows forbids in file names (e.g. : or ?) still
    cannot be saved.

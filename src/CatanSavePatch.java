import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Patches the encrypted class archive "code" of Catan - Staedte und Ritter (KI-Version).
 *
 * Usage (needs JDK 24+ because of java.lang.classfile):
 *   java CatanSavePatch.java <ClientDir> <helperClassDir>   apply patch (adds all .class files of the dir)
 *   java CatanSavePatch.java <ClientDir> --dump <outDir>    only write decrypted original classes
 *
 * The patch is always built from the original archive (code.orig once it exists),
 * so running it again replaces an older patch version instead of patching twice.
 *
 * Classes in "code" are XOR-encrypted with a rolling key (start 238, +3 per byte),
 * see de.brettspielwelt.client.base.B in ..\bin. The patch decrypts, rewrites the
 * bytecode, encrypts again and keeps the original as code.orig.
 */
public class CatanSavePatch {

    static final String FIX_INTERNAL = "de/brettspielwelt/client/base/SaveDirFix";
    static final ClassDesc FIX = ClassDesc.ofInternalName(FIX_INTERNAL);
    static final ClassDesc FILE = ClassDesc.of("java.io.File");
    static final ClassDesc VECTOR = ClassDesc.of("java.util.Vector");
    static final MethodTypeDesc VOID = MethodTypeDesc.ofDescriptor("()V");

    static final String MENU_BOARD = "de/brettspielwelt/client/board/MenuBoard";
    static final String SPIELER_DATA = "de/brettspielwelt/client/board/menucomponents/SpielerData";
    static final String SPIELER_WAHL = "de/brettspielwelt/client/board/MenuSpielerWahl";
    static final String MENU_RANGLISTE = "de/brettspielwelt/client/board/MenuRangliste";
    static final String GAME_STORE = "de/brettspielwelt/server/games/GameStore";
    static final String COW_PREFS = "de/brettspielwelt/client/base/COWPreferences";
    static final String BASE_GAME_BOARD = "de/brettspielwelt/client/games/boards/BaseGameBoard";
    static final String CHAT_TOOL = "de/brettspielwelt/client/tool/ChatTool2";
    static final String SPIELHILFE = "de/brettspielwelt/client/tool/SpielhilfeTool";
    static final String TEXT_RECT = "de/brettspielwelt/client/util/TextRectangle";
    static final String KI_SPEECH = "de/brettspielwelt/server/ki/KISpeech";
    static final String SUR_INFORMER = "de/brettspielwelt/server/games/StaedteUndRitterInformer";
    static final String SUR_BOARD = "de/brettspielwelt/client/games/boards/StaedteUndRitterBoard";
    static final String SIEDLER_BOARD = "de/brettspielwelt/client/games/boards/SiedlerBoard";
    static final String SIEDLER_BASE_INF = "de/brettspielwelt/server/games/SiedlerBaseInformer";
    static final ClassDesc BUGFIX = ClassDesc.of("de.brettspielwelt.client.base.BugFix");
    static final String SAVE_GAME_DESC =
            "(Lde/brettspielwelt/server/base/ServerThread;Lde/brettspielwelt/server/base/BaseInformer;ZLjava/lang/String;)V";

    /** Rules that must hit at least once, otherwise this is an unknown game version. */
    static final String[] REQUIRED = {
        "File.list -> SaveDirFix.list",
        "File.mkdir -> File.mkdirs",
        "MenuBoard.<clinit>: ensureDirs",
        "SpielerData.save: ensureDirs",
        "GameStore.saveGame: ensureDirs",
        "MenuSpielerWahl.selected: row guard",
        "COWPreferences.gross: 40 -> 512",
        "COWPreferences.getDeviceResolutions: 180 -> 512",
        "BaseGameBoard.drawAnim: null-safe body",
        "ChatTool2.paint: openBubbles.elementAt safe",
        "ChatTool2.paint: Bubble.paint null-safe",
        "SpielhilfeTool.buttonPressed: back needs 2 pages",
        "TextRectangle: getFontMetrics(Font) null-safe",
        "TextRectangle: substring clamped",
        "KISpeech: String.replace null-safe",
        "StaedteUndRitterInformer.setHaendler: -> SuRFix",
        "StaedteUndRitterInformer.setFlotte: -> SuRFix",
        "ChatTool2$Bubble.paint: real screen border",
        "closeWindow: hide dialogs",
        "SiedlerBaseInformer.tausch: clear competing offers",
    };

    /** Whole method bodies that are replaced by a call to a static helper (this, args...). */
    record Delegate(String cls, String namePrefix, String nameSuffix, String desc, String helper, String rule) {
        boolean matches(String c, String n, String d) {
            return cls.equals(c) && n.startsWith(namePrefix) && n.endsWith(nameSuffix) && desc.equals(d);
        }
    }

    static final Delegate[] DELEGATES = {
        new Delegate(BASE_GAME_BOARD, "drawAnim", "", "(Ljava/awt/Graphics;)V",
                "de/brettspielwelt/client/games/boards/BoardFix", "BaseGameBoard.drawAnim: null-safe body"),
        // "setHändler": the umlaut is matched via prefix/suffix
        new Delegate(SUR_INFORMER, "setH", "ndler", "(III)Z",
                "de/brettspielwelt/server/games/SuRFix", "StaedteUndRitterInformer.setHaendler: -> SuRFix"),
        new Delegate(SUR_INFORMER, "setFlotte", "", "(I[I)I",
                "de/brettspielwelt/server/games/SuRFix", "StaedteUndRitterInformer.setFlotte: -> SuRFix"),
    };

    static final Map<String, Integer> hits = new TreeMap<>();

    static void hit(String rule) {
        hits.merge(rule, 1, Integer::sum);
    }

    static int totalHits() {
        return hits.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Encrypt and decrypt are the same operation. */
    static byte[] crypt(byte[] in) {
        byte[] out = in.clone();
        int key = 238;
        for (int i = 0; i < out.length; i++) {
            key = (key + 3) & 255;
            out[i] ^= (byte) key;
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: java CatanSavePatch.java <ClientDir> <helperClassDir> | --dump <outDir>");
            System.exit(2);
        }
        Path client = Path.of(args[0]);
        Path code = client.resolve("code");
        Path orig = client.resolve("code.orig");

        Map<String, byte[]> classes = readArchive(Files.exists(orig) ? orig : code);
        if (classes.containsKey(FIX_INTERNAL + ".class")) {
            throw new IllegalStateException("'code' ist schon gepatcht, aber code.orig fehlt - Original zuerst wiederherstellen.");
        }
        if (args[1].equals("--dump")) {
            Path out = Path.of(args[2]);
            for (var e : classes.entrySet()) {
                Path p = out.resolve(e.getKey());
                Files.createDirectories(p.getParent());
                Files.write(p, crypt(e.getValue()));
            }
            System.out.println("Decrypted " + classes.size() + " classes to " + out);
            return;
        }
        Map<String, byte[]> patched = new LinkedHashMap<>();
        List<String> changed = new ArrayList<>();
        for (var e : classes.entrySet()) {
            byte[] plain = crypt(e.getValue());
            if (!isClassFile(plain)) {
                throw new IllegalStateException("Entschluesselung fehlgeschlagen: " + e.getKey());
            }
            int before = totalHits();
            byte[] out = patchClass(plain);
            if (totalHits() != before) {
                changed.add(e.getKey());
                patched.put(e.getKey(), crypt(out));
            } else {
                patched.put(e.getKey(), e.getValue());
            }
        }

        List<String> missing = new ArrayList<>();
        for (String r : REQUIRED) {
            if (!hits.containsKey(r)) missing.add(r);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Unbekannte Spielversion, Regeln ohne Treffer: " + missing);
        }

        Path helperDir = Path.of(args[1]);
        List<Path> helpers;
        try (var st = Files.walk(helperDir)) {
            helpers = st.filter(f -> f.toString().endsWith(".class")).sorted().toList();
        }
        for (Path h : helpers) {
            byte[] b = Files.readAllBytes(h);
            if (!isClassFile(b)) throw new IllegalStateException("Keine Klassendatei: " + h);
            String name = helperDir.relativize(h).toString().replace('\\', '/');
            if (classes.containsKey(name)) throw new IllegalStateException("Hilfsklasse ueberschreibt Spielklasse: " + name);
            patched.put(name, crypt(b));
            System.out.println("Hilfsklasse: " + name);
        }
        if (!patched.containsKey(FIX_INTERNAL + ".class")) {
            throw new IllegalStateException("SaveDirFix.class fehlt in " + helperDir);
        }

        Path tmp = client.resolve("code.new");
        writeArchive(tmp, patched);
        // sanity check: everything in the new archive decrypts and parses
        for (var e : readArchive(tmp).entrySet()) {
            ClassFile.of().parse(crypt(e.getValue()));
        }
        if (!Files.exists(orig)) {
            Files.copy(code, orig);
            System.out.println("Backup: " + orig);
        }
        Files.move(tmp, code, StandardCopyOption.REPLACE_EXISTING);
        new java.io.File(client.toFile(), "kiStuff/save/ki").mkdirs();
        new java.io.File(client.toFile(), "KiSaveGames").mkdirs();

        System.out.println("Gepatchte Klassen (" + changed.size() + "):");
        changed.forEach(c -> System.out.println("  " + c));
        System.out.println("Treffer pro Regel:");
        hits.forEach((k, v) -> System.out.println("  " + v + "x " + k));
        System.out.println("Fertig.");
    }

    static boolean isClassFile(byte[] b) {
        return b.length > 4 && (b[0] & 255) == 0xCA && (b[1] & 255) == 0xFE
                && (b[2] & 255) == 0xBA && (b[3] & 255) == 0xBE;
    }

    static Map<String, byte[]> readArchive(Path zip) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
                ZipEntry ze = en.nextElement();
                if (ze.isDirectory()) continue;
                try (InputStream in = z.getInputStream(ze)) {
                    m.put(ze.getName(), in.readAllBytes());
                }
            }
        }
        return m;
    }

    static void writeArchive(Path zip, Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (var e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        Files.write(zip, bos.toByteArray());
    }

    static byte[] patchClass(byte[] plain) {
        ClassFile cf = ClassFile.of();
        ClassModel cm = cf.parse(plain);
        String cls = cm.thisClass().asInternalName();
        ClassTransform ct = (cb, ce) -> {
            Delegate del = null;
            if (ce instanceof MethodModel mm) {
                for (Delegate dl : DELEGATES) {
                    if (dl.matches(cls, mm.methodName().stringValue(), mm.methodType().stringValue())) del = dl;
                }
            }
            if (del != null) {
                MethodModel mm = (MethodModel) ce;
                Delegate dl = del;
                MethodTypeDesc mt = mm.methodTypeSymbol();
                // helper method name = method name with non-ASCII letters replaced (setHändler -> setHaendler)
                String helperName = mm.methodName().stringValue().replace(String.valueOf((char) 0xE4), "ae");
                MethodTypeDesc helperType = mt.insertParameterTypes(0, ClassDesc.ofInternalName(cls));
                cb.transformMethod(mm, (mb, me) -> {
                    if (me instanceof CodeModel) {
                        mb.withCode(c -> {
                            c.aload(0);
                            int slot = 1;
                            for (ClassDesc pt : mt.parameterList()) {
                                TypeKind k = TypeKind.from(pt);
                                c.loadLocal(k, slot);
                                slot += k.slotSize();
                            }
                            c.invokestatic(ClassDesc.ofInternalName(dl.helper()), helperName, helperType);
                            c.return_(TypeKind.from(mt.returnType()));
                        });
                        hit(dl.rule());
                    } else {
                        mb.with(me);
                    }
                });
            } else if (ce instanceof MethodModel mm && mm.code().isPresent()) {
                cb.transformMethod(mm, MethodTransform.transformingCode(
                        codeTransform(cls, mm.methodName().stringValue(), mm.methodType().stringValue())));
            } else {
                cb.with(ce);
            }
        };
        return cf.transformClass(cm, ct);
    }

    static CodeTransform codeTransform(String cls, String method, String desc) {
        return new CodeTransform() {
            String lastGetfield = "";
            boolean firstSizeDone;

            /** Small call-site replacements for the remaining crashes in err.log. */
            boolean bugFix(CodeBuilder b, CodeElement e) {
                if (cls.equals(CHAT_TOOL + "$Bubble") && method.equals("paint")
                        && e instanceof FieldInstruction bf && bf.opcode() == Opcode.GETFIELD
                        && bf.owner().asInternalName().equals(CHAT_TOOL) && bf.name().equalsString("border")) {
                    // bubbles were clamped into a fixed 1024x768 area -> wrong place on bigger screens
                    b.invokestatic(ClassDesc.of("de.brettspielwelt.client.tool.ChatFix"), "border",
                            MethodTypeDesc.ofDescriptor("(L" + CHAT_TOOL + ";)Ljava/awt/Rectangle;"));
                    hit("ChatTool2$Bubble.paint: real screen border");
                    return true;
                }
                if (e instanceof FieldInstruction f && f.opcode() == Opcode.GETFIELD) {
                    lastGetfield = f.name().stringValue();
                    return false;
                }
                if (!(e instanceof InvokeInstruction i)) return false;
                String o = i.owner().asInternalName();
                String n = i.name().stringValue();
                String d = i.type().stringValue();
                if (cls.equals(CHAT_TOOL) && method.equals("paint")) {
                    // speech bubbles are removed by another thread while paint() iterates over them
                    if (o.equals("java/util/Vector") && n.equals("elementAt") && lastGetfield.equals("openBubbles")) {
                        b.invokestatic(BUGFIX, "elementAtOrNull", MethodTypeDesc.ofDescriptor("(Ljava/util/Vector;I)Ljava/lang/Object;"));
                        hit("ChatTool2.paint: openBubbles.elementAt safe");
                        return true;
                    }
                    if (o.equals(CHAT_TOOL + "$Bubble") && n.equals("paint") && d.equals("(Ljava/awt/Graphics;)V")) {
                        b.invokestatic(ClassDesc.of("de.brettspielwelt.client.tool.ChatFix"), "paintBubble",
                                MethodTypeDesc.ofDescriptor("(L" + CHAT_TOOL + "$Bubble;Ljava/awt/Graphics;)V"));
                        hit("ChatTool2.paint: Bubble.paint null-safe");
                        return true;
                    }
                }
                if (cls.equals(SPIELHILFE) && method.equals("buttonPressed") && !firstSizeDone
                        && o.equals("java/util/Vector") && n.equals("size") && lastGetfield.equals("history")) {
                    // "back": removes page 0 and then reads page 0 -> needs at least 2 entries
                    firstSizeDone = true;
                    b.invokestatic(BUGFIX, "sizeMinusOne", MethodTypeDesc.ofDescriptor("(Ljava/util/Vector;)I"));
                    hit("SpielhilfeTool.buttonPressed: back needs 2 pages");
                    return true;
                }
                if (cls.equals(TEXT_RECT)) {
                    if (o.equals("java/awt/Graphics") && n.equals("getFontMetrics") && d.equals("(Ljava/awt/Font;)Ljava/awt/FontMetrics;")) {
                        b.invokestatic(BUGFIX, "fontMetrics",
                                MethodTypeDesc.ofDescriptor("(Ljava/awt/Graphics;Ljava/awt/Font;)Ljava/awt/FontMetrics;"));
                        hit("TextRectangle: getFontMetrics(Font) null-safe");
                        return true;
                    }
                    if ((method.equals("drawAll") || method.equals("getStrWidth"))
                            && o.equals("java/lang/String") && n.equals("substring") && d.equals("(II)Ljava/lang/String;")) {
                        b.invokestatic(BUGFIX, "substringSafe",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;II)Ljava/lang/String;"));
                        hit("TextRectangle: substring clamped");
                        return true;
                    }
                }
                if (cls.equals(KI_SPEECH) && o.equals("java/lang/String") && n.equals("replace")
                        && d.equals("(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")) {
                    b.invokestatic(BUGFIX, "replaceSafe", MethodTypeDesc.ofDescriptor(
                            "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"));
                    hit("KISpeech: String.replace null-safe");
                    return true;
                }
                return false;
            }

            @Override
            public void atStart(CodeBuilder b) {
                if (cls.equals(SPIELER_DATA) && method.equals("save")) {
                    b.invokestatic(FIX, "ensureDirs", VOID);
                    hit("SpielerData.save: ensureDirs");
                }
                if (cls.equals(GAME_STORE) && method.equals("saveGame") && desc.equals(SAVE_GAME_DESC)) {
                    b.invokestatic(FIX, "ensureDirs", VOID);
                    hit("GameStore.saveGame: ensureDirs");
                }
                if ((cls.equals(SUR_BOARD) || cls.equals(SIEDLER_BOARD)) && method.equals("closeWindow") && desc.equals("()V")) {
                    b.aload(0).invokestatic(ClassDesc.of("de.brettspielwelt.client.games.boards.BoardFix"), "hideWindows",
                            MethodTypeDesc.ofDescriptor("(Lde/brettspielwelt/client/games/boards/SiedlerBaseBoard;)V"));
                    hit("closeWindow: hide dialogs");
                }
                if (cls.equals(SIEDLER_BASE_INF) && method.equals("tausch") && desc.equals("(II)J")) {
                    b.aload(0).iload(1).iload(2).invokestatic(ClassDesc.of("de.brettspielwelt.server.games.TradeFix"),
                            "clearCompetingOffers", MethodTypeDesc.ofDescriptor("(L" + SIEDLER_BASE_INF + ";II)V"));
                    hit("SiedlerBaseInformer.tausch: clear competing offers");
                }
                if (cls.equals(SPIELER_WAHL) && method.equals("selected") && desc.equals("(II)V")) {
                    // if (SaveDirFix.badRow(tab, row)) return;
                    Label ok = b.newLabel();
                    b.aload(0)
                     .getfield(ClassDesc.ofInternalName(SPIELER_WAHL), "tab", VECTOR)
                     .iload(2)
                     .invokestatic(FIX, "badRow", MethodTypeDesc.ofDescriptor("(Ljava/util/Vector;I)Z"))
                     .ifeq(ok)
                     .return_()
                     .labelBinding(ok);
                    hit("MenuSpielerWahl.selected: row guard");
                }
            }

            @Override
            public void accept(CodeBuilder b, CodeElement e) {
                if (bugFix(b, e)) return;
                if (e instanceof InvokeInstruction i && i.opcode() == Opcode.INVOKEVIRTUAL
                        && i.owner().asInternalName().equals("java/io/File")) {
                    String n = i.name().stringValue();
                    String d = i.type().stringValue();
                    // only where the profile folder is listed; other classes (e.g. CommandLoader)
                    // deliberately check list() == null for a fallback
                    if (n.equals("list") && d.equals("()[Ljava/lang/String;")
                            && (cls.equals(SPIELER_WAHL) || cls.equals(MENU_RANGLISTE))) {
                        b.invokestatic(FIX, "list", MethodTypeDesc.ofDescriptor("(Ljava/io/File;)[Ljava/lang/String;"));
                        hit("File.list -> SaveDirFix.list");
                        return;
                    }
                    if (n.equals("mkdir") && d.equals("()Z")) {
                        b.invokevirtual(FILE, "mkdirs", MethodTypeDesc.ofDescriptor("()Z"));
                        hit("File.mkdir -> File.mkdirs");
                        return;
                    }
                }
                if (cls.equals(MENU_BOARD) && method.equals("<clinit>")
                        && e instanceof ReturnInstruction r && r.opcode() == Opcode.RETURN) {
                    b.invokestatic(FIX, "ensureDirs", VOID);
                    hit("MenuBoard.<clinit>: ensureDirs");
                }
                if (cls.equals(COW_PREFS) && e instanceof ConstantInstruction.ArgumentConstantInstruction c) {
                    // resolution table: 20 entries were too few for modern monitors (AIOOBE "Index 40")
                    if (method.equals("<clinit>") && c.opcode() == Opcode.BIPUSH && c.constantValue() == 40) {
                        b.sipush(512);
                        hit("COWPreferences.gross: 40 -> 512");
                        return;
                    }
                    if (method.equals("getDeviceResolutions") && c.opcode() == Opcode.SIPUSH && c.constantValue() == 180) {
                        b.sipush(512);
                        hit("COWPreferences.getDeviceResolutions: 180 -> 512");
                        return;
                    }
                }
                b.with(e);
            }
        };
    }
}

package ru.deewend.tsrgapplytool;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TSRGApplyTool {
    public static final String VERSION = "1.0";

    private final File projectDir;
    private final File sourceFile;
    private int lineNumber;

    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, String> functions = new HashMap<>();

    public TSRGApplyTool(File projectDir, File sourceFile) {
        this.projectDir = projectDir;
        this.sourceFile = sourceFile;
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Welcome to TSRGApplyTool v" + VERSION + "!");
        System.out.println("Designed for Minecraft Forge 1.16.5 and earlier " +
                "versions to make deobfuscating mods a bit easier");
        System.out.println();

        if (args.length < 2) {
            printUsageAndExit();
        }
        if (args.length > 2) {
            System.out.println("Arguments \"" + args[2] + "\" and so on will be ignored");
            System.out.println("Run the utility with no arguments to view Usage help");
            System.out.println();
            waitForEnterKey();
        }
        File projectDir = new File(args[0]);
        if (!projectDir.isDirectory()) {
            System.err.println("The first argument \"" + args[0] + "\" " +
                    "should be a valid path to the project directory");

            errorExit();
        }
        File sourceFile = new File(args[1]);
        if (!sourceFile.exists()) {
            System.err.println("The second argument \"" + args[1] + "\" " +
                    "should be a valid path to the source file");

            errorExit();
        }
        System.out.println("Checked arguments, starting...");

        (new TSRGApplyTool(projectDir, sourceFile)).start();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void waitForEnterKey() throws IOException {
        System.out.println("Press Enter to resume running the utility...");
        System.in.read();
    }

    public static void printUsageAndExit() {
        System.err.println("Usage: java -jar TSRGApplyTool.jar " +
                "<path_to_forgegradle_project> <path_to_source_file>");
        System.err.println("Example: java -jar TSRGApplyTool.jar " +
                "C:\\ForgeMod " +
                "C:\\ForgeMod\\src\\main\\java\\com\\example\\mod\\ExampleMod.java");

        errorExit();
    }

    public static void errorExit() {
        System.exit(-1);
    }

    @SuppressWarnings("CommentedOutCode")
    public void start() throws IOException {
        File tsrgFile = new File(projectDir + "/build/createMcpToSrg/output.tsrg");
        if (!tsrgFile.exists()) {
            throw new RuntimeException("Could " +
                    "not find this tsrg file: " + tsrgFile.getAbsolutePath() + ". " +
                    "Have you run any Gradle commands against the project?");
        }

        System.out.println("Loading createMcpToSrg/output.tsrg...");
        try (BufferedReader reader = reader(tsrgFile)) {
            reader.readLine(); lineNumber++; // "tsrg2 left right"
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                String[] parts;
                if (!line.startsWith("\t")) {
                    parts = line.split(" ");
                    if (parts.length != 2) badLine("Expected strictly 2 tokens");

                    if (!parts[0].equals(parts[1])) {
                        badLine("Expected first token to be equal to second one");
                    }

                    continue;
                }
                line = line.substring(1);
                parts = line.split(" ");

                //System.out.println(line);
                //System.out.println(Arrays.toString(parts));
                if (parts.length == 2) {
                    if (!parts[1].startsWith("field_")) {
                        if (parts[0].equals(parts[1])) continue;

                        badLine("Expected second token to start with \"field_\"");
                    }
                    if (fields.containsKey(parts[1])) {
                        badLine("Found " +
                                "second mention of the field we've registered earlier");
                    }

                    fields.put(parts[1], parts[0]);
                } else if (parts.length == 3) {
                    if (!parts[2].startsWith("func_")) {
                        if (parts[0].equals(parts[2])) continue;

                        badLine("Expected second token to start with \"func_\"");
                    }
                    if (functions.containsKey(parts[2])) {
                        String value = functions.get(parts[2]);
                        if (parts[0].equals(value)) continue;

                        badLine("Found " +
                                "second mention of the function we've registered earlier");
                    }

                    functions.put(parts[2], parts[0]);
                } else {
                    badLine("Expected strictly 2 or 3 tokens");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Done loading DB!");
        System.out.println("Total count of fields: " + fields.size());
        System.out.println("Total count of [unique] function names: " + functions.size());
        System.out.println();

        System.out.println("Processing " + sourceFile.getAbsolutePath() + "...");
        System.out.println("Please confirm that you have a backup of this file");
        waitForEnterKey();

        System.out.println("Resuming...");
        lineNumber = 0;
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = reader(sourceFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                while (line.contains("field_") || line.contains("func_")) {
                    int idx = line.indexOf("field_");
                    if (idx == -1) idx = line.indexOf("func_");

                    int end = getEndIndex(line, idx);
                    String name = line.substring(idx, end);

                    String value;
                    if (fields.containsKey(name)) {
                        value = fields.get(name);
                    } else if (functions.containsKey(name)) {
                        value = functions.get(name);
                    } else {
                        System.err.println("[WARN] " +
                                "Could not deobfuscate \"" + name + "\". Skipping...");

                        continue;
                    }
                    line = line.replace(name, value);
                }

                lines.add(line);
            }
        }

        System.out.println("Writing changes...");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile))) {
            for (String line : lines) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        }

        System.out.println("Done!");
        System.out.println();
        System.out.println("McLord Discord: https://mclord.ru/discord");
        System.out.println("McLord Forum: https://forum.mclord.ru");
    }

    private static int getEndIndex(String line, int idx) {
        for ( ; idx < line.length(); idx++) {
            char current = line.charAt(idx);

            if (current == '_') continue;
            if (current >= '0' && current <= '9') continue;
            if (current >= 'a' && current <= 'z') continue;
            if (current >= 'A' && current <= 'Z') continue;

            return idx;
        }

        return line.length();
    }

    private static BufferedReader reader(File file) throws FileNotFoundException {
        return new BufferedReader(new FileReader(file));
    }

    private void badLine(String description) {
        description = (description == null ? "<not provided>" : description);

        throw new RuntimeException("Found an " +
                "issue on line " + lineNumber + ". Description: " + description);
    }
}

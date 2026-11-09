/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.sheriff.token.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps every DSL-JSON read in the production sources inside a catch that covers
 * {@link ArrayIndexOutOfBoundsException}.
 * <p>
 * DSL-JSON reports a payload truncated inside a string escape — a trailing lone backslash, a cut-off
 * unicode escape — by reading past the end of its buffer, i.e. as an
 * {@link ArrayIndexOutOfBoundsException} rather than the {@link IOException} its API declares. A read
 * guarded only by {@code catch (IOException e)} therefore lets an attacker-shaped body escape as an
 * undeclared unchecked exception. Every site found so far was fixed by hand; this guard keeps the next
 * one from landing.
 * <p>
 * <strong>What is scanned.</strong> The {@code src/main/java} tree of every module in the repository, as
 * source text — the client module is not on this module's test classpath, so a bytecode scan could not
 * see it. A <em>site</em> is a {@code .deserialize(...)} or {@code .newReader(...)} call in a file that
 * imports {@code com.dslplatform.json}, plus every call of {@code MapRepresentation.fromJson}, the
 * validation module's DSL-JSON wrapper, which propagates the exception unchanged.
 * <p>
 * <strong>What counts as covered.</strong> A site is covered when a {@code try} block of its enclosing
 * method holds it and one of that {@code try}'s catch clauses names
 * {@link ArrayIndexOutOfBoundsException} or {@link IndexOutOfBoundsException}. A broad catch
 * ({@code RuntimeException}, {@code Exception}, {@code Throwable}) does <em>not</em> count: it counts
 * only when the code unit is a listed {@link #EXEMPT exemption} with its reason, so a new uncovered site
 * fails on the day it lands. A {@code try} outside a block lambda does not cover the lambda body,
 * because the body runs later.
 * <p>
 * Non-vacuity legs keep a passing verdict meaningful: both the validation and the client sources must
 * be in the scanned universe, the detector must find sites, it must classify a site it is pointed at in
 * both directions, and every {@link #EXEMPT} entry must still excuse a live uncovered site — so an
 * exemption made obsolete by a fix fails here until the entry is removed.
 */
@DisplayName("Every DSL-JSON read catches the ArrayIndexOutOfBoundsException a truncated escape raises")
class DslJsonTruncatedEscapeGuardTest {

    /** Reason category: the read propagates to callers that each catch the exception themselves. */
    private static final String EVERY_CALLER_CATCHES =
            "declares IOException and propagates; every caller catches ArrayIndexOutOfBoundsException";

    /** Reason category: a deliberate broad catch that folds every unchecked library failure. */
    private static final String BROAD_FOLD =
            "deliberate broad catch (RuntimeException) folding every unchecked DSL-JSON failure into the"
                    + " IOException the call sites already handle";

    /**
     * Every production code unit holding a DSL-JSON read that no narrow catch in the same method
     * covers, with the reason it is safe. Keyed by {@code binary.TypeName#method}. An entry that stops
     * excusing a live uncovered site is a failure, not a leftover.
     */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            Map.entry("de.cuioss.sheriff.token.validation.json.MapRepresentation#fromJson",
                    EVERY_CALLER_CATCHES + " — each MapRepresentation.fromJson call is itself a site of"
                            + " this guard, so the claim is checked rather than trusted"),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.NonValidatingJwtParser#decodeJwtHeader",
                    EVERY_CALLER_CATCHES + " — decodeTokenParts and handleJweToken catch"
                            + " IOException | ArrayIndexOutOfBoundsException"),
            Map.entry("de.cuioss.sheriff.token.validation.pipeline.NonValidatingJwtParser#decodePayload",
                    EVERY_CALLER_CATCHES + " — its only caller decodeTokenParts catches"
                            + " IOException | ArrayIndexOutOfBoundsException"),
            Map.entry("de.cuioss.sheriff.token.client.flow.TokenEndpointClient#deserialize",
                    BROAD_FOLD + " — the single deserialize seam for every token-endpoint payload"),
            Map.entry("de.cuioss.sheriff.token.client.flow.TokenEndpointClient#duplicateTopLevelMember",
                    BROAD_FOLD + " — the newReader walk over the top-level member names"));

    /** Catch types that cover the truncated-escape failure. */
    private static final Set<String> NARROW_CATCH_TYPES = Set.of(
            "ArrayIndexOutOfBoundsException", "IndexOutOfBoundsException");

    private static final Pattern DSL_JSON_READ = Pattern.compile("\\.\\s*(deserialize|newReader)\\s*\\(");
    private static final Pattern MAP_REPRESENTATION_READ =
            Pattern.compile("\\bMapRepresentation\\s*\\.\\s*fromJson\\s*\\(");
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern THROWS_CLAUSE = Pattern.compile("\\)\\s*+throws\\s++[\\w.$\\s,<>?]++$");
    private static final Set<String> NON_METHOD_NAMES = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "try", "return", "new", "throw");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", "node_modules");

    /** The repository root, located by walking up from the module directory. */
    private static Path repositoryRoot;

    /** Every scanned production source file, relative to {@link #repositoryRoot}. */
    private static List<String> scannedFiles;

    /** Every DSL-JSON read site found in the scanned universe. */
    private static List<Site> sites;

    /**
     * One DSL-JSON read.
     *
     * @param codeUnit  the {@code binary.TypeName#method} key an exemption is recorded under
     * @param location  file and line, for messages
     * @param covered   whether a narrow catch of the enclosing method covers the call
     */
    record Site(String codeUnit, String location, boolean covered) {
    }

    @BeforeAll
    static void scanProductionSources() throws IOException {
        repositoryRoot = locateRepositoryRoot();
        List<Path> files = collectMainSources(repositoryRoot);
        scannedFiles = new ArrayList<>();
        sites = new ArrayList<>();
        for (Path file : files) {
            String relative = repositoryRoot.relativize(file).toString().replace('\\', '/');
            scannedFiles.add(relative);
            sites.addAll(analyze(relative, Files.readString(file)));
        }
    }

    @Test
    @DisplayName("the scan covers the validation and the client sources and finds sites in them")
    void shouldScanANonEmptyUniverseAndFindSites() {
        assertTrue(scannedFiles.stream().anyMatch(f -> f.startsWith("token-sheriff-validation/src/main/java/")),
                "no validation production source was scanned under " + repositoryRoot);
        assertTrue(scannedFiles.stream().anyMatch(f -> f.startsWith("token-sheriff-client/src/main/java/")),
                "no client production source was scanned under " + repositoryRoot + " — the client reads"
                        + " authorization-server bodies with DSL-JSON, so a scan blind to it proves nothing");
        assertFalse(sites.isEmpty(), "the scan found no DSL-JSON read in " + scannedFiles.size()
                + " files. The modules are known to hold several, so zero sites means the detector stopped"
                + " matching, not that the tree is clean.");
        assertTrue(sitesOf("de.cuioss.sheriff.token.validation.jwks.parser.JwksParser#parse").stream()
                .allMatch(Site::covered) && !sitesOf(
                "de.cuioss.sheriff.token.validation.jwks.parser.JwksParser#parse").isEmpty(),
                "positive control: JwksParser.parse reads with DSL-JSON inside"
                        + " catch (IOException | ArrayIndexOutOfBoundsException) and must be found covered");
        assertTrue(sitesOf("de.cuioss.sheriff.token.client.flow.TokenEndpointClient#deserialize").stream()
                .noneMatch(Site::covered) && !sitesOf(
                "de.cuioss.sheriff.token.client.flow.TokenEndpointClient#deserialize").isEmpty(),
                "negative control: TokenEndpointClient.deserialize is guarded only by a broad"
                        + " catch (RuntimeException), which must not count as coverage");
    }

    @Test
    @DisplayName("no DSL-JSON read is left without a catch covering ArrayIndexOutOfBoundsException")
    void shouldRejectAnUncoveredRead() {
        List<String> uncovered = new ArrayList<>();
        for (Site site : sites) {
            if (!site.covered() && !EXEMPT.containsKey(site.codeUnit())) {
                uncovered.add(site.codeUnit() + " at " + site.location());
            }
        }
        assertTrue(uncovered.isEmpty(),
                "DSL-JSON reads with no catch covering ArrayIndexOutOfBoundsException in their enclosing"
                        + " method: " + uncovered + ". DSL-JSON raises that exception, not IOException, for a"
                        + " payload truncated inside a string escape. Add ArrayIndexOutOfBoundsException to the"
                        + " catch around the read (it is the same malformed-input condition). A broad"
                        + " RuntimeException catch counts only as a listed EXEMPT entry with its reason.");
    }

    @Test
    @DisplayName("no exemption has outlived the uncovered read it excuses")
    void shouldRejectAStaleExemption() {
        Set<String> uncoveredUnits = new TreeSet<>();
        for (Site site : sites) {
            if (!site.covered()) {
                uncoveredUnits.add(site.codeUnit());
            }
        }
        List<String> stale = new ArrayList<>();
        EXEMPT.forEach((codeUnit, reason) -> {
            if (!uncoveredUnits.contains(codeUnit)) {
                stale.add(codeUnit);
            }
        });
        assertTrue(stale.isEmpty(), "EXEMPT names code units holding no uncovered DSL-JSON read: " + stale
                + ". Either the read was removed or now carries a narrow catch — delete the entry, because"
                + " left in place it silently pre-approves a future uncovered read — or the class or method"
                + " was renamed and the key must follow it.");
    }

    @Test
    @DisplayName("every exemption records a reason")
    void shouldRequireAReasonForEveryExemption() {
        List<String> withoutReason = new ArrayList<>();
        EXEMPT.forEach((codeUnit, reason) -> {
            if (reason == null || reason.isBlank()) {
                withoutReason.add(codeUnit);
            }
        });
        assertTrue(withoutReason.isEmpty(), "EXEMPT entries carrying no reason: " + withoutReason);
    }

    @Test
    @DisplayName("the detector tells a narrow catch from a broad one, a missing one and a lambda boundary")
    void shouldClassifySyntheticSites() {
        String source = """
                package sample;
                import com.dslplatform.json.DslJson;
                class Sample {
                    // dslJson.deserialize(X.class) in a comment is not a site
                    Object narrow(byte[] b) {
                        try {
                            return dslJson.deserialize(Map.class, b, b.length);
                        } catch (IOException | ArrayIndexOutOfBoundsException e) {
                            return null;
                        }
                    }
                    Object broad(byte[] b) throws IOException {
                        try {
                            return dslJson.newReader(b, b.length);
                        }
                        // a comment between the try block and its catch
                        catch (RuntimeException e) {
                            throw new IOException("}", e);
                        }
                    }
                    Object none(byte[] b) throws IOException {
                        String s = "dslJson.deserialize(";
                        return MapRepresentation.fromJson(dslJson, b);
                    }
                    Runnable lambda(byte[] b) {
                        try {
                            return () -> {
                                dslJson.deserialize(Map.class, b, b.length);
                            };
                        } catch (ArrayIndexOutOfBoundsException e) {
                            return null;
                        }
                    }
                    Object arrowCase(byte[] b, int mode) {
                        try {
                            switch (mode) {
                                case 1 -> {
                                    return dslJson.deserialize(Map.class, b, b.length);
                                }
                                default -> {
                                    return null;
                                }
                            }
                        } catch (ArrayIndexOutOfBoundsException e) {
                            return null;
                        }
                    }
                    static class Inner {
                        <T> T generic(Class<T> t, byte[] b) throws IOException {
                            try (var in = open(); var other = open()) {
                                return dslJson.deserialize(t, b, b.length);
                            } catch (final IndexOutOfBoundsException e) {
                                return null;
                            }
                        }
                    }
                }
                """;

        Map<String, Boolean> byUnit = new TreeMap<>();
        for (Site site : analyze("Sample.java", source)) {
            byUnit.put(site.codeUnit(), site.covered());
        }

        assertEquals(Map.of(
                        "sample.Sample#narrow", true,
                        "sample.Sample#broad", false,
                        "sample.Sample#none", false,
                        "sample.Sample#lambda", false,
                        "sample.Sample#arrowCase", true,
                        "sample.Sample$Inner#generic", true),
                byUnit, "the synthetic sites must be found once each and classified as annotated");
    }

    private static List<Site> sitesOf(String codeUnit) {
        return sites.stream().filter(site -> site.codeUnit().equals(codeUnit)).toList();
    }

    // ------------------------------------------------------------------------------------------------
    // Source analysis
    // ------------------------------------------------------------------------------------------------

    /**
     * Finds every DSL-JSON read in one source file and classifies it.
     *
     * @param fileName a name used in the site locations
     * @param source   the file content
     * @return the sites found, in source order
     */
    static List<Site> analyze(String fileName, String source) {
        String code = blankOutCommentsAndLiterals(source);
        List<Integer> callOffsets = new ArrayList<>();
        if (source.contains("com.dslplatform.json")) {
            Matcher matcher = DSL_JSON_READ.matcher(code);
            while (matcher.find()) {
                callOffsets.add(matcher.start());
            }
        }
        Matcher wrapper = MAP_REPRESENTATION_READ.matcher(code);
        while (wrapper.find()) {
            callOffsets.add(wrapper.start());
        }
        if (callOffsets.isEmpty()) {
            return List.of();
        }
        callOffsets.sort(Comparator.naturalOrder());

        Matcher packageMatcher = PACKAGE.matcher(code);
        String packagePrefix = packageMatcher.find() ? packageMatcher.group(1) + "." : "";
        Map<Integer, Integer> blocks = matchBraces(code);

        List<Site> result = new ArrayList<>();
        for (int offset : callOffsets) {
            result.add(classify(fileName, code, blocks, packagePrefix, offset));
        }
        return result;
    }

    private static Site classify(String fileName, String code, Map<Integer, Integer> blocks,
            String packagePrefix, int offset) {
        List<Integer> enclosing = new ArrayList<>();
        blocks.forEach((open, close) -> {
            if (open < offset && offset < close) {
                enclosing.add(open);
            }
        });
        enclosing.sort(Comparator.reverseOrder());

        boolean covered = false;
        boolean coverageBoundary = false;
        String method = null;
        List<String> types = new ArrayList<>();
        for (int open : enclosing) {
            BlockKind kind = blockKind(code, open);
            switch (kind.type()) {
                case TRY -> {
                    if (!coverageBoundary && method == null
                            && catchTypes(code, blocks.get(open), blocks).stream().anyMatch(NARROW_CATCH_TYPES::contains)) {
                        covered = true;
                    }
                }
                case LAMBDA -> coverageBoundary = true;
                case METHOD -> {
                    if (method == null) {
                        method = kind.name();
                    }
                    coverageBoundary = true;
                }
                case TYPE -> {
                    types.addFirst(kind.name());
                    coverageBoundary = true;
                }
                case OTHER -> {
                    // plain blocks (if, for, switch, initializers) neither cover nor bound
                }
            }
        }
        String typeName = packagePrefix + String.join("$", types);
        if (method == null) {
            method = "<initializer>";
        } else if (!types.isEmpty() && method.equals(types.getLast())) {
            method = "<init>";
        }
        return new Site(typeName + "#" + method, fileName + ":" + lineOf(code, offset), covered);
    }

    private enum Kind {TRY, LAMBDA, METHOD, TYPE, OTHER}

    private record BlockKind(Kind type, String name) {
    }

    /** Classifies the block opened at {@code open} by the code in front of its brace. */
    private static BlockKind blockKind(String code, int open) {
        int last = skipWhitespaceBackward(code, open - 1);
        if (last < 0) {
            return new BlockKind(Kind.OTHER, null);
        }
        if (endsWithWord(code, last, "try")) {
            return new BlockKind(Kind.TRY, null);
        }
        int boundary = Math.max(code.lastIndexOf(';', open - 1),
                Math.max(code.lastIndexOf('{', open - 1), code.lastIndexOf('}', open - 1)));
        String header = code.substring(boundary + 1, open);
        if (last > 0 && code.charAt(last) == '>' && code.charAt(last - 1) == '-') {
            // an arrow switch case runs in place; only a lambda body runs later
            String arrowHead = header.strip();
            boolean switchCase = arrowHead.startsWith("case ") || arrowHead.startsWith("default");
            return new BlockKind(switchCase ? Kind.OTHER : Kind.LAMBDA, null);
        }
        Matcher type = TYPE_DECLARATION.matcher(header);
        if (type.find() && !header.substring(0, type.start()).contains("(")) {
            return new BlockKind(Kind.TYPE, type.group(1));
        }
        int closeParen;
        if (code.charAt(last) == ')') {
            closeParen = last;
        } else {
            Matcher throwsClause = THROWS_CLAUSE.matcher(code.substring(boundary + 1, last + 1));
            if (!throwsClause.find()) {
                return new BlockKind(Kind.OTHER, null);
            }
            closeParen = boundary + 1 + throwsClause.start();
        }
        int openParen = matchingOpenParen(code, closeParen);
        if (openParen < 0) {
            return new BlockKind(Kind.OTHER, null);
        }
        int nameEnd = skipWhitespaceBackward(code, openParen - 1);
        int nameStart = nameEnd;
        while (nameStart >= 0 && Character.isJavaIdentifierPart(code.charAt(nameStart))) {
            nameStart--;
        }
        String name = code.substring(nameStart + 1, nameEnd + 1);
        if ("try".equals(name)) {
            return new BlockKind(Kind.TRY, null);
        }
        if (name.isEmpty() || NON_METHOD_NAMES.contains(name) || !Character.isJavaIdentifierStart(name.charAt(0))) {
            return new BlockKind(Kind.OTHER, null);
        }
        int before = skipWhitespaceBackward(code, nameStart);
        if (before >= 0 && (code.charAt(before) == '.' || endsWithWord(code, before, "new"))) {
            // a qualified call or an anonymous class body, not a declaration
            return new BlockKind(Kind.OTHER, null);
        }
        return new BlockKind(Kind.METHOD, name);
    }

    /** The simple names of every type caught by the catch clauses following the try block closing at {@code close}. */
    private static List<String> catchTypes(String code, int close, Map<Integer, Integer> blocks) {
        List<String> caught = new ArrayList<>();
        int position = close + 1;
        while (true) {
            int start = skipWhitespaceForward(code, position);
            if (!code.startsWith("catch", start)) {
                return caught;
            }
            int openParen = skipWhitespaceForward(code, start + "catch".length());
            int closeParen = code.indexOf(')', openParen);
            if (code.charAt(openParen) != '(' || closeParen < 0) {
                return caught;
            }
            String parameter = code.substring(openParen + 1, closeParen).trim()
                    .replaceAll("\\s++[\\w$]++$", "").replaceAll("\\bfinal\\b", "");
            for (String alternative : parameter.split("\\|")) {
                String type = alternative.trim();
                caught.add(type.substring(type.lastIndexOf('.') + 1));
            }
            int body = skipWhitespaceForward(code, closeParen + 1);
            Integer bodyClose = blocks.get(body);
            if (bodyClose == null) {
                return caught;
            }
            position = bodyClose + 1;
        }
    }

    /** Pairs every opening brace with its closing brace. */
    private static Map<Integer, Integer> matchBraces(String code) {
        Map<Integer, Integer> pairs = new TreeMap<>();
        Deque<Integer> open = new ArrayDeque<>();
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                open.push(i);
            } else if (c == '}' && !open.isEmpty()) {
                pairs.put(open.pop(), i);
            }
        }
        return pairs;
    }

    private static int matchingOpenParen(String code, int closeParen) {
        int depth = 0;
        for (int i = closeParen; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean endsWithWord(String code, int last, String word) {
        int start = last - word.length() + 1;
        return start >= 0 && code.startsWith(word, start)
                && (start == 0 || !Character.isJavaIdentifierPart(code.charAt(start - 1)));
    }

    private static int skipWhitespaceBackward(String code, int index) {
        int i = index;
        while (i >= 0 && Character.isWhitespace(code.charAt(i))) {
            i--;
        }
        return i;
    }

    private static int skipWhitespaceForward(String code, int index) {
        int i = index;
        while (i < code.length() && Character.isWhitespace(code.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int lineOf(String code, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Replaces comments, string literals, text blocks and character literals with spaces, keeping line
     * breaks, so braces, parentheses and call patterns inside them are not mistaken for code.
     */
    static String blankOutCommentsAndLiterals(String source) {
        char[] out = source.toCharArray();
        int length = source.length();
        int i = 0;
        while (i < length) {
            char c = source.charAt(i);
            int end;
            if (source.startsWith("//", i)) {
                end = source.indexOf('\n', i);
                end = end < 0 ? length : end;
            } else if (source.startsWith("/*", i)) {
                end = source.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
            } else if (source.startsWith("\"\"\"", i)) {
                end = closingTextBlock(source, i + 3);
            } else if (c == '"' || c == '\'') {
                end = closingQuote(source, i + 1, c);
            } else {
                i++;
                continue;
            }
            for (int k = i; k < end; k++) {
                if (out[k] != '\n') {
                    out[k] = ' ';
                }
            }
            i = end;
        }
        return new String(out);
    }

    private static int closingTextBlock(String source, int from) {
        int i = from;
        while (i < source.length()) {
            if (source.charAt(i) == '\\') {
                i += 2;
            } else if (source.startsWith("\"\"\"", i)) {
                return i + 3;
            } else {
                i++;
            }
        }
        return source.length();
    }

    private static int closingQuote(String source, int from, char quote) {
        int i = from;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote || c == '\n') {
                return i + 1;
            } else {
                i++;
            }
        }
        return source.length();
    }

    // ------------------------------------------------------------------------------------------------
    // Universe
    // ------------------------------------------------------------------------------------------------

    private static Path locateRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("token-sheriff-validation"))
                    && Files.isDirectory(candidate.resolve("token-sheriff-client"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("repository root (holding token-sheriff-validation and"
                + " token-sheriff-client) not found above " + Path.of("").toAbsolutePath());
    }

    private static List<Path> collectMainSources(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(root) && (name.startsWith(".") || SKIPPED_DIRECTORIES.contains(name))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String path = file.toString().replace('\\', '/');
                if (path.endsWith(".java") && path.contains("/src/main/java/")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.naturalOrder());
        return files;
    }
}

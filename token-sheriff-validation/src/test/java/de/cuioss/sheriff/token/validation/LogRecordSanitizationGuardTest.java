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
package de.cuioss.sheriff.token.validation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps remote text out of this module's log lines unless it passed the commons-layer sanitiser
 * (ADR-0022: untrusted text is neutralised by the one {@code ErrorTextSanitizer} utility).
 * <p>
 * Key ids, key types, algorithm names and curve names arrive in JWKS documents and token headers, and
 * a parser's exception message quotes the input it rejected. Written raw into a log line, any of them
 * can carry CR/LF and forge further lines (CWE-117).
 * <p>
 * <strong>What is scanned.</strong> Every {@code LOGGER.trace/debug/info/warn/error(...)} call in the
 * module's {@code src/main/java}, as source text. An argument is <em>remote text</em> when it names
 * one of {@link #REMOTE_TEXT_NAMES} — the identifiers and accessors through which such values flow —
 * or reads an exception message ({@code .getMessage()}). A remote-text argument must reach the call
 * through a {@code sanitize...(...)} method. An exception object passed to a call that also renders
 * its message is a violation too: the appender prints the same raw message with the stack trace.
 * <p>
 * A site that is remote by name but safe for a recorded reason belongs in {@link #EXEMPT}, keyed by
 * {@code file:argument}. An exemption that no longer matches a site fails the stale leg.
 */
@DisplayName("Remote text reaches a log line only through the sanitiser")
class LogRecordSanitizationGuardTest {

    /**
     * Identifiers and accessor names that carry text from a JWKS document, a token header or a remote
     * error. Matched as whole identifier tokens.
     */
    private static final Set<String> REMOTE_TEXT_NAMES = Set.of(
            "kid", "kty", "keyId", "keyType", "alg", "algorithm", "crv", "getMessage");

    /**
     * Remote-by-name arguments that are safe for a recorded reason. Keyed by
     * {@code file-name:normalised-argument}.
     */
    private static final Map<String, String> EXEMPT = Map.of();

    private static final Pattern LOGGER_CALL =
            Pattern.compile("\\bLOGGER\\s*\\.\\s*(trace|debug|info|warn|error)\\s*\\(");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final Pattern SANITIZE_CALL = Pattern.compile("\\bsanitize\\w*\\s*\\(");
    private static final Pattern NUMERIC_VIEW = Pattern.compile("\\.(length|size|bitLength)\\(\\)$");

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static List<LoggerCall> calls;

    /**
     * One logger call.
     *
     * @param location  file name and line
     * @param arguments the top-level argument expressions, comments and literals blanked
     */
    record LoggerCall(String fileName, String location, List<String> arguments) {
    }

    @BeforeAll
    static void scanLoggerCalls() throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCES), "main sources not found at " + MAIN_SOURCES.toAbsolutePath());
        calls = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                calls.addAll(loggerCalls(file.getFileName().toString(),
                        Files.readString(file)));
            }
        }
    }

    @Test
    @DisplayName("the scan finds logger calls and remote-text arguments in them")
    void shouldScanANonEmptyUniverse() {
        assertTrue(calls.size() > 50, "only " + calls.size() + " logger calls found under "
                + MAIN_SOURCES.toAbsolutePath() + " — the detector stopped matching");
        assertTrue(calls.stream().anyMatch(call -> "KeyProcessor.java".equals(call.fileName())),
                "positive control: KeyProcessor logs remote key data and must be among the scanned calls");
        assertTrue(calls.stream().flatMap(call -> call.arguments().stream()).anyMatch(this::isRemoteText),
                "the scan found no remote-text argument at all, so its silence proves nothing");
    }

    @Test
    @DisplayName("every remote-text argument passes through a sanitize method")
    void shouldRejectUnsanitisedRemoteText() {
        List<String> violations = new ArrayList<>();
        for (LoggerCall call : calls) {
            for (String argument : violations(call)) {
                violations.add(call.location() + " -> " + argument);
            }
        }
        assertTrue(violations.isEmpty(), "Logger calls render remote text unsanitised: " + violations
                + ". Wrap the value in ErrorTextSanitizer.sanitize(...) (ADR-0022), and pass no exception"
                + " object whose message is the rejected input. A value that is remote by name but safe"
                + " belongs in EXEMPT with its reason.");
    }

    @Test
    @DisplayName("no exemption has outlived the site it excuses")
    void shouldRejectAStaleExemption() {
        Set<String> live = new HashSet<>();
        for (LoggerCall call : calls) {
            for (String argument : unsanitisedRemoteText(call)) {
                live.add(call.fileName() + ":" + argument);
            }
        }
        List<String> stale = EXEMPT.keySet().stream().filter(key -> !live.contains(key)).sorted().toList();
        assertTrue(stale.isEmpty(), "EXEMPT entries that match no logger argument: " + stale);
    }

    @Test
    @DisplayName("the classifier tells sanitised, unsanitised and exception arguments apart")
    void shouldClassifySyntheticCalls() {
        String source = """
                class Sample {
                    void m(JwkKey jwk, Exception e, String kid) {
                        LOGGER.warn(WARN.A, kid);
                        LOGGER.warn(WARN.B, ErrorTextSanitizer.sanitize(kid, '?', 64, "..."));
                        LOGGER.warn(e, WARN.C, sanitizeRemote(kid), sanitizeRemote(e.getMessage()));
                        LOGGER.debug("Parsed %s with %s", jwk.alg(), keys.size());
                        LOGGER.warn(WARN.D, "kid in a literal is not an argument", keyIds.length, kid.length());
                        // LOGGER.warn(WARN.E, kid); in a comment is not a call
                    }
                }
                """;
        List<LoggerCall> synthetic = loggerCalls("Sample.java", source);

        assertEquals(5, synthetic.size(), "the commented call must not be scanned: " + synthetic);
        assertEquals(List.of("kid"), violations(synthetic.getFirst()));
        assertEquals(List.of(), violations(synthetic.get(1)));
        assertEquals(List.of("e (exception rendered with its message)"), violations(synthetic.get(2)));
        assertEquals(List.of("jwk.alg()"), violations(synthetic.get(3)));
        assertEquals(List.of(), violations(synthetic.get(4)));
    }

    private boolean isRemoteText(String argument) {
        if (NUMERIC_VIEW.matcher(argument).find()) {
            // the length or size of a remote value carries no text of it
            return false;
        }
        Matcher identifiers = IDENTIFIER.matcher(argument);
        while (identifiers.find()) {
            if (REMOTE_TEXT_NAMES.contains(identifiers.group())) {
                return true;
            }
        }
        return false;
    }

    private List<String> unsanitisedRemoteText(LoggerCall call) {
        List<String> found = new ArrayList<>();
        for (String argument : call.arguments()) {
            if (isRemoteText(argument) && !SANITIZE_CALL.matcher(argument).find()) {
                found.add(argument);
            }
        }
        return found;
    }

    private List<String> violations(LoggerCall call) {
        List<String> found = new ArrayList<>();
        for (String argument : unsanitisedRemoteText(call)) {
            if (!EXEMPT.containsKey(call.fileName() + ":" + argument)) {
                found.add(argument);
            }
        }
        List<String> arguments = call.arguments();
        boolean rendersExceptionMessage = arguments.stream().anyMatch(argument -> argument.contains(".getMessage()"));
        if (!arguments.isEmpty() && rendersExceptionMessage && IDENTIFIER.matcher(arguments.getFirst()).matches()
                && arguments.stream().skip(1).anyMatch(argument -> argument.contains(arguments.getFirst() + ".getMessage()"))) {
            found.add(arguments.getFirst() + " (exception rendered with its message)");
        }
        return found;
    }

    // ------------------------------------------------------------------------------------------------
    // Source parsing
    // ------------------------------------------------------------------------------------------------

    static List<LoggerCall> loggerCalls(String fileName, String source) {
        String code = blankOutCommentsAndLiterals(source);
        List<LoggerCall> result = new ArrayList<>();
        Matcher matcher = LOGGER_CALL.matcher(code);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int close = matchingParen(code, open);
            if (close < 0) {
                continue;
            }
            List<String> arguments = splitTopLevel(code.substring(open + 1, close));
            result.add(new LoggerCall(fileName, fileName + ":" + lineOf(code, matcher.start()), arguments));
        }
        return result;
    }

    private static List<String> splitTopLevel(String argumentList) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : argumentList.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                arguments.add(normalise(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String last = normalise(current.toString());
        if (!last.isEmpty()) {
            arguments.add(last);
        }
        return arguments;
    }

    private static String normalise(String argument) {
        return argument.strip().replaceAll("\\s+", " ");
    }

    private static int matchingParen(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
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
     * Replaces comments and the content of string, text-block and character literals with spaces,
     * keeping the quotes and the line breaks, so a name inside a literal is never read as an argument.
     */
    static String blankOutCommentsAndLiterals(String source) {
        char[] out = source.toCharArray();
        int length = source.length();
        int i = 0;
        while (i < length) {
            char c = source.charAt(i);
            int start;
            int end;
            if (source.startsWith("//", i)) {
                start = i;
                end = source.indexOf('\n', i);
                end = end < 0 ? length : end;
            } else if (source.startsWith("/*", i)) {
                start = i;
                end = source.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
            } else if (source.startsWith("\"\"\"", i)) {
                start = i + 3;
                end = closingTextBlock(source, start);
                for (int k = start; k < end - 3; k++) {
                    blank(out, k);
                }
                i = end;
                continue;
            } else if (c == '"' || c == '\'') {
                start = i + 1;
                end = closingQuote(source, start, c);
                for (int k = start; k < end - 1; k++) {
                    blank(out, k);
                }
                i = end;
                continue;
            } else {
                i++;
                continue;
            }
            for (int k = start; k < end; k++) {
                blank(out, k);
            }
            i = end;
        }
        return new String(out);
    }

    private static void blank(char[] out, int index) {
        if (out[index] != '\n') {
            out[index] = ' ';
        }
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
}

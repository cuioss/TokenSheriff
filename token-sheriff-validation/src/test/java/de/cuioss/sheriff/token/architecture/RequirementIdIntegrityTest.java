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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Keeps the requirement ids of every registered prefix sequential and resolvable.
 * <p>
 * Each registered prefix owns one requirements document in which its ids are defined by anchor
 * lines of the form {@code [#PREFIX-N]} or {@code [#PREFIX-N.M]}. Three checks guard them:
 * <ol>
 * <li><b>Monotonic order</b> - in document order the top-level ids run 1, 2, 3, ... N with no gap
 * and no reversal, and every sub-id {@code N.M} sits under its parent {@code N} with {@code M}
 * running 1, 2, ... in order.</li>
 * <li><b>No dangling reference</b> - every id token found in the repository's documentation and
 * sources resolves to a definition.</li>
 * <li><b>No duplicate definition</b> - every id is defined once, and only in its prefix's own
 * requirements document.</li>
 * </ol>
 * The "Former identifiers" section of a requirements document (from the {@code [#former-identifiers]}
 * anchor to the next level-2 heading) lists ids that no longer exist by design and is excluded from
 * all three checks.
 * <p>
 * A prefix registered as {@link Enforcement#PENDING} is still parsed, but its checks abort through a
 * JUnit assumption, so they are reported as skipped rather than passed.
 * <p>
 * This file never contains a literal requirement id. Every sample id is composed at runtime from a
 * prefix and a number, the same technique {@link DocumentedPackagePatternTest} uses for its ellipsis
 * glyph: the repository sweep scans {@code .java} files, so a literal sample would make this test
 * report itself.
 */
class RequirementIdIntegrityTest {

    private static final Set<String> SCANNED_EXTENSIONS = Set.of(".adoc", ".md", ".java");

    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("target", ".git", ".plan", "node_modules", ".claude", ".github");

    private static final String FORMER_IDENTIFIERS_ANCHOR = "[#former-identifiers]";

    private static final String LEVEL_TWO_HEADING = "== ";

    private static final String SEPARATOR = "-";

    private static final String SAMPLE_PREFIX = RequirementPrefix.COMMONS.name();

    private static final String SAMPLE_DOCUMENT = "sample/requirements.adoc";

    private static Path repositoryRoot;

    private static final Map<RequirementPrefix, List<Definition>> OWN_DEFINITIONS = new EnumMap<>(RequirementPrefix.class);

    private static final Map<RequirementPrefix, List<Definition>> SWEPT_DEFINITIONS = new EnumMap<>(RequirementPrefix.class);

    private static final Map<RequirementPrefix, List<Reference>> SWEPT_REFERENCES = new EnumMap<>(RequirementPrefix.class);

    /** Whether a prefix's checks fail the build or are reported as skipped. */
    enum Enforcement {
        ENFORCED,
        PENDING
    }

    /** The registered requirement-id prefixes, each with its defining document. */
    enum RequirementPrefix {
        COMMONS("doc/commons/requirements.adoc", Enforcement.ENFORCED),
        VALIDATION("doc/validation/requirements.adoc", Enforcement.ENFORCED),
        CLIENT("doc/client/requirements.adoc", Enforcement.PENDING);

        private final String document;
        private final Enforcement enforcement;

        RequirementPrefix(String document, Enforcement enforcement) {
            this.document = document;
            this.enforcement = enforcement;
        }
    }

    /**
     * A requirement id; {@code subNumber} is {@code 0} for a top-level id.
     *
     * @param number the top-level number
     * @param subNumber the sub-id number, or {@code 0}
     */
    record RequirementId(int number, int subNumber) {

        boolean isTopLevel() {
            return subNumber == 0;
        }

        String render(String prefix) {
            return prefix + SEPARATOR + number + (isTopLevel() ? "" : "." + subNumber);
        }
    }

    /**
     * An anchor defining a requirement id.
     *
     * @param id the defined id
     * @param source the repository-relative path of the defining file
     * @param location {@code path:line} of the anchor
     * @param text the stripped anchor line
     */
    record Definition(RequirementId id, String source, String location, String text) {
    }

    /**
     * A token naming a requirement id.
     *
     * @param id the referenced id
     * @param location {@code path:line} of the token
     * @param text the stripped line carrying the token
     */
    record Reference(RequirementId id, String location, String text) {
    }

    @BeforeAll
    static void sweepRepository() throws IOException {
        repositoryRoot = resolveRepositoryRoot();
        assertTrue(Files.isRegularFile(repositoryRoot.resolve("pom.xml")),
                "Repository root did not resolve to a Maven project root (resolved to " + repositoryRoot
                        + "). Refusing to report success: the sweep never ran, so a passing result here "
                        + "would be vacuous rather than evidence that the requirement ids are intact.");
        for (RequirementPrefix prefix : RequirementPrefix.values()) {
            assertTrue(Files.isRegularFile(repositoryRoot.resolve(prefix.document)),
                    "The requirements document of " + prefix + " is missing at " + prefix.document
                            + ". Refusing to report success: without it no id of the prefix can be checked.");
            OWN_DEFINITIONS.put(prefix, new ArrayList<>());
            SWEPT_DEFINITIONS.put(prefix, new ArrayList<>());
            SWEPT_REFERENCES.put(prefix, new ArrayList<>());
        }
        Files.walkFileTree(repositoryRoot, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                Path name = directory.getFileName();
                return name != null && EXCLUDED_DIRECTORIES.contains(name.toString())
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (isScanned(file)) {
                    collect(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RequirementPrefix.class)
    @DisplayName("Requirement ids run 1..N in document order, sub-ids under their parent")
    void idsRunSequentiallyInDocumentOrder(RequirementPrefix prefix) {
        List<String> violations = findOrderViolations(prefix.name(), OWN_DEFINITIONS.get(prefix));
        assumeEnforced(prefix);
        assertNoViolations(prefix, "ids must run 1, 2, 3, ... N in document order with no gap or reversal, "
                + "each sub-id under its parent and numbered 1, 2, ... in order", violations);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RequirementPrefix.class)
    @DisplayName("Every requirement-id reference resolves to a definition")
    void everyReferenceResolves(RequirementPrefix prefix) {
        List<String> violations = findDanglingReferences(prefix.name(), SWEPT_REFERENCES.get(prefix),
                definedIds(OWN_DEFINITIONS.get(prefix)));
        assumeEnforced(prefix);
        assertNoViolations(prefix, "every reference must name an id defined in " + prefix.document,
                violations);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RequirementPrefix.class)
    @DisplayName("Every requirement id is defined once, in its own requirements document")
    void everyIdIsDefinedOnce(RequirementPrefix prefix) {
        List<String> violations = findDuplicateDefinitions(prefix.name(), prefix.document,
                SWEPT_DEFINITIONS.get(prefix));
        assumeEnforced(prefix);
        assertNoViolations(prefix, "every id must be defined exactly once, in " + prefix.document, violations);
    }

    @Test
    @DisplayName("The order check accepts 1..N with sub-ids and rejects gaps, reversals and misplaced sub-ids")
    void orderPredicateDiscriminates() {
        assertAll("sequential orders are clean",
                () -> assertTrue(findOrderViolations(SAMPLE_PREFIX, definitions("1", "2", "2.1", "2.2", "3"))
                        .isEmpty(), "1, 2, 2.1, 2.2, 3 is sequential"),
                () -> assertTrue(findOrderViolations(SAMPLE_PREFIX, definitions("1")).isEmpty(),
                        "a single id 1 is sequential"));

        assertAll("broken orders are violations",
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, definitions("1", "2", "4")).isEmpty(),
                        "a gap is a violation"),
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, definitions("1", "3", "2")).isEmpty(),
                        "a reversal is a violation"),
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, definitions("2", "3")).isEmpty(),
                        "a sequence not starting at 1 is a violation"),
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, definitions("1", "2", "1.1")).isEmpty(),
                        "a sub-id under the wrong parent is a violation"),
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, definitions("1", "1.2")).isEmpty(),
                        "a sub-id numbering gap is a violation"),
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, definitions("1", "1.2", "1.1")).isEmpty(),
                        "a sub-id reversal is a violation"),
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, definitions("1", "1")).isEmpty(),
                        "a repeated id is a violation"),
                () -> assertFalse(findOrderViolations(SAMPLE_PREFIX, List.of()).isEmpty(),
                        "a document defining no id is a violation, not a vacuous pass"));
    }

    @Test
    @DisplayName("The order check rejects the pre-renumbering VALIDATION and COMMONS orders")
    void orderPredicateRejectsPreRenumberingOrders() {
        List<Definition> formerValidationOrder =
                definitions("1", "2", "3", "4", "5", "6", "7", "8", "12", "13", "14", "9", "10");
        List<Definition> formerCommonsOrder = definitions("1", "2", "3", "4", "17", "5", "6", "7", "8", "9",
                "10", "11", "12", "16", "13", "14", "15");

        List<String> validationViolations = findOrderViolations(RequirementPrefix.VALIDATION.name(),
                formerValidationOrder);
        List<String> commonsViolations = findOrderViolations(RequirementPrefix.COMMONS.name(), formerCommonsOrder);

        assertAll("the former orders are not sequential",
                () -> assertFalse(validationViolations.isEmpty(), "... 8, 12 ... is not sequential"),
                () -> assertTrue(validationViolations.get(0).contains(id(RequirementPrefix.VALIDATION.name(), "12")),
                        () -> "the first violation is the jump from 8 to 12, got " + validationViolations),
                () -> assertFalse(commonsViolations.isEmpty(), "... 4, 17 ... is not sequential"),
                () -> assertTrue(commonsViolations.get(0).contains(id(RequirementPrefix.COMMONS.name(), "17")),
                        () -> "the first violation is the jump from 4 to 17, got " + commonsViolations));
    }

    @Test
    @DisplayName("The reference check resolves defined ids and reports dangling ids and sub-ids")
    void danglingPredicateDiscriminates() {
        Set<RequirementId> defined = definedIds(definitions("1", "2", "2.1"));

        assertAll("defined references are clean",
                () -> assertTrue(dangling("see " + id(SAMPLE_PREFIX, "2") + " and " + id(SAMPLE_PREFIX, "2.1"),
                        defined).isEmpty(), "defined ids and sub-ids resolve"),
                () -> assertTrue(dangling("| " + id(SAMPLE_PREFIX, "1") + "..9 | transport", defined).isEmpty(),
                        "a range token reads as its first id only, which is defined"),
                () -> assertTrue(dangling("ends with " + id(SAMPLE_PREFIX, "2") + ".", defined).isEmpty(),
                        "a sentence-ending period is not a sub-id"),
                () -> assertTrue(dangling("OTHER" + SAMPLE_PREFIX + SEPARATOR + 99, defined).isEmpty(),
                        "a prefix embedded in a longer word is not a reference"));

        assertAll("undefined references are violations",
                () -> assertFalse(dangling("see " + id(SAMPLE_PREFIX, "99"), defined).isEmpty(),
                        "an undefined id dangles"),
                () -> assertFalse(dangling("see " + id(SAMPLE_PREFIX, "2.7"), defined).isEmpty(),
                        "an undefined sub-id of a defined parent dangles"),
                () -> assertFalse(dangling("see " + id(SAMPLE_PREFIX, "1.1"), defined).isEmpty(),
                        "a sub-id is not resolved by its parent alone"));
    }

    @Test
    @DisplayName("The duplicate check reports a repeated anchor and an anchor outside the owning document")
    void duplicatePredicateDiscriminates() {
        Definition first = definition("1", SAMPLE_DOCUMENT);
        Definition second = definition("2", SAMPLE_DOCUMENT);

        assertAll("single anchors in the owning document are clean",
                () -> assertTrue(findDuplicateDefinitions(SAMPLE_PREFIX, SAMPLE_DOCUMENT, List.of(first, second))
                        .isEmpty(), "distinct anchors in the owning document are clean"));

        assertAll("duplicate and foreign anchors are violations",
                () -> assertFalse(findDuplicateDefinitions(SAMPLE_PREFIX, SAMPLE_DOCUMENT,
                        List.of(first, second, definition("2", SAMPLE_DOCUMENT))).isEmpty(),
                        "an anchor defined twice is a violation"),
                () -> assertFalse(findDuplicateDefinitions(SAMPLE_PREFIX, SAMPLE_DOCUMENT,
                        List.of(first, definition("2", "sample/other.adoc"))).isEmpty(),
                        "an anchor outside the owning document is a violation"));
    }

    @Test
    @DisplayName("The former-identifiers section is excluded from definitions and references")
    void formerIdentifiersRegionIsExcluded() {
        List<String> lines = List.of(
                anchor("1"),
                "=== " + id(SAMPLE_PREFIX, "1") + ": first",
                "== Traceability",
                FORMER_IDENTIFIERS_ANCHOR,
                "== Former identifiers",
                "| " + id(SAMPLE_PREFIX, "99") + " | " + id(SAMPLE_PREFIX, "1"),
                anchor("98"),
                "== Appendix",
                anchor("2"));

        boolean[] excluded = formerIdentifiersMask(lines);
        List<Definition> parsedDefinitions = parseDefinitions(SAMPLE_PREFIX, SAMPLE_DOCUMENT, lines, excluded);
        List<Reference> parsedReferences = parseReferences(SAMPLE_PREFIX, SAMPLE_DOCUMENT, lines, excluded);

        assertAll("the region ends at the next level-2 heading",
                () -> assertEquals(List.of(new RequirementId(1, 0), new RequirementId(2, 0)),
                        parsedDefinitions.stream().map(Definition::id).toList(),
                        "anchors inside the region are not definitions; anchors after it are"),
                () -> assertTrue(parsedReferences.stream().noneMatch(reference -> reference.id().number() >= 98),
                        "ids inside the region are not references"),
                () -> assertTrue(findOrderViolations(SAMPLE_PREFIX, parsedDefinitions).isEmpty(),
                        "the region does not break the order of the surrounding definitions"));
    }

    private static void assumeEnforced(RequirementPrefix prefix) {
        assumeTrue(prefix.enforcement == Enforcement.ENFORCED,
                () -> prefix + " ids are registered but pending until PLAN-14 renumbers them; "
                        + "this check is skipped, not passed");
    }

    private static void assertNoViolations(RequirementPrefix prefix, String rule, List<String> violations) {
        assertTrue(violations.isEmpty(),
                () -> prefix + " requirement ids: " + rule + ". " + violations.size() + " violation(s) found:"
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), violations));
    }

    /**
     * Reports every definition that breaks the 1..N document order of its prefix.
     *
     * @param prefix the prefix the definitions belong to
     * @param definitions the definitions in document order
     * @return one {@code path:line -> text} entry per violation; empty when the order is sequential
     */
    static List<String> findOrderViolations(String prefix, List<Definition> definitions) {
        List<String> violations = new ArrayList<>();
        if (definitions.isEmpty()) {
            violations.add("no " + prefix + " id is defined; an empty document is not a sequential one");
            return violations;
        }
        int expectedNumber = 1;
        int currentParent = 0;
        int expectedSubNumber = 1;
        for (Definition definition : definitions) {
            RequirementId id = definition.id();
            if (id.isTopLevel()) {
                if (id.number() != expectedNumber) {
                    violations.add(describe(definition, "expected " + prefix + SEPARATOR + expectedNumber
                            + " but found " + id.render(prefix)));
                }
                currentParent = id.number();
                expectedNumber = id.number() + 1;
                expectedSubNumber = 1;
            } else if (id.number() != currentParent) {
                violations.add(describe(definition, "sub-id " + id.render(prefix)
                        + " is not under its parent; the enclosing id is " + prefix + SEPARATOR + currentParent));
            } else {
                if (id.subNumber() != expectedSubNumber) {
                    violations.add(describe(definition, "expected " + prefix + SEPARATOR + currentParent + "."
                            + expectedSubNumber + " but found " + id.render(prefix)));
                }
                expectedSubNumber = id.subNumber() + 1;
            }
        }
        return violations;
    }

    /**
     * Reports every reference naming an id that is not defined.
     *
     * @param prefix the prefix the references belong to
     * @param references the references found by the sweep
     * @param defined the ids defined in the prefix's requirements document
     * @return one {@code path:line -> text} entry per dangling reference
     */
    static List<String> findDanglingReferences(String prefix, List<Reference> references, Set<RequirementId> defined) {
        List<String> violations = new ArrayList<>();
        for (Reference reference : references) {
            if (!defined.contains(reference.id())) {
                violations.add(reference.location() + " -> " + reference.id().render(prefix) + " is not defined: "
                        + reference.text());
            }
        }
        return violations;
    }

    /**
     * Reports every anchor that defines an id a second time or outside the prefix's own document.
     *
     * @param prefix the prefix the definitions belong to
     * @param ownDocument the repository-relative path of the prefix's requirements document
     * @param definitions every anchor of the prefix found by the sweep
     * @return one {@code path:line -> text} entry per violation
     */
    static List<String> findDuplicateDefinitions(String prefix, String ownDocument, List<Definition> definitions) {
        List<String> violations = new ArrayList<>();
        Map<RequirementId, Definition> firstDefinitions = new HashMap<>();
        for (Definition definition : definitions) {
            if (!ownDocument.equals(definition.source())) {
                violations.add(describe(definition, definition.id().render(prefix)
                        + " is defined outside its requirements document " + ownDocument));
                continue;
            }
            Definition first = firstDefinitions.putIfAbsent(definition.id(), definition);
            if (first != null) {
                violations.add(describe(definition, definition.id().render(prefix)
                        + " is already defined at " + first.location()));
            }
        }
        return violations;
    }

    /**
     * Marks the lines of the former-identifiers section: from the {@code [#former-identifiers]} anchor
     * through its own level-2 heading up to, but excluding, the next level-2 heading or the end of the file.
     *
     * @param lines the lines of a requirements document
     * @return {@code true} at every index inside the section
     */
    static boolean[] formerIdentifiersMask(List<String> lines) {
        boolean[] excluded = new boolean[lines.size()];
        boolean inRegion = false;
        boolean regionHeadingSeen = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (FORMER_IDENTIFIERS_ANCHOR.equals(line)) {
                inRegion = true;
                regionHeadingSeen = false;
            } else if (inRegion && line.startsWith(LEVEL_TWO_HEADING)) {
                if (regionHeadingSeen) {
                    inRegion = false;
                } else {
                    regionHeadingSeen = true;
                }
            }
            excluded[index] = inRegion;
        }
        return excluded;
    }

    /**
     * Collects the anchors {@code [#PREFIX-N]} and {@code [#PREFIX-N.M]} outside the excluded lines.
     *
     * @param prefix the prefix to look for
     * @param source the repository-relative path of the scanned file
     * @param lines the lines of the scanned file
     * @param excluded the lines to skip, one flag per line
     * @return the definitions in document order
     */
    static List<Definition> parseDefinitions(String prefix, String source, List<String> lines, boolean[] excluded) {
        Pattern anchor = Pattern.compile("\\[#" + prefix + SEPARATOR + "(\\d+)(?:\\.(\\d+))?]");
        List<Definition> definitions = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (excluded[index]) {
                continue;
            }
            Matcher matcher = anchor.matcher(lines.get(index));
            while (matcher.find()) {
                definitions.add(new Definition(toId(matcher), source, source + ":" + (index + 1),
                        lines.get(index).strip()));
            }
        }
        return definitions;
    }

    /**
     * Collects the id tokens of a prefix outside the excluded lines. A sub-id needs a digit after the
     * dot, so a range written with two periods reads as its first id only.
     *
     * @param prefix the prefix to look for
     * @param source the repository-relative path of the scanned file
     * @param lines the lines of the scanned file
     * @param excluded the lines to skip, one flag per line
     * @return the references in document order
     */
    static List<Reference> parseReferences(String prefix, String source, List<String> lines, boolean[] excluded) {
        Pattern token = Pattern.compile("\\b" + prefix + SEPARATOR + "(\\d+)(?:\\.(\\d+))?");
        List<Reference> references = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (excluded[index]) {
                continue;
            }
            Matcher matcher = token.matcher(lines.get(index));
            while (matcher.find()) {
                references.add(new Reference(toId(matcher), source + ":" + (index + 1), lines.get(index).strip()));
            }
        }
        return references;
    }

    private static RequirementId toId(Matcher matcher) {
        int number = Integer.parseInt(matcher.group(1));
        int subNumber = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return new RequirementId(number, subNumber);
    }

    private static Set<RequirementId> definedIds(List<Definition> definitions) {
        Set<RequirementId> ids = new HashSet<>();
        definitions.forEach(definition -> ids.add(definition.id()));
        return ids;
    }

    private static String describe(Definition definition, String problem) {
        return definition.location() + " -> " + problem + ": " + definition.text();
    }

    private static Path resolveRepositoryRoot() {
        Path moduleBase = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        Path parent = moduleBase.getParent();
        return parent != null ? parent : moduleBase;
    }

    private static boolean isScanned(Path file) {
        String name = file.getFileName().toString();
        return SCANNED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static void collect(Path file) {
        List<String> lines;
        try {
            lines = Arrays.asList(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n", -1));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file
                    + " during the requirement-id sweep; skipping it would leave the file unchecked", e);
        }
        String source = repositoryRoot.relativize(file).toString().replace('\\', '/');
        boolean isRequirementsDocument = Arrays.stream(RequirementPrefix.values())
                .anyMatch(prefix -> prefix.document.equals(source));
        boolean[] excluded = isRequirementsDocument ? formerIdentifiersMask(lines) : new boolean[lines.size()];
        for (RequirementPrefix prefix : RequirementPrefix.values()) {
            List<Definition> definitions = parseDefinitions(prefix.name(), source, lines, excluded);
            SWEPT_DEFINITIONS.get(prefix).addAll(definitions);
            if (prefix.document.equals(source)) {
                OWN_DEFINITIONS.get(prefix).addAll(definitions);
            }
            SWEPT_REFERENCES.get(prefix).addAll(parseReferences(prefix.name(), source, lines, excluded));
        }
    }

    private static String id(String prefix, String number) {
        return prefix + SEPARATOR + number;
    }

    private static String anchor(String number) {
        return "[#" + id(SAMPLE_PREFIX, number) + "]";
    }

    private static Definition definition(String number, String source) {
        return parseDefinitions(SAMPLE_PREFIX, source, List.of(anchor(number)), new boolean[1]).get(0);
    }

    private static List<Definition> definitions(String... numbers) {
        List<String> lines = Arrays.stream(numbers).map(RequirementIdIntegrityTest::anchor).toList();
        return parseDefinitions(SAMPLE_PREFIX, SAMPLE_DOCUMENT, lines, new boolean[lines.size()]);
    }

    private static List<String> dangling(String line, Set<RequirementId> defined) {
        List<String> lines = List.of(line);
        return findDanglingReferences(SAMPLE_PREFIX,
                parseReferences(SAMPLE_PREFIX, SAMPLE_DOCUMENT, lines, new boolean[1]), defined);
    }
}

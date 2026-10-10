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
package de.cuioss.sheriff.token.quarkus.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the two surefire fork groups that {@code token-sheriff-quarkus-parent/pom.xml} configures
 * for every Quarkus module.
 * <p>
 * Surefire runs the unit tests of a module in two reused JVMs: the default execution excludes the
 * JUnit tag {@value #GROUP_TAG}, the execution {@code quarkus-boot-test} selects only that tag. A
 * test class boots Quarkus when it is annotated {@code @QuarkusTest} or declares a
 * {@code QuarkusExtensionTest} field. Such a class must carry the tag, and no other class may, so
 * that a plain class never shares a JVM with a Quarkus application.
 * <p>
 * The guard reads the Java test sources of every module directory next to this module that has a
 * {@code src/test/java} tree. A module added later is therefore covered without editing this class.
 * Reading sources instead of compiled classes is what lets one test cover the sibling modules; a
 * build limited to a sibling module does not run it, the full build does.
 */
@DisplayName("Surefire fork groups of the Quarkus modules")
class SurefireForkGroupGuardTest {

    private static final String GROUP_TAG = "quarkus-boot";

    private static final Path TEST_SOURCE_TREE = Path.of("src", "test", "java");

    private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(?:class|interface|record|enum)\\s+\\w+");

    private static final Pattern QUARKUS_TEST_ANNOTATION = Pattern
            .compile("@(?:io\\.quarkus\\.test\\.junit\\.)?QuarkusTest\\b");

    private static final Pattern EXTENSION_TEST_FIELD = Pattern.compile("\\bQuarkusExtensionTest\\s+\\w+\\s*[=;]");

    private static final Pattern TAG_ANNOTATION = Pattern
            .compile("@Tag\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"\\s*\\)");

    /**
     * What the guard needs to know about one test source file.
     *
     * @param bootsQuarkus      whether the top-level class boots a Quarkus application
     * @param taggedOnClass     whether the top-level class carries the group tag
     * @param taggedAnywhere    whether the group tag occurs anywhere in the file
     */
    private record ForkGroup(boolean bootsQuarkus, boolean taggedOnClass, boolean taggedAnywhere) {

        boolean inWrongFork() {
            return bootsQuarkus ? !taggedOnClass : taggedAnywhere;
        }
    }

    private record SourceFile(Path path, ForkGroup forkGroup) {
    }

    @Nested
    @DisplayName("Test sources of all Quarkus modules")
    class ModuleSources {

        @Test
        @DisplayName("Should find the test source trees and classes that boot Quarkus")
        void shouldFindSourceTreesAndBootingClasses() {
            List<Path> sourceTrees = testSourceTrees();
            List<SourceFile> sources = testSources();

            assertAll("scanned population",
                    () -> assertTrue(sourceTrees.contains(moduleDirectory().resolve(TEST_SOURCE_TREE)),
                            "The guard must see its own module's test sources, found: " + sourceTrees),
                    () -> assertTrue(sourceTrees.size() > 1,
                            "The guard must see the sibling modules' test sources, found: " + sourceTrees),
                    () -> assertTrue(sources.stream().anyMatch(source -> source.forkGroup().bootsQuarkus()),
                            "No class that boots Quarkus was found in " + sourceTrees
                                    + " - the detection is broken or the guard looks at the wrong place"),
                    () -> assertTrue(sources.stream().anyMatch(source -> !source.forkGroup().bootsQuarkus()),
                            "No plain test class was found in " + sourceTrees));
        }

        @Test
        @DisplayName("Should tag every class that boots Quarkus")
        void shouldTagEveryClassThatBootsQuarkus() {
            List<Path> untagged = testSources().stream()
                    .filter(source -> source.forkGroup().bootsQuarkus() && source.forkGroup().inWrongFork())
                    .map(SourceFile::path)
                    .toList();

            assertEquals(List.of(), untagged, "These classes boot Quarkus and must carry @Tag(\"" + GROUP_TAG
                    + "\") as a string literal on the top-level class, otherwise they run in the fork of the plain classes");
        }

        @Test
        @DisplayName("Should not tag a class that does not boot Quarkus")
        void shouldNotTagPlainClasses() {
            List<Path> wronglyTagged = testSources().stream()
                    .filter(source -> !source.forkGroup().bootsQuarkus() && source.forkGroup().inWrongFork())
                    .map(SourceFile::path)
                    .toList();

            assertEquals(List.of(), wronglyTagged, "These classes do not boot Quarkus and must not carry @Tag(\""
                    + GROUP_TAG + "\"), otherwise they run in the fork of the Quarkus applications");
        }

        @Test
        @DisplayName("Should bind both surefire executions of the parent pom to the tag")
        void shouldBindSurefireExecutionsToTag() {
            String pom = read(moduleDirectory().resolveSibling("pom.xml"));

            assertAll("surefire executions",
                    () -> assertTrue(pom.contains("<excludedGroups>" + GROUP_TAG + "</excludedGroups>"),
                            "The default surefire execution must exclude the tag " + GROUP_TAG),
                    () -> assertTrue(pom.contains("<groups>" + GROUP_TAG + "</groups>"),
                            "A second surefire execution must select the tag " + GROUP_TAG));
        }

        @Test
        @DisplayName("Should not switch off the check for a test selection that matches nothing")
        void shouldKeepNoMatchingTestCheckStrict() {
            String pom = read(moduleDirectory().resolveSibling("pom.xml"));

            assertFalse(pom.contains("<failIfNoSpecifiedTests>"),
                    "The parent pom must not set failIfNoSpecifiedTests: with the check switched off a mistyped "
                            + "-Dtest selection ends in BUILD SUCCESS with zero tests run");
        }
    }

    @Nested
    @DisplayName("Classification of a source file")
    class Classification {

        @Test
        @DisplayName("Should accept a tagged @QuarkusTest class")
        void shouldAcceptTaggedQuarkusTest() {
            ForkGroup forkGroup = classify("""
                    @QuarkusTest
                    @Tag("quarkus-boot")
                    class SampleTest {
                    }
                    """);

            assertAll(() -> assertTrue(forkGroup.bootsQuarkus(), "@QuarkusTest boots Quarkus"),
                    () -> assertFalse(forkGroup.inWrongFork(), "A tagged booting class is in the right fork"));
        }

        @Test
        @DisplayName("Should reject a @QuarkusTest class without the tag")
        void shouldRejectUntaggedQuarkusTest() {
            ForkGroup forkGroup = classify("""
                    @io.quarkus.test.junit.QuarkusTest
                    @Tag("slow")
                    class SampleTest {
                    }
                    """);

            assertAll(() -> assertTrue(forkGroup.bootsQuarkus(), "@QuarkusTest boots Quarkus"),
                    () -> assertTrue(forkGroup.inWrongFork(), "An untagged booting class is in the wrong fork"));
        }

        @Test
        @DisplayName("Should reject a QuarkusExtensionTest class without the tag")
        void shouldRejectUntaggedExtensionTest() {
            ForkGroup forkGroup = classify("""
                    import io.quarkus.test.QuarkusExtensionTest;

                    class SampleTest {
                        @RegisterExtension
                        static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest();
                    }
                    """);

            assertAll(() -> assertTrue(forkGroup.bootsQuarkus(), "A QuarkusExtensionTest field boots Quarkus"),
                    () -> assertTrue(forkGroup.inWrongFork(), "An untagged booting class is in the wrong fork"));
        }

        @Test
        @DisplayName("Should reject a booting class whose tag sits on a member only")
        void shouldRejectTagBelowClassLevel() {
            ForkGroup forkGroup = classify("""
                    @QuarkusTest
                    class SampleTest {
                        @Test
                        @Tag("quarkus-boot")
                        void sample() {
                        }
                    }
                    """);

            assertTrue(forkGroup.inWrongFork(), "The tag must sit on the top-level class to move the whole class");
        }

        @Test
        @DisplayName("Should reject a plain class that carries the tag")
        void shouldRejectTaggedPlainClass() {
            ForkGroup forkGroup = classify("""
                    class SampleTest {
                        @Test
                        @Tag(value = "quarkus-boot")
                        void sample() {
                        }
                    }
                    """);

            assertAll(() -> assertFalse(forkGroup.bootsQuarkus(), "The class boots nothing"),
                    () -> assertTrue(forkGroup.inWrongFork(), "A tagged plain class is in the wrong fork"));
        }

        @Test
        @DisplayName("Should ignore mentions in comments, literals and other annotations")
        void shouldIgnoreMentionsOutsideCode() {
            ForkGroup forkGroup = classify("""
                    import io.quarkus.test.QuarkusExtensionTest;
                    import io.quarkus.test.junit.QuarkusTestProfile;

                    /**
                     * The sibling {@code @QuarkusTest} is tagged @Tag("quarkus-boot").
                     */
                    // @QuarkusTest @Tag("quarkus-boot")
                    @QuarkusIntegrationTest
                    class SampleTest implements QuarkusTestProfile {
                        String glob = "**/*Test.java @QuarkusTest @Tag(\\"quarkus-boot\\")";
                        char quote = '"';
                        Object extension = new QuarkusExtensionTest();
                    }
                    """);

            assertAll(() -> assertFalse(forkGroup.bootsQuarkus(), "Nothing here boots Quarkus under surefire"),
                    () -> assertFalse(forkGroup.taggedAnywhere(), "No annotation here carries the tag"),
                    () -> assertFalse(forkGroup.inWrongFork(), "A plain class without the tag is in the right fork"));
        }
    }

    private static Path moduleDirectory() {
        return Path.of("").toAbsolutePath();
    }

    private static List<Path> testSourceTrees() {
        try (Stream<Path> siblings = Files.list(moduleDirectory().getParent())) {
            return siblings.map(directory -> directory.resolve(TEST_SOURCE_TREE))
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<SourceFile> testSources() {
        List<SourceFile> sources = new ArrayList<>();
        for (Path sourceTree : testSourceTrees()) {
            try (Stream<Path> files = Files.walk(sourceTree)) {
                files.filter(file -> file.getFileName().toString().endsWith(".java"))
                        .sorted()
                        .forEach(file -> sources.add(new SourceFile(file, classify(read(file)))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return sources;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ForkGroup classify(String source) {
        String code = maskCommentsAndLiterals(source);
        Matcher typeDeclaration = TYPE_DECLARATION.matcher(code);
        int headerEnd = typeDeclaration.find() ? typeDeclaration.start() : code.length();

        boolean bootsQuarkus = QUARKUS_TEST_ANNOTATION.matcher(code).region(0, headerEnd).find()
                || EXTENSION_TEST_FIELD.matcher(code).find();
        boolean taggedOnClass = false;
        boolean taggedAnywhere = false;
        Matcher tag = TAG_ANNOTATION.matcher(code);
        while (tag.find()) {
            if (GROUP_TAG.equals(source.substring(tag.start(1), tag.end(1)))) {
                taggedAnywhere = true;
                taggedOnClass |= tag.start() < headerEnd;
            }
        }
        return new ForkGroup(bootsQuarkus, taggedOnClass, taggedAnywhere);
    }

    /**
     * Replaces the content of comments, string literals, text blocks and character literals with
     * blanks. The result has the length and the line structure of the input, so an offset found in
     * it addresses the same position in the input.
     */
    private static String maskCommentsAndLiterals(String source) {
        StringBuilder masked = new StringBuilder(source);
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("//", index)) {
                index = mask(masked, index, endOf(source, "\n", index + 2, 0));
            } else if (source.startsWith("/*", index)) {
                index = mask(masked, index, endOf(source, "*/", index + 2, 2));
            } else if (source.startsWith("\"\"\"", index)) {
                index = mask(masked, index + 3, endOfLiteral(source, "\"\"\"", index + 3)) + 3;
            } else if (source.charAt(index) == '"' || source.charAt(index) == '\'') {
                String delimiter = String.valueOf(source.charAt(index));
                index = mask(masked, index + 1, endOfLiteral(source, delimiter, index + 1)) + 1;
            } else {
                index++;
            }
        }
        return masked.toString();
    }

    private static int endOf(String source, String terminator, int from, int terminatorLength) {
        int end = source.indexOf(terminator, from);
        return end < 0 ? source.length() : end + terminatorLength;
    }

    private static int endOfLiteral(String source, String delimiter, int from) {
        int index = from;
        while (index < source.length() && !source.startsWith(delimiter, index)) {
            index += source.charAt(index) == '\\' ? 2 : 1;
        }
        return Math.min(index, source.length());
    }

    private static int mask(StringBuilder masked, int from, int to) {
        for (int index = from; index < to; index++) {
            if (masked.charAt(index) != '\n') {
                masked.setCharAt(index, ' ');
            }
        }
        return to;
    }
}

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
package de.cuioss.sheriff.token.client;

import de.cuioss.sheriff.token.client.token.TokenResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Test support shared by the client's native-image drift guards.
 * <p>
 * Both guards derive their expected population from the classes actually compiled into the
 * client's main output instead of from a hand-written list, so a newly added converter or a newly
 * added static {@code Random} field shows up without anyone remembering to extend a constant. The
 * main output is located through the code source of a known main class ({@link TokenResponse}),
 * which resolves to {@code target/classes} in a reactor build and to the module jar when the tests
 * run against the packaged artifact; both shapes are enumerated.
 *
 * @author Oliver Wolff
 */
final class NativeImageClassScanner {

    private static final String CLASS_SUFFIX = ".class";
    private static final String MODULE_INFO = "module-info";
    private static final String PACKAGE_INFO = "package-info";

    private NativeImageClassScanner() {
        // test-support utility
    }

    /**
     * Enumerates the binary names of every class compiled into the client's main output.
     * <p>
     * {@code module-info} and {@code package-info} descriptors are skipped, since neither is a
     * loadable type. Nested classes are included under their binary ({@code Outer$Inner}) name.
     *
     * @return the binary names of all main classes, sorted
     * @throws IllegalStateException if the main output cannot be located or read
     */
    static SortedSet<String> mainClassNames() {
        Path location = mainOutputLocation();
        SortedSet<String> names = new TreeSet<>();
        if (Files.isDirectory(location)) {
            collectFromDirectory(location, names);
        } else {
            collectFromJar(location, names);
        }
        return Collections.unmodifiableSortedSet(names);
    }

    /**
     * Reads a classpath resource as UTF-8.
     *
     * @param resource the classpath-relative resource path
     * @return the resource content
     * @throws IllegalStateException if the resource is not on the classpath, naming the resource
     */
    static String readResource(String resource) {
        try (InputStream stream = classLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Shipped native-image metadata is missing: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + resource, e);
        }
    }

    /**
     * @return the class loader that loaded the client's main classes
     */
    static ClassLoader classLoader() {
        return TokenResponse.class.getClassLoader();
    }

    private static Path mainOutputLocation() {
        CodeSource codeSource = TokenResponse.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("No code source for " + TokenResponse.class.getName());
        }
        URL location = codeSource.getLocation();
        try {
            return Path.of(location.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unusable code-source location: " + location, e);
        }
    }

    private static void collectFromDirectory(Path root, SortedSet<String> names) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"))
                    .forEach(entry -> addIfClass(entry, names));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to walk " + root, e);
        }
    }

    private static void collectFromJar(Path jar, SortedSet<String> names) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(JarEntry::getName)
                    .forEach(entry -> addIfClass(entry, names));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + jar, e);
        }
    }

    private static void addIfClass(String entry, SortedSet<String> names) {
        if (!entry.endsWith(CLASS_SUFFIX) || entry.startsWith("META-INF/")) {
            return;
        }
        String binaryName = entry.substring(0, entry.length() - CLASS_SUFFIX.length()).replace('/', '.');
        String simplePart = binaryName.substring(binaryName.lastIndexOf('.') + 1);
        if (MODULE_INFO.equals(simplePart) || PACKAGE_INFO.equals(simplePart)) {
            return;
        }
        names.add(binaryName);
    }
}

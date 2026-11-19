/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doclava;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Launches {@link Doclava} in an isolated child JVM process so each test invocation starts with
 * clean static state across {@link Doclava}, {@link Converter}, and {@link Errors}.
 */
public final class DoclavaRunner {

  private static final List<String> ADD_EXPORTS =
      Arrays.asList(
          "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
          "--add-exports=jdk.javadoc/jdk.javadoc.internal.doclets.formats.html=ALL-UNNAMED",
          "--add-exports=jdk.javadoc/jdk.javadoc.internal.tool=ALL-UNNAMED");

  private DoclavaRunner() {}

  /** Child-process entry point invoking the JDK {@code javadoc} {@link ToolProvider}. */
  public static void main(String[] args) {
    System.exit(
        ToolProvider.findFirst("javadoc").orElseThrow().run(System.out, System.err, args));
  }

  /** Writes {@code content} as UTF-8 to {@code path}, creating parent directories as needed. */
  static void writeFile(Path path, String content) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, content, UTF_8);
  }

  /** Returns sorted paths of all {@code .java} files under {@code root}. */
  static List<String> findJavaFiles(Path root) throws Exception {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(p -> p.toString().endsWith(".java"))
          .map(Path::toString)
          .sorted()
          .collect(Collectors.toList());
    }
  }

  /**
   * Builds a classpath containing both the active {@link CodeSource} JARs (needed when TradeFed's
   * {@code HostTest} loads tests via {@code URLClassLoader}) and {@code java.class.path}.
   */
  static String getRuntimeClasspath() throws Exception {
    Set<String> entries = new LinkedHashSet<>();
    for (Class<?> cls : new Class<?>[] {DoclavaRunner.class, Doclava.class}) {
      CodeSource cs = cls.getProtectionDomain().getCodeSource();
      if (cs != null && cs.getLocation() != null) {
        entries.add(Path.of(cs.getLocation().toURI()).toString());
      }
    }
    String sysCp = System.getProperty("java.class.path", "");
    if (!sysCp.isEmpty()) {
      entries.addAll(Arrays.asList(sysCp.split(File.pathSeparator)));
    }
    return String.join(File.pathSeparator, entries);
  }

  /** Runs {@link Doclava} with {@code -nodocs} and the supplied extra flags/files. */
  static void runNoDocs(List<String> extraArgs) throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-nodocs");
    args.addAll(extraArgs);
    run(args);
  }

  /** Runs {@link Doclava} writing HTML output to {@code docsDir} with the supplied extra flags. */
  static void runWithDocs(Path docsDir, List<String> extraArgs) throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(docsDir.toString());
    args.addAll(extraArgs);
    run(args);
  }

  /** Runs {@link Doclava} in a fresh child JVM and asserts exit code {@code 0}. */
  static void run(List<String> extraArgs) throws Exception {
    String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    String classpath = getRuntimeClasspath();

    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin);
    cmd.addAll(ADD_EXPORTS);
    cmd.add("-cp");
    cmd.add(classpath);
    cmd.add(DoclavaRunner.class.getName());
    cmd.add("-doclet");
    cmd.add("com.google.doclava.Doclava");
    cmd.add("-docletpath");
    cmd.add(classpath);
    cmd.add("-encoding");
    cmd.add("UTF-8");
    cmd.add("-quiet");
    cmd.addAll(extraArgs);

    Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), UTF_8);
    int exitCode = process.waitFor();
    assertEquals("Doclava failed with output:\n" + output, 0, exitCode);
  }
}

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

import static com.google.doclava.DoclavaRunner.findJavaFiles;
import static com.google.doclava.DoclavaRunner.getRuntimeClasspath;
import static com.google.doclava.DoclavaRunner.writeFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.spi.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PackageHideAnnotationTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void hideAnnotationOnPackage_hidesUnannotatedAndShowsAnnotatedClasses() throws Exception {
    Path srcRoot = writeSampleSources();
    Path apiFile = tempFolder.getRoot().toPath().resolve("api.txt");
    Path proguardFile = tempFolder.getRoot().toPath().resolve("proguard.cfg");

    runDoclava(
        srcRoot,
        apiFile,
        proguardFile,
        Arrays.asList(
            "-showUnannotated",
            "-showAnnotation",
            "test.internal.Show",
            "-hideAnnotation",
            "test.internal.Hide"));

    String api = Files.readString(apiFile, UTF_8);
    String proguard = Files.readString(proguardFile, UTF_8);

    assertTrue("Expected PublicClass in api.txt:\n" + api, api.contains("class PublicClass"));
    assertTrue(
        "Expected PublicClass in proguard.cfg:\n" + proguard,
        proguard.contains("-keep class test.publicpkg.PublicClass"));

    assertFalse("Unexpected HiddenByPackage in api.txt:\n" + api, api.contains("HiddenByPackage"));
    assertFalse(
        "Unexpected HiddenByPackage in proguard.cfg:\n" + proguard,
        proguard.contains("HiddenByPackage"));

    assertFalse("Unexpected Hide in api.txt:\n" + api, api.contains("interface Hide"));
    assertFalse(
        "Unexpected Hide in proguard.cfg:\n" + proguard, proguard.contains("test.internal.Hide"));

    assertTrue(
        "Expected ShownInHiddenPackage in api.txt:\n" + api,
        api.contains("class ShownInHiddenPackage"));
    assertTrue("Expected shownMethod in api.txt:\n" + api, api.contains("shownMethod"));
    assertTrue(
        "Expected ShownInHiddenPackage in proguard.cfg:\n" + proguard,
        proguard.contains("-keep class test.hiddenpkg.ShownInHiddenPackage"));
  }

  @Test
  public void hideAnnotationOnPackage_withShowHiddenFlag_includesHiddenPackageClasses()
      throws Exception {
    Path srcRoot = writeSampleSources();
    Path apiFile = tempFolder.getRoot().toPath().resolve("hidden_api.txt");
    Path proguardFile = tempFolder.getRoot().toPath().resolve("hidden_proguard.cfg");

    runDoclava(
        srcRoot,
        apiFile,
        proguardFile,
        Arrays.asList("-hidden", "-hideAnnotation", "test.internal.Hide"));

    String api = Files.readString(apiFile, UTF_8);
    String proguard = Files.readString(proguardFile, UTF_8);

    assertTrue(
        "Expected HiddenByPackage in api.txt with -hidden:\n" + api,
        api.contains("class HiddenByPackage"));
    assertTrue(
        "Expected HiddenByPackage in proguard.cfg with -hidden:\n" + proguard,
        proguard.contains("-keep class test.hiddenpkg.HiddenByPackage"));
  }

  @Test
  public void hideAnnotationFromClasspathJar_doesNotThrowConcurrentModificationException()
      throws Exception {
    Path cpSrcRoot = tempFolder.newFolder("cp_src").toPath();
    Path cpClassesDir = tempFolder.newFolder("cp_classes").toPath();

    writeFile(
        cpSrcRoot.resolve("test/cp/Hide.java"),
        """
        package test.cp;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.PACKAGE, ElementType.TYPE})
        public @interface Hide {}
        """);
    writeFile(
        cpSrcRoot.resolve("test/cp/PkgOnlyTag.java"),
        """
        package test.cp;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.CLASS)
        @Target(ElementType.PACKAGE)
        public @interface PkgOnlyTag {}
        """);
    writeFile(
        cpSrcRoot.resolve("test/cppkg/package-info.java"),
        """
        @Hide
        package test.cppkg;
        import test.cp.Hide;
        """);
    writeFile(
        cpSrcRoot.resolve("test/cppkg/HiddenClasspathBase.java"),
        """
        package test.cppkg;
        public class HiddenClasspathBase {
          public HiddenClasspathBase() {}
          public void inheritedFromHiddenBase() {}
        }
        """);

    List<String> cpJavaFiles = findJavaFiles(cpSrcRoot);
    ToolProvider javac = ToolProvider.findFirst("javac").orElseThrow();
    List<String> javacArgs = new ArrayList<>();
    javacArgs.add("-d");
    javacArgs.add(cpClassesDir.toString());
    javacArgs.addAll(cpJavaFiles);
    assertEquals(0, javac.run(System.out, System.err, javacArgs.toArray(new String[0])));

    Path srcRoot = tempFolder.newFolder("main_src").toPath();
    writeFile(
        srcRoot.resolve("test/srcpkg/package-info.java"),
        """
        @Hide
        @PkgOnlyTag
        package test.srcpkg;
        import test.cp.Hide;
        import test.cp.PkgOnlyTag;
        """);
    writeFile(
        srcRoot.resolve("test/srcpkg/HiddenInSrcPkg.java"),
        """
        package test.srcpkg;
        public class HiddenInSrcPkg {
          public HiddenInSrcPkg() {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/pubpkg/package-info.java"),
        """
        @PkgOnlyTag
        package test.pubpkg;
        import test.cp.PkgOnlyTag;
        """);
    writeFile(
        srcRoot.resolve("test/pubpkg/PublicSubClass.java"),
        """
        package test.pubpkg;
        import test.cppkg.HiddenClasspathBase;
        public class PublicSubClass extends HiddenClasspathBase {
          public PublicSubClass() {}
          @Override
          public void inheritedFromHiddenBase() {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("cp_api.txt");
    Path proguardFile = tempFolder.getRoot().toPath().resolve("cp_proguard.cfg");

    runDoclava(
        srcRoot,
        apiFile,
        proguardFile,
        Arrays.asList(
            "-classpath",
            cpClassesDir.toString() + File.pathSeparator + getRuntimeClasspath(),
            "-showUnannotated",
            "-hideAnnotation",
            "test.cp.Hide"));

    String api = Files.readString(apiFile, UTF_8);
    assertFalse("Unexpected HiddenInSrcPkg in api.txt:\n" + api, api.contains("HiddenInSrcPkg"));
    assertTrue("Expected PublicSubClass in api.txt:\n" + api, api.contains("class PublicSubClass"));
    assertFalse(
        "Unexpected HiddenClasspathBase in extends clause:\n" + api,
        api.contains("HiddenClasspathBase"));
    assertTrue(
        "Expected overridden method retained on PublicSubClass:\n" + api,
        api.contains("inheritedFromHiddenBase"));
  }

  private Path writeSampleSources() throws Exception {
    Path srcRoot = tempFolder.newFolder("src").toPath();

    // Put @Hide in its own @Hide-annotated package to exercise circular package/annotation lookup.
    writeFile(
        srcRoot.resolve("test/internal/package-info.java"),
        """
        @Hide
        package test.internal;
        """);
    writeFile(
        srcRoot.resolve("test/internal/Hide.java"),
        """
        package test.internal;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.CLASS)
        @Target({
          ElementType.PACKAGE,
          ElementType.TYPE,
          ElementType.METHOD,
          ElementType.FIELD,
          ElementType.CONSTRUCTOR
        })
        public @interface Hide {}
        """);
    writeFile(
        srcRoot.resolve("test/internal/Show.java"),
        """
        package test.internal;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.CLASS)
        @Target({
          ElementType.TYPE,
          ElementType.METHOD,
          ElementType.FIELD,
          ElementType.CONSTRUCTOR
        })
        public @interface Show {}
        """);

    writeFile(
        srcRoot.resolve("test/hiddenpkg/package-info.java"),
        """
        @Hide
        package test.hiddenpkg;
        import test.internal.Hide;
        """);
    writeFile(
        srcRoot.resolve("test/hiddenpkg/HiddenByPackage.java"),
        """
        package test.hiddenpkg;
        public class HiddenByPackage {
          public HiddenByPackage() {}
          public void hiddenMethod() {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/hiddenpkg/ShownInHiddenPackage.java"),
        """
        package test.hiddenpkg;
        import test.internal.Show;
        @Show
        public class ShownInHiddenPackage {
          @Show
          public ShownInHiddenPackage() {}
          @Show
          public void shownMethod() {}
        }
        """);

    writeFile(
        srcRoot.resolve("test/publicpkg/PublicClass.java"),
        """
        package test.publicpkg;
        public class PublicClass {
          public PublicClass() {}
          public void publicMethod() {}
        }
        """);

    return srcRoot;
  }

  private static void runDoclava(
      Path srcRoot, Path apiFile, Path proguardFile, List<String> extraDoclavaFlags)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-sourcepath");
    args.add(srcRoot.toString());
    args.add("-api");
    args.add(apiFile.toString());
    args.add("-proguard");
    args.add(proguardFile.toString());
    args.addAll(extraDoclavaFlags);
    args.addAll(findJavaFiles(srcRoot));
    DoclavaRunner.runNoDocs(args);
  }
}

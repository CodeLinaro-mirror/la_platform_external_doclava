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

import static com.google.doclava.DoclavaRunner.runNoDocs;
import static com.google.doclava.DoclavaRunner.runWithDocs;
import static com.google.doclava.DoclavaRunner.writeFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DocletAdapterFixesTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void subpackagesInvocation_includesPublicClassesAndExcludesPackagePrivate()
      throws Exception {
    Path srcRoot = tempFolder.newFolder("subpkg_src").toPath();
    writeFile(
        srcRoot.resolve("test/pkg/sub/PublicApi.java"),
        """
        package test.pkg.sub;
        public class PublicApi {
          public PublicApi() {}
          public void apiMethod() {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/pkg/sub/PackagePrivateInternal.java"),
        """
        package test.pkg.sub;
        class PackagePrivateInternal {
          PackagePrivateInternal() {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("subpkg_api.txt");
    runNoDocs(
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-subpackages",
            "test.pkg"));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue("Expected PublicApi in api.txt:\n" + api, api.contains("class PublicApi"));
    assertFalse(
        "Unexpected PackagePrivateInternal in api.txt:\n" + api,
        api.contains("PackagePrivateInternal"));
  }

  @Test
  public void nestedEnumsAndAnnotations_areIncludedAndLinkable() throws Exception {
    Path srcRoot = tempFolder.newFolder("nested_src").toPath();
    writeFile(
        srcRoot.resolve("test/nested/Outer.java"),
        """
        package test.nested;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        /**
         * Links to {@link NestedEnum#FIRST} and {@link NestedAnnotation}.
         */
        public class Outer {
          public Outer() {}

          public enum NestedEnum {
            FIRST,
            SECOND
          }

          @Retention(RetentionPolicy.CLASS)
          public @interface NestedAnnotation {
            int value() default 0;
          }
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("nested_api.txt");
    Path docsDir = tempFolder.newFolder("nested_docs").toPath();
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-error",
            "101",
            srcRoot.resolve("test/nested/Outer.java").toString()));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue("Expected Outer.NestedEnum in api.txt:\n" + api, api.contains("Outer.NestedEnum"));
    assertTrue(
        "Expected Outer.NestedAnnotation in api.txt:\n" + api,
        api.contains("class Outer.NestedAnnotation implements java.lang.annotation.Annotation"));
    String nestedAnnotHtml =
        Files.readString(
            docsDir.resolve("reference/test/nested/Outer.NestedAnnotation.html"), UTF_8);
    assertTrue(
        "Expected annotation element value() in HTML:\n" + nestedAnnotHtml,
        nestedAnnotHtml.contains("value"));
  }

  @Test
  public void packageHtmlWithoutSourcepath_appliesPackageHide() throws Exception {
    Path srcRoot = tempFolder.newFolder("pkghtml_src").toPath();
    writeFile(
        srcRoot.resolve("test/annot/Show.java"),
        """
        package test.annot;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD})
        public @interface Show {}
        """);
    writeFile(srcRoot.resolve("test/hiddenhtml/package.html"), "<body>{@hide}</body>\n");
    writeFile(
        srcRoot.resolve("test/hiddenhtml/InternalClass.java"),
        """
        package test.hiddenhtml;
        /** Broken link {@link NonExistentType} should be ignored on hidden class. */
        public class InternalClass {
          public InternalClass() {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/hiddenhtml/PublishedClass.java"),
        """
        package test.hiddenhtml;
        import test.annot.Show;
        @Show
        public class PublishedClass {
          @Show
          public PublishedClass() {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("pkghtml_api.txt");
    Path docsDir = tempFolder.newFolder("pkghtml_docs").toPath();
    // Intentionally omit -sourcepath: pass only .java files directly on the command line.
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-api",
            apiFile.toString(),
            "-showAnnotation",
            "test.annot.Show",
            "-error",
            "101",
            srcRoot.resolve("test/annot/Show.java").toString(),
            srcRoot.resolve("test/hiddenhtml/InternalClass.java").toString(),
            srcRoot.resolve("test/hiddenhtml/PublishedClass.java").toString()));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue(
        "Expected PublishedClass in api.txt:\n" + api, api.contains("class PublishedClass"));
    assertFalse(
        "Unexpected InternalClass in api.txt:\n" + api, api.contains("InternalClass"));
  }

  @Test
  public void primitiveVoidAndArrayClassLiteralsInAnnotations_succeed() throws Exception {
    Path srcRoot = tempFolder.newFolder("class_literals_src").toPath();
    writeFile(
        srcRoot.resolve("test/literals/TypeRef.java"),
        """
        package test.literals;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        @Retention(RetentionPolicy.RUNTIME)
        public @interface TypeRef {
          Class<?>[] value() default {int.class, void.class, String[].class};
        }
        """);
    writeFile(
        srcRoot.resolve("test/literals/AnnotatedTarget.java"),
        """
        package test.literals;
        @TypeRef({boolean.class, byte[].class, AnnotatedTarget.class})
        public class AnnotatedTarget {
          public AnnotatedTarget() {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("literals_api.txt");
    Path docsDir = tempFolder.newFolder("literals_docs").toPath();
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-subpackages",
            "test.literals"));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue(
        "Expected TypeRef in api.txt:\n" + api,
        api.contains("class TypeRef implements java.lang.annotation.Annotation"));
    assertTrue(
        "Expected AnnotatedTarget in api.txt:\n" + api, api.contains("class AnnotatedTarget"));
  }

  @Test
  public void topLevelAndNestedRecords_areIncludedAndLinkable() throws Exception {
    Path srcRoot = tempFolder.newFolder("records_src").toPath();
    writeFile(
        srcRoot.resolve("test/records/PointRecord.java"),
        """
        package test.records;
        /**
         * Top-level record.
         *
         * @see PointRecord
         */
        public record PointRecord(int x, int y) {
          public static final int ORIGIN = 0;

          public int manhattan() {
            return Math.abs(x) + Math.abs(y);
          }
        }
        """);
    writeFile(
        srcRoot.resolve("test/records/OuterWithRecord.java"),
        """
        package test.records;
        /**
         * Links to {@link NestedRecord}.
         */
        public class OuterWithRecord {
          public OuterWithRecord() {}

          public record NestedRecord(String label) {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("records_api.txt");
    Path docsDir = tempFolder.newFolder("records_docs").toPath();
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-error",
            "101",
            "-subpackages",
            "test.records"));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue("Expected PointRecord in api.txt:\n" + api, api.contains("class PointRecord"));
    assertTrue("Expected manhattan() in api.txt:\n" + api, api.contains("manhattan()"));
    assertTrue("Expected ORIGIN field in api.txt:\n" + api, api.contains("ORIGIN"));
    assertTrue(
        "Expected OuterWithRecord.NestedRecord in api.txt:\n" + api,
        api.contains("class OuterWithRecord.NestedRecord"));
  }

  @Test
  public void typeUseAnnotatedParameterizedType_preservesTypeArguments() throws Exception {
    Path srcRoot = tempFolder.newFolder("typeuse_src").toPath();
    writeFile(
        srcRoot.resolve("test/typeuse/Nullable.java"),
        """
        package test.typeuse;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.CLASS)
        @Target(ElementType.TYPE_USE)
        public @interface Nullable {}
        """);
    writeFile(
        srcRoot.resolve("test/typeuse/Holder.java"),
        """
        package test.typeuse;
        import java.util.List;
        public class Holder {
          public Holder() {}
          public @Nullable List<String> names() {
            return null;
          }
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("typeuse_api.txt");
    runNoDocs(
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-subpackages",
            "test.typeuse"));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue(
        "Expected List<java.lang.String> return type in api.txt:\n" + api,
        api.contains("method public java.util.List<java.lang.String> names();"));
  }

  @Test
  public void annotationTypeElements_areNotEmittedAsClassMethodsInApi() throws Exception {
    Path srcRoot = tempFolder.newFolder("annot_methods_src").toPath();
    writeFile(
        srcRoot.resolve("test/annotmethods/Config.java"),
        """
        package test.annotmethods;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        @Retention(RetentionPolicy.RUNTIME)
        public @interface Config {
          String label() default "default";
          int count();
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("annot_methods_api.txt");
    Path docsDir = tempFolder.newFolder("annot_methods_docs").toPath();
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-subpackages",
            "test.annotmethods"));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue(
        "Expected Config in api.txt:\n" + api,
        api.contains("class Config implements java.lang.annotation.Annotation"));
    assertFalse(
        "Unexpected regular method entry for annotation element in api.txt:\n" + api,
        api.contains("method "));
    String html =
        Files.readString(docsDir.resolve("reference/test/annotmethods/Config.html"), UTF_8);
    assertTrue("Expected element label in HTML:\n" + html, html.contains("label"));
    assertTrue("Expected element count in HTML:\n" + html, html.contains("count"));
  }
}

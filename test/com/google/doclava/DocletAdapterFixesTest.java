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
  public void packageHtmlOnSourcepathForNonIncludedPackage_appliesPackageHide() throws Exception {
    Path srcRoot = tempFolder.newFolder("sourcepath_pkghtml_src").toPath();
    writeFile(srcRoot.resolve("test/hiddenbase/package.html"), "<body>{@hide}</body>\n");
    writeFile(
        srcRoot.resolve("test/hiddenbase/HiddenBase.java"),
        """
        package test.hiddenbase;
        public class HiddenBase {
          public HiddenBase() {}
          public void baseMethod() {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/pubpkg/PublicSub.java"),
        """
        package test.pubpkg;
        import test.hiddenbase.HiddenBase;
        public class PublicSub extends HiddenBase {
          public PublicSub() {}
          @Override
          public void baseMethod() {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("sourcepath_pkghtml_api.txt");
    // Pass only PublicSub.java so test.hiddenbase is loaded from -sourcepath, not included.
    runNoDocs(
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            srcRoot.resolve("test/pubpkg/PublicSub.java").toString()));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue("Expected PublicSub in api.txt:\n" + api, api.contains("class PublicSub"));
    assertFalse("Unexpected HiddenBase in api.txt:\n" + api, api.contains("HiddenBase"));
    assertTrue("Expected baseMethod in api.txt:\n" + api, api.contains("baseMethod"));
  }

  @Test
  public void sourcepathPackages_doNotCompleteUnreferencedSiblingClasses() throws Exception {
    Path srcRoot = tempFolder.newFolder("sourcepath_siblings_src").toPath();
    writeFile(
        srcRoot.resolve("test/other/GoodBase.java"),
        """
        package test.other;
        public class GoodBase {
          public GoodBase() {}
        }
        """);
    // Unreferenced sibling files in both a non-included package (test.other) and an included
    // package (test.pubpkg) that cannot be compiled. If PackageDocImpl calls
    // PackageElement.getEnclosedElements(), javac completes them and fails the javadoc run.
    writeFile(
        srcRoot.resolve("test/other/BrokenNonIncludedSibling.java"),
        """
        package test.other;
        public class BrokenNonIncludedSibling {
          this is not valid java syntax
        }
        """);
    writeFile(
        srcRoot.resolve("test/pubpkg/BrokenIncludedSibling.java"),
        """
        package test.pubpkg;
        public class BrokenIncludedSibling {
          this is not valid java syntax
        }
        """);
    Path publicSubSource = srcRoot.resolve("test/pubpkg/PublicSub.java");
    writeFile(
        publicSubSource,
        """
        package test.pubpkg;
        import test.other.GoodBase;
        public class PublicSub extends GoodBase {
          public PublicSub() {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("sourcepath_siblings_api.txt");
    runNoDocs(
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            publicSubSource.toString()));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue("Expected PublicSub in api.txt:\n" + api, api.contains("class PublicSub"));
    assertTrue(
        "Expected GoodBase in api.txt:\n" + api, api.contains("extends test.other.GoodBase"));
  }

  @Test
  public void docsAndHiddenPackages_doNotCompleteUnreferencedSiblingClasses() throws Exception {
    Path srcRoot = tempFolder.newFolder("docs_siblings_src").toPath();
    writeFile(srcRoot.resolve("test/hiddenbase/package.html"), "<body>{@hide}</body>\n");
    writeFile(
        srcRoot.resolve("test/hiddenbase/HiddenBase.java"),
        """
        package test.hiddenbase;
        public class HiddenBase {
          public HiddenBase() {}
        }
        """);
    // Unreferenced invalid siblings in both a non-included hidden package and an included package.
    // PackageInfo.isHidden(), HTML package rendering, and unqualified {@link} lookup must not call
    // PackageElement.getEnclosedElements() and trigger compilation of these files.
    writeFile(
        srcRoot.resolve("test/hiddenbase/BrokenHiddenSibling.java"),
        """
        package test.hiddenbase;
        public class BrokenHiddenSibling {
          this is not valid java syntax
        }
        """);
    writeFile(
        srcRoot.resolve("test/pubpkg/BrokenIncludedSibling.java"),
        """
        package test.pubpkg;
        public class BrokenIncludedSibling {
          this is not valid java syntax
        }
        """);
    Path publicSubSource = srcRoot.resolve("test/pubpkg/PublicSub.java");
    writeFile(
        publicSubSource,
        """
        package test.pubpkg;
        import test.hiddenbase.HiddenBase;
        /** Links to {@link PublicSub}. */
        public class PublicSub extends HiddenBase {
          public PublicSub() {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("docs_siblings_api.txt");
    Path docsDir = tempFolder.newFolder("docs_siblings_docs").toPath();
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-error",
            "101",
            publicSubSource.toString()));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue("Expected PublicSub in api.txt:\n" + api, api.contains("class PublicSub"));
    assertFalse("Unexpected HiddenBase in api.txt:\n" + api, api.contains("HiddenBase"));
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

  @Test
  public void rawGenericTypes_omitTypeArgumentsInApiAndDocs() throws Exception {
    Path srcRoot = tempFolder.newFolder("raw_src").toPath();
    writeFile(
        srcRoot.resolve("test/raw/Box.java"),
        """
        package test.raw;
        import java.util.List;
        public class Box<T> {
          public Box() {}
          public class Inner {
            public Inner() {}
          }
          @SuppressWarnings("rawtypes")
          public Box rawMethod(List rawList, Box.Inner rawInner) {
            return null;
          }
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("raw_api.txt");
    Path docsDir = tempFolder.newFolder("raw_docs").toPath();
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-subpackages",
            "test.raw"));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue(
        "Expected unparameterized raw types in api.txt:\n" + api,
        api.contains("method public test.raw.Box rawMethod(java.util.List, test.raw.Box.Inner);"));
  }

  @Test
  public void overrideWithTypeUseAnnotationAndVarargs_elidesMatchingOverride() throws Exception {
    Path srcRoot = tempFolder.newFolder("sig_src").toPath();
    writeFile(
        srcRoot.resolve("test/sig/Nullable.java"),
        """
        package test.sig;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.CLASS)
        @Target(ElementType.TYPE_USE)
        public @interface Nullable {}
        """);
    writeFile(
        srcRoot.resolve("test/sig/Base.java"),
        """
        package test.sig;
        import java.util.List;
        public class Base {
          public Base() {}
          public void update(@Nullable List<String> items, String... tags) {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/sig/Child.java"),
        """
        package test.sig;
        import java.util.List;
        public class Child extends Base {
          public Child() {}
          @Override
          public void update(List<String> items, String... tags) {}
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("sig_api.txt");
    runNoDocs(
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-subpackages",
            "test.sig"));

    String api = Files.readString(apiFile, UTF_8);
    int occurrences =
        api.split(
                    "method public void update\\(java\\.util\\.List<java\\.lang\\.String>,"
                        + " java\\.lang\\.String\\.\\.\\.\\);",
                    -1)
                .length
            - 1;
    assertTrue(
        "Expected update(...) to appear once on Base and be elided on Child, got "
            + occurrences
            + ":\n"
            + api,
        occurrences == 1);
  }

  @Test
  public void ignoreSourceErrorsWithUnresolvedBounds_succeedsWithoutNpe() throws Exception {
    Path srcRoot = tempFolder.newFolder("err_src").toPath();
    writeFile(
        srcRoot.resolve("test/err/PartialClass.java"),
        """
        package test.err;
        public class PartialClass<T extends MissingUpperBound> {
          public PartialClass() {}
          public T fetch(MissingParam param) {
            return null;
          }
        }
        """);

    Path apiFile = tempFolder.getRoot().toPath().resolve("err_api.txt");
    runNoDocs(
        Arrays.asList(
            "--ignore-source-errors",
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            apiFile.toString(),
            "-subpackages",
            "test.err"));

    String api = Files.readString(apiFile, UTF_8);
    assertTrue("Expected PartialClass in api.txt:\n" + api, api.contains("class PartialClass"));
  }

  @Test
  public void nestedTypesInPackagePrivateEnclosingTypes_matchLegacyInclusion() throws Exception {
    Path srcRoot = tempFolder.newFolder("nested_vis_src").toPath();
    writeFile(
        srcRoot.resolve("test/vis/PackagePrivateTop.java"),
        """
        package test.vis;
        class PackagePrivateTop {
          public static class PublicNested {
            public PublicNested() {}
          }
        }
        """);
    writeFile(
        srcRoot.resolve("test/vis/PublicEntry.java"),
        """
        package test.vis;
        public class PublicEntry {
          PackagePrivateTop ref;
          public PublicEntry() {}

          static class PackagePrivateHolder {
            /** Links to non-documented {@link PackagePrivateHolder}. */
            public interface Callback {
              void onEvent();
            }
          }

          private static class PrivateHolder {
            public static class InsidePrivate {
              public InsidePrivate() {}
            }
          }
        }
        """);

    // 1. Package invocation (-subpackages): PublicNested inside referenced package-private
    // PackagePrivateTop is included because Context.isVisible() is true and its containing
    // package is included.
    Path keepFile = tempFolder.getRoot().toPath().resolve("vis_proguard.cfg");
    Path pkgApiFile = tempFolder.getRoot().toPath().resolve("vis_pkg_api.txt");
    runNoDocs(
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            pkgApiFile.toString(),
            "-proguard",
            keepFile.toString(),
            "-subpackages",
            "test.vis"));

    String keep = Files.readString(keepFile, UTF_8);
    assertTrue(
        "Expected PublicNested inside package-private top-level class in proguard keep list:\n"
            + keep,
        keep.contains("PublicNested"));

    // 2. Explicit source-file invocation with HTML docs (-error 101): RootDocImpl stops at
    // non-documented enclosing classes, so Callback inside PackagePrivateHolder and InsidePrivate
    // inside PrivateHolder are skipped without unresolved-link errors.
    Path explicitApiFile = tempFolder.getRoot().toPath().resolve("vis_explicit_api.txt");
    Path docsDir = tempFolder.newFolder("vis_docs").toPath();
    runWithDocs(
        docsDir,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-api",
            explicitApiFile.toString(),
            "-error",
            "101",
            srcRoot.resolve("test/vis/PackagePrivateTop.java").toString(),
            srcRoot.resolve("test/vis/PublicEntry.java").toString()));

    String explicitApi = Files.readString(explicitApiFile, UTF_8);
    assertFalse(
        "Unexpected Callback inside non-documented PackagePrivateHolder in api.txt:\n"
            + explicitApi,
        explicitApi.contains("Callback"));
    assertFalse(
        "Unexpected InsidePrivate inside private nested class in api.txt:\n" + explicitApi,
        explicitApi.contains("InsidePrivate"));
  }
}

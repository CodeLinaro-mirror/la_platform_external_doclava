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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeConversionAndSubstitutionTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void annotatedNestedAndParameterizedTypes_areOrderIndependent() throws Exception {
    Path srcRoot = tempFolder.newFolder("annot_src").toPath();
    writeFile(
        srcRoot.resolve("test/annot/Nullable.java"),
        """
        package test.annot;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        /** Nullable type-use annotation. */
        @Retention(RetentionPolicy.CLASS)
        @Target(ElementType.TYPE_USE)
        public @interface Nullable {}
        """);
    writeFile(
        srcRoot.resolve("test/annot/Outer.java"),
        """
        package test.annot;
        /** Outer interface. */
        public interface Outer {
          /** Inner interface. */
          interface Inner {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/annot/AnnotatedUser.java"),
        """
        package test.annot;
        import java.util.List;
        /** Uses annotated nested and parameterized types. */
        public class AnnotatedUser {
          public void useAnnotated(Outer.@Nullable Inner inner, @Nullable List<String> items) {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/annot/PlainUser.java"),
        """
        package test.annot;
        import java.util.List;
        /** Uses unannotated nested and parameterized types. */
        public class PlainUser {
          public void usePlain(Outer.Inner inner, List<String> items) {}
        }
        """);

    // Put AnnotatedUser last so Converter's LIFO mClassesNeedingInit processes it first.
    Path docsOrder1 = tempFolder.newFolder("annot_docs_1").toPath();
    runWithDocs(
        docsOrder1,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            srcRoot.resolve("test/annot/Nullable.java").toString(),
            srcRoot.resolve("test/annot/Outer.java").toString(),
            srcRoot.resolve("test/annot/PlainUser.java").toString(),
            srcRoot.resolve("test/annot/AnnotatedUser.java").toString()));

    Path docsOrder2 = tempFolder.newFolder("annot_docs_2").toPath();
    runWithDocs(
        docsOrder2,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            srcRoot.resolve("test/annot/Nullable.java").toString(),
            srcRoot.resolve("test/annot/AnnotatedUser.java").toString(),
            srcRoot.resolve("test/annot/Outer.java").toString(),
            srcRoot.resolve("test/annot/PlainUser.java").toString()));

    String plainHtml1 =
        Files.readString(docsOrder1.resolve("reference/test/annot/PlainUser.html"), UTF_8);
    String plainHtml2 =
        Files.readString(docsOrder2.resolve("reference/test/annot/PlainUser.html"), UTF_8);
    assertEquals(plainHtml1, plainHtml2);
    assertTrue(
        "Expected qualified nested type Outer.Inner with link:\n" + plainHtml1,
        plainHtml1.contains("Outer.Inner.html\">Outer.Inner</a>"));
    assertTrue(
        "Expected parameterized type List<String>:\n" + plainHtml1,
        plainHtml1.contains("List&lt;String&gt;"));

    String annotHtml1 =
        Files.readString(docsOrder1.resolve("reference/test/annot/AnnotatedUser.html"), UTF_8);
    String annotHtml2 =
        Files.readString(docsOrder2.resolve("reference/test/annot/AnnotatedUser.html"), UTF_8);
    assertEquals(annotHtml1, annotHtml2);
    assertTrue(
        "Expected qualified nested type Outer.Inner in AnnotatedUser:\n" + annotHtml1,
        annotHtml1.contains("Outer.Inner.html\">Outer.Inner</a>"));
    assertTrue(
        "Expected parameterized type List<String> in AnnotatedUser:\n" + annotHtml1,
        annotHtml1.contains("List&lt;String&gt;"));
  }

  @Test
  public void boundedTypeVariables_substituteInheritedMethodsAndPreserveArrays() throws Exception {
    Path srcRoot = tempFolder.newFolder("tv_src").toPath();
    writeFile(
        srcRoot.resolve("test/tv/Base.java"),
        """
        package test.tv;
        /** Generic base interface with bounded type variable. */
        public interface Base<T extends Number> {
          T getValue();
          T[] getArray();
        }
        """);
    writeFile(
        srcRoot.resolve("test/tv/Sub.java"),
        """
        package test.tv;
        /** Concrete subtype implementing Base<Integer>. */
        public abstract class Sub implements Base<Integer> {
          public Sub() {}
        }
        """);
    writeFile(
        srcRoot.resolve("test/tv/Holder.java"),
        """
        package test.tv;
        /** Class with bounded type variable field and recurring parameter usage. */
        public class Holder<T extends Number> {
          public T value;
          public Holder() {}
          public void pair(T first, T second) {}
        }
        """);

    Path docsOrder1 = tempFolder.newFolder("tv_docs_1").toPath();
    Path stubsOrder1 = tempFolder.newFolder("tv_stubs_1").toPath();
    runWithDocs(
        docsOrder1,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-stubs",
            stubsOrder1.toString(),
            "-stubpackages",
            "test.tv",
            srcRoot.resolve("test/tv/Base.java").toString(),
            srcRoot.resolve("test/tv/Sub.java").toString(),
            srcRoot.resolve("test/tv/Holder.java").toString()));

    Path docsOrder2 = tempFolder.newFolder("tv_docs_2").toPath();
    Path stubsOrder2 = tempFolder.newFolder("tv_stubs_2").toPath();
    runWithDocs(
        docsOrder2,
        Arrays.asList(
            "-sourcepath",
            srcRoot.toString(),
            "-stubs",
            stubsOrder2.toString(),
            "-stubpackages",
            "test.tv",
            srcRoot.resolve("test/tv/Holder.java").toString(),
            srcRoot.resolve("test/tv/Sub.java").toString(),
            srcRoot.resolve("test/tv/Base.java").toString()));

    String subHtml1 = Files.readString(docsOrder1.resolve("reference/test/tv/Sub.html"), UTF_8);
    String subHtml2 = Files.readString(docsOrder2.resolve("reference/test/tv/Sub.html"), UTF_8);
    assertEquals(subHtml1, subHtml2);
    Pattern getValueWithInteger =
        Pattern.compile(
            "Integer\\s*(?:</nobr>\\s*)?</td>\\s*<td[^>]*>\\s*(?:<nobr>\\s*)?"
                + "<span class=\"sympad\"><a[^>]*>getValue</a>");
    assertTrue(
        "Expected substituted return type Integer for inherited Base.getValue():\n" + subHtml1,
        getValueWithInteger.matcher(subHtml1).find());
    Pattern getArrayWithBrackets =
        Pattern.compile(
            "T\\[\\]\\s*(?:</nobr>\\s*)?</td>\\s*<td[^>]*>\\s*(?:<nobr>\\s*)?"
                + "<span class=\"sympad\"><a[^>]*>getArray</a>");
    assertTrue(
        "Expected array dimension preserved on inherited Base.getArray():\n" + subHtml1,
        getArrayWithBrackets.matcher(subHtml1).find());

    String holderStub1 = Files.readString(stubsOrder1.resolve("test/tv/Holder.java"), UTF_8);
    String holderStub2 = Files.readString(stubsOrder2.resolve("test/tv/Holder.java"), UTF_8);
    assertEquals(holderStub1, holderStub2);
    assertTrue(
        "Expected stub field without upper bound:\n" + holderStub1,
        holderStub1.contains("public T value;"));
    assertFalse(
        "Unexpected upper bound on stub field:\n" + holderStub1,
        holderStub1.contains("extends java.lang.Number value;"));
  }

  @Test
  public void boundedTypeVariableFieldWithoutMethods_omitsUpperBoundInStubs() throws Exception {
    Path srcRoot = tempFolder.newFolder("stub_src").toPath();
    writeFile(
        srcRoot.resolve("test/stub/FieldOnly.java"),
        """
        package test.stub;
        /** Generic class with a field using a bounded type variable. */
        public class FieldOnly<T extends Number> {
          public T value;
        }
        """);

    Path stubsDir = tempFolder.newFolder("stub_out").toPath();
    runNoDocs(
        List.of(
            "-sourcepath",
            srcRoot.toString(),
            "-stubs",
            stubsDir.toString(),
            "-stubpackages",
            "test.stub",
            srcRoot.resolve("test/stub/FieldOnly.java").toString()));

    String stub = Files.readString(stubsDir.resolve("test/stub/FieldOnly.java"), UTF_8);
    assertTrue("Expected stub field without upper bound:\n" + stub, stub.contains("public T value;"));
    assertFalse(
        "Unexpected upper bound on stub field:\n" + stub,
        stub.contains("extends java.lang.Number value;"));
  }
}

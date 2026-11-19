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

import static com.google.doclava.DoclavaRunner.runWithDocs;
import static com.google.doclava.DoclavaRunner.writeFile;
import static java.nio.charset.StandardCharsets.UTF_8;
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
}

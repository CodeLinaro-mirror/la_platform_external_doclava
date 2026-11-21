/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.google.doclava.javadoc;

import com.google.doclava.annotation.Used;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

class PackageDocImpl extends DocImpl<PackageElement> implements PackageDoc {

    private final PackageElement packageElement;


    private String qualifiedName;
    private ClassDoc[] allClasses;
    private ClassDoc[] allClassesFiltered;
    private ClassDoc[] ordinaryClasses;
    private ClassDoc[] exceptions;
    private ClassDoc[] errors;
    private ClassDoc[] enums;
    private ClassDoc[] interfaces;
    private AnnotationTypeDoc[] annotationTypes;
    private AnnotationDesc[] annotations;

    protected PackageDocImpl(PackageElement e, Context context) {
        super(e, context);
        this.packageElement = e;
    }

    static PackageDocImpl create(PackageElement e, Context context) {
        var ret = context.caches.packages.computeIfAbsent(e, el -> new PackageDocImpl(el, context));
        return ret;
    }

    private boolean docCommentTreeInitialized;
    private DocCommentTree docCommentTree;

    @Override
    protected DocCommentTree getDocCommentTree() {
        if (!docCommentTreeInitialized) {
            docCommentTreeInitialized = true;
            DocTrees docTrees = context.environment.getDocTrees();
            docCommentTree = docTrees.getDocCommentTree(packageElement);
            if (docCommentTree == null) {
                docCommentTree = findPackageHtmlCommentTree(docTrees);
            }
        }
        return docCommentTree;
    }

    private DocCommentTree findPackageHtmlCommentTree(DocTrees docTrees) {
        JavaFileManager fileManager = context.environment.getJavaFileManager();
        if (fileManager == null) {
            return null;
        }
        DocCommentTree tree =
                findPackageHtmlInLocation(docTrees, fileManager, StandardLocation.SOURCE_PATH);
        if (tree != null) {
            return tree;
        }
        tree = findSiblingPackageHtml(docTrees, fileManager);
        if (tree != null) {
            return tree;
        }
        return findPackageHtmlInLocation(docTrees, fileManager, StandardLocation.CLASS_PATH);
    }

    private DocCommentTree findPackageHtmlInLocation(
            DocTrees docTrees, JavaFileManager fileManager, Location location) {
        if (!fileManager.hasLocation(location)) {
            return null;
        }
        try {
            FileObject packageHtml =
                    fileManager.getFileForInput(location, qualifiedName(), "package.html");
            return packageHtml == null ? null : docTrees.getDocCommentTree(packageHtml);
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private DocCommentTree findSiblingPackageHtml(DocTrees docTrees, JavaFileManager fileManager) {
        if (!(fileManager instanceof StandardJavaFileManager standardFileManager)) {
            return null;
        }
        List<TypeElement> includedTypes = context.getIncludedTypes(packageElement);
        if (includedTypes.isEmpty()) {
            return null;
        }
        Set<File> visitedDirectories = new HashSet<>();
        for (TypeElement typeElement : includedTypes) {
            if (typeElement.getNestingKind() != NestingKind.TOP_LEVEL) {
                continue;
            }
            var treePath = docTrees.getPath(typeElement);
            if (treePath == null
                    || treePath.getCompilationUnit() == null
                    || treePath.getCompilationUnit().getSourceFile() == null) {
                continue;
            }
            URI uri = treePath.getCompilationUnit().getSourceFile().toUri();
            if ("file".equals(uri.getScheme())) {
                File parentDirectory = new File(uri).getParentFile();
                if (parentDirectory != null && visitedDirectories.add(parentDirectory)) {
                    File packageHtml = new File(parentDirectory, "package.html");
                    if (packageHtml.isFile()) {
                        var fileObjects =
                                standardFileManager.getJavaFileObjects(packageHtml).iterator();
                        if (fileObjects.hasNext()) {
                            DocCommentTree tree = docTrees.getDocCommentTree(fileObjects.next());
                            if (tree != null) {
                                return tree;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] allClasses(boolean filter) {
        if (!filter && allClasses != null) {
            return allClasses;
        }
        if (filter && allClassesFiltered != null) {
            return allClassesFiltered;
        }

        Stream<TypeElement> types =
                filter ? context.getIncludedTypes(packageElement).stream() : allEnclosedTypes();
        List<ClassDocImpl> classes =
                types.filter(e -> e.getKind().isClass() || e.getKind().isInterface())
                        .map(e -> ClassDocImpl.create(e, context))
                        .toList();

        if (filter) {
            return allClassesFiltered = classes.toArray(ClassDoc[]::new);
        } else {
            return allClasses = classes.toArray(ClassDoc[]::new);
        }
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] allClasses() {
        return allClasses(true);
    }

    private Stream<TypeElement> filterIncludedTypes(Predicate<TypeElement> predicate) {
        return context.getIncludedTypes(packageElement).stream().filter(predicate);
    }

    private Stream<TypeElement> allEnclosedTypes() {
        List<TypeElement> result = new ArrayList<>();
        Deque<TypeElement> queue =
                new ArrayDeque<>(ElementFilter.typesIn(packageElement.getEnclosedElements()));
        while (!queue.isEmpty()) {
            TypeElement current = queue.pop();
            if (!context.isSynthetic(current)) {
                result.add(current);
            }
            queue.addAll(ElementFilter.typesIn(current.getEnclosedElements()));
        }
        return result.stream();
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] ordinaryClasses() {
        if (ordinaryClasses == null) {
            ordinaryClasses = filterIncludedTypes(
                    e -> e.getKind() == ElementKind.CLASS || e.getKind() == ElementKind.RECORD)
                    .map(e -> ClassDocImpl.create(e, context))
                    .filter(cls -> !cls.isError() && !cls.isException())
                    .toArray(ClassDocImpl[]::new);
        }
        return ordinaryClasses;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] exceptions() {
        if (exceptions == null) {
            exceptions = filterIncludedTypes(e -> e.getKind() == ElementKind.CLASS)
                    .map(element -> ClassDocImpl.create(element, context))
                    .filter(ClassDocImpl::isException)
                    .toArray(ClassDocImpl[]::new);
        }
        return exceptions;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] errors() {
        if (errors == null) {
            errors = filterIncludedTypes(e -> e.getKind() == ElementKind.CLASS)
                    .map(element -> ClassDocImpl.create(element, context))
                    .filter(ClassDocImpl::isError)
                    .toArray(ClassDocImpl[]::new);
        }
        return errors;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] enums() {
        if (enums == null) {
            enums = filterIncludedTypes(e -> e.getKind() == ElementKind.ENUM)
                    .map(element -> ClassDocImpl.create(element, context))
                    .toArray(ClassDocImpl[]::new);
        }
        return enums;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] interfaces() {
        if (interfaces == null) {
            interfaces = filterIncludedTypes(e -> e.getKind() == ElementKind.INTERFACE)
                    .map(element -> ClassDocImpl.create(element, context))
                    .toArray(ClassDocImpl[]::new);
        }
        return interfaces;
    }

    @Override
    @Used(implemented = true)
    public AnnotationTypeDoc[] annotationTypes() {
        if (annotationTypes == null) {
            annotationTypes = filterIncludedTypes(
                    e -> e.getKind() == ElementKind.ANNOTATION_TYPE)
                    .map(element -> AnnotationTypeDocImpl.create(element, context))
                    .toArray(AnnotationTypeDocImpl[]::new);
        }
        return annotationTypes;
    }

    @Override
    @Used(implemented = true)
    public AnnotationDesc[] annotations() {
        if (annotations == null) {
            annotations = packageElement.getAnnotationMirrors()
                    .stream()
                    // b/270334687: Filter out elements that are not ANNOTATION_TYPE
                    .filter(am -> ((TypeElement) am.getAnnotationType().asElement()).getKind() == ElementKind.ANNOTATION_TYPE)
                    .map(am -> new AnnotationDescImpl(am, context))
                    .toArray(AnnotationDesc[]::new);
        }
        return annotations;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc findClass(String className) {
        for (ClassDoc c : allClasses()) {
            if (c.name().equals(className)) {
                return c;
            }
        }
        return null;
    }

    @Override
    @Used(implemented = true)
    public boolean isIncluded() {
        return context.environment.isIncluded(packageElement);
    }

    @Override
    @Used(implemented = true)
    public String name() {
        return qualifiedName();
    }

    @Override
    public String qualifiedName() {
        if (qualifiedName == null) {
            qualifiedName = packageElement.getQualifiedName().toString();
        }
        return qualifiedName;
    }
}

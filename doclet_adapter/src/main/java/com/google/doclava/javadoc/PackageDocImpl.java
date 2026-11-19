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
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardJavaFileManager;

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
            if (docCommentTree == null
                    && context.environment.getJavaFileManager()
                            instanceof StandardJavaFileManager sfm) {
                for (Element enclosed : packageElement.getEnclosedElements()) {
                    var treePath = docTrees.getPath(enclosed);
                    if (treePath == null
                            || treePath.getCompilationUnit() == null
                            || treePath.getCompilationUnit().getSourceFile() == null) {
                        continue;
                    }
                    URI uri = treePath.getCompilationUnit().getSourceFile().toUri();
                    if ("file".equals(uri.getScheme())) {
                        File parent = new File(uri).getParentFile();
                        if (parent != null) {
                            File packageHtml = new File(parent, "package.html");
                            if (packageHtml.isFile()) {
                                var it = sfm.getJavaFileObjects(packageHtml).iterator();
                                if (it.hasNext()) {
                                    docCommentTree = docTrees.getDocCommentTree(it.next());
                                    if (docCommentTree != null) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return docCommentTree;
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

        List<ClassDocImpl> classes =
                filterEnclosedElements(
                        e -> e.getKind().isClass() || e.getKind().isInterface(), filter)
                .map(e -> ClassDocImpl.create((TypeElement) e, context))
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

    private Stream<Element> filterEnclosedElements(Predicate<Element> predicate) {
        return filterEnclosedElements(predicate, true);
    }

    private Stream<Element> filterEnclosedElements(Predicate<Element> predicate, boolean filter) {
        EnclosedElementsIterator it = new EnclosedElementsIterator(packageElement, filter);
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false)
                .filter(e -> e instanceof TypeElement te
                        && !context.isSynthetic(te)
                        && (!filter || context.shouldDocument(te)))
                .filter(predicate);
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] ordinaryClasses() {
        if (ordinaryClasses == null) {
            ordinaryClasses = filterEnclosedElements(
                    e -> e.getKind() == ElementKind.CLASS || e.getKind() == ElementKind.RECORD)
                    .map(e -> ClassDocImpl.create((TypeElement) e, context))
                    .filter(cls -> !cls.isError() && !cls.isException())
                    .toArray(ClassDocImpl[]::new);
        }
        return ordinaryClasses;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] exceptions() {
        if (exceptions == null) {
            exceptions = filterEnclosedElements(e -> e.getKind() == ElementKind.CLASS)
                    .map(element -> ClassDocImpl.create((TypeElement) element, context))
                    .filter(ClassDocImpl::isException)
                    .toArray(ClassDocImpl[]::new);
        }
        return exceptions;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] errors() {
        if (errors == null) {
            errors = filterEnclosedElements(e -> e.getKind() == ElementKind.CLASS)
                    .map(element -> ClassDocImpl.create((TypeElement) element, context))
                    .filter(ClassDocImpl::isError)
                    .toArray(ClassDocImpl[]::new);
        }
        return errors;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] enums() {
        if (enums == null) {
            enums = filterEnclosedElements(e -> e.getKind() == ElementKind.ENUM)
                    .map(element -> ClassDocImpl.create((TypeElement) element, context))
                    .toArray(ClassDocImpl[]::new);
        }
        return enums;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] interfaces() {
        if (interfaces == null) {
            interfaces = filterEnclosedElements(e -> e.getKind() == ElementKind.INTERFACE)
                    .map(element -> ClassDocImpl.create((TypeElement) element, context))
                    .toArray(ClassDocImpl[]::new);
        }
        return interfaces;
    }

    @Override
    @Used(implemented = true)
    public AnnotationTypeDoc[] annotationTypes() {
        if (annotationTypes == null) {
            annotationTypes = filterEnclosedElements(
                    e -> e.getKind() == ElementKind.ANNOTATION_TYPE)
                    .map(element -> AnnotationTypeDocImpl.create((TypeElement) element, context))
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

    private final class EnclosedElementsIterator implements Iterator<Element> {

        private final Deque<Element> queue;
        private final boolean filter;

        public EnclosedElementsIterator(Element element, boolean filter) {
            this.queue = new ArrayDeque<>(element.getEnclosedElements());
            this.filter = filter;
        }

        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }

        @Override
        public Element next() {
            Element current = queue.pop();
            // Match legacy PackageDocImpl.getClasses(true) -> ClassDocImpl.addAllClasses(list, true)
            // by not descending into non-documented types when filtering; allClasses(false) passes
            // filter = false and still traverses all enclosed types.
            if (current instanceof TypeElement te
                    && (!filter || context.shouldDocument(te))
                    && !current.getEnclosedElements().isEmpty()) {
                queue.addAll(current.getEnclosedElements());
            }
            return current;
        }
    }
}

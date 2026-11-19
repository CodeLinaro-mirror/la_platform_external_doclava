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

import com.google.doclava.annotation.Unused;
import com.google.doclava.annotation.Used;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SourcePosition;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import jdk.javadoc.doclet.DocletEnvironment;

/**
 * The {@code RootDocImpl} is an implementation of the "old" Doclet APIs (previously under
 * {@code com.sun.javadoc.*}, deprecated since 9 and removed in 13) in terms of new Doclet APIs
 * {@link jdk.javadoc.doclet.Doclet}. ({@link DocletEnvironment}).
 */
public class RootDocImpl extends DocImpl<Element> implements RootDoc {

    private final List<ClassDoc> includedClasses = new ArrayList<>();
    private ClassDoc[] classes;

    public RootDocImpl(DocletEnvironment environment) {
        super(null, new Context(environment));

        for (var e : environment.getIncludedElements()) {
            switch (e.getKind()) {
                case CLASS, INTERFACE, ENUM, ANNOTATION_TYPE ->
                        includedClasses.add(ClassDocImpl.create((TypeElement) e, context));
                case PACKAGE -> PackageDocImpl.create((PackageElement) e, context);
                case RECORD -> throw new UnsupportedOperationException(
                        "records are not yet supported.");
                case MODULE -> {}
                default -> throw new UnsupportedOperationException(
                        "Not yet supported top-level TypeElement kind:" + e.getKind());
            }
        }
    }

    @Override
    @Unused
    public String[][] options() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public PackageDoc[] specifiedPackages() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public ClassDoc[] specifiedClasses() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] classes() {
        if (classes == null) {
            classes = includedClasses.toArray(ClassDoc[]::new);
        }
        return classes;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc classNamed(String qualifiedName) {
        TypeElement cls = context.environment.getElementUtils().getTypeElement(qualifiedName);
        return cls == null ? null : ClassDocImpl.create(cls, context);
    }

    @Override
    @Used(implemented = true)
    public PackageDoc packageNamed(String name) {
        for (PackageDoc pkg : context.caches.packages.values()) {
            if (pkg.name().equals(name)) {
                return pkg;
            }
        }
        return null;
    }

    @Override
    @Used(implemented = true)
    public String name() {
        return "*! Doclava !*";
    }

    @Override
    public String qualifiedName() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public boolean isIncluded() {
        return false;
    }

    @Override
    @Unused
    public void printError(String msg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public void printError(SourcePosition pos, String msg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public void printWarning(String msg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public void printWarning(SourcePosition pos, String msg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public void printNotice(String msg) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public void printNotice(SourcePosition pos, String msg) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}

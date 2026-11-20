/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.doclava.javadoc;

import com.sun.javadoc.Doc;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.ThrowsTree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.tools.JavaFileObject;
import jdk.javadoc.doclet.DocletEnvironment;

/**
 * Holds temporary objects required to construct {@link RootDocImpl} from a {@link
 * jdk.javadoc.doclet.DocletEnvironment}.
 */
class Context {

    public final DocletEnvironment environment;
    public final Caches caches = new Caches();
    public final DocletElementUtils docletElementUtils;
    private Map<PackageElement, List<TypeElement>> includedTopLevelTypesByPackage;

    public Doc obtain(Element element) {
        return OBTAIN_VISITOR.visit(element, this);
    }

    List<TypeElement> getIncludedTopLevelTypes(PackageElement packageElement) {
        if (includedTopLevelTypesByPackage == null) {
            Map<PackageElement, List<TypeElement>> typesByPackage = new HashMap<>();
            for (Element element : environment.getIncludedElements()) {
                if (element instanceof TypeElement typeElement
                        && typeElement.getEnclosingElement()
                                instanceof PackageElement enclosingPackage) {
                    typesByPackage
                            .computeIfAbsent(enclosingPackage, ignored -> new ArrayList<>())
                            .add(typeElement);
                }
            }
            includedTopLevelTypesByPackage = typesByPackage;
        }
        return includedTopLevelTypesByPackage.getOrDefault(packageElement, List.of());
    }

    public static class Caches {

        public final Map<TypeElement, ClassDocImpl> classes = new HashMap<>();
        public final Map<TypeElement, AnnotationTypeDocImpl> annotations = new HashMap<>();
        public final Map<PackageElement, PackageDocImpl> packages = new HashMap<>();
        public final Map<ExecutableElement, AnnotationMethodDocImpl> annotationMethods =
                new HashMap<>();
        public final Map<AnnotationValue, AnnotationValueImpl> annotationValues = new HashMap<>();
        public final Map<ExecutableElement, ConstructorDocImpl> constructors = new HashMap<>();
        public final Map<ExecutableElement, MethodDocImpl> methods = new HashMap<>();
        public final Map<VariableElement, FieldDocImpl> fields = new HashMap<>();
        public final Map<VariableElement, ParameterImpl> parameters = new HashMap<>();

        public final Tags tags = new Tags();
        public final Types types = new Types();

        public static class Tags {

            public final Map<Element, Map<DocTree, TagImpl>> generic = new HashMap<>();
            public final Map<Element, Map<SeeTree, SeeTagImpl>> see = new HashMap<>();
            public final Map<Element, Map<SerialFieldTree, SerialFieldTagImpl>> serialField =
                    new HashMap<>();
            public final Map<Element, Map<ParamTree, ParamTagImpl>> param = new HashMap<>();
            public final Map<Element, Map<ThrowsTree, ThrowsTagImpl>> throwz = new HashMap<>();
        }

        public static class Types {

            public final Map<TypeMirror, TypeImpl> common = new HashMap<>();
            public final Map<DeclaredType, AnnotatedTypeImpl> annotated = new HashMap<DeclaredType, AnnotatedTypeImpl>();
            public final Map<WildcardType, WildcardTypeImpl> wildcard = new HashMap<>();
            public final Map<DeclaredType, ParameterizedTypeImpl> parameterized = new HashMap<>();
            public final Map<TypeVariable, TypeVariableImpl> typevar = new HashMap<>();
            public final Map<ArrayType, ArrayTypeImpl> array = new HashMap<>();
        }
    }

    public Context(DocletEnvironment environment) {
        this.environment = environment;
        this.docletElementUtils = new DocletElementUtils(environment);
    }

    private Boolean protectedSelected;

    private boolean isProtectedSelected() {
        if (protectedSelected == null) {
            TypeElement obj = environment.getElementUtils().getTypeElement("java.lang.Object");
            protectedSelected = obj != null && obj.getEnclosedElements().stream()
                    .filter(e -> e.getModifiers().contains(Modifier.PROTECTED))
                    .findFirst()
                    .map(environment::isSelected)
                    .orElse(true);
        }
        return protectedSelected;
    }

    boolean isSynthetic(Element e) {
        return environment.getElementUtils().getOrigin(e) == Elements.Origin.SYNTHETIC;
    }

    boolean checkAccess(TypeElement te) {
        Set<Modifier> mods = te.getModifiers();
        if (mods.contains(Modifier.PUBLIC) || environment.isSelected(te)) {
            return true;
        }
        if (mods.contains(Modifier.PROTECTED)) {
            return isProtectedSelected();
        }
        return false;
    }

    /**
     * Matches legacy {@code com.sun.tools.javadoc.DocEnv.isVisible(ClassSymbol)}: a static member
     * type (including interfaces, enums, annotations, and records, which are implicitly static) is
     * visible whenever its own access modifier is selected, without requiring its enclosing type to
     * be visible. Top-down traversal in {@link RootDocImpl} and {@link PackageDocImpl} still stops
     * at non-documented enclosing types so nested members of private/package-private classes are
     * not listed in {@code RootDoc.classes()} or {@code PackageDoc.allClasses(true)}.
     */
    boolean isVisible(TypeElement te) {
        if (!checkAccess(te)) {
            return false;
        }
        Element encl = te.getEnclosingElement();
        if (encl instanceof TypeElement enclType) {
            if (te.getModifiers().contains(Modifier.STATIC)
                    || te.getKind().isInterface()
                    || te.getKind() == ElementKind.ENUM
                    || te.getKind() == ElementKind.RECORD) {
                return true;
            }
            return isVisible(enclType);
        }
        return true;
    }

    boolean shouldDocument(TypeElement te) {
        if (isSynthetic(te)) {
            return false;
        }
        if (environment.getFileKind(te) != JavaFileObject.Kind.SOURCE) {
            return false;
        }
        return isVisible(te);
    }

    private static final SimpleElementVisitor14<Doc, Context> OBTAIN_VISITOR =
            new SimpleElementVisitor14<>() {

                @Override
                public Doc visitPackage(PackageElement e, Context context) {
                    return PackageDocImpl.create(e, context);
                }

                @Override
                public Doc visitType(TypeElement e, Context context) {
                    return switch (e.getKind()) {
                        case ANNOTATION_TYPE, CLASS, ENUM, INTERFACE, RECORD ->
                                ClassDocImpl.create(e, context);
                        default -> throw new IllegalArgumentException(
                                "Expected ANNOTATION_TYPE, CLASS, "
                                        + "ENUM, INTERFACE, or RECORD; but got " + e.getKind());
                    };
                }
            };
}

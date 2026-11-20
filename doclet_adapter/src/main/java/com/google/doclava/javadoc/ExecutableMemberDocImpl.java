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
import com.sun.javadoc.ExecutableMemberDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.ThrowsTag;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements.Origin;

abstract class ExecutableMemberDocImpl extends MemberDocImpl<ExecutableElement> implements
        ExecutableMemberDoc {

    protected final ExecutableElement executableElement;

    // Cached fields
    private ClassDoc[] thrownExceptions;
    private Parameter[] parameters;
    private String signature;
    private String flatSignature;
    private TypeVariable[] typeParameters;

    protected ExecutableMemberDocImpl(ExecutableElement e, Context context) {
        super(e, context);
        this.executableElement = e;
    }

    @Override
    @Used(implemented = true)
    public boolean isNative() {
        return (reflectModifiers & Modifier.NATIVE) != 0;
    }

    @Override
    @Used(implemented = true)
    public boolean isSynchronized() {
        return (reflectModifiers & Modifier.SYNCHRONIZED) != 0;
    }

    @Override
    @Used(implemented = true)
    public boolean isVarArgs() {
        return executableElement.isVarArgs();
    }

    @Used(implemented = true)
    public boolean isSynthetic() {
        return context.environment.getElementUtils()
                .getOrigin(executableElement) == Origin.SYNTHETIC;
    }

    @Override
    @Used(implemented = true)
    public boolean isIncluded() {
        return context.environment.isIncluded(executableElement);
    }

    @Override
    @Unused
    public ThrowsTag[] throwsTags() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public ParamTag[] paramTags() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public ParamTag[] typeParamTags() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public ClassDoc[] thrownExceptions() {
        if (thrownExceptions == null) {
            thrownExceptions = executableElement.getThrownTypes()
                    .stream()
                    .map(typeMirror -> TypeImpl.create(typeMirror, context).asClassDoc())
                    .filter(Objects::nonNull)
                    .toArray(ClassDoc[]::new);
        }
        return thrownExceptions;
    }

    @Override
    @Unused
    public Type[] thrownExceptionTypes() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public Parameter[] parameters() {
        if (parameters == null) {
            parameters = executableElement.getParameters()
                    .stream()
                    .map(variableElement -> ParameterImpl.create(variableElement, context))
                    .toArray(ParameterImpl[]::new);
        }
        return parameters;
    }

    @Override
    @Unused
    public Type receiverType() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public TypeVariable[] typeParameters() {
        if (typeParameters == null) {
            typeParameters = executableElement.getTypeParameters()
                    .stream()
                    .map(tpe -> TypeVariableImpl.create(tpe.asType(), context))
                    .toArray(TypeVariable[]::new);
        }
        return typeParameters;
    }

    @Override
    @Used(implemented = true)
    public String signature() {
        if (signature == null) {
            signature = makeSignature(true);
        }
        return signature;
    }

    @Override
    @Used(implemented = true)
    public String flatSignature() {
        if (flatSignature == null) {
            flatSignature = makeSignature(false);
        }
        return flatSignature;
    }

    private String makeSignature(boolean full) {
        var params = executableElement.getParameters();
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatSignatureType(params.get(i).asType(), full));
        }
        if (isVarArgs() && sb.length() >= 2 && sb.substring(sb.length() - 2).equals("[]")) {
            sb.replace(sb.length() - 2, sb.length(), "...");
        }
        sb.append(")");
        return sb.toString();
    }

    private String formatSignatureType(TypeMirror m, boolean full) {
        return switch (m.getKind()) {
            case ARRAY -> formatSignatureType(((ArrayType) m).getComponentType(), full) + "[]";
            case DECLARED -> {
                DeclaredType dt = (DeclaredType) m;
                TypeElement te = (TypeElement) dt.asElement();
                // Match legacy ParameterizedTypeImpl.parameterizedTypeToString and
                // ClassDocImpl.getClassName: flatSignature() strips the package prefix but retains
                // enclosing class names (e.g. "Map.Entry") and any enclosing type arguments.
                String base;
                if (dt.getEnclosingType().getKind() == TypeKind.DECLARED) {
                    base = formatSignatureType(dt.getEnclosingType(), full)
                            + "." + te.getSimpleName();
                } else {
                    base = full
                            ? te.getQualifiedName().toString()
                            : ClassDocImpl.create(te, context).name();
                }
                if (dt.getTypeArguments().isEmpty()) {
                    yield base;
                }
                String args = dt.getTypeArguments().stream()
                        .map(arg -> formatSignatureType(arg, full))
                        .collect(Collectors.joining(","));
                yield base + "<" + args + ">";
            }
            case WILDCARD -> {
                WildcardType wt = (WildcardType) m;
                if (wt.getExtendsBound() != null) {
                    yield "? extends " + formatSignatureType(wt.getExtendsBound(), full);
                }
                if (wt.getSuperBound() != null) {
                    yield "? super " + formatSignatureType(wt.getSuperBound(), full);
                }
                yield "?";
            }
            // Qualified to avoid colliding with imported com.sun.javadoc.TypeVariable above.
            case TYPEVAR -> ((javax.lang.model.type.TypeVariable) m)
                    .asElement().getSimpleName().toString();
            default -> {
                Type t = TypeImpl.create(m, context);
                yield full ? t.qualifiedTypeName() : t.simpleTypeName();
            }
        };
    }

    @Override
    @Used(implemented = true)
    public SourcePosition position() {
        return SourcePositionImpl.create(context, executableElement);
    }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.Type;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import org.junit.Ignore;
import org.junit.Test;

public class AnnotatedTypeImplTest extends BaseTest {

    private AnnotatedTypeImpl annotatedClass;
    private AnnotatedTypeImpl annotatedList;

    @Override
    public void setUp() {
        super.setUp();

        annotatedClass = AnnotatedTypeImpl.create((DeclaredType) CLASS.annotatedClass.asType(),
                context);
        var field = ElementFilter.fieldsIn(CLASS.annotatedClass.getEnclosedElements())
                .stream()
                .filter(f -> "annotatedParameterizedField".equals(f.getSimpleName().toString()))
                .findFirst()
                .orElseThrow();
        annotatedList = AnnotatedTypeImpl.create((DeclaredType) field.asType(), context);
    }

    @Ignore("Not used")
    @Test
    public void annotations() {
    }

    @Test
    public void underlyingType() {
        Type underlyingType = annotatedClass.underlyingType();
        assertEquals("AnnotatedClass", underlyingType.typeName());
    }

    @Test
    public void delegatesTypeMethods() {
        Type underlying = annotatedClass.underlyingType();
        assertEquals(underlying.typeName(), annotatedClass.typeName());
        assertEquals(underlying.qualifiedTypeName(), annotatedClass.qualifiedTypeName());
        assertEquals(underlying.simpleTypeName(), annotatedClass.simpleTypeName());
        assertEquals(underlying.dimension(), annotatedClass.dimension());
        assertEquals(underlying.isPrimitive(), annotatedClass.isPrimitive());
        assertEquals(underlying.asClassDoc(), annotatedClass.asClassDoc());
        assertNull(annotatedClass.asParameterizedType());
        assertNull(annotatedClass.asTypeVariable());
        assertNull(annotatedClass.asWildcardType());
        assertNull(annotatedClass.asAnnotationTypeDoc());
        assertNull(annotatedClass.getElementType());
    }

    @Test
    public void delegatesParameterizedTypeMethods() {
        Type underlying = annotatedList.underlyingType();
        ParameterizedType parameterized = annotatedList.asParameterizedType();
        assertNotNull(parameterized);
        assertEquals(underlying.asParameterizedType(), parameterized);
        assertEquals(1, parameterized.typeArguments().length);
        assertEquals("java.lang.String", parameterized.typeArguments()[0].qualifiedTypeName());
        assertEquals(underlying.qualifiedTypeName(), annotatedList.qualifiedTypeName());
        assertEquals(underlying.simpleTypeName(), annotatedList.simpleTypeName());
        assertEquals(underlying.asClassDoc(), annotatedList.asClassDoc());
        assertNull(annotatedList.getElementType());
    }
}
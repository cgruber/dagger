/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.internal.codegen;

import com.google.common.collect.Iterables;
import com.google.testing.compile.CompilationRule;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static dagger.internal.codegen.AnnotationValues.annotationValuesEquivalent;
import static dagger.internal.codegen.AnnotationValuesTest.SimpleEnum.BLAH;
import static dagger.internal.codegen.AnnotationValuesTest.SimpleEnum.FOO;
import static org.truth0.Truth.ASSERT;

/**
 * Tests {@link Key}.
 */
@RunWith(JUnit4.class)
public class AnnotationValuesTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;
  private Types types;
  private Key.Factory keyFactory;

  @Before public void setUp() {
    this.types = compilationRule.getTypes();
    this.elements = compilationRule.getElements();
    this.keyFactory = new Key.Factory(types, elements);
  }

  enum SimpleEnum{
    BLAH, FOO
  }

  @interface Outer {
    SimpleEnum value();
  }

  @Outer(BLAH) static class TestClassBlah {}
  @Outer(BLAH) static class TestClassBlah2 {}
  @Outer(FOO) static class TestClassFoo {}

  @Test public void simpleValueEquivalence() {
    AnnotationMirror blah = Iterables.getOnlyElement(elements.getTypeElement(
        TestClassBlah.class.getCanonicalName()).getAnnotationMirrors());
    AnnotationMirror blah2 = Iterables.getOnlyElement(elements.getTypeElement(
        TestClassBlah2.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(
        annotationValuesEquivalent(blah.getElementValues(), blah2.getElementValues())).isTrue();

    AnnotationMirror foo = Iterables.getOnlyElement(elements.getTypeElement(
        TestClassFoo.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(
        annotationValuesEquivalent(blah.getElementValues(), foo.getElementValues())).isFalse();
  }


  @interface DefaultingOuter {
    SimpleEnum value() default SimpleEnum.BLAH;
  }

  @DefaultingOuter class TestWithDefaultingOuterDefault {}
  @DefaultingOuter(BLAH) class TestWithDefaultingOuterBlah {}
  @DefaultingOuter(FOO) class TestWithDefaultingOuterFoo {}

  @Test public void simpleValueEquivalenceWithDefault() {
    AnnotationMirror defaultBlah = Iterables.getOnlyElement(elements.getTypeElement(
        TestWithDefaultingOuterDefault.class.getCanonicalName()).getAnnotationMirrors());
    AnnotationMirror blah = Iterables.getOnlyElement(elements.getTypeElement(
        TestWithDefaultingOuterBlah.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(annotationValuesEquivalent(
        blah.getElementValues(), defaultBlah.getElementValues())).isTrue();

    AnnotationMirror foo = Iterables.getOnlyElement(elements.getTypeElement(
        TestClassFoo.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(
        annotationValuesEquivalent(blah.getElementValues(), foo.getElementValues())).isFalse();
  }

  @interface SimpleAnnotation {}

  @interface AnnotatedOuter {
    DefaultingOuter value();
  }

  @AnnotatedOuter(@DefaultingOuter) class TestDefaultNestedAnnotated {}
  @AnnotatedOuter(@DefaultingOuter(BLAH)) class TestBlahNestedAnnotated {}
  @AnnotatedOuter(@DefaultingOuter(FOO)) class TestFooNestedAnnotated {}

  @Test public void equivalenceOfAnnotatedAnnotations() {
    // Annotations containing AnnotationMirrors as their annotation values.

    AnnotationMirror defaultBlah = Iterables.getOnlyElement(elements.getTypeElement(
        TestDefaultNestedAnnotated.class.getCanonicalName()).getAnnotationMirrors());
    AnnotationMirror blah = Iterables.getOnlyElement(elements.getTypeElement(
        TestBlahNestedAnnotated.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(annotationValuesEquivalent(
        blah.getElementValues(), defaultBlah.getElementValues())).isTrue();

    AnnotationMirror foo = Iterables.getOnlyElement(elements.getTypeElement(
        TestFooNestedAnnotated.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(
        annotationValuesEquivalent(blah.getElementValues(), foo.getElementValues())).isFalse();
  }

  @interface OuterWithValueArray {
    DefaultingOuter[] value() default {};
  }


  @OuterWithValueArray class TestValueArrayWithDefault {}
  @OuterWithValueArray({}) class TestValueArrayWithEmpty {}

  @Test public void equivalenceOfAnnotatedAnnotationArrays_EmptyAndDefaultEmpty() {
    AnnotationMirror defaultEmpty = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithDefault.class.getCanonicalName()).getAnnotationMirrors());
    AnnotationMirror empty = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithEmpty.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(annotationValuesEquivalent(
        defaultEmpty.getElementValues(), empty.getElementValues())).isTrue();
  }

  @OuterWithValueArray({@DefaultingOuter}) class TestValueArrayWithOneDefault {}
  @OuterWithValueArray(@DefaultingOuter(BLAH)) class TestValueArrayWithOneBlah {}
  @OuterWithValueArray(@DefaultingOuter(FOO)) class TestValueArrayWithOneFoo {}

  @Test public void equivalenceOfAnnotatedAnnotationArrays_ContainingOneAnnotation() {
    AnnotationMirror defaultBlah = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithOneDefault.class.getCanonicalName()).getAnnotationMirrors());
    AnnotationMirror blah = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithOneBlah.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(annotationValuesEquivalent(
        defaultBlah.getElementValues(), blah.getElementValues())).isTrue();

    AnnotationMirror foo = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithOneFoo.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(annotationValuesEquivalent(
        blah.getElementValues(), foo.getElementValues())).isFalse();
  }


  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter(BLAH)})
  class TestValueArrayWithFooBlah {}
  @OuterWithValueArray({@DefaultingOuter(FOO), @DefaultingOuter(BLAH)})
  class TestValueArrayWithFooBlah2 {} // Different instances than on TestValueArrayWithFooBlah.
  @OuterWithValueArray({@DefaultingOuter(BLAH), @DefaultingOuter(FOO)})
  class TestValueArrayWithBlahFoo {}

  @Test public void equivalenceOfAnnotatedAnnotationArrays_MultipleAnnotations_InOrder() {
    AnnotationMirror array = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithFooBlah.class.getCanonicalName()).getAnnotationMirrors());
    AnnotationMirror array2 = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithFooBlah2.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(annotationValuesEquivalent(
        array.getElementValues(), array2.getElementValues())).isTrue();

    AnnotationMirror inverse = Iterables.getOnlyElement(elements.getTypeElement(
        TestValueArrayWithBlahFoo.class.getCanonicalName()).getAnnotationMirrors());
    ASSERT.that(
        annotationValuesEquivalent(array.getElementValues(), inverse.getElementValues())).isFalse();
  }

}

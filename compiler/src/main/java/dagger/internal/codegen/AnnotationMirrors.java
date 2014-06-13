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

import com.google.auto.common.MoreElements;
import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.AnnotationValues.annotationValuesEquivalent;

/**
 * A utility class for working with {@link AnnotationMirror} instances.
 *
 * @author Gregory Kick
 */
final class AnnotationMirrors {
  /**
   * An alternative to {@link Element#getAnnotation} that returns an {@link AnnotationMirror} rather
   * than the weird, half-implementation returned by that method.
   */
  static Optional<AnnotationMirror> getAnnotationMirror(Element element,
      Class<? extends Annotation> annotationType) {
    String annotationName = annotationType.getName();
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (MoreElements.asType(annotationMirror.getAnnotationType().asElement())
          .getQualifiedName().contentEquals(annotationName)) {
        return Optional.of(annotationMirror);
      }
    }
    return Optional.absent();
  }

  /**
   * Takes a {@link Map} like that returned from {@link Elements#getElementValuesWithDefaults} and
   * key it by the member name rather than the {@link ExecutableElement}.
   */
  static ImmutableMap<String, AnnotationValue> simplifyAnnotationValueMap(
      Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap) {
    ImmutableMap.Builder<String, AnnotationValue> builder = ImmutableMap.builder();
    for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
        : annotationValueMap.entrySet()) {
      builder.put(entry.getKey().getSimpleName().toString(), entry.getValue());
    }
    return builder.build();
  }

  static ImmutableList<TypeMirror> getAttributeAsListOfTypes(Elements elements,
      AnnotationMirror annotationMirror, String attributeName) {
    checkNotNull(annotationMirror);
    checkNotNull(attributeName);
    ImmutableMap<String, AnnotationValue> valueMap =
        simplifyAnnotationValueMap(elements.getElementValuesWithDefaults(annotationMirror));
    ImmutableList.Builder<TypeMirror> builder = ImmutableList.builder();

    List<? extends AnnotationValue> typeValues =
        (List<? extends AnnotationValue>) valueMap.get(attributeName).getValue();
    for (AnnotationValue typeValue : typeValues) {
      builder.add((TypeMirror) typeValue.getValue());
    }
    return builder.build();
  }

  private static final Equivalence<AnnotationMirror> ANNOTATION_MIRROR_EQUIVALENCE =
    new Equivalence<AnnotationMirror>() {
      @Override
      protected boolean doEquivalent(AnnotationMirror left, AnnotationMirror right) {
        if (!MoreTypes.equivalence()
            .equivalent(left.getAnnotationType(), right.getAnnotationType())) {
          return false;
        }
        return annotationValuesEquivalent(left.getElementValues(), right.getElementValues());
      }
      @Override protected int doHash(AnnotationMirror annotation) {
        // Note: Will collide with values, but in practice sets containing annotation values
        //     will be so small as to be negligible.
        // TODO(cgruber): Revisit this if performance becomes problematic in the processor.
        return Objects.hashCode(annotation.getAnnotationType());
      }
    };

  static Equivalence<AnnotationMirror> equivalence() {
    return ANNOTATION_MIRROR_EQUIVALENCE;
  }

  private AnnotationMirrors() {}
}

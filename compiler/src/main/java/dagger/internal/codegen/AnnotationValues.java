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

import com.google.common.base.Equivalence;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;

/**
 * A utility class for working with {@link AnnotationValue} instances.
 *
 * @author Christian Gruber
 */
final class AnnotationValues {

  static boolean annotationValuesEquivalent(
      Map<? extends ExecutableElement, ? extends AnnotationValue> leftValues,
      Map<? extends ExecutableElement, ? extends AnnotationValue> rightValues) {
    // Requires the union set of annotation properties since one annotation may
    // explicitly set its value with the default value for documentation purposes
    for (ExecutableElement e : Sets.union(leftValues.keySet(), rightValues.keySet())) {
      AnnotationValue left =
          leftValues.get(e) != null ? leftValues.get(e) : e.getDefaultValue();
      AnnotationValue right =
          rightValues.get(e) != null ? rightValues.get(e) : e.getDefaultValue();
      if (!equivalence().equivalent(left,right)) {
        return false; // Short circuit.
      }
    }
    return true;
  }

  // TODO(cgruber) DO NOT SUBMIT - fix value equivalence.
  private static final Equivalence<AnnotationValue> ANNOTATION_VALUE_EQUIVALENCE =
      new Equivalence<AnnotationValue>() {
        @Override protected boolean doEquivalent(AnnotationValue left, AnnotationValue right) {
          return left.accept(new SimpleAnnotationValueVisitor6<Boolean, AnnotationValue>() {
            // LHS is not an annotation or array of annotation values, so just test equality.
            @Override protected Boolean defaultAction(Object left, AnnotationValue right) {
              return left.equals(right.accept(
                  new SimpleAnnotationValueVisitor6<Object, Void>() {
                    @Override protected Object defaultAction(Object object, Void unused) {
                      return object;
                    }
                  }, null));
            }

            // LHS is an annotation mirror so test equivalence for RHS annotation mirrors
            // and false for other types.
            @Override public Boolean visitAnnotation(AnnotationMirror left, AnnotationValue right) {
              return right.accept(
                  new SimpleAnnotationValueVisitor6<Boolean, AnnotationMirror>() {
                    @Override protected Boolean defaultAction(Object right, AnnotationMirror left) {
                      return false; // Not an annotation mirror, so can't be equal to such.
                    }
                    @Override
                    public Boolean visitAnnotation(AnnotationMirror right, AnnotationMirror left) {
                      return AnnotationMirrors.equivalence().equivalent(left, right);
                    }
                  }, left);
            }

            // LHS is a list of annotation values have to collect-test equivalences, or false
            // for any other types.
            @Override
            public Boolean visitArray(List<? extends AnnotationValue> left, AnnotationValue right) {
              return right.accept(new SimpleAnnotationValueVisitor6<Boolean, List<? extends AnnotationValue>>() {
                @Override protected Boolean defaultAction(Object o, List<? extends AnnotationValue> left) {
                  return false; // Not an annotation mirror, so can't be equal to such.
                }

                @Override public Boolean visitArray(
                    List<? extends AnnotationValue> right ,
                    List<? extends AnnotationValue> left) {
                  // TODO(cgruber): Consider @InOrder or some other qualifier on a qualifier.
                  if (left.size() != right.size()) {
                    return false;
                  }
                  for (int i = 0; i < left.size() ; i++) {
                    if (!AnnotationValues.equivalence().equivalent(left.get(i),  right.get(i))) {
                      return false;
                    }
                  }
                  return true;
                }
              }, left);
            }
          }, right);
        }

        @Override protected int doHash(AnnotationValue value) {
          throw new UnsupportedOperationException();
        }
      };

  static Equivalence<AnnotationValue> equivalence() {
    return ANNOTATION_VALUE_EQUIVALENCE;
  }

  private AnnotationValues() {}
}

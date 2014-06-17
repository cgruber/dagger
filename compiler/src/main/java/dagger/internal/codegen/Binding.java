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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor6;

/**
 * An abstract type for classes representing a Dagger binding.  Particularly, contains the
 * {@link Element} that generated the binding and the {@link DependencyRequest} instances that are
 * required to satisfy the binding, but leaves the specifics of the <i>mechanism</i> of the binding
 * to the subtypes.
 *
 * @author Gregory Kick
 * @since 2.0
 */
abstract class Binding {
  /** Returns the {@link Element} instance that is responsible for declaring the binding. */
  abstract Element bindingElement();

  /**
   * The type relevant to the {@link #bindingElement()}, either itself, or its enclosing element.
   */
  TypeElement typeElement() {
    return bindingElement().accept(new SimpleElementVisitor6<TypeElement, Void>() {
      @Override
      protected TypeElement defaultAction(Element e, Void p) {
        return MoreElements.asType(bindingElement().getEnclosingElement());
      }

      @Override
      public TypeElement visitType(TypeElement e, Void p) {
        return e;
      }
    }, null);
  }

  /** The list of {@link DependencyRequest dependencies} required to satisfy this binding. */
  abstract ImmutableCollection<DependencyRequest> dependencies();

  /** Returns the {@link #dependencies()} indexed by {@link Key}. */
  ImmutableMap<FrameworkKey, DependencyRequest> dependenciesByKey() {
    ImmutableMap.Builder<FrameworkKey, DependencyRequest> builder = ImmutableMap.builder();
    for (DependencyRequest dependency : dependencies()) {
      builder.put(FrameworkKey.forDependencyRequest(dependency), dependency);
    }
    return builder.build();
  }
}

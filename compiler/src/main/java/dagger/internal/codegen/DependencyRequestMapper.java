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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.writer.ClassName;
import javax.inject.Provider;

import static com.google.common.collect.Iterables.getOnlyElement;

/**
 * A mapper for associating a {@link DependencyRequest} to a framework class, dependent on
 * the type of code to be generated (e.g., for {@link Provider} or {@link Producer}).
 *
 *  @author Jesse Beder
 *  @since 2.0
 */
abstract class DependencyRequestMapper {
  abstract ClassName getFrameworkClass(DependencyRequest request);

  /**
   * Returns the framework class to use for a collection of requests of the same {@link BindingKey}.
   * This allows factories to only take a single argument for multiple requests of the same key.
   */
  ClassName getFrameworkClass(Iterable<DependencyRequest> requests) {
    ImmutableSet<ClassName> classes = FluentIterable.from(requests)
        .transform(new Function<DependencyRequest, ClassName>() {
          @Override public ClassName apply(DependencyRequest request) {
            return getFrameworkClass(request);
          }
        })
        .toSet();
    if (classes.size() == 1) {
      return getOnlyElement(classes);
    } else if (classes.equals(ImmutableSet.of(ClassNames.PRODUCER, ClassNames.PROVIDER))) {
      return ClassNames.PROVIDER;
    } else {
      throw new IllegalStateException("Bad set of framework classes: " + classes);
    }
  }

  private static final class MapperForProvider extends DependencyRequestMapper {
    @Override public ClassName getFrameworkClass(DependencyRequest request) {
      switch (request.kind()) {
        case INSTANCE:
        case PROVIDER:
        case LAZY:
          return ClassNames.PROVIDER;
        case MEMBERS_INJECTOR:
          return ClassNames.MEMBERS_INJECTOR;
        case PRODUCED:
        case PRODUCER:
          throw new IllegalArgumentException();
        default:
          throw new AssertionError();
      }
    }
  }

  static final DependencyRequestMapper FOR_PROVIDER = new MapperForProvider();

  private static final class MapperForProducer extends DependencyRequestMapper {
    @Override public ClassName getFrameworkClass(DependencyRequest request) {
      switch (request.kind()) {
        case INSTANCE:
        case PRODUCED:
        case PRODUCER:
          return ClassNames.PRODUCER;
        case PROVIDER:
        case LAZY:
          return ClassNames.PROVIDER;
        case MEMBERS_INJECTOR:
          return ClassNames.MEMBERS_INJECTOR;
        default:
          throw new AssertionError();
      }
    }
  }

  static final DependencyRequestMapper FOR_PRODUCER = new MapperForProducer();
}

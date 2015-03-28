/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import dagger.internal.codegen.writer.ClassName;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * ClassName constants used in place of .class objects in various places to avoid loading
 * possibly absent types.
 */
final class ClassNames {
  // JSR-330 types
  static final ClassName INJECT = ClassName.create("javax.inject", "Inject");
  static final ClassName PROVIDER = ClassName.create("javax.inject", "Provider");

  // Dagger types
  static final ClassName COMPONENT = ClassName.create("dagger", "Component");
  static final ClassName LAZY = ClassName.create("dagger", "Lazy");
  static final ClassName MAP_KEY = ClassName.create("dagger", "MapKey");
  static final ClassName MEMBERS_INJECTOR = ClassName.create("dagger", "MembersInjector");
  static final ClassName MODULE = ClassName.create("dagger", "Module");
  static final ClassName PROVIDES = ClassName.create("dagger", "Provides");

  // Producer types
  static final ClassName ABSTRACT_PRODUCER =
      ClassName.create("dagger.producers.internal", "AbstractProducer");
  static final ClassName PRODUCED = ClassName.create("dagger.producers", "Produced");
  static final ClassName PRODUCER = ClassName.create("dagger.producers", "Producer");
  static final ClassName PRODUCERS = ClassName.create("dagger.producers.internal", "Producers");
  static final ClassName PRODUCER_MODULE = ClassName.create("dagger.producers", "ProducerModule");
  static final ClassName PRODUCES = ClassName.create("dagger.producers", "Produces");
  static final ClassName PRODUCTION_COMPONENT =
      ClassName.create("dagger.producers", "ProductionComponent");
  static final ClassName SET_PRODUCER =
      ClassName.create("dagger.producers.internal", "SetProducer");

  // Guava types
  public static final ClassName LISTENABLE_FUTURE =
      ClassName.create("com.google.common.util.concurrent", "ListenableFuture");

  /**
   * Returns true iff the supplied {@link TypeMirror} represents the raw class equivalent of
   * the supplied {@link ClassName}.  In particular, if their qualified/canonical names match.
   *
   * @See MoreTypes#isTypeOf(Class, TypeMirror)
   */
  static boolean isTypeOf(final ClassName name, TypeMirror mirror) {
    return mirror.accept(new SimpleTypeVisitor6<Boolean, Void>() {
      @Override public Boolean visitDeclared(DeclaredType type, Void p) {
        return name.canonicalName()
            .equals(MoreTypes.asTypeElement(type).getQualifiedName().toString());
      }
      @Override protected Boolean defaultAction(TypeMirror e, Void p) {
        return false;
      }
    }, null);
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose
   * {@linkplain AnnotationMirror#getAnnotationType() annotation type} has the same canonical name
   * as {@code annotationName}. This method is a safer alternative to calling
   * {@link Element#getAnnotation} and checking for {@code null} as it avoids any interaction with
   * annotation proxies.
   *
   * @see MoreElements#isAnnotationPresent(Element, String)
   */
  public static boolean isAnnotationPresent(Element element, ClassName annotationName) {
    return MoreElements.isAnnotationPresent(element, annotationName.canonicalName());
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationName} on
   * {@code element}, or {@link Optional#absent()} if no such annotation exists. This method is a
   * safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  public static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, ClassName annotationName) {
    return MoreElements.getAnnotationMirror(element, annotationName.canonicalName());
  }

  private ClassNames() {}
}

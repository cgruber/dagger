/**
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
package dagger.tests.integration.operation;

import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.ComponentProcessor;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static java.util.Arrays.asList;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public final class PrimitiveInjectionTest {

  JavaFileObject annotation = JavaFileObjects.forSourceLines("test.ForTest",
      "package test;",
      "import javax.inject.Qualifier;",
      "@Qualifier",
      "public @interface ForTest {",
      "}");

  JavaFileObject primitiveInjectable = JavaFileObjects.forSourceLines("test.PrimitiveInjectable",
      "package test;",
      "import javax.inject.Inject;",
      "class PrimitiveInjectable {",
      "  @Inject PrimitiveInjectable(@ForTest int ignored) {}",
      "}");

  JavaFileObject primitiveModule = JavaFileObjects.forSourceLines("test.PrimitiveModule",
      "package test;",
      "import dagger.Module;",
      "import dagger.Provides;",
      "@Module",
      "class PrimitiveModule {",
      "  @Provides @ForTest int primitiveInt() { return Integer.MAX_VALUE; }",
      "}");

  JavaFileObject component = JavaFileObjects.forSourceLines("test.PrimitiveComponent",
      "package test;",
      "import dagger.Component;",
      "import dagger.Provides;",
      "@Component(modules = PrimitiveModule.class)",
      "interface PrimitiveComponent {",
      "  PrimitiveInjectable primitiveInjectable();",
      "  @ForTest int primitiveInt();",
      "}");

  @Test public void primitiveArrayTypesAllInjected() {
    ASSERT.about(javaSources())
        .that(asList(annotation, component, primitiveInjectable, primitiveModule))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }
}

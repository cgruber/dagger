/**
 * Copyright (c) 2013 Google, Inc.
 * Copyright (c) 2013 Square, Inc.
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
package dagger.tests.integration.validation;

import com.google.common.base.Joiner;
import com.google.testing.compile.CompileTester;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.ComponentProcessor;
import java.util.Arrays;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static java.util.Arrays.asList;
import static org.truth0.Truth.ASSERT;

/**
 * Integration test to ensure that custom invalid use of scope constraints result in
 * graph errors.
 */
@RunWith(Parameterized.class)
public class ScopeValidityTest {
  private static final Joiner EOL = Joiner.on("\n");

  private final static JavaFileObject SHORT_SCOPE =
      JavaFileObjects.forSourceLines("test.ShortScope",
        "package test;",
        "import javax.inject.Scope;",
        "@Scope public @interface ShortScope {}");

  @Parameters(name= "{index}: {0} component, {1} injectable")
  public static Collection<String[]> generateData() {
     return Arrays.asList(new String[][] {
        {"", "", null },
        {"", "@Singleton", "Cannot reference a class with an incompatible scope."},
        {"", "@ShortScope", "Cannot reference a class with an incompatible scope."},
        {"@Singleton", "", null },
        {"@Singleton", "@Singleton", null },
        {"@Singleton", "@ShortScope", "Cannot reference a class with an incompatible scope."},
        {"@ShortScope", "", null },
        {"@ShortScope", "@Singleton",  "Cannot reference a class with an incompatible scope."},
        {"@ShortScope", "@ShortScope", null},
    });
  }

  private final String componentAnnotation;
  private final String typeAnnotation;
  private final String error;

  public ScopeValidityTest(
      String componentAnnotation,
      String typeAnnotation,
      String error) {
    this.componentAnnotation = componentAnnotation;
    this.typeAnnotation = typeAnnotation;
    this.error = error;
  }

  private static final String INJECTABLE_TYPE_TEMPLATE = EOL.join(
      "package test;",
      "import javax.inject.Inject;",
      "import javax.inject.Singleton;",
      "%s", // typeAnnotation
      "class SimpleInjectable {",
      "  @Inject SimpleInjectable() {}",
      "}");

  private static final String COMPONENT_TEMPLATE = EOL.join(
      "package test;",
      "import dagger.Component;",
      "import javax.inject.Singleton;",
      "%s @Component interface SimpleComponent {", // componentAnnotation
      "  SimpleInjectable getInjectable();",
      "}");

  @Test public void testComponentsAndImplicitlyInjectableTypeScopePairs() {
    JavaFileObject unscopedInjectable =
        JavaFileObjects.forSourceString("test.SimpleInjectable",
          String.format(INJECTABLE_TYPE_TEMPLATE, typeAnnotation));
    JavaFileObject simpleComponent =
        JavaFileObjects.forSourceLines("test.SimpleComponent",
            String.format(COMPONENT_TEMPLATE, componentAnnotation));

    CompileTester tester = ASSERT.about(javaSources())
        .that(asList(simpleComponent, unscopedInjectable))
        .processedWith(new ComponentProcessor());
    if (error == null) {
      tester.compilesWithoutError();
    } else {
      tester.failsToCompile().withErrorContaining(error);
    }
  }
}

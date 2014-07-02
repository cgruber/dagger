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
public class ScopeValidityModulePairTest {
  private final static Joiner EOL = Joiner.on("\n");

  private final static JavaFileObject SHORT_SCOPE =
      JavaFileObjects.forSourceString("test.ShortScope", EOL.join(
        "package test;",
        "import javax.inject.Scope;",
        "@Scope public @interface ShortScope {}"));

  private final String componentAnnotation;
  private final String providesAnnotation;
  private final String stringProvidesAnnotation;
  private final String error;

  public ScopeValidityModulePairTest(
      String componentAnnotation,
      String providesAnnotation,
      String providesAnnotation2,
      String error) {
    this.componentAnnotation = componentAnnotation;
    this.providesAnnotation = providesAnnotation;
    this.stringProvidesAnnotation = providesAnnotation2;
    this.error = error;
  }

  @Parameters(name= "{index}: {0} component, {1} SimpleType provision, {2} String provision")
  public static Collection<String[]> generateData() {
     return Arrays.asList(new String[][] {
         // component, module, module2, error

         // component: unscoped
         {"", "", "", "incompatible with component"},
         {"", "", "@Singleton", "incompatible with component"},
         {"", "", "@ShortScope", "incompatible with component"},
         {"", "@Singleton", "", "incompatible with component"},
         {"", "@Singleton", "@Singleton", "incompatible with component"},
         {"", "@Singleton", "@ShortScope", "incompatible with component"},
         {"", "@ShortScope", "", "incompatible with component"},
         {"", "@ShortScope", "@ShortScope", "incompatible with component"},
         {"", "@ShortScope", "@Singleton", "incompatible scope"},

         // component: @Singleton
         {"@Singleton", "", "", null },
         {"@Singleton", "", "@Singleton", null},
         {"@Singleton", "", "@ShortScope", "incompatible with component"},
         {"@Singleton", "@Singleton", "", null},
         {"@Singleton", "@Singleton", "@Singleton", null},
         {"@Singleton", "@Singleton", "@ShortScope", "incompatible with component"},
         {"@Singleton", "@ShortScope", "", "incompatible with component"},
         {"@Singleton", "@ShortScope", "@ShortScope", "incompatible with component"},
         {"@Singleton", "@ShortScope", "@Singleton", "incompatible with component"},

         // component: @ShortScope
         {"@ShortScope", "", "", null },
         {"@ShortScope", "", "@Singleton", "incompatible with component"},
         {"@ShortScope", "", "@ShortScope", null},
         {"@ShortScope", "@Singleton", "", "incompatible with component"},
         {"@ShortScope", "@Singleton", "@Singleton", "incompatible with component"},
         {"@ShortScope", "@Singleton", "@ShortScope", "incompatible with component"},
         {"@ShortScope", "@ShortScope", "", null},
         {"@ShortScope", "@ShortScope", "@ShortScope", null},
         {"@ShortScope", "@ShortScope", "@Singleton", "incompatible with component"},
    });
  }

  private static final JavaFileObject SIMPLE_TYPE =
      JavaFileObjects.forSourceLines("test.SimpleType",
          "package test;",
          "class SimpleType {",
          "  SimpleType() {}",
          "}");

  private static final String MODULE_TEMPLATE = EOL.join(
      "package test;",
      "import dagger.Module;",
      "import dagger.Provides;",
      "import javax.inject.Singleton;",
      "@Module(includes = StringModule.class)", // typeAnnotation
      "class TypeModule {",
      "  @Provides %s SimpleType simpleType(String s) {}", // providesAnnotation
      "}");

  private static final String STRING_MODULE_TEMPLATE = EOL.join(
      "package test;",
      "import dagger.Module;",
      "import dagger.Provides;",
      "import javax.inject.Singleton;",
      "@Module", // typeAnnotation
      "class StringModule {",
      "  @Provides %s String string() {}", // stringProvidesAnnotation
      "}");

  private static final String COMPONENT_TEMPLATE = EOL.join(
      "package test;",
      "import dagger.Component;",
      "import javax.inject.Singleton;",
      "%s", // componentAnnotation
      "@Component(modules = TypeModule.class)",
      "interface SimpleComponent {",
      "  SimpleType getSimpleType();",
      "}");

  @Test public void testComponentsAndImplicitlyInjectableTypeScopePairs() {

    // For each combination of component scoping and module provides scoping,
    // ensure that scopes are consistent, or that errors are reported.

    JavaFileObject module =
        JavaFileObjects.forSourceString("test.TypeModule",
          String.format(MODULE_TEMPLATE, providesAnnotation));
    JavaFileObject module2 =
        JavaFileObjects.forSourceString("test.StringModule",
          String.format(STRING_MODULE_TEMPLATE, stringProvidesAnnotation));
    JavaFileObject component =
        JavaFileObjects.forSourceLines("test.SimpleComponent",
            String.format(COMPONENT_TEMPLATE, componentAnnotation));

    CompileTester tester = ASSERT.about(javaSources())
        .that(asList(SHORT_SCOPE, SIMPLE_TYPE, module, module2, component))
        .processedWith(new ComponentProcessor());
    if (error == null) {
      tester.compilesWithoutError();
    } else {
      tester.failsToCompile().withErrorContaining(error);
    }
  }

}

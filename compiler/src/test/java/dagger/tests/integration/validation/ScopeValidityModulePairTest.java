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
import java.util.Arrays;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
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

  private final String inclusionMode;
  private final String rootAnnotation;
  private final String includedAnnotation;
  private final String error;

  public ScopeValidityModulePairTest(
      String inclusionMode,
      String rootAnnotation,
      String includedAnnotation,
      String error) {
    this.inclusionMode = inclusionMode;
    this.rootAnnotation = rootAnnotation;
    this.includedAnnotation = includedAnnotation;
    this.error = error;
  }

  @Parameters
  public static Collection<String[]> generateData() {
     return Arrays.asList(new String[][] {
        /* inclusion type, root annotation, included annotation, expected error */
        {"includes", "Singleton", "Singleton", null },
        {"includes", "Singleton", "ShortScope", "Cannot include a module with a different scope." },
        {"includes", "ShortScope", "Singleton", "Cannot include a module with a different scope." },
        {"includes", "ShortScope", "ShortScope", null },
        {"addsTo", "Singleton", "Singleton", "Cannot use addsTo references with the same scope." },
        {"addsTo", "Singleton", "ShortScope", "module with longer-lived scope than Singleton." },
        {"addsTo", "ShortScope", "Singleton", null },
        {"addsTo", "ShortScope", "ShortScope", "Cannot use addsTo references with the same scope" },
    });
  }

  @Test public void moduleIncludesModule() {
    JavaFileObject root = JavaFileObjects.forSourceString("test.RootModule", EOL.join(
        "package test;",
        "import dagger.Module;",
        "import javax.inject.Singleton;",
        "@Module(scope = " + rootAnnotation + ".class, "
            + inclusionMode + " = IncludedModule.class)",
        "class RootModule {}"));
    JavaFileObject included = JavaFileObjects.forSourceString("test.IncludedModule", EOL.join(
        "package test;",
        "import dagger.Module;",
        "import javax.inject.Singleton;",
        "@Module(scope = " + includedAnnotation + ".class)",
        "class IncludedModule {}"));
    CompileTester tester = ASSERT.about(javaSources()).that(asList(SHORT_SCOPE, root, included))
      .processedWith(daggerProcessors());
    if (error == null) {
      tester.compilesWithoutError();
    } else {
      tester.failsToCompile().withErrorContaining(error);
    }
  }
}

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
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static java.util.Arrays.asList;
import static org.truth0.Truth.ASSERT;

/**
 * Integration test to ensure that custom invalid use of scope constraints result in
 * graph errors.
 */
@RunWith(JUnit4.class)
public class ScopeValidityComplexModuleTest {
  private static final Joiner EOL = Joiner.on("\n");
  private static final String IMPORTS = "import dagger.Module;";

  private static final JavaFileObject SHORT_SCOPE =
      source("PerSession", "import javax.inject.Scope;", "@Scope public @interface PerSession {}");
  private static final JavaFileObject INTERMEDIATE_SCOPE =
      source("PerRequest", "import javax.inject.Scope;", "@Scope public @interface PerRequest {}");

  @Test public void modulesIncludeScopeCycle_Fail() {
    JavaFileObject a = source("A", IMPORTS,
        "@Module(scope = javax.inject.Singleton.class)", "class A {}");
    JavaFileObject b = source("B", IMPORTS,
        "@Module(includes = A.class)", "class B {}"); // Singleton by default.
    JavaFileObject c = source("C", IMPORTS,
        "@Module(scope = PerRequest.class)", "class C {}");
    JavaFileObject d = source("D", IMPORTS,
        "@Module(includes = E.class, addsTo = C.class, scope = PerSession.class)", "class D {}");
    JavaFileObject e = source("E", IMPORTS,
        "@Module(addsTo = B.class, scope = PerSession.class)", "class E {}");
    JavaFileObject f = source("F", IMPORTS,
        "@Module(includes = C.class, addsTo = E.class, scope = PerRequest.class)", "class F {}");
    JavaFileObject g = source("G", IMPORTS,
        "@Module(includes = F.class, addsTo = D.class, scope = PerRequest.class)", "class G {}");
    ASSERT.about(javaSources()).that(asList(SHORT_SCOPE, INTERMEDIATE_SCOPE, a, b, c, d, e, f, g))
      .processedWith(daggerProcessors())
      .failsToCompile().withErrorContaining("foo");
  }

  @Ignore
  @Test public void foo() {
    JavaFileObject a = source("A", IMPORTS, "@Module", "class A {}");
    JavaFileObject b = source("B", IMPORTS, "@Module", "class B {}");
    JavaFileObject c = source("C", IMPORTS, "@Module(scope = IntermediateScope) class C {}");
    JavaFileObject d = source("D", IMPORTS, "@Module(scope = IntermediateScope) class D {}");
    JavaFileObject e = source("E", IMPORTS, "@Module(scope = ShortScope) class E {}");
    JavaFileObject f = source("F", IMPORTS, "@Module(scope = ShortScope) class F {}");
    JavaFileObject g = source("G", IMPORTS, "@Module(scope = ShortScope) class G {}");
    ASSERT.about(javaSources()).that(asList(SHORT_SCOPE, INTERMEDIATE_SCOPE, a, b, c, d, e, f, g))
      .processedWith(daggerProcessors())
      .compilesWithoutError();
      //.failsToCompile().withErrorContaining("foo");

  }

  private static JavaFileObject source(String name, String ... lines) {
    return JavaFileObjects.forSourceString(name, EOL.join(lines));
  }

}

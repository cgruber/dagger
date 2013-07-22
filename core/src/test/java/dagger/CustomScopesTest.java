/*
 * Copyright (C) 2012 Google Inc.
 * Copyright (C) 2012 Square Inc.
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
package dagger;

import dagger.internal.TestingLoader;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Inject;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class)
public final class CustomScopesTest {
  @Scope @Documented @Retention(RUNTIME) @Target({TYPE, METHOD}) @interface AlternateScope { }

  @Scope @Documented @Retention(RUNTIME) @Target({TYPE, METHOD}) @interface YetAnotherScope { }

  @Singleton
  static class A {
    @Inject A() {}
  }

  static class B {
    @Inject A a;
  }

  @AlternateScope // to be used with @Singleton to force an error.
  static class BadB {
    @Inject A a;
  }

  @AlternateScope
  static class C {
    @Inject A a;
    @Inject B b;
  }

  static class D {
    @Inject A a;
    @Inject B b;
    @Inject C c;
  }

  @Module(scope = Singleton.class, injects = { A.class, B.class }) static class RootModule { }

  @Module(injects = C.class)
  static class MixedScopeRootModuleWithoutProvidesMethods { }

  // Currently fails to find the error at run-time as the check on provided objects is too
  // expensive.  This needs to be re-enabled for provides methods once some changes coming from
  // Google are introduced which provide a structure for evaluating provided/built-in bindings
  // without extra expense. It could be done here, but the same kind of change would have to be
  // made and the code is already under review.
  @Ignore // This should be handled at compile-time for valid modules.
  @Test public void bindMixedScopedObjectsInModuleSet() {
    try {
      // In general this should be caught at compile-time.
      ObjectGraph.createWith(new TestingLoader(), MixedScopeRootModuleWithoutProvidesMethods.class)
          .validate();
      Assert.fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains(""
          + "Cannot bind C in @AlternateScope scope for "
          + "module MixedRootModule in @Singleton scope");
    }
  }

  @Test public void rootGraphCanBeScopedAsSingleton() {
    ObjectGraph.createWith(new TestingLoader(), RootModule.class).validate(); // Expect no error.
  }

  @Module(scope = AlternateScope.class, injects = { A.class }) // Used to test failure.
  static class CustomScopedRootModule { }

  @Test public void rootGraphCannotBeAlternativelyScoped() {
    try {
      ObjectGraph.createWith(new TestingLoader(), CustomScopedRootModule.class);
      Assert.fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .contains("Root graph modules may only be constrained to @Singleton");
    }
  }

  @Test public void rootGraphCanUseDefaultSingletonInAnnotation() {
    @Module(includes = RootModule.class)
    class IncludingRootModule {}

    ObjectGraph.createWith(new TestingLoader(), new IncludingRootModule());
  }

  @Test public void extensionGraphCannotUseDefaultSingletonInAnnotation() {
    @Module
    class IncludedModule {}

    @Module(includes = IncludedModule.class, scope = AlternateScope.class)
    class IncludingExtensionModule {}

    try {
      ObjectGraph.createWith(new TestingLoader(), RootModule.class)
          .plus(new IncludingExtensionModule(), new IncludedModule());
      Assert.fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("javax.inject.Singleton found in "
        + "dagger.CustomScopesTest$1IncludedModule already constrains a parent graph.");
      assertThat(e.getMessage()).contains("Scopes must be arranged hierarchically.");
    }
  }


  @Module(scope = AlternateScope.class, addsTo = RootModule.class, injects = { C.class, D.class })
  static class ExtensionModule { }

  @Test public void customScopeOnExtensionGraph() {
    // expect no error.
    ObjectGraph.createWith(new TestingLoader(), RootModule.class)
        .plus(ExtensionModule.class)
        .validate();
  }

  @Module(scope = YetAnotherScope.class, includes = ExtensionModule.class)
  static class MixedScopesModule { }

  @Test public void failOnMixedScopesInIncludedModuleSet() {
    try {
      ObjectGraph.createWith(new TestingLoader(), RootModule.class)
          .plus(MixedScopesModule.class);
      Assert.fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Included modules must use the same scope constraint.");
      assertThat(e.getMessage()).contains("Found:");
      assertThat(e.getMessage()).contains(AlternateScope.class.toString());
      assertThat(e.getMessage()).contains(YetAnotherScope.class.toString());
    }
  }

  static class E {
    @Inject A a;
  }

  @Module(scope = Singleton.class, injects = E.class, addsTo = RootModule.class)
  static class DuplicateScopesModule { }

  @Test public void failOnDuplicateScopeOnExtensionGraph() {
    try {
      ObjectGraph.createWith(new TestingLoader(), RootModule.class)
          .plus(DuplicateScopesModule.class);
      Assert.fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains(
          Singleton.class.getName() + " found in " + DuplicateScopesModule.class.getName());
      assertThat(e.getMessage()).contains("already constrains a parent graph.");
    }
  }

  @Ignore // This test case is satisfied through a compiler error.
  @Test public void customScopeOnExtensionGraphWithDefaultScopeUsesSingleton() { }

  @Ignore // This test case is satisfied through a compiler error.
  @Test public void failWhenModuleScopeMismatchesProvidedScope() { }

  @Ignore // This test case is satisfied through a compiler error.
  @Test public void failWhenModuleScopeMismatchesIncludedModulesProvidedScope() { }

}

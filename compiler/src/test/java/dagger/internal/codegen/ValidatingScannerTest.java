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

import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public class ValidatingScannerTest {
  @Test public void bestGuessForString_simpleClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FooModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "class FooModule {",
        "  @Provides @Named(\"Foo\") String string() { return \"Foo\"; }",
        "}");
    ASSERT.about(javaSource()).that(file).processedWith(new TestProcessor())
      .compilesWithoutError();


  }


  static class TestProcessor extends AbstractProcessor {

    @Override public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.RELEASE_6;
    }

    @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
      for (TypeElement type : types) {
        ValidatingScanner.scan(type);
      }
      return false;
    }

  }

}

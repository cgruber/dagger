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

import dagger.internal.codegen.Util.CodeGenerationIncompleteException;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;


/**
 * An AST scanner which evaluates an AST for valid content.  This should be called on Elements
 * to validate their structure and ensure that no up-stream compilation errors have resulted in
 * bogus ASTs being returned by the javac processing environment.
 *
 * @since 2.0
 */
abstract class ValidatingScanner {

  /**
   * Scans the given type element, ensuring that the type and all contained elements are valid
   * such that code-generation and analysis on this type will not throw unexpected errors.
   */
  public static void scan(TypeElement type) {
    type.asType().accept(TYPE_SCANNER, null);
  }

  private static final  SimpleTypeVisitor6<Void, Void> TYPE_SCANNER =
      new SimpleTypeVisitor6<Void, Void>() {
        @Override public Void visitError(ErrorType errorType, Void v) {
          throw new CodeGenerationIncompleteException(
              "Type reported as <any> is likely a not-yet generated parameterized type.");
        }

        @Override protected Void defaultAction(TypeMirror typeMirror, Void v) {
          throw new UnsupportedOperationException(
              "Unexpected TypeKind " + typeMirror.getKind() + " for "  + typeMirror);
        }
      };

}

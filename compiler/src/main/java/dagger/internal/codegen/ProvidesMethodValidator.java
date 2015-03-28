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

import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.Provides;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ConfigurationAnnotations.getMapKeys;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_MAP_HAS_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_STATIC;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_MULTIPLE_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_NO_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * A {@link Validator} for {@link Provides} methods.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ProvidesMethodValidator implements Validator<ExecutableElement> {
  private final Elements elements;

  ProvidesMethodValidator(Elements elements) {
    this.elements = checkNotNull(elements);
  }

  private TypeElement getSetElement() {
    return elements.getTypeElement(Set.class.getCanonicalName());
  }

  @Override
  public ValidationReport<ExecutableElement> validate(
      final ExecutableElement providesMethodElement) {
    final ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.Builder.about(providesMethodElement);

    final Optional<AnnotationMirror> providesAnnotation =
        ClassNames.getAnnotationMirror(providesMethodElement, ClassNames.PROVIDES);
    checkArgument(providesAnnotation.isPresent());

    Element enclosingElement = providesMethodElement.getEnclosingElement();
    if (!isAnnotationPresent(enclosingElement, Module.class)) {
      builder.addItem(formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE),
          providesMethodElement);
    }

    if (!providesMethodElement.getTypeParameters().isEmpty()) {
      builder.addItem(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER),
          providesMethodElement);
    }

    Set<Modifier> modifiers = providesMethodElement.getModifiers();
    if (modifiers.contains(PRIVATE)) {
      builder.addItem(formatErrorMessage(BINDING_METHOD_PRIVATE),
          providesMethodElement);
    }
    if (modifiers.contains(STATIC)) {
      // TODO(gak): why not?
      builder.addItem(formatErrorMessage(BINDING_METHOD_STATIC), providesMethodElement);
    }
    if (modifiers.contains(ABSTRACT)) {
      builder.addItem(formatErrorMessage(BINDING_METHOD_ABSTRACT), providesMethodElement);
    }

    final TypeMirror returnType = providesMethodElement.getReturnType();
    TypeKind returnTypeKind = returnType.getKind();
    if (returnTypeKind.equals(VOID)) {
      builder.addItem(formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE),
          providesMethodElement);
    }

    validateMethodQualifiers(builder, providesMethodElement);

    AnnotationValue type = AnnotationMirrors.getAnnotationValue(providesAnnotation.get(), "type");
    type.accept(new SimpleAnnotationValueVisitor6<Void, Void>() {
      @Override public Void visitEnumConstant(VariableElement enumValue, Void p) {
        String enumValueName = enumValue.getSimpleName().toString();
        // check mapkey is right
        boolean hasMapKeys = !getMapKeys(providesMethodElement).isEmpty();
        if (hasMapKeys && !enumValueName.equals("MAP")) {
           builder.addItem(
               formatErrorMessage(BINDING_METHOD_NOT_MAP_HAS_MAP_KEY), providesMethodElement);
        }
        if (enumValueName.equals("UNIQUE")) {
          validateKeyType(builder, returnType);
        } else if (enumValueName.equals("SET")) {
          validateKeyType(builder, returnType);
        } else if (enumValueName.equals("SET_VALUES")) {
          if (!returnType.getKind().equals(DECLARED)) {
            builder.addItem(PROVIDES_METHOD_SET_VALUES_RETURN_SET, providesMethodElement);
          } else {
            DeclaredType declaredReturnType = (DeclaredType) returnType;
            // TODO(gak): should we allow "covariant return" for set values?
            if (!declaredReturnType.asElement().equals(getSetElement())) {
              builder.addItem(PROVIDES_METHOD_SET_VALUES_RETURN_SET, providesMethodElement);
            } else if (declaredReturnType.getTypeArguments().isEmpty()) {
              builder.addItem(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET),
                  providesMethodElement);
            } else {
              validateKeyType(builder,
                  Iterables.getOnlyElement(declaredReturnType.getTypeArguments()));
            }
          }
        } else if (enumValueName.equals("MAP")) {
          validateKeyType(builder, returnType);
          ImmutableSet<? extends AnnotationMirror> annotationMirrors =
              getMapKeys(providesMethodElement);
          switch (annotationMirrors.size()) {
            case 0:
              builder.addItem(formatErrorMessage(BINDING_METHOD_WITH_NO_MAP_KEY),
                  providesMethodElement);
              break;
            case 1:
              break;
            default:
              builder.addItem(formatErrorMessage(BINDING_METHOD_WITH_MULTIPLE_MAP_KEY),
                  providesMethodElement);
              break;
          }
        } else {
          throw new IllegalStateException("Unknown provision type: " + enumValueName);
        }
        return null; // Void from visitor.
      }
    }, null);

    return builder.build();
  }

  /** Validates that a Provides or Produces method doesn't have multiple qualifiers. */
  static void validateMethodQualifiers(ValidationReport.Builder<ExecutableElement> builder,
      ExecutableElement methodElement) {
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(methodElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addItem(PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS, methodElement, qualifier);
      }
    }
  }

  private String formatErrorMessage(String msg) {
    return String.format(msg, ClassNames.PROVIDES.simpleName());
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, ClassNames.PROVIDES.simpleName(), ClassNames.MODULE.simpleName());
  }

  private void validateKeyType(ValidationReport.Builder<? extends Element> reportBuilder,
      TypeMirror type) {
    TypeKind kind = type.getKind();
    if (!(kind.isPrimitive() || kind.equals(DECLARED) || kind.equals(ARRAY))) {
      reportBuilder.addItem(PROVIDES_METHOD_RETURN_TYPE, reportBuilder.getSubject());
    }
  }
}

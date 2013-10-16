/*
 * Copyright (C) 2012 Square, Inc.
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

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.Util.CodeGenerationIncompleteException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates an implementation of {@link dagger.internal.ModuleAdapter} that includes a binding
 * for each {@code @Provides} method of a target class.
 */
@SupportedAnnotationTypes({ "*" })
public final class ModuleAdapterProcessor extends AbstractDaggerProcessor {
  private static final List<String> INVALID_RETURN_TYPES =
      Arrays.asList(Provider.class.getCanonicalName(), Lazy.class.getCanonicalName());

  private final LinkedHashMap<String, List<ExecutableElement>> remainingTypes =
      new LinkedHashMap<String, List<ExecutableElement>>();

  @Inject ModuleAdapterGenerator generator;
  @Inject ModuleType.Factory typeFactory;
  @Inject Filer filer;

  @Override protected Object getModule() {
    @Module(injects = ModuleAdapterProcessor.class) class ProcessorModule { }
    return new ProcessorModule();
  }

  @Override protected void doProcess(Set<? extends TypeElement> types, RoundEnvironment env) {
    remainingTypes.putAll(providerMethodsByClass(env));
    for (Iterator<String> i = remainingTypes.keySet().iterator(); i.hasNext();) {
      try {
        String name = i.next();
        ModuleType moduleType = typeFactory.create(name, remainingTypes.get(name));
        if (moduleType != null) {
          //TODO(cgruber): Figure out an initial sizing of the StringWriter.
          StringWriter stringWriter = new StringWriter();
          try {
            generator.generate(stringWriter, moduleType);
            JavaFileObject sourceFile = processingEnv.getFiler()
                .createSourceFile(generator.adapterName(moduleType), moduleType.type);
            Writer sourceWriter = sourceFile.openWriter();
            sourceWriter.append(stringWriter.getBuffer());
            sourceWriter.close();
          } catch (IOException e) {
            note.on(moduleType.type).error("Code gen failed: %s", e);
          }
        }
      } catch (CodeGenerationIncompleteException e) {
        continue; // A dependent type was not defined, we'll try to catch it on another pass.
      }
      i.remove();
    }
    if (env.processingOver() && remainingTypes.size() > 0) {
      note.error("Could not find types required by provides methods for %s",
          remainingTypes.keySet());
    }
  }

  /**
   * Returns a map containing all {@code @Provides} methods, indexed by class.
   */
  private Map<String, List<ExecutableElement>> providerMethodsByClass(RoundEnvironment env) {
    Map<String, List<ExecutableElement>> result = new HashMap<String, List<ExecutableElement>>();

    provides:
    for (Element providerMethod : findProvidesMethods(env)) {
      switch (providerMethod.getEnclosingElement().getKind()) {
        case CLASS:
          break; // valid, move along
        default:
          // TODO(tbroyer): pass annotation information
          note.on(providerMethod)
              .error("Unexpected @Provides on %s", Util.elementToString(providerMethod));
          continue;
      }
      TypeElement type = (TypeElement) providerMethod.getEnclosingElement();
      Set<Modifier> typeModifiers = type.getModifiers();
      if (typeModifiers.contains(PRIVATE)
          || typeModifiers.contains(ABSTRACT)) {
        note.on(type)
            .error("Classes declaring @Provides methods must not be private or abstract: %s",
                type.getQualifiedName());
        continue;
      }

      Set<Modifier> methodModifiers = providerMethod.getModifiers();
      if (methodModifiers.contains(PRIVATE)
          || methodModifiers.contains(ABSTRACT)
          || methodModifiers.contains(STATIC)) {
        note.on(providerMethod)
            .error("@Provides methods must not be private, abstract or static: %s.%s",
                type.getQualifiedName(), providerMethod);
        continue;
      }

      ExecutableElement providerMethodAsExecutable = (ExecutableElement) providerMethod;
      if (!providerMethodAsExecutable.getThrownTypes().isEmpty()) {
        note.on(providerMethod)
        .error("@Provides methods must not have a throws clause: %s.%s",
            type.getQualifiedName(), providerMethod);
        continue;
      }

      // Invalidate return types.
      TypeMirror returnType = typeUtils.erasure(providerMethodAsExecutable.getReturnType());
      if (!returnType.getKind().equals(TypeKind.ERROR)) {
        // Validate if we have a type to validate (a type yet to be generated by other
        // processors is not "invalid" in this way, so ignore).
        for (String invalidTypeName : INVALID_RETURN_TYPES) {
          TypeElement invalidTypeElement = elements.getTypeElement(invalidTypeName);
          if (invalidTypeElement != null && typeUtils.isSameType(returnType,
              typeUtils.erasure(invalidTypeElement.asType()))) {
            note.on(providerMethod)
                .error("@Provides method must not return %s directly: %s.%s",
                    invalidTypeElement, type.getQualifiedName(), providerMethod);
            continue provides; // Skip to next provides method.
          }
        }
      }

      List<ExecutableElement> methods = result.get(type.getQualifiedName().toString());
      if (methods == null) {
        methods = new ArrayList<ExecutableElement>();
        result.put(type.getQualifiedName().toString(), methods);
      }
      methods.add(providerMethodAsExecutable);
    }

    TypeMirror objectType = elements.getTypeElement("java.lang.Object").asType();

    // Catch any stray modules without @Provides since their injectable types
    // should still be registered and a ModuleAdapter should still be written.
    for (Element module : env.getElementsAnnotatedWith(Module.class)) {
      if (!module.getKind().equals(ElementKind.CLASS)) {
        note.on(module).error("Modules must be classes: %s", Util.elementToString(module));
        continue;
      }

      TypeElement moduleType = (TypeElement) module;

      // Verify that all modules do not extend from non-Object types.
      if (!moduleType.getSuperclass().equals(objectType)) {
        note.on(module)
            .error("Modules must not extend from other classes: %s", Util.elementToString(module));
      }

      String moduleName = moduleType.getQualifiedName().toString();
      if (result.containsKey(moduleName)) continue;
      result.put(moduleName, new ArrayList<ExecutableElement>());
    }
    return result;
  }

  private Set<? extends Element> findProvidesMethods(RoundEnvironment env) {
    Set<Element> result = new LinkedHashSet<Element>();
    result.addAll(env.getElementsAnnotatedWith(Provides.class));
    return result;
  }
}

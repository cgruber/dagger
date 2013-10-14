/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
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

import dagger.Module;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

import static dagger.internal.codegen.Util.elementToString;
import static dagger.internal.codegen.Util.rawTypeToString;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates an implementation of {@link dagger.internal.Binding} that injects the
 * {@literal @}{@code Inject}-annotated members of a class.
 */
@SupportedAnnotationTypes("javax.inject.Inject")
public final class InjectAdapterProcessor extends AbstractDaggerProcessor {
  private final Set<String> remainingTypeNames = new LinkedHashSet<String>();

  @Inject InjectAdapterGenerator injectAdapterGenerator;
  @Inject StaticInjectionGenerator staticInjectionGenerator;
  @Inject InjectableType.Factory injectableClassFactory;
  @Inject Filer filer;

  @Override protected Object getModule() {
    @Module(injects = InjectAdapterProcessor.class) class ProcessorModule { }
    return new ProcessorModule();
  }

  @Override protected void doProcess(Set<? extends TypeElement> types, RoundEnvironment env) {
    remainingTypeNames.addAll(findInjectedClassNames(env));
    for (Iterator<String> i = remainingTypeNames.iterator(); i.hasNext();) {
      InjectableType injectedClass = injectableClassFactory.create(i.next());
      // Verify that we have access to all types to be injected on this pass.
      boolean missingDependentClasses =
          !allTypesExist(injectedClass.fields)
          || (injectedClass.constructor != null && !allTypesExist(injectedClass.constructor
              .getParameters()))
          || !allTypesExist(injectedClass.staticFields);
      if (!missingDependentClasses) {
        try {
          generateAndWrite(injectAdapterGenerator, injectedClass);
          generateAndWrite(staticInjectionGenerator, injectedClass);
        } catch (IOException e) {
          note.on(injectedClass.type).error("Code gen failed: %s", e);
        }
        i.remove();
      }
    }
    if (env.processingOver() && !remainingTypeNames.isEmpty()) {
      note.error("Could not find injection type required by " + remainingTypeNames);
    }
  }

  private void generateAndWrite(AbstractAdapterGenerator<InjectableType> generator,
      InjectableType injectable) throws IOException {
    StringWriter ioWriter = new StringWriter();
    generator.generate(ioWriter, injectable);
    JavaFileObject sourceFile = filer.createSourceFile(injectable.adapterName, injectable.type);
    Writer sourceWriter = sourceFile.openWriter();
    sourceWriter.append(ioWriter.getBuffer());
    sourceWriter.close();
  }

  /**
   * Return true if all element types are currently available in this code
   * generation pass. Unavailable types will be of kind {@link TypeKind#ERROR}.
   */
  private boolean allTypesExist(Collection<? extends Element> elements) {
    for (Element element : elements) {
      if (element.asType().getKind() == TypeKind.ERROR) {
        return false;
      }
    }
    return true;
  }

  private Set<String> findInjectedClassNames(RoundEnvironment env) {
    // First gather the set of classes that have @Inject-annotated members.
    Set<String> injectedTypeNames = new LinkedHashSet<String>();
    for (Element element : env.getElementsAnnotatedWith(Inject.class)) {
      if (!validateInjectable(element)) {
        continue;
      }
      injectedTypeNames.add(rawTypeToString(element.getEnclosingElement().asType(), '.'));
    }
    return injectedTypeNames;
  }

  private boolean validateInjectable(Element injectable) {
    Element injectableType = injectable.getEnclosingElement();

    if (injectable.getKind() == ElementKind.CLASS) {
      note.on(injectable)
          .error("@Inject is not valid on a class: %s", elementToString(injectable));
      return false;
    }

    if (injectable.getKind() == ElementKind.METHOD) {
      note.on(injectable)
          .error("Method injection is not supported: %s", elementToString(injectable));
      return false;
    }

    if (injectable.getKind() == ElementKind.FIELD
        && injectable.getModifiers().contains(FINAL)) {
      note.on(injectable)
          .error("Can't inject a final field: %s", elementToString(injectable));
      return false;
    }

    if (injectable.getKind() == ElementKind.FIELD
        && injectable.getModifiers().contains(PRIVATE)) {
      note.on(injectable)
          .error("Can't inject a private field: %s", elementToString(injectable));
      return false;
    }

    if (injectable.getKind() == ElementKind.CONSTRUCTOR
        && injectable.getModifiers().contains(PRIVATE)) {
      note.on(injectable)
          .error("Can't inject a private constructor: %s", elementToString(injectable));
      return false;
    }

    ElementKind elementKind = injectableType.getEnclosingElement().getKind();
    boolean isClassOrInterface = elementKind.isClass() || elementKind.isInterface();
    boolean isStatic = injectableType.getModifiers().contains(STATIC);

    if (isClassOrInterface && !isStatic) {
      note.on(injectableType)
          .error("Can't inject a non-static inner class: %s", elementToString(injectable));
      return false;
    }
    return true;
  }
}

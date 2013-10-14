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

import dagger.Module;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Binding.InvalidBindingException;
import dagger.internal.Linker;
import dagger.internal.ProblemDetector;
import dagger.internal.SetBinding;
import dagger.internal.codegen.Util.CodeGenerationIncompleteException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;

/**
 * Performs full graph analysis on a module.
 */
@SupportedAnnotationTypes("dagger.Module")
public final class GraphAnalysisProcessor extends AbstractDaggerProcessor {
  private final Set<String> delayedModuleNames = new LinkedHashSet<String>();

  @Inject Filer filer;
  @Inject GraphAnalysisLoader loader;
  @Inject GraphAnalysisErrorHandler errorHandlerFactory;
  @Inject GraphVisualizer visualizer;

  @Override protected Object getModule() {
    @Module(injects = GraphAnalysisProcessor.class) class ProcessorModule { }
    return new ProcessorModule();
  }

  /**
   * Perform full-graph analysis on complete modules. This checks that all of
   * the module's dependencies are satisfied.
   */
  @Override protected void doProcess(Set<? extends TypeElement> types, RoundEnvironment env) {
    if (!env.processingOver()) {
      // Storing module names for later retrieval as the element instance is invalidated across
      // passes.
      for (Element e : env.getElementsAnnotatedWith(Module.class)) {
        if (!(e instanceof TypeElement)) {
          note.on(e)
              .error("@Module applies to a type, %s is a %s", e.getSimpleName(), e.getKind());
          continue;
        }
        delayedModuleNames.add(((TypeElement) e).getQualifiedName().toString());
      }
    }

    Set<Element> modules = new LinkedHashSet<Element>();
    for (String moduleName : delayedModuleNames) {
      modules.add(elements.getTypeElement(moduleName));
    }

    for (Element element : modules) {
      Map<String, Object> annotation = null;
      try {
        annotation = Util.getAnnotation(Module.class, element);
      } catch (CodeGenerationIncompleteException e) {
        continue; // skip this element. An up-stream compiler error is in play.
      }

      TypeElement moduleType = (TypeElement) element;
      if (annotation == null) {
        note.on(moduleType).error("Missing @Module annotation.");
        continue;
      }
      if (annotation.get("complete").equals(Boolean.TRUE)) {
        Map<String, Binding<?>> bindings;
        try {
          bindings = processCompleteModule(moduleType, false);
          new ProblemDetector().detectCircularDependencies(bindings.values());
        } catch (ModuleValidationException e) {
          note.on(e.source).error("Graph validation failed: %s", e);
          continue;
        } catch (InvalidBindingException e) {
          note.on(elements.getTypeElement(e.type)).error("Graph validation failed: %s", e);
          continue;
        } catch (RuntimeException e) {
          note.on(moduleType).error("Graph validation failed: %s", e);
          continue;
        }
        try {
          writeDotFile(moduleType, bindings);
        } catch (IOException e) {
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          note.on(moduleType)
              .warn("Graph visualization failed. Please report this as a bug.\n\n" + sw);
        }
      }

      if (annotation.get("library").equals(Boolean.FALSE)) {
        Map<String, Binding<?>> bindings = processCompleteModule(moduleType, true);
        try {
          new ProblemDetector().detectUnusedBinding(bindings.values());
        } catch (IllegalStateException e) {
          note.on(moduleType).error("Graph validation failed: %s", e);
        }
      }
    }
  }

  private Map<String, Binding<?>> processCompleteModule(TypeElement rootModule,
      boolean ignoreCompletenessErrors) {
    Map<String, TypeElement> allModules = new LinkedHashMap<String, TypeElement>();
    collectIncludesRecursively(rootModule, allModules, new LinkedList<String>());
    ArrayList<GraphAnalysisStaticInjection> staticInjections =
        new ArrayList<GraphAnalysisStaticInjection>();

    Linker.ErrorHandler errorHandler = errorHandlerFactory.create(
        ignoreCompletenessErrors, rootModule.getQualifiedName().toString());

    Linker linker = new Linker(null, loader, errorHandler);
    // Linker requires synchronization for calls to requestBinding and linkAll.
    // We know statically that we're single threaded, but we synchronize anyway
    // to make the linker happy.
    synchronized (linker) {
      Map<String, Binding<?>> baseBindings = new LinkedHashMap<String, Binding<?>>();
      Map<String, Binding<?>> overrideBindings = new LinkedHashMap<String, Binding<?>>();
      for (TypeElement module : allModules.values()) {
        Map<String, Object> annotation = Util.getAnnotation(Module.class, module);
        boolean overrides = (Boolean) annotation.get("overrides");
        boolean library = (Boolean) annotation.get("library");
        Map<String, Binding<?>> addTo = overrides ? overrideBindings : baseBindings;

        // Gather the injectable types from the annotation.
        for (Object injectableTypeObject : (Object[]) annotation.get("injects")) {
          TypeMirror injectableType = (TypeMirror) injectableTypeObject;
          String key = Util.isInterface(injectableType)
              ? GeneratorKeys.get(injectableType)
              : GeneratorKeys.rawMembersKey(injectableType);
          linker.requestBinding(key, module.getQualifiedName().toString(),
              getClass().getClassLoader(), false, true);
        }

        // Gather the static injections.
        for (Object staticInjection : (Object[]) annotation.get("staticInjections")) {
          TypeMirror staticInjectionTypeMirror = (TypeMirror) staticInjection;
          Element element = processingEnv.getTypeUtils().asElement(staticInjectionTypeMirror);
          staticInjections.add(new GraphAnalysisStaticInjection(element));
        }

        // Gather the enclosed @Provides methods.
        for (Element enclosed : module.getEnclosedElements()) {
          Provides provides = enclosed.getAnnotation(Provides.class);
          if (provides == null) {
            continue;
          }
          ExecutableElement providerMethod = (ExecutableElement) enclosed;
          String key = GeneratorKeys.get(providerMethod);
          Binding<?> binding = new GraphAnalysisProvidesBinding(key, providerMethod, library);

          Binding<?> previous = addTo.get(key);
          if (previous != null) {
            if ((provides.type() == SET || provides.type() == SET_VALUES)
                && previous instanceof SetBinding) {
              // No duplicate bindings error if both bindings are set bindings.
            } else {
              note.on(providerMethod).error("Duplicate bindings for %s%s:\n    $s\n    %s",
                  key,
                  (overrides) ? "" : " in override module(s) - cannot override overrides",
                  previous.requiredBy,
                  binding.requiredBy);
            }
          }

          switch (provides.type()) {
            case UNIQUE:
              addTo.put(key, binding);
              break;

            case SET:
              String setKey = GeneratorKeys.getSetKey(providerMethod);
              SetBinding.add(addTo, setKey, binding);
              break;

            case SET_VALUES:
              SetBinding.add(addTo, key, binding);
              break;

            default:
              throw new AssertionError("Unknown @Provides type " + provides.type());
          }
        }
      }

      linker.installBindings(baseBindings);
      linker.installBindings(overrideBindings);
      for (GraphAnalysisStaticInjection staticInjection : staticInjections) {
        staticInjection.attach(linker);
      }

      // Link the bindings. This will traverse the dependency graph, and report
      // errors if any dependencies are missing.
      return linker.linkAll();
    }
  }

  void collectIncludesRecursively(
      TypeElement module, Map<String, TypeElement> result, Deque<String> path) {
    Map<String, Object> annotation = Util.getAnnotation(Module.class, module);
    if (annotation == null) {
      // TODO(tbroyer): pass annotation information
      throw new ModuleValidationException("No @Module on " + module, module);
    }

    // Add the module.
    String name = module.getQualifiedName().toString();
    if (path.contains(name)) {
      StringBuilder message = new StringBuilder("Module Inclusion Cycle: ");
      if (path.size() == 1) {
        message.append(name).append(" includes itself directly.");
      } else {
        String current = null;
        String includer = name;
        for (int i = 0; path.size() > 0; i++) {
          current = includer;
          includer = path.pop();
          message.append("\n").append(i).append(". ")
              .append(current).append(" included by ").append(includer);
        }
        message.append("\n0. ").append(name);
      }
      throw new ModuleValidationException(message.toString(), module);
    }
    result.put(name, module);

    // Recurse for each included module.
    Types types = processingEnv.getTypeUtils();
    List<Object> seedModules = new ArrayList<Object>();
    seedModules.addAll(Arrays.asList((Object[]) annotation.get("includes")));
    if (!annotation.get("addsTo").equals(Void.class)) seedModules.add(annotation.get("addsTo"));
    for (Object include : seedModules) {
      if (!(include instanceof TypeMirror)) {
        // TODO(tbroyer): pass annotation information
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
            "Unexpected value for include: " + include + " in " + module, module);
        continue;
      }
      TypeElement includedModule = (TypeElement) types.asElement((TypeMirror) include);
      path.push(name);
      collectIncludesRecursively(includedModule, result, path);
      path.pop();
    }
  }

  void writeDotFile(TypeElement module, Map<String, Binding<?>> bindings) throws IOException {
    JavaFileManager.Location location = StandardLocation.SOURCE_OUTPUT;
    String path = Util.getPackage(module).getQualifiedName().toString();
    String file = module.getQualifiedName().toString().substring(path.length() + 1) + ".dot";
    try {
      FileObject resource = filer.createResource(location, path, file, module);
      Writer writer = resource.openWriter();
      GraphVizWriter dotWriter = new GraphVizWriter(writer);
      visualizer.write(bindings, dotWriter);
      dotWriter.close();
    } catch (FilerException e) {
      // We have already written this = subsequent rounds don't require a re-write.
    }
  }

  static class ModuleValidationException extends IllegalStateException {
    final TypeElement source;

    public ModuleValidationException(String message, TypeElement source) {
      super(message);
      this.source = source;
    }
  }
}

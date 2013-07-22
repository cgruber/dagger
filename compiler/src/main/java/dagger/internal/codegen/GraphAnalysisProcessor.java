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

import com.google.common.base.Objects;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import dagger.Module;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Binding.InvalidBindingException;
import dagger.internal.BindingsGroup;
import dagger.internal.Linker;
import dagger.internal.ProblemDetector;
import dagger.internal.ProvidesBinding;
import dagger.internal.SetBinding;
import dagger.internal.codegen.Util.CodeGenerationIncompleteException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static dagger.internal.codegen.Util.className;
import static dagger.internal.codegen.Util.getAnnotation;
import static dagger.internal.codegen.Util.getPackage;
import static dagger.internal.codegen.Util.isInterface;
import static java.util.Arrays.asList;

/**
 * Performs full graph analysis on a module.
 */
@SupportedAnnotationTypes("dagger.Module")
public final class GraphAnalysisProcessor extends AbstractProcessor {
  private static final Set<String> ERROR_NAMES_TO_PROPAGATE = new LinkedHashSet<String>(asList(
      "com.sun.tools.javac.code.Symbol$CompletionFailure"));

  private final Set<String> delayedModuleNames = new LinkedHashSet<String>();

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Perform full-graph analysis on complete modules. This checks that all of
   * the module's dependencies are satisfied.
   */
  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    if (!env.processingOver()) {
      // Storing module names for later retrieval as the element instance is invalidated across
      // passes.
      for (Element e : env.getElementsAnnotatedWith(Module.class)) {
        if (!(e instanceof TypeElement)) {
          error("@Module applies to a type, " + e.getSimpleName() + " is a " + e.getKind(), e);
          continue;
        }
        delayedModuleNames.add(((TypeElement) e).getQualifiedName().toString());
      }
      return false;
    }

    Set<Element> modules = new LinkedHashSet<Element>();
    for (String moduleName : delayedModuleNames) {
      modules.add(elements().getTypeElement(moduleName));
    }

    for (Element element : modules) {
      Map<String, Object> annotation = null;
      try {
        annotation = getAnnotation(Module.class, element, true);
      } catch (ModuleValidationException e) {
        error("Missing @Module annotation.", e.source);
        continue;
      } catch (CodeGenerationIncompleteException e) {
        continue; // skip this element. An up-stream compiler error is in play.
      }

      TypeElement moduleType = (TypeElement) element;

      if (annotation.get("complete").equals(Boolean.TRUE)) {
        Map<String, Binding<?>> bindings;
        try {
          bindings = processCompleteModule(moduleType, false);
          new ProblemDetector().detectCircularDependencies(bindings.values());
        } catch (ModuleValidationException e) {
          error("Graph validation failed: " + e.getMessage(), e.source);
          continue;
        } catch (InvalidBindingException e) {
          error("Graph validation failed: " + e.getMessage(), elements().getTypeElement(e.type));
          continue;
        } catch (RuntimeException e) {
          if (ERROR_NAMES_TO_PROPAGATE.contains(e.getClass().getName())) {
            throw e;
          }
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          error("Unknown error " + e.getClass().getName() + " thrown by javac in graph validation: "
              + e.getMessage() + sw, moduleType);
          continue;
        }
        try {
          writeDotFile(moduleType, bindings);
        } catch (IOException e) {
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          processingEnv.getMessager()
              .printMessage(Diagnostic.Kind.WARNING,
                  "Graph visualization failed. Please report this as a bug.\n\n" + sw, moduleType);
        }
      }

      if (annotation.get("library").equals(Boolean.FALSE)) {
        Map<String, Binding<?>> bindings = processCompleteModule(moduleType, true);
        try {
          new ProblemDetector().detectUnusedBinding(bindings.values());
        } catch (IllegalStateException e) {
          error("Graph validation failed: " + e.getMessage(), moduleType);
        }
      }
    }
    return false;
  }

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }

  private Map<String, Binding<?>> processCompleteModule(TypeElement rootModule,
      boolean ignoreCompletenessErrors) {

    Iterable<TypeElement> modules = collectAndValidateModules(rootModule);

    ArrayList<GraphAnalysisStaticInjection> staticInjections =
        new ArrayList<GraphAnalysisStaticInjection>();

    Linker.ErrorHandler errorHandler = ignoreCompletenessErrors ? Linker.ErrorHandler.NULL
        : new GraphAnalysisErrorHandler(processingEnv, rootModule.getQualifiedName().toString());
    Linker linker = new Linker(null, new GraphAnalysisLoader(processingEnv), errorHandler);
    // Linker requires synchronization for calls to requestBinding and linkAll.
    // We know statically that we're single threaded, but we synchronize anyway
    // to make the linker happy.
    synchronized (linker) {
      BindingsGroup baseBindings = new BindingsGroup() {
        @Override public Binding<?> contributeSetBinding(String key, SetBinding<?> value) {
          return super.put(key, value);
        }
      };
      BindingsGroup overrideBindings = new BindingsGroup() {
        @Override public Binding<?> contributeSetBinding(String key, SetBinding<?> value) {
          throw new IllegalStateException("Module overrides cannot contribute set bindings.");
        }
      };
      for (TypeElement module : modules) {
        Map<String, Object> annotation = getAnnotation(Module.class, module, true);
        boolean overrides = (Boolean) annotation.get("overrides");
        boolean library = (Boolean) annotation.get("library");
        BindingsGroup addTo = overrides ? overrideBindings : baseBindings;

        // Gather the injectable types from the annotation.
        Set<String> injectsProvisionKeys = new LinkedHashSet<String>();
        for (Object injectableTypeObject : (Object[]) annotation.get("injects")) {
          TypeMirror injectableType = (TypeMirror) injectableTypeObject;
          String providerKey = GeneratorKeys.get(injectableType);
          injectsProvisionKeys.add(providerKey);
          String key = isInterface(injectableType)
              ? providerKey
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
          ProvidesBinding<?> binding = new ProviderMethodBinding(key, providerMethod, library);

          Binding<?> previous = addTo.get(key);
          if (previous != null) {
            if ((provides.type() == SET || provides.type() == SET_VALUES)
                && previous instanceof SetBinding) {
              // No duplicate bindings error if both bindings are set bindings.
            } else {
              String message = "Duplicate bindings for " + key;
              if (overrides) {
                message += " in override module(s) - cannot override an override";
              }
              message += ":\n    " + previous.requiredBy + "\n    " + binding.requiredBy;
              error(message, providerMethod);
            }
          }

          switch (provides.type()) {
            case UNIQUE:
              if (injectsProvisionKeys.contains(binding.provideKey)) {
                binding.setDependedOn(true);
              }
              try {
                addTo.contributeProvidesBinding(key, binding);
              } catch (IllegalStateException ise) {
                throw new ModuleValidationException(ise.getMessage(), providerMethod);
              }
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

  private Types types() {
    return processingEnv.getTypeUtils();
  }

  private Elements elements() {
    return processingEnv.getElementUtils();
  }

  private Iterable<TypeElement> collectAndValidateModules(TypeElement module) {
    Modules modules = new Modules(module);
    collectModulesRecursively(module, modules, false);
    return modules.processedModules;
  }

  private void collectModulesRecursively(TypeElement module, Modules modules, boolean ancestor) {
    if (ancestor && modules.moduleStack.isEmpty()) {
      throw new AssertionError();
    }
    /*
     * Algorithm:
     * √ 0. if module completely processed, return
     * √ 1. extract scope from annotation
     * √ 2. validate scope and add it if called as an ancestor reference (addsTo).
     * √ 3. validate module not already on module stack.
     * √ 4. add module to scope->module multimap
     *   5. add scope to module->scope map.
     * √ 6. for each module in includes -> recurse to step 1
     * √ 7. for addsTo if any -> recurse to step 1
     * √ 8. mark module as processed.
     */
    if (!modules.processedModules.contains(module)) {
      Map<String, Object> annotation = getAnnotation(Module.class, module, true);
      throwCycleErrorIfInPath(module, modules.moduleStack, "Module Inclusion Cycle: ", module);

      String scope = Util.getScopeFromModule(annotation);
      if (modules.moduleStack.isEmpty()) {
        modules.scopeStack.push(scope);
      } else {
        if (ancestor) {
          if (modules.scopeStack.peek().equals(scope)) {
            String error = String.format(
                "Cannot use addsTo references with the same scope. "
                + "Either use a longer-lived scope, or use includes=");
            throw new ModuleValidationException(error, modules.moduleStack.pop());
          }
          if (modules.scopeStack.contains("javax.inject.Singleton")) {
            throw new ModuleValidationException(
                "Cannot add a module with longer-lived scope than Singleton.",
                modules.moduleStack.pop());
          }
          throwCycleErrorIfInPath(module, modules.scopeStack, "Scope cycle: ", scope);
          modules.scopeStack.push(scope);
        } else {
          if (!modules.scopeStack.peek().equals(scope)) {
            throw new ModuleValidationException(
                "Cannot include a module with a different scope. Did you mean addsTo?",
                    modules.moduleStack.pop());
          }
        }
      }

      modules.scopesToModules.put(scope, module);
      modules.moduleStack.push(module);

      for (Object include : (Object[]) annotation.get("includes")) {
        String label = "included module";
        if (include instanceof TypeMirror) {
          TypeElement includedModule = (TypeElement) types().asElement((TypeMirror) include);
          collectModulesRecursively(includedModule, modules, false);
        } else {
          // TODO(tbroyer): pass annotation information
          processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
              "Unexpected value for " + label + ": " + include + " in " + module, module);
        }
      }

      if (annotation.containsKey("addsTo")) {
        String label = "addsTo";
        Object addsTo = annotation.get("addsTo");
        if (addsTo instanceof TypeMirror) {
          TypeElement includedModule = (TypeElement) types().asElement((TypeMirror) addsTo);

          collectModulesRecursively(includedModule, modules, true);

        } else {
          // TODO(tbroyer): pass annotation information
          processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
              "Unexpected value for " + label + ": " + addsTo + " in " + module, module);
        }

      }

      modules.moduleStack.pop();
      if (modules.moduleStack.isEmpty() || ancestor) {
        modules.scopeStack.pop();
      }
      modules.processedModules.add(module);
    }
  }

  private <T> void throwCycleErrorIfInPath(TypeElement module, Deque<T> path,
      String messagePrefix, T name) {
    if (path.contains(name)) {
      StringBuilder message = new StringBuilder(messagePrefix);
      if (path.size() == 1) {
        message.append(name).append(" refers to itself directly.");
      } else {
        String current = null;
        String includer = name.toString();
        for (int i = 0; path.size() > 0; i++) {
          current = includer;
          includer = path.pop().toString();
          message.append("\n").append(i).append(". ")
              .append(current).append(" included by ").append(includer);
        }
        message.append("\n0. ").append(name);
      }
      throw new ModuleValidationException(message.toString(), module);
    }
  }

  static class ProviderMethodBinding extends ProvidesBinding<Object> {
    private final ExecutableElement method;
    private final Binding<?>[] parameters;

    protected ProviderMethodBinding(String provideKey, ExecutableElement method, boolean library) {
      super(provideKey, Util.getScopeAnnotation(method), className(method),
          method.getSimpleName().toString());
      this.method = method;
      this.parameters = new Binding[method.getParameters().size()];
      setLibrary(library);
    }

    @Override public void attach(Linker linker) {
      for (int i = 0; i < method.getParameters().size(); i++) {
        VariableElement parameter = method.getParameters().get(i);
        String parameterKey = GeneratorKeys.get(parameter);
        parameters[i] = linker.requestBinding(parameterKey, method.toString(),
            getClass().getClassLoader());
      }
    }

    @Override public Object get() {
      throw new AssertionError("Compile-time binding should never be called to inject.");
    }

    @Override public void injectMembers(Object t) {
      throw new AssertionError("Compile-time binding should never be called to inject.");
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      Collections.addAll(get, parameters);
    }

    @Override public String toString() {
      return "ProvidesBinding[key=" + provideKey
          + " method=" + moduleClass + "." + method.getSimpleName() + "()";
    }
  }

  void writeDotFile(TypeElement module, Map<String, Binding<?>> bindings) throws IOException {
    JavaFileManager.Location location = StandardLocation.SOURCE_OUTPUT;
    String path = getPackage(module).getQualifiedName().toString();
    String file = module.getQualifiedName().toString().substring(path.length() + 1) + ".dot";
    FileObject resource = processingEnv.getFiler().createResource(location, path, file, module);

    Writer writer = resource.openWriter();
    GraphVizWriter dotWriter = new GraphVizWriter(writer);
    new GraphVisualizer().write(bindings, dotWriter);
    dotWriter.close();
  }

  private static class Modules {
    final TypeElement rootModule;
    final Deque<String> scopeStack = new ArrayDeque<String>();
    final Deque<TypeElement> moduleStack = new ArrayDeque<TypeElement>();
    final Multimap<String, TypeElement> scopesToModules = LinkedHashMultimap.create();
    //final Map<TypeElement, String> modulesToScope = new LinkedHashMap<TypeElement, String>();
    final Set<TypeElement> processedModules = new HashSet<TypeElement>();
    Modules(TypeElement rootModule) {
      this.rootModule = rootModule;
    }
    @Override public String toString() {
      return Objects.toStringHelper(this).omitNullValues()
          .add("scopeStack", scopeStack)
          .add("moduleStack", moduleStack)
          .add("processed", processedModules)
          .add("root", rootModule)
          .toString();
    }
  }

  static class ModuleValidationException extends IllegalStateException {
    final Element source;

    public ModuleValidationException(String message, Element element) {
      super(message);
      this.source = element;
    }
  }
}

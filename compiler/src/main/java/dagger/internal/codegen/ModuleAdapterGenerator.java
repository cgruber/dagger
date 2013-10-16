/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

import com.squareup.javawriter.JavaWriter;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Linker;
import dagger.internal.ModuleAdapter;
import dagger.internal.SetBinding;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static dagger.internal.codegen.AdapterJavadocs.bindingTypeDocs;
import static dagger.internal.loaders.GeneratedAdapters.MODULE_ADAPTER_SUFFIX;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates a ModuleAdapter and Provides bindings.
 */
class ModuleAdapterGenerator extends AbstractAdapterGenerator<ModuleType> {
  private static final String BINDINGS_MAP = JavaWriter.type(
      Map.class, String.class.getCanonicalName(), Binding.class.getCanonicalName() + "<?>");

  @Override String adapterName(ModuleType type) {
    return Util.adapterName(type.type, MODULE_ADAPTER_SUFFIX);
  }

  /**
   * Write a companion class for {@code type} that implements
   * {@link ModuleAdapter} to expose its provider methods.
   */
  @Override
  void generate(Writer ioWriter, ModuleType module) throws IOException {
    JavaWriter writer = new JavaWriter(ioWriter);

    boolean multibindings = checkForMultibindings(module.methods);
    boolean providerMethodDependencies = checkForDependencies(module.methods);

    writer.emitSingleLineComment(AdapterJavadocs.GENERATED_BY_DAGGER);
    writer.emitPackage(Util.getPackage(module.type).getQualifiedName().toString());
    writer.emitImports(findImports(multibindings, !module.methods.isEmpty(),
        providerMethodDependencies));

    String typeName = module.type.getQualifiedName().toString();
    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.MODULE_TYPE);
    writer.beginType(adapterName(module), "class", EnumSet.of(PUBLIC, FINAL),
        JavaWriter.type(ModuleAdapter.class, typeName));

    StringBuilder injectsField = new StringBuilder().append("{ ");
    for (Object injectableType : module.injects) {
      TypeMirror typeMirror = (TypeMirror) injectableType;
      String key = Util.isInterface(typeMirror)
          ? GeneratorKeys.get(typeMirror)
          : GeneratorKeys.rawMembersKey(typeMirror);
      injectsField.append(JavaWriter.stringLiteral(key)).append(", ");
    }
    injectsField.append("}");
    writer.emitField(
        "String[]", "INJECTS", EnumSet.of(PRIVATE, STATIC, FINAL), injectsField.toString());

    StringBuilder staticInjectionsField = new StringBuilder().append("{ ");
    for (Object staticInjection : module.staticInjections) {
      TypeMirror typeMirror = (TypeMirror) staticInjection;
      staticInjectionsField.append(Util.typeToString(typeMirror)).append(".class, ");
    }
    staticInjectionsField.append("}");
    writer.emitField("Class<?>[]", "STATIC_INJECTIONS", EnumSet.of(PRIVATE, STATIC, FINAL),
        staticInjectionsField.toString());

    StringBuilder includesField = new StringBuilder().append("{ ");
    for (Object include : module.includes) {
      if (!(include instanceof TypeMirror)) {
        // TODO(tbroyer): pass annotation information
        note.on(module.type).warn("Unexpected value: %s in includes of %s", include, module.type);
        continue;
      }
      TypeMirror typeMirror = (TypeMirror) include;
      includesField.append(Util.typeToString(typeMirror)).append(".class, ");
    }
    includesField.append("}");
    writer.emitField(
        "Class<?>[]", "INCLUDES", EnumSet.of(PRIVATE, STATIC, FINAL), includesField.toString());

    writer.emitEmptyLine();
    writer.beginMethod(null, adapterName(module), EnumSet.of(PUBLIC));
    writer.emitStatement("super(INJECTS, STATIC_INJECTIONS, %s %s, INCLUDES, %s %s, %s %s)",
        module.overrides, "/*overrides*/",
        module.complete, "/*complete*/",
        module.library, "/*library*/");
    writer.endMethod();

    ExecutableElement noArgsConstructor = Util.getNoArgsConstructor(module.type);
    if (noArgsConstructor != null && Util.isCallableConstructor(noArgsConstructor)) {
      writer.emitEmptyLine();
      writer.emitAnnotation(Override.class);
      writer.beginMethod(typeName, "newModule", EnumSet.of(PUBLIC));
      writer.emitStatement("return new %s()", typeName);
      writer.endMethod();
    }

    // caches
    Map<ExecutableElement, String> methodToClassName =
        new LinkedHashMap<ExecutableElement, String>();
    Map<String, AtomicInteger> methodNameToNextId = new LinkedHashMap<String, AtomicInteger>();

    if (!module.methods.isEmpty()) {
      writer.emitEmptyLine();
      writer.emitJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD);
      writer.emitAnnotation(Override.class);
      writer.beginMethod("void", "getBindings", EnumSet.of(PUBLIC), BINDINGS_MAP, "map");

      for (ExecutableElement providerMethod : module.methods) {
        Provides provides = providerMethod.getAnnotation(Provides.class);
        switch (provides.type()) {
        case UNIQUE: {
          String key = GeneratorKeys.get(providerMethod);
          writer.emitStatement(
              "map.put(%s, new %s(module))",
              JavaWriter.stringLiteral(key),
              bindingClassName(providerMethod, methodToClassName, methodNameToNextId));
          break;
        }
        case SET: {
          String key = GeneratorKeys.getSetKey(providerMethod);
          writer.emitStatement(
              "SetBinding.add(map, %s, new %s(module))",
              JavaWriter.stringLiteral(key),
              bindingClassName(providerMethod, methodToClassName, methodNameToNextId));
          break;
        }
        case SET_VALUES: {
          String key = GeneratorKeys.get(providerMethod);
          writer.emitStatement(
              "SetBinding.add(map, %s, new %s(module))",
              JavaWriter.stringLiteral(key),
              bindingClassName(providerMethod, methodToClassName, methodNameToNextId));
          break;
        }
        default:
          throw new AssertionError("Unknown @Provides type " + provides.type());
        }
      }
      writer.endMethod();
    }

    for (ExecutableElement providerMethod : module.methods) {
      generateProvidesAdapter(
          writer, providerMethod, methodToClassName, methodNameToNextId, module.library);
    }

    writer.endType();
    writer.close();
  }

  private boolean checkForDependencies(List<ExecutableElement> providerMethods) {
    for (ExecutableElement element : providerMethods) {
      if (!element.getParameters().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private boolean checkForMultibindings(List<ExecutableElement> providerMethods) {
    for (ExecutableElement element : providerMethods) {
      Provides.Type providesType = element.getAnnotation(Provides.class).type();
      if (providesType == SET || providesType == SET_VALUES) {
        return true;
      }
    }
    return false;
  }

  private Set<String> findImports(boolean multibindings, boolean providers, boolean dependencies) {
    Set<String> imports = new LinkedHashSet<String>();
    imports.add(ModuleAdapter.class.getCanonicalName());
    if (providers) {
      imports.add(Binding.class.getCanonicalName());
      imports.add(Map.class.getCanonicalName());
      imports.add(Provider.class.getCanonicalName());
    }
    if (dependencies) {
      imports.add(Linker.class.getCanonicalName());
      imports.add(Set.class.getCanonicalName());
    }
    if (multibindings) {
      imports.add(SetBinding.class.getCanonicalName());
    }
    return imports;
  }

  private String bindingClassName(ExecutableElement providerMethod,
      Map<ExecutableElement, String> methodToClassName,
      Map<String, AtomicInteger> methodNameToNextId) {
    String className = methodToClassName.get(providerMethod);
    if (className != null) return className;

    String methodName = providerMethod.getSimpleName().toString();
    String suffix = "";
    AtomicInteger id = methodNameToNextId.get(methodName);
    if (id == null) {
      methodNameToNextId.put(methodName, new AtomicInteger(2));
    } else {
      suffix = id.toString();
      id.incrementAndGet();
    }
    String uppercaseMethodName = Character.toUpperCase(methodName.charAt(0))
        + methodName.substring(1);
    className = uppercaseMethodName + "ProvidesAdapter" + suffix;
    methodToClassName.put(providerMethod, className);
    return className;
  }

  private void generateProvidesAdapter(JavaWriter writer, ExecutableElement providerMethod,
      Map<ExecutableElement, String> methodToClassName,
      Map<String, AtomicInteger> methodNameToNextId, boolean library)
      throws IOException {
    String methodName = providerMethod.getSimpleName().toString();
    String moduleType = Util.typeToString(providerMethod.getEnclosingElement().asType());
    String className =
        bindingClassName(providerMethod, methodToClassName, methodNameToNextId);
    String returnType = Util.typeToString(providerMethod.getReturnType());
    List<? extends VariableElement> parameters = providerMethod.getParameters();
    boolean dependent = !parameters.isEmpty();

    writer.emitEmptyLine();
    writer.emitJavadoc(bindingTypeDocs(returnType, false, false, dependent));
    writer.beginType(className, "class", EnumSet.of(PUBLIC, STATIC, FINAL),
        JavaWriter.type(Binding.class, returnType),
        JavaWriter.type(Provider.class, returnType));
    writer.emitField(moduleType, "module", EnumSet.of(PRIVATE, FINAL));
    for (Element parameter : parameters) {
      TypeMirror parameterType = parameter.asType();
      writer.emitField(JavaWriter.type(Binding.class, Util.typeToString(parameterType)),
          parameterName(parameter.getSimpleName().contentEquals("module"), parameter),
          EnumSet.of(PRIVATE));
    }

    writer.emitEmptyLine();
    writer.beginMethod(null, className, EnumSet.of(PUBLIC), moduleType, "module");
    boolean singleton = providerMethod.getAnnotation(Singleton.class) != null;
    String key = JavaWriter.stringLiteral(GeneratorKeys.get(providerMethod));
    String membersKey = null;
    writer.emitStatement("super(%s, %s, %s, %s)",
        key, membersKey, (singleton ? "IS_SINGLETON" : "NOT_SINGLETON"),
        JavaWriter.stringLiteral(moduleType + "." + methodName + "()"));
    writer.emitStatement("this.module = module");
    writer.emitStatement("setLibrary(%s)", library);
    writer.endMethod();

    if (dependent) {
      writer.emitEmptyLine();
      writer.emitJavadoc(AdapterJavadocs.ATTACH_METHOD);
      writer.emitAnnotation(Override.class);
      writer.emitAnnotation(SuppressWarnings.class, JavaWriter.stringLiteral("unchecked"));
      writer.beginMethod(
          "void", "attach", EnumSet.of(PUBLIC), Linker.class.getCanonicalName(), "linker");
      for (VariableElement parameter : parameters) {
        String parameterKey = GeneratorKeys.get(parameter);
        writer.emitStatement(
            "%s = (%s) linker.requestBinding(%s, %s.class, getClass().getClassLoader())",
            parameterName(parameter.getSimpleName().contentEquals("module"), parameter),
            writer.compressType(JavaWriter.type(Binding.class,
                Util.typeToString(parameter.asType()))),
            JavaWriter.stringLiteral(parameterKey),
            writer.compressType(moduleType));
      }
      writer.endMethod();

      writer.emitEmptyLine();
      writer.emitJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD);
      writer.emitAnnotation(Override.class);
      String setOfBindings = JavaWriter.type(Set.class, "Binding<?>");
      writer.beginMethod("void", "getDependencies", EnumSet.of(PUBLIC), setOfBindings,
          "getBindings", setOfBindings, "injectMembersBindings");
      for (Element parameter : parameters) {
        writer.emitStatement("getBindings.add(%s)", parameter.getSimpleName().toString());
      }
      writer.endMethod();
    }

    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.GET_METHOD, returnType);
    writer.emitAnnotation(Override.class);
    writer.beginMethod(returnType, "get", EnumSet.of(PUBLIC));
    StringBuilder args = new StringBuilder();
    boolean first = true;
    for (Element parameter : parameters) {
      if (!first) args.append(", ");
      else first = false;
      args.append(String.format("%s.get()", parameter.getSimpleName().toString()));
    }
    writer.emitStatement("return module.%s(%s)", methodName, args.toString());
    writer.endMethod();

    writer.endType();
  }

}

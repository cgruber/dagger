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
import dagger.MembersInjector;
import dagger.internal.Binding;
import dagger.internal.Linker;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import static dagger.internal.codegen.AdapterJavadocs.bindingTypeDocs;
import static dagger.internal.codegen.Util.getApplicationSupertype;
import static dagger.internal.codegen.Util.getPackage;
import static dagger.internal.codegen.Util.rawTypeToString;
import static dagger.internal.loaders.GeneratedAdapters.INJECT_ADAPTER_SUFFIX;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates an InjectAdapter for an injectable type.
 */
class InjectAdapterGenerator extends AbstractAdapterGenerator<InjectableType> {

  @Inject public InjectAdapterGenerator() { }

  @Override String adapterName(InjectableType type) {
    return Util.adapterName(type.type, INJECT_ADAPTER_SUFFIX);
  }

  /**
   * Write a companion class for {@code type} that extends {@link Binding}.
   *
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   */
  @Override void generate(Writer ioWriter, InjectableType injectable) throws IOException {
    if (injectable.constructor != null || hasInjectableFields(injectable)) {
      String packageName = getPackage(injectable.type).getQualifiedName().toString();
      String strippedTypeName =
          strippedTypeName(injectable.type.getQualifiedName().toString(), packageName);
      TypeMirror supertype = getApplicationSupertype(injectable.type);
      JavaWriter writer = new JavaWriter(ioWriter);
      boolean isAbstract = injectable.type.getModifiers().contains(ABSTRACT);
      boolean injectMembers = hasInjectableFields(injectable) || supertype != null;
      boolean disambiguateFields =
          hasInjectableFields(injectable) && hasInjectableConstructorParameters(injectable);
      boolean dependent = injectMembers || hasInjectableConstructorParameters(injectable);

      writer.emitSingleLineComment(AdapterJavadocs.GENERATED_BY_DAGGER);
      writer.emitPackage(packageName);
      writer.emitImports(findImports(dependent, injectMembers, injectable.constructor != null));
      writer.emitEmptyLine();
      writer.emitJavadoc(bindingTypeDocs(strippedTypeName, isAbstract, injectMembers, dependent));
      writer.beginType(adapterName(injectable), "class", EnumSet.of(PUBLIC, FINAL),
          JavaWriter.type(Binding.class, strippedTypeName),
          implementedInterfaces(strippedTypeName, injectMembers, injectable.constructor != null));
      writeMemberBindingsFields(writer, injectable.fields, disambiguateFields);
      if (injectable.constructor != null) {
        writeParameterBindingsFields(writer, injectable.constructor, disambiguateFields);
      }
      if (supertype != null) {
        writeSupertypeInjectorField(writer, supertype);
      }
      writer.emitEmptyLine();
      writeInjectAdapterConstructor(writer, injectable.constructor, injectable.type,
          strippedTypeName, adapterName(injectable));
      if (dependent) {
        writeAttachMethod(writer, injectable.constructor, injectable.fields,
            disambiguateFields, strippedTypeName, supertype, true);
        writeGetDependenciesMethod(writer, injectable.constructor, injectable.fields,
            disambiguateFields, supertype, true);
      }
      if (injectable.constructor != null) {
        writeGetMethod(writer, injectable.constructor, disambiguateFields, injectMembers,
            strippedTypeName);
      }
      if (injectMembers) {
        writeMembersInjectMethod(writer, injectable.fields, disambiguateFields, strippedTypeName,
            supertype);
      }
      writer.endType();
      writer.close();
    }
  }

  private boolean hasInjectableFields(InjectableType injectable) {
    return !injectable.fields.isEmpty();
  }

  private boolean hasInjectableConstructorParameters(InjectableType injectable) {
    return (injectable.constructor != null) && !injectable.constructor.getParameters().isEmpty();
  }

  private Set<String> findImports(boolean dependent, boolean injectMembers, boolean isProvider) {
    Set<String> imports = new LinkedHashSet<String>();
    imports.add(Binding.class.getCanonicalName());
    if (dependent) {
      imports.add(Linker.class.getCanonicalName());
      imports.add(Set.class.getCanonicalName());
    }
    if (injectMembers) imports.add(MembersInjector.class.getCanonicalName());
    if (isProvider) imports.add(Provider.class.getCanonicalName());
    return imports;
  }

  private String strippedTypeName(String type, String packageName) {
    return type.substring(packageName.isEmpty() ? 0 : packageName.length() + 1);
  }

  private void writeGetMethod(JavaWriter writer, ExecutableElement constructor,
      boolean disambiguateFields, boolean injectMembers, String strippedTypeName)
      throws IOException {
    writer.emitJavadoc(AdapterJavadocs.GET_METHOD, strippedTypeName);
    writer.emitAnnotation(Override.class);
    writer.beginMethod(strippedTypeName, "get", EnumSet.of(PUBLIC));
    StringBuilder newInstance = new StringBuilder();
    newInstance.append(strippedTypeName).append(" result = new ");
    newInstance.append(strippedTypeName).append('(');
    boolean first = true;
    for (VariableElement parameter : constructor.getParameters()) {
      if (!first) newInstance.append(", ");
      else first = false;
      newInstance.append(parameterName(disambiguateFields, parameter))
          .append(".get()");
    }
    newInstance.append(')');
    writer.emitStatement(newInstance.toString());
    if (injectMembers) {
      writer.emitStatement("injectMembers(result)");
    }
    writer.emitStatement("return result");
    writer.endMethod();
    writer.emitEmptyLine();
  }

  private void writeMembersInjectMethod(JavaWriter writer, List<Element> fields,
      boolean disambiguateFields, String strippedTypeName, TypeMirror supertype)
      throws IOException {
    writer.emitJavadoc(AdapterJavadocs.MEMBERS_INJECT_METHOD, strippedTypeName);
    writer.emitAnnotation(Override.class);
    writer.beginMethod("void", "injectMembers", EnumSet.of(PUBLIC), strippedTypeName, "object");
    for (Element field : fields) {
      writer.emitStatement("object.%s = %s.get()",
          field.getSimpleName(),
          fieldName(disambiguateFields, field));
    }
    if (supertype != null) {
      writer.emitStatement("supertype.injectMembers(object)");
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }

  private String[] implementedInterfaces(
      String strippedTypeName, boolean hasFields, boolean isProvider) {
    List<String> interfaces = new ArrayList<String>();
    if (isProvider) {
      interfaces.add(JavaWriter.type(Provider.class, strippedTypeName));
    }
    if (hasFields) {
      interfaces.add(JavaWriter.type(MembersInjector.class, strippedTypeName));
    }
    return interfaces.toArray(new String[interfaces.size()]);
  }

  private void writeSupertypeInjectorField(JavaWriter writer, TypeMirror supertype)
      throws IOException {
    writer.emitField(JavaWriter.type(Binding.class, rawTypeToString(supertype, '.')), "supertype",
        EnumSet.of(PRIVATE));
  }

  private void writeInjectAdapterConstructor(JavaWriter writer, ExecutableElement constructor,
      TypeElement type, String strippedTypeName, String adapterName) throws IOException {
    writer.beginMethod(null, adapterName, EnumSet.of(PUBLIC));
    String key = (constructor != null)
        ? JavaWriter.stringLiteral(GeneratorKeys.get(type.asType()))
        : null;
    String membersKey = JavaWriter.stringLiteral(GeneratorKeys.rawMembersKey(type.asType()));
    boolean singleton = type.getAnnotation(Singleton.class) != null;
    writer.emitStatement("super(%s, %s, %s, %s.class)",
        key, membersKey, (singleton ? "IS_SINGLETON" : "NOT_SINGLETON"), strippedTypeName);
    writer.endMethod();
    writer.emitEmptyLine();
  }

  private void writeGetDependenciesMethod(JavaWriter writer, ExecutableElement constructor,
      List<Element> fields, boolean disambiguateFields, TypeMirror supertype,
      boolean extendsBinding) throws IOException {
    writer.emitJavadoc(AdapterJavadocs.GET_DEPENDENCIES_METHOD);
    if (extendsBinding) {
      writer.emitAnnotation(Override.class);
    }
    String setOfBindings = JavaWriter.type(Set.class, "Binding<?>");
    writer.beginMethod("void", "getDependencies", EnumSet.of(PUBLIC), setOfBindings,
        "getBindings", setOfBindings, "injectMembersBindings");
    if (constructor != null) {
      for (Element parameter : constructor.getParameters()) {
        writer.emitStatement("getBindings.add(%s)",
            parameterName(disambiguateFields, parameter));
      }
    }
    for (Element field : fields) {
      writer.emitStatement("injectMembersBindings.add(%s)",
          fieldName(disambiguateFields, field));
    }
    if (supertype != null) {
      writer.emitStatement("injectMembersBindings.add(%s)", "supertype");
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }
}
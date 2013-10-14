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
import dagger.internal.Binding;
import dagger.internal.Linker;
import dagger.internal.codegen.AbstractDaggerProcessor.DaggerMessageWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import static dagger.internal.codegen.Util.rawTypeToString;
import static dagger.internal.codegen.Util.typeToString;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

abstract class AbstractAdapterGenerator<T> {

  @Inject DaggerMessageWriter note;
  @Inject Filer filer;

  abstract void generate(Writer out, T type) throws IOException;

  protected String fieldName(boolean disambiguateFields, Element field) {
    return (disambiguateFields ? "field_" : "") + field.getSimpleName().toString();
  }

  protected String parameterName(boolean disambiguateFields, Element parameter) {
    return (disambiguateFields ? "parameter_" : "") + parameter.getSimpleName().toString();
  }

  protected void writeMemberBindingsFields(
      JavaWriter writer, List<Element> fields, boolean disambiguateFields) throws IOException {
    for (Element field : fields) {
      writer.emitField(JavaWriter.type(Binding.class, typeToString(field.asType())),
          fieldName(disambiguateFields, field), EnumSet.of(PRIVATE));
    }
  }

  protected void writeParameterBindingsFields(
      JavaWriter writer, ExecutableElement constructor, boolean disambiguateFields)
      throws IOException {
    for (VariableElement parameter : constructor.getParameters()) {
      writer.emitField(JavaWriter.type(Binding.class,
          typeToString(parameter.asType())),
          parameterName(disambiguateFields, parameter), EnumSet.of(PRIVATE));
    }
  }

  protected void writeAttachMethod(JavaWriter writer, ExecutableElement constructor,
      List<Element> fields, boolean disambiguateFields, String typeName, TypeMirror supertype,
      boolean extendsBinding) throws IOException {
    writer.emitJavadoc(AdapterJavadocs.ATTACH_METHOD);
    if (extendsBinding) {
      writer.emitAnnotation(Override.class);
    }
    writer.emitAnnotation(SuppressWarnings.class, JavaWriter.stringLiteral("unchecked"));
    writer.beginMethod(
        "void", "attach", EnumSet.of(PUBLIC), Linker.class.getCanonicalName(), "linker");
    if (constructor != null) {
      for (VariableElement parameter : constructor.getParameters()) {
        writer.emitStatement(
            "%s = (%s) linker.requestBinding(%s, %s.class, getClass().getClassLoader())",
            parameterName(disambiguateFields, parameter),
            writer.compressType(JavaWriter.type(Binding.class, typeToString(parameter.asType()))),
            JavaWriter.stringLiteral(GeneratorKeys.get(parameter)), typeName);
      }
    }
    for (Element field : fields) {
      writer.emitStatement(
          "%s = (%s) linker.requestBinding(%s, %s.class, getClass().getClassLoader())",
          fieldName(disambiguateFields, field),
          writer.compressType(JavaWriter.type(Binding.class, typeToString(field.asType()))),
          JavaWriter.stringLiteral(GeneratorKeys.get((VariableElement) field)), typeName);
    }
    if (supertype != null) {
      writer.emitStatement(
          "%s = (%s) linker.requestBinding(%s, %s.class, getClass().getClassLoader()"
              + ", false, true)",
          "supertype",
          writer.compressType(JavaWriter.type(Binding.class, rawTypeToString(supertype, '.'))),
          JavaWriter.stringLiteral(GeneratorKeys.rawMembersKey(supertype)), typeName);
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }

}
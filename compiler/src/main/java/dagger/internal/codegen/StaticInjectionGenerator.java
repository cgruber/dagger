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
import dagger.internal.StaticInjection;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.inject.Inject;
import javax.lang.model.element.Element;

import static dagger.internal.codegen.Util.getPackage;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates a StaticInjection for an injectable type.
 */
final class StaticInjectionGenerator extends AbstractAdapterGenerator<InjectableType> {

  @Inject public StaticInjectionGenerator() { }

  /**
   * Write a companion class for {@code type} that extends {@link StaticInjection}.
   */
  @Override void generate(Writer out, InjectableType injectedClass) throws IOException {
    if (!injectedClass.staticFields.isEmpty()) {
      String typeName = injectedClass.type.getQualifiedName().toString();
      JavaWriter writer = new JavaWriter(out);

      writer.emitSingleLineComment(AdapterJavadocs.GENERATED_BY_DAGGER);
      writer.emitPackage(getPackage(injectedClass.type).getQualifiedName().toString());
      writer.emitImports(Arrays.asList(
          StaticInjection.class.getName(),
          Binding.class.getName(),
          Linker.class.getName()));
      writer.emitEmptyLine();
      writer.emitJavadoc(AdapterJavadocs.STATIC_INJECTION_TYPE,
          injectedClass.type.getSimpleName());
      writer.beginType(injectedClass.adapterName, "class", EnumSet.of(PUBLIC, FINAL),
          StaticInjection.class.getSimpleName());
      writeMemberBindingsFields(writer, injectedClass.staticFields, false);
      writer.emitEmptyLine();
      writeAttachMethod(
          writer, null, injectedClass.staticFields, false, typeName, null, true);
      writeStaticInjectMethod(writer, injectedClass.staticFields, typeName);
      writer.endType();
      writer.close();
    }
  }

  private void writeStaticInjectMethod(JavaWriter writer, List<Element> fields, String typeName)
      throws IOException {
    writer.emitEmptyLine();
    writer.emitJavadoc(AdapterJavadocs.STATIC_INJECT_METHOD);
    writer.emitAnnotation(Override.class);
    writer.beginMethod("void", "inject", EnumSet.of(PUBLIC));
    for (Element field : fields) {
      writer.emitStatement("%s.%s = %s.get()",
          writer.compressType(typeName),
          field.getSimpleName().toString(),
          fieldName(false, field));
    }
    writer.endMethod();
    writer.emitEmptyLine();
  }
}

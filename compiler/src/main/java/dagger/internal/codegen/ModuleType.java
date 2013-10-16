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

import dagger.Module;
import dagger.internal.codegen.AbstractDaggerProcessor.DaggerMessageWriter;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Represents the key structural elements of an injectable type.
 */
class ModuleType {
    final TypeElement type;
    final List<ExecutableElement> methods;
    final Object[] staticInjections;
    final Object[] injects;
    final Object[] includes;
    final boolean overrides;
    final boolean complete;
    final boolean library;

    ModuleType(TypeElement type, Map<String, Object> annotation, List<ExecutableElement> methods) {
      this.type = type;
      this.methods = methods;
      staticInjections = (Object[]) annotation.get("staticInjections");
      injects = (Object[]) annotation.get("injects");
      includes = (Object[]) annotation.get("includes");
      overrides = (Boolean) annotation.get("overrides");
      complete = (Boolean) annotation.get("complete");
      library = (Boolean) annotation.get("library");
    }

    static class Factory {
      @Inject Elements elements;
      @Inject DaggerMessageWriter note;

      /**
       * @param injectedClassName the name of a class with an @Inject-annotated member.
       */
      ModuleType create(String typeName, List<ExecutableElement> methods) {
        TypeElement type = elements.getTypeElement(typeName);
        // Attempt to get the annotation. If types are missing, this will throw
        // IllegalStateException.
        Map<String, Object> annotationData = Util.getAnnotation(Module.class, type);
        if (annotationData == null) {
          note.on(type).error("%s has @Provides methods but no @Module annotation", type);
          return null;
        }
        return new ModuleType(type, annotationData, methods);
      }
    }
  }
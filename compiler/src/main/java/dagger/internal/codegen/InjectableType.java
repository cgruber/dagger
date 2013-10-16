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

import dagger.internal.codegen.AbstractDaggerProcessor.DaggerMessageWriter;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static dagger.internal.codegen.Util.elementToString;
import static dagger.internal.codegen.Util.getNoArgsConstructor;
import static dagger.internal.codegen.Util.isCallableConstructor;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Represents the key structural elements of an injectable type.
 */
class InjectableType {
    final TypeElement type;
    final List<Element> staticFields;
    final ExecutableElement constructor;
    final List<Element> fields;

    private InjectableType(TypeElement type, List<Element> staticFields,
        ExecutableElement constructor, List<Element> fields) {
      this.type = type;
      this.staticFields = staticFields;
      this.constructor = constructor;
      this.fields = fields;
    }

    static class Factory {
      @Inject Elements elements;
      @Inject DaggerMessageWriter note;

      /**
       * @param injectedClassName the name of a class with an @Inject-annotated member.
       */
      InjectableType create(String injectedClassName) {
        TypeElement type = elements.getTypeElement(injectedClassName);
        boolean isAbstract = type.getModifiers().contains(ABSTRACT);
        List<Element> staticFields = new ArrayList<Element>();
        ExecutableElement constructor = null;
        List<Element> fields = new ArrayList<Element>();
        for (Element member : type.getEnclosedElements()) {
          if (member.getAnnotation(Inject.class) == null) {
            continue;
          }

          switch (member.getKind()) {
            case FIELD:
              if (member.getModifiers().contains(STATIC)) {
                staticFields.add(member);
              } else {
                fields.add(member);
              }
              break;
            case CONSTRUCTOR:
              if (constructor != null) {
                // TODO(tbroyer): pass annotation information
                note.on(member)
                    .error("Too many injectable constructors on %s", type.getQualifiedName());
              } else if (isAbstract) {
                // TODO(tbroyer): pass annotation information
                note.on(member)
                    .error("Abstract class %s must not have an @Inject-annotated constructor.",
                        type.getQualifiedName());
              }
              constructor = (ExecutableElement) member;
              break;
            default:
              // TODO(tbroyer): pass annotation information
              note.on(member).error("Cannot inject %s", elementToString(member));
              break;
          }
        }

        if (constructor == null && !isAbstract) {
          constructor = getNoArgsConstructor(type);
          if (constructor != null && !isCallableConstructor(constructor)) {
            constructor = null;
          }
        }
        return new InjectableType(type, staticFields, constructor, fields);
      }
    }
  }
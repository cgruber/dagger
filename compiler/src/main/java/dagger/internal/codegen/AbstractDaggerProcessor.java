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

import dagger.ProcessorGraph;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

abstract class AbstractDaggerProcessor extends AbstractProcessor {
  @Inject DaggerMessageWriter note;
  @Inject Elements elements;
  @Inject Types typeUtils;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    ProcessorGraph.create(new ProcessorModule(processingEnv), getModule()).inject(this);
  }

  protected abstract Object getModule();

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    try {
     doProcess(types, env);
    } catch (Throwable t) {
      note.error("Unrecoverable error in Dagger processor: %s", t.getMessage());
    }
    return false;
  }

  protected abstract void doProcess(Set<? extends TypeElement> types, RoundEnvironment env);

  interface MessageWriter {
    void error(CharSequence format, Object ... formatParamters);
    void warn(CharSequence format, Object ... formatParamters);
  }

  static class DaggerMessageWriter implements MessageWriter {
    @Inject Messager messager;

    public MessageWriter on(final Element e) {
      return new MessageWriter() {
        @Override public void error(CharSequence format, Object... formatParamters) {
          messager.printMessage(Kind.ERROR, String.format(format.toString(), formatParamters), e);
        }

        @Override public void warn(CharSequence format, Object... formatParamters) {
          messager.printMessage(Kind.WARNING, String.format(format.toString(), formatParamters), e);
        }
      };
    }

    @Override public void error(CharSequence format, Object ... formatParamters) {
      messager.printMessage(Kind.ERROR, String.format(format.toString(), formatParamters));
    }

    @Override public void warn(CharSequence format, Object ... formatParamters) {
      messager.printMessage(Kind.WARNING, String.format(format.toString(), formatParamters));
    }
  }
}
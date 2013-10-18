/*
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
package dagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Singleton;

/**
 * Annotates an interface to indicate that it should represent a strongly-typed
 * group of entry points for a given graph. An interface that is annotated with
 * {@code Graph} may extend an interface annotated with {@link GraphSpec} and
 * will be considered "equivalent to" the so-annotated parent interface during
 * graph analysis.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Graph {
  Class<?> scope() default Singleton.class;
  Class<?>[] bindings() default { };
  Class<?>[] dependencies() default { };

  @Graph
  public static class Root { }
}

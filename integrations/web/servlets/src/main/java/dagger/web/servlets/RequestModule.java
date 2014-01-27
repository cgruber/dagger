/*
 * Copyright (C) 2013 Square, Inc.
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
package dagger.web.servlets;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Module(/* scope = PerRequest.class, */library = true)
public final class RequestModule {
  private final HttpServletRequest request;
  private final HttpServletResponse response;
  RequestModule(HttpServletRequest request, HttpServletResponse response) {
    this.request = request;
    this.response = response;
  }
  @Provides @Singleton /* @PerRequest */ HttpServletRequest request() {
    return request;
  }
  @Provides @Singleton /* @PerRequest */ HttpServletResponse response() {
    return response;
  }
}
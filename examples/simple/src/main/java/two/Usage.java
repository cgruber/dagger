/*
 * Copyright (C) 2012 Google Inc.
 * Copyright (C) 2012 Square Inc.
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
package two;

import dagger.ObjectGraph;
import two.Context.HttpRequest;
import two.Context.HttpSession;
import two.Graphs.ApplicationGraph;
import two.Graphs.ProductionApplicationGraph;
import two.Graphs.ProductionSessionGraph;
import two.Graphs.RequestGraph;
import two.Graphs.SessionGraph;
import two.ModelForRequest.HomeAction;
import two.ModelForRequest.RequestBindings;
import two.ModelForSession.SessionBindings;
import two.TestGraphs.TestSessionGraph;

public final class Usage {

  // f'rinstance
  boolean testMode = true;

  void usage() {
    ApplicationGraph ag = ObjectGraph.create(ProductionApplicationGraph.class);

    HttpSession session = new HttpSession() { };
    SessionGraph sg = testMode
        ? ObjectGraph.extend(ag).with(TestSessionGraph.class) // no bindings needed for mocks.
        : ObjectGraph.extend(ag).with(ProductionSessionGraph.class, new SessionBindings(session));

    HttpRequest request = new HttpRequest() { };
    RequestGraph rg = ObjectGraph.extend(sg).with(RequestGraph.class, new RequestBindings(request));

    HomeAction ha = rg.homeAction();
    ha.doStuff();
  }
}

package two;

import javax.inject.Scope;

final class Scopes {
  private  Scopes() { }

  @Scope @interface PerRequest { }
  @Scope @interface PerSession { }
}

package dagger2.nested;

import dagger.Component;
import javax.inject.Inject;


public class Outer {

  public static void main(String ... args) {
    MyComponent main = new Outer$Dagger_MyComponent();
  }

  static class A {
    @Inject A() {}
  }

  static class B {
    @Inject A a;
  }

  @Component interface MyComponent {
    A getA();
  }
}

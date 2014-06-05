package coffee;

public class Flavor {
  private final String name;
  public Flavor(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}

package coffee;

import java.util.Set;
import javax.inject.Inject;

public class MilkSteamer {
  private final Set<Flavor> flavors;
  @Inject MilkSteamer(Set<Flavor> flavors) {
    this.flavors = flavors;
  }

  public void steam() {
    System.out.println(" [_]P steamed " + flavors + " milk! [_]P ");
  }
}

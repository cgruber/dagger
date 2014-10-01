package coffee;

import dagger.Lazy;
import javax.inject.Inject;

class CoffeeMaker {
  private final Lazy<Heater> heater; // Create a possibly costly heater only when we use it.
  private final Pump pump;
  private final String name;
  @Inject CoffeeMaker(Lazy<Heater> heater, Pump pump, String name) {
    this.heater = heater;
    this.pump = pump;
    this.name = name;
  }

  public void brew() {
    System.out.println("Name: " + this.name);
    heater.get().on();
    pump.pump();
    System.out.println(" [_]P coffee! [_]P ");
    heater.get().off();
  }
}

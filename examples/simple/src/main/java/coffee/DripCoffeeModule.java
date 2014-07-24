package coffee;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import static dagger.Provides.Type.SET;

@Module(
    injects = CoffeeApp.class,
    includes = PumpModule.class,
    library = true // Satisfy Dagger 1 constraint until we turn off the orphan analysis.
)
class DripCoffeeModule {
  @Provides @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }

  @Provides(type=SET) Flavor vanilla() {
    return new Flavor("vanilla");
  }
}

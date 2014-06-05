package coffee;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import static dagger.Provides.Type.SET;

@Module(
    injects = CoffeeApp.class,
    includes = PumpModule.class
)
class DripCoffeeModule {
  @Provides @Singleton Heater provideHeater() {
    return new ElectricHeater();
  }

  @Provides(type=SET) Flavor vanilla() {
    return new Flavor("vanilla");
  }
}

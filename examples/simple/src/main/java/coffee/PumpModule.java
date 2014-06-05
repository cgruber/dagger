package coffee;

import dagger.Module;
import dagger.Provides;

import static dagger.Provides.Type.SET;

@Module(complete = false, library = true)
class PumpModule {
  @Provides Pump providePump(Thermosiphon pump) {
    return pump;
  }

  @Provides(type=SET) Flavor chocolate() {
    return new Flavor("chocolate");
  }
}

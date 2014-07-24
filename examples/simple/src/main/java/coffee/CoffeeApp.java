package coffee;


public class CoffeeApp {


  public static void main(String[] args) {
    CoffeeMain coffee = Dagger_CoffeeMain.builder()
        .dripCoffeeModule(new DripCoffeeModule())
        .pumpModule(new PumpModule())
        .build();
    coffee.getMaker().brew();
  }
}

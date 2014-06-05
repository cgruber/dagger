package coffee;


public class CoffeeApp {


  public static void main(String[] args) {
    CoffeeMain coffee = new Dagger_CoffeeMain(new DripCoffeeModule(), new PumpModule());
    coffee.getMaker().brew();
  }
}

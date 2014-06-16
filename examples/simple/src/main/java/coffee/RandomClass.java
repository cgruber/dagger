package coffee;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Scope;
import javax.inject.Singleton;

import static coffee.RandomClass.WebPath.ADMIN;

public class RandomClass {

  @Scope @interface PerRequest {}

  static enum WebPath {
    LOGIN("/login"),
    ADMIN("/admin"),
    CUSTOM_SHOP("/shop/custom/.*"),
    SHOP("/shop/.*");

    public final String path;
    WebPath(String path) {
      this.path = path;
    }

    static WebPath match(String string) {
      for (WebPath path : values()) {
        if(path.path.matches(string)) {
          return path;
        }
      }
      throw new IllegalArgumentException("No such path bound: " + string);
    }
  }

  @MapKey @interface ForPath {
    WebPath value();
  }

  @Component
  interface Server {
    Main main();
  }

  @PerRequest
  @Component(
      /*dependencies = Server.class*/
      modules = {MyModule.class, MyOtherModule.class, RequestModule.class}
  )
  interface WebRequest {
    Map<WebRequest, Handler> handlers();
  }

  class Main implements Runnable {
    @Inject HttpListener listener;
    @Inject Server server;
    @Inject Dispatcher dispatcher;

    @Override public void run() {

      boolean notQuitting = true;
      while (notQuitting) {
        HttpTransaction tx = listener.poll();
        WebRequest webRequest = new Dagger_WebRequest(server);
        dispatcher.dispatch(tx);
      }
    }
  }

  @Singleton
  class Dispatcher {
    @Inject Map<WebPath, Handler> handlers;

    public void dispatch(HttpTransaction transaction) {
      WebPath path = WebPath.match(transaction.request().getPath());
      Handler handler = handlers.get(path);
      if (handler == null) {
        throw new IllegalArgumentException("No page found for " + transaction.request().getPath());
      }
      handler.handle(transaction);
    }
  }

  @Module
  @PerRequest
  static class RequestModule {
    @Provides User user(HttpRequest request, UserDAO dao) {
      User user = dao.find(request.getCookie("cookie_id"))
      throw new Redirect(WebPath.LOGIN);
    }
  }

  @PerRequest
  @Module
  static class MyModule {
    @PerRequest
    @Provides(type=MAP)
    @ForPath(ADMIN)
    Handler adminHandler(User user) {
      return new Handler(/* use user*/){
        @Override public void handle(HttpTransaction transaction) {
         transaction.response().getOutout().append("Server down.").flush();
        }
      };
    }
  }

  @Module
  static class MyOtherModule {
    @PerRequest
    @Provides(type=MAP)
    @ForPath(ADMIN)
    Handler loginHandler() {
      return new Handler(){
        @Override public void handle(HttpTransaction transaction) {
         transaction.response().getOutout().append("... some login page...").flush();
        }
      };
    }
  }

  public static void main(String[] args) {
    Server server = new Dagger_Server();
    new Thread(server.main()).start();
  }

  //// Pretend it exists

  interface Handler {
    void handle(HttpTransaction transaction);
  }
  interface HttpListener {
    HttpTransaction poll();
  }
  interface HttpTransaction {
    HttpRequest request();
    HttpResponse response();
  }
  interface HttpRequest {
    Properties getAttributes();
    Object getCookie(String string);
    String getPath();
    String[] getQueryParameters();
  }
  interface HttpResponse {
    OutputStreamWriter getOutout();
  }

  static class MapEntries {
    static <K, V> Map.Entry<K, V> create(K key, V value) {
      return null; // in real life we would do something.
    }
  }

  interface User {}

  static class Redirect extends Throwable {
    Redirect(WebPath path) {
      super();
    }
  }

  interface UserDAO {

    User find(Object cookie);

  }

  @interface MapKey {}
}

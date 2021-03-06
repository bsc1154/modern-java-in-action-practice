package modernjavainaction.chap16.v1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import modernjavainaction.chap16.ExchangeService;
import modernjavainaction.chap16.ExchangeService.Money;

public class BestPriceFinder {

  private final List<Shop> shops = Arrays.asList(
      new Shop("BestPrice"),
      new Shop("LetsSaveBig"),
      new Shop("MyFavoriteShop"),
      new Shop("BuyItAll")/*,
      new Shop("ShopEasy")*/);

  private final Executor executor = Executors.newFixedThreadPool(shops.size(), (Runnable r) -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    return t;
  });

  public List<String> findPricesSequential(String product) {
    return shops.stream()
        .map(shop -> shop.getName() + " price is " + shop.getPrice(product))
        .collect(Collectors.toList());
  }

  public List<String> findPricesParallel(String product) {
    return shops.parallelStream()
        .map(shop -> shop.getName() + " price is " + shop.getPrice(product))
        .collect(Collectors.toList());
  }

  public List<String> findPricesFuture(String product) {
    List<CompletableFuture<String>> priceFutures =
        shops.stream()
            .map(shop -> CompletableFuture.supplyAsync(() -> shop.getName() + " price is "
                + shop.getPrice(product), executor))
            .collect(Collectors.toList());

    List<String> prices = priceFutures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList());
    return prices;
  }

  public List<String> findPricesInUSD(String product) {
    List<CompletableFuture<Double>> priceFutures = new ArrayList<>();
    for (Shop shop : shops) {
      // ?????? 10-20 ??????.
      // ?????? CompletableFuture::join??? ??????????????? futurePriceInUSD??? ????????? CompletableFuture??? ??????.
      CompletableFuture<Double> futurePriceInUSD =
          CompletableFuture.supplyAsync(() -> shop.getPrice(product))
          .thenCombine(
              CompletableFuture.supplyAsync(
                  () ->  ExchangeService.getRate(Money.EUR, Money.USD))
              // ?????? 9??? ????????? ???????????? ?????? ??????
              .completeOnTimeout(ExchangeService.DEFAULT_RATE, 1, TimeUnit.SECONDS),
              (price, rate) -> price * rate
          )
          // ?????? 9??? ????????? ???????????? ?????? ??????
          .orTimeout(3, TimeUnit.SECONDS);
      priceFutures.add(futurePriceInUSD);
    }
    // ??????: ?????? ????????? shop??? ????????? ??? ???????????? ?????? getName() ????????? ???????????????.
    // so the getName() call below has been commented out.
    List<String> prices = priceFutures.stream()
        .map(CompletableFuture::join)
        .map(price -> /*shop.getName() +*/ " price is " + price)
        .collect(Collectors.toList());
    return prices;
  }

  public List<String> findPricesInUSDJava7(String product) {
    ExecutorService executor = Executors.newCachedThreadPool();
    List<Future<Double>> priceFutures = new ArrayList<>();
    for (Shop shop : shops) {
      final Future<Double> futureRate = executor.submit(new Callable<Double>() {
        @Override
        public Double call() {
          return ExchangeService.getRate(Money.EUR, Money.USD);
        }
      });
      Future<Double> futurePriceInUSD = executor.submit(new Callable<Double>() {
        @Override
        public Double call() {
          try {
            double priceInEUR = shop.getPrice(product);
            return priceInEUR * futureRate.get();
          }
          catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
          }
        }
      });
      priceFutures.add(futurePriceInUSD);
    }
    List<String> prices = new ArrayList<>();
    for (Future<Double> priceFuture : priceFutures) {
      try {
        prices.add(/*shop.getName() +*/ " price is " + priceFuture.get());
      }
      catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }
    return prices;
  }

  public List<String> findPricesInUSD2(String product) {
    List<CompletableFuture<String>> priceFutures = new ArrayList<>();
    for (Shop shop : shops) {
      // ???????????? ?????? ????????? ????????? ??? ????????? ????????? ?????????. ??????????????? CompletableFuture<String> ??????????????? ????????? ??? ??????.
      CompletableFuture<String> futurePriceInUSD =
          CompletableFuture.supplyAsync(() -> shop.getPrice(product))
          .thenCombine(
              CompletableFuture.supplyAsync(
                  () -> ExchangeService.getRate(Money.EUR, Money.USD)),
              (price, rate) -> price * rate
          ).thenApply(price -> shop.getName() + " price is " + price);
      priceFutures.add(futurePriceInUSD);
    }
    List<String> prices = priceFutures
        .stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList());
    return prices;
  }

  public List<String> findPricesInUSD3(String product) {
    // ????????? ?????? ????????? ??????...
    Stream<CompletableFuture<String>> priceFuturesStream = shops.stream()
        .map(shop -> CompletableFuture
            .supplyAsync(() -> shop.getPrice(product))
            .thenCombine(
                CompletableFuture.supplyAsync(() -> ExchangeService.getRate(Money.EUR, Money.USD)),
                (price, rate) -> price * rate)
            .thenApply(price -> shop.getName() + " price is " + price));
    // ????????? ????????? ?????? ????????? ??????????????? CompletableFuture??? ???????????? ??????
    List<CompletableFuture<String>> priceFutures = priceFuturesStream.collect(Collectors.toList());
    List<String> prices = priceFutures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList());
    return prices;
  }

}

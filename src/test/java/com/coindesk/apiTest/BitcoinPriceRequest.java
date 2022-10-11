package com.coindesk.apiTest;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BitcoinPriceRequest {

    private static final int CYCLE_TIME = 30;

    private void waitFor() {
        try {
            Thread.sleep(1000 * CYCLE_TIME);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Integer> getMinute() {
        int timeToRun = Integer.parseInt(ConfigurationReader.getProperty("time.to.run"));
        return new ArrayList<>(List.of(new Random().nextInt(timeToRun) + 1));
    }

    private <T> Set<T> getMedian(List<T> values) {
        List<T> median = new ArrayList<>();
        values.sort((o1, o2) -> {
            if (o1 instanceof Double) {
                Double left = (Double) o1;
                Double right = (Double) o2;
                return left.compareTo(right);
            } else {
                Long left = (Long) o1;
                Long right = (Long) o2;
                return left.compareTo(right);
            }
        });

        if (values.size() % 2 != 0) {
            median.add(values.get(values.size() / 2));
        } else {
            median.add(values.get(values.size() / 2 - 1));
            median.add(values.get(values.size() / 2));
        }
        return new LinkedHashSet<>(median);
    }

    private double getAverage(Stream<Double> stream) {
        return stream.mapToDouble(Double::doubleValue).average().getAsDouble();
    }

    private Double getStdDeviation(List<Double> list) {
        double avg = getAverage(list.stream());
        double sum = list.stream().mapToDouble(each -> Math.pow((each - avg), 2)).sum();
        return Math.sqrt(sum / ((long) list.size() - 1));
    }

    private boolean checkFluctuation(Stream<Double> stream, double price) {
        double median = getMedian(stream.collect(Collectors.toList()))
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .getAsDouble();

        return price < (median - (median * 0.05)) || price > (median + (median * 0.05));
    }

    @DisplayName("Comparing Bitcoin values")
    @ParameterizedTest
    @MethodSource("getMinute")
    public void test1(Integer min) {

        Map<String, Double> values = new LinkedHashMap<>();
        List<Double> responseTimes = new ArrayList<>();
        Map<String, Double> fluctuations = new LinkedHashMap<>();
        int i = 0;
        while (i++ < min * 2) {
            System.out.println("Request time " + i + ": " + LocalDateTime.now());
            Response response = RestAssured.given()
                    .baseUri("https://api.coindesk.com")
                    .basePath("/v1/bpi")
                    .get("/currentprice.json")
                    .then()
                    .statusCode(200)
                    .extract().response();

            // storing the response times of each request
            responseTimes.add((double) response.getTime());

            // storing the request date-times and BTC prices as double values from the response body
            Double price = Double.parseDouble(response.then().extract().jsonPath().getString("bpi.USD.rate")
                    .replace(",", ""));
            values.put(response.header("Date"), price);

            // Bonus task
            if (checkFluctuation(values.values().stream(), price)) {
                fluctuations.put(response.header("Date"), price);
                System.out.println("New fluctuation!\nPrice: " + price + " - Median: "
                        + getMedian(Arrays.asList(values.values().toArray())));
            }

            int timeOutLimit = Integer.parseInt(ConfigurationReader.getProperty("time.out.limit"));
            if (i != min * timeOutLimit) waitFor();
        }

        System.out.println("Number of collected BTC values: " + values.values().stream().distinct().count());
        System.out.println("Collected responses:");
        values.forEach((k, v) -> System.out.println(k + " : " + v));
        System.out.println("Average of the BTC prices from responses: " + getAverage(values.values().stream()) + " USD");
        System.out.println("Median of the prices of BTC: " + getMedian(Arrays.asList(values.values().toArray())) + " in USD");
        System.out.println("Standard Deviation of BTC prices: " + getStdDeviation(new ArrayList<>(values.values())));
        System.out.println("Average of the response times: " + getAverage(responseTimes.stream()) + " ms");
        System.out.println("Median of the response times: " + getMedian(responseTimes) + " in milliseconds");
        System.out.println("Minimum response time: " + Collections.min(responseTimes) + " ms");
        System.out.println("Maximum response time: " + Collections.max(responseTimes) + " ms");

        if (!fluctuations.isEmpty())
            System.out.println("Fluctuations: " + fluctuations);
    }
}
package com.oracle.alphaoffice.priceservice;

import java.util.HashMap;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArray;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * A simple price service for the Alpha Office application
 * 
 *  Examples:
 *
 * Get default service name and version:
 * curl -X GET http://localhost:8080/price
 *
 * Get price for product id 1001:
 * curl -X GET http://localhost:8080/price/1001
 *
 * The message is returned as a JSON object
 */

public class PriceService implements Service {

    /**
     * This gets config from application.yaml on classpath
     * and uses "app" section.
     */
    private static final Config CONFIG = Config.create().get("app");

    /**
     * The config value for the key {@code version}.
     */
    private static String version = CONFIG.get("version").asString("1.0.0");

    /**
     * In-memory price catalog
     */
    private static java.util.Map<Integer, Double> prices;

    public PriceService() {

        prices = new HashMap<Integer, Double>();
        try {
            System.out.println("**reading catalog:  " + CONFIG.get("catalog_path").asString());
            java.io.InputStream in = getClass().getResourceAsStream(CONFIG.get("catalog_path").asString());
            javax.json.JsonReader reader = Json.createReader(in);
            JsonObject productRoot = reader.readObject();
            reader.close();

            JsonArray products = productRoot.getJsonArray("Products");
            for (int x=0; x < products.size(); x++) {
                JsonObject item = products.getJsonObject(x);
                prices.put(Integer.valueOf(item.getInt("PRODUCT_ID")), Double.valueOf(item.getJsonNumber("LIST_PRICE").doubleValue()));
                System.out.println("Loading: " + Integer.valueOf(item.getInt("PRODUCT_ID")) + "="+ Double.valueOf(item.getJsonNumber("LIST_PRICE").doubleValue()));
            }
        }
        catch (Exception exc) {
            System.out.println(exc);
        }
    }


    /**
     * A service registers itself by updating the routine rules.
     * @param rules the routing rules.
     */
    @Override
    public final void update(final Routing.Rules rules) {
        rules
            .get("/", this::getDefaultMessage)
            .get("/{id}", this::getPrice);
    }

    /**
     * Return a default message with the name of the service and the version.
     * @param request the server request
     * @param response the server response
     */
    private void getDefaultMessage(final ServerRequest request,
                                   final ServerResponse response) {
        String msg = String.format("PriceService:%s", version);

        JsonObject returnObject = Json.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }

    /**
     * Return a list price for the product id provided
     * @param request the server request
     * @param response the server response
     */
    private void getPrice(final ServerRequest request,
                            final ServerResponse response) {
        String id = request.path().param("id");
        String price;
        
        try {
            Integer idKey = Integer.valueOf(id);;
            if (!prices.containsKey(idKey))
                throw new Exception("item " + id + " not found in catalog");

            price = String.format("%.2f", prices.get(idKey));
        } catch (Exception exc) {
            System.out.println(exc);
            price = "0.00";
        }

        JsonObject returnObject = Json.createObjectBuilder()
                .add("price", price)
                .build();
        response.send(returnObject);
    }
}

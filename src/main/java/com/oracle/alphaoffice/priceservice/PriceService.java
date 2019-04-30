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

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import oracle.jdbc.pool.OracleDataSource;
import oracle.jdbc.OracleConnection;
import java.sql.DatabaseMetaData;

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
    private static java.util.Map < Integer, Double > prices;

    /** 
     * database data
     */
    private static boolean UseDB = true;
    private final static String DB_URL = "jdbc:oracle:thin:@atp_medium?TNS_ADMIN=/Users/eshneken/OCI/Wallet_ATP";
    private final static String DB_USER = "admin";
    private final static String DB_PASSWORD = "";

    public PriceService() {

        prices = new HashMap < Integer, Double > ();
        try {
            System.out.println("**reading catalog:  " + CONFIG.get("catalog_path").asString());
            java.io.InputStream in = getClass().getResourceAsStream(CONFIG.get("catalog_path").asString());
            javax.json.JsonReader reader = Json.createReader( in );
            JsonObject productRoot = reader.readObject();
            reader.close();

            JsonArray products = productRoot.getJsonArray("Products");
            for (int x = 0; x < products.size(); x++) {
                JsonObject item = products.getJsonObject(x);
                prices.put(Integer.valueOf(item.getInt("PRODUCT_ID")), Double.valueOf(item.getJsonNumber("LIST_PRICE").doubleValue()));
                System.out.println("Loading: " + Integer.valueOf(item.getInt("PRODUCT_ID")) + "=" + Double.valueOf(item.getJsonNumber("LIST_PRICE").doubleValue()));
            }

            // database connect
            System.out.println("\n**checking DB catalog");
            Properties info = new Properties();
            info.put(OracleConnection.CONNECTION_PROPERTY_USER_NAME, DB_USER);
            info.put(OracleConnection.CONNECTION_PROPERTY_PASSWORD, DB_PASSWORD);
            info.put(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "50");

            OracleDataSource ods = new OracleDataSource();
            ods.setURL(DB_URL);
            ods.setConnectionProperties(info);

            // With AutoCloseable, the connection is closed automatically.
            try (OracleConnection connection = (OracleConnection) ods.getConnection()) {
                // Get the JDBC driver name and version 
                DatabaseMetaData dbmd = connection.getMetaData();
                System.out.println("Driver Name: " + dbmd.getDriverName());
                System.out.println("Driver Version: " + dbmd.getDriverVersion());
                // Print some connection properties
                System.out.println("Default Row Prefetch Value is: " +
                    connection.getDefaultRowPrefetch());
                System.out.println("Database Username is: " + connection.getUserName());
                System.out.println();

                // Perform a database operation 
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet resultSet = statement
                        .executeQuery("select product_id, list_price from product_catalog order by product_id")) {
                        int counter = 0;
                        while (resultSet.next()) {
                            System.out.println(resultSet.getString(1) + "=" +
                                resultSet.getString(2) + " ");
                            counter++;
                        }
                        System.out.println("**total rows in catalog=" + counter);
                        if (counter < 10)
                            throw new Exception("DB Catalog returns " + counter + " rows");
                    }
                }
            } catch (Exception exc) {
                System.out.println("Exception in DB Init: " + exc);
                UseDB = false;
            }
        } catch (Exception exc) {
            System.out.println(exc);
        } finally {
            System.out.println("**UseDB=" + UseDB);
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

            if (UseDB) {
                price = getPriceFromDB(idKey);
            } else {
                if (!prices.containsKey(idKey))
                    throw new Exception("item " + id + " not found in catalog");

                price = String.format("%.2f", prices.get(idKey));
            }
        } catch (Exception exc) {
            System.out.println(exc);
            price = "0.00";
        }

        JsonObject returnObject = Json.createObjectBuilder()
            .add("price", price)
            .build();
        response.send(returnObject);
    }

    private String getPriceFromDB(Integer ID) {
        Properties info = new Properties();
        info.put(OracleConnection.CONNECTION_PROPERTY_USER_NAME, DB_USER);
        info.put(OracleConnection.CONNECTION_PROPERTY_PASSWORD, DB_PASSWORD);
        info.put(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "50");

        try {
            OracleDataSource ods = new OracleDataSource();
            ods.setURL(DB_URL);
            ods.setConnectionProperties(info);

            // With AutoCloseable, the connection is closed automatically.
            try (OracleConnection connection = (OracleConnection) ods.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet resultSet = statement
                        .executeQuery("select list_price from product_catalog where product_id = " + ID)) {
                        if (resultSet.next()) {
                            System.out.println("**ATP says " + ID + "=" + resultSet.getString(1));
                            return resultSet.getString(1);
                        }
                        else {
                            throw new Exception("item " + ID + " not found in catalog");

                        }
                    }
                }

            }
        } catch (Exception exc) {
            System.out.println("EXCEPTION in DB Select: " + exc);
        }

        return "0.00";
    }

}
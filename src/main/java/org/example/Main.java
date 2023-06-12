package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import it.cnr.iit.jscontact.tools.exceptions.CardException;
import it.cnr.iit.jscontact.tools.vcard.converters.config.VCard2JSContactConfig;
import it.cnr.iit.jscontact.tools.vcard.converters.vcard2jscontact.VCard2JSContact;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_OK;

public class Main {
    private static final VCard2JSContact vCard2JSContact = VCard2JSContact.builder().config(VCard2JSContactConfig.builder().build()).build();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8080), 0);

        server.createContext("/convert", exchange -> {
            try {
                var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var cards = vCard2JSContact.convert(body);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(HTTP_OK, 0);
                exchange.getResponseBody().write(mapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(cards)
                        .getBytes(StandardCharsets.UTF_8));
                 exchange.getResponseBody().close();
            } catch (CardException e) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(HTTP_BAD_REQUEST, 0);
                exchange.getResponseBody().write(mapper.writeValueAsString(e.getMessage()).getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().close();
            }
        });

        server.start();
    }
}
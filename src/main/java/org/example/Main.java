package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import it.cnr.iit.jscontact.tools.dto.Card;
import it.cnr.iit.jscontact.tools.exceptions.CardException;
import it.cnr.iit.jscontact.tools.vcard.converters.config.JSContact2VCardConfig;
import it.cnr.iit.jscontact.tools.vcard.converters.config.VCard2JSContactConfig;
import it.cnr.iit.jscontact.tools.vcard.converters.jscontact2vcard.JSContact2VCard;
import it.cnr.iit.jscontact.tools.vcard.converters.vcard2jscontact.VCard2JSContact;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.*;

public class Main {
    private static final VCard2JSContact vCard2JSContact = VCard2JSContact.builder().config(VCard2JSContactConfig.builder().build()).build();
    private static final JSContact2VCard jsContact2vCard = JSContact2VCard.builder().config(JSContact2VCardConfig.builder().setCardMustBeValidated(true).build()).build();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void usage() {
        System.out.println("Arguments:");
        System.out.println(" <host>:<port> (default: \"localhost:8080\"");
        System.exit(-1);
    }
    public static void main(String[] args) throws Exception {
        String hostname = "localhost";
        int port = 8080;

        if (args.length > 1) {
            usage();
        }

        if (args.length == 1) {
            var vals = args[0].split(":", 2);
            if (vals.length != 2) {
                usage();
            }
            hostname = vals[0];
            try {
                port = Integer.parseInt(vals[1]);
            } catch (NumberFormatException e) {
                usage();
            }
        }

        System.out.println("Starting server listening on " + hostname + ":" + port);

        HttpServer server = HttpServer.create(new InetSocketAddress(hostname, port), 0);
        server.createContext("/convert", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Allow", "POST");
                exchange.sendResponseHeaders(HTTP_BAD_METHOD, 0);
                exchange.getResponseBody().close();
                return;
            }

            var reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var reqCType = exchange.getRequestHeaders().getFirst("Content-Type");
            String resCType = null;
            String resBody = null;
            int statusCode;

            try {
                if ("text/vcard;charset=utf-8".equals(reqCType)) {
                    var cards = vCard2JSContact.convert(reqBody);
                    if (cards.isEmpty()) {
                        statusCode = 422;
                    } else {
                        resCType = "application/jscontact+json";
                        resBody = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cards.get(0));
                        statusCode = HTTP_OK;
                    }
                } else if ("application/jscontact+json".equals(reqCType)) {
                    var cards = Card.toJSCards(reqBody);
                    if (cards.length == 0) {
                        statusCode = 422;
                    } else {
                        resCType = "text/vcard;charset=utf-8";
                        resBody = jsContact2vCard.convertToText(cards[0]);
                        statusCode = HTTP_OK;
                    }
                } else {
                    statusCode = HTTP_UNSUPPORTED_TYPE;
                    resCType = "application/jscontact+json";
                    resBody = "Must be text/vcard or application/jscontact+json";
                }
            } catch (CardException e) {
                statusCode = HTTP_BAD_REQUEST;
                resCType = "text/plain";
                resBody = e.getMessage();
            } catch (Exception e) {
                statusCode = HTTP_SERVER_ERROR;
                resCType = "text/plain";
                resBody = e.getMessage();
            }

            if (resCType != null) {
                exchange.getResponseHeaders().add("Content-Type", resCType);
            }
            exchange.sendResponseHeaders(statusCode, 0);
            if (resBody != null) {
                var utf8ResBody = resBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseBody().write(utf8ResBody);
            }
            exchange.getResponseBody().close();
        });

        server.start();
    }
}
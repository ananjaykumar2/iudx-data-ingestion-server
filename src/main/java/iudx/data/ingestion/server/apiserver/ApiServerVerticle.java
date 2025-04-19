package iudx.data.ingestion.server.apiserver;

import static iudx.data.ingestion.server.apiserver.response.ResponseUrn.SUCCESS;
import static iudx.data.ingestion.server.apiserver.util.Constants.API;
import static iudx.data.ingestion.server.apiserver.util.Constants.API_ENDPOINT;
import static iudx.data.ingestion.server.apiserver.util.Constants.APPLICATION_JSON;
import static iudx.data.ingestion.server.apiserver.util.Constants.CONTENT_TYPE;
import static iudx.data.ingestion.server.apiserver.util.Constants.EPOCH_TIME;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_ACCEPT;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_ALLOW_ORIGIN;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_CONTENT_LENGTH;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_CONTENT_TYPE;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_HOST;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_ORIGIN;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_REFERER;
import static iudx.data.ingestion.server.apiserver.util.Constants.HEADER_TOKEN;
import static iudx.data.ingestion.server.apiserver.util.Constants.ID;
import static iudx.data.ingestion.server.apiserver.util.Constants.ISO_TIME;
import static iudx.data.ingestion.server.apiserver.util.Constants.JSON_DETAIL;
import static iudx.data.ingestion.server.apiserver.util.Constants.JSON_TITLE;
import static iudx.data.ingestion.server.apiserver.util.Constants.JSON_TYPE;
import static iudx.data.ingestion.server.apiserver.util.Constants.MIME_APPLICATION_JSON;
import static iudx.data.ingestion.server.apiserver.util.Constants.MIME_TEXT_HTML;
import static iudx.data.ingestion.server.apiserver.util.Constants.ORIGIN;
import static iudx.data.ingestion.server.apiserver.util.Constants.ORIGIN_SERVER;
import static iudx.data.ingestion.server.apiserver.util.Constants.PROVIDER;
import static iudx.data.ingestion.server.apiserver.util.Constants.RESPONSE_SIZE;
import static iudx.data.ingestion.server.apiserver.util.Constants.ROUTE_DOC;
import static iudx.data.ingestion.server.apiserver.util.Constants.ROUTE_STATIC_SPEC;
import static iudx.data.ingestion.server.apiserver.util.Constants.USER_ID;
import static iudx.data.ingestion.server.metering.util.Constants.PRIMARY_KEY;
import static iudx.data.ingestion.server.metering.util.Constants.PROVIDER_ID;
import static iudx.data.ingestion.server.metering.util.Constants.RESULTS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import iudx.data.ingestion.server.apiserver.handlers.AuthHandler;
import iudx.data.ingestion.server.apiserver.handlers.FailureHandler;
import iudx.data.ingestion.server.apiserver.handlers.ValidationHandler;
import iudx.data.ingestion.server.apiserver.response.ResponseUrn;
import iudx.data.ingestion.server.apiserver.service.CatalogueService;
import iudx.data.ingestion.server.apiserver.util.HttpStatusCode;
import iudx.data.ingestion.server.apiserver.util.RequestType;
import iudx.data.ingestion.server.common.Api;
import iudx.data.ingestion.server.databroker.DataBrokerService;
import iudx.data.ingestion.server.metering.MeteringService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Data Ingestion API Verticle.
 * <h1>Data Ingestion API Verticle</h1>
 * <p>
 * The API Server verticle implements the IUDX Data Ingestion APIs. It handles the API requests from
 * the clients and interacts with the associated Service to respond.
 * </p>
 *
 * @version 1.0
 * @see io.vertx.core.Vertx
 * @see io.vertx.core.AbstractVerticle
 * @see io.vertx.core.http.HttpServer
 * @see io.vertx.ext.web.Router
 * @see io.vertx.servicediscovery.ServiceDiscovery
 * @see io.vertx.servicediscovery.types.EventBusService
 * @since 2021-08-04
 */

public class ApiServerVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LogManager.getLogger(ApiServerVerticle.class);

  /**
   * Service addresses
   */
  private static final String BROKER_SERVICE_ADDRESS = "iudx.data.ingestion.broker.service";
  private static final String METERING_SERVICE_ADDRESS = "iudx.data.ingestion.metering.service";

  private HttpServer server;
  private Router router;
  private int port;
  private boolean isSecureConnection;
  private String keystore;
  private String keystorePassword;
  private DataBrokerService databroker;
  private CatalogueService catalogueService;
  private MeteringService meteringService;

  private String dxApiBasePath;


  @Override
  public void start() throws Exception {

    Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add(HEADER_ACCEPT);
    allowedHeaders.add(HEADER_TOKEN);
    allowedHeaders.add(HEADER_CONTENT_LENGTH);
    allowedHeaders.add(HEADER_CONTENT_TYPE);
    allowedHeaders.add(HEADER_HOST);
    allowedHeaders.add(HEADER_ORIGIN);
    allowedHeaders.add(HEADER_REFERER);
    allowedHeaders.add(HEADER_ALLOW_ORIGIN);

    Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.POST);
    allowedMethods.add(HttpMethod.OPTIONS);
    /* Define the APIs, methods, endpoints and associated methods. */


    /* Get base paths from config */
    dxApiBasePath = config().getString("dxApiBasePath");

    router = Router.router(vertx);
    router.route().handler(
        CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));

    router.route().handler(requestHandler -> {
      requestHandler.response()
          .putHeader("Cache-Control", "no-cache, no-store,  must-revalidate,max-age=0")
          .putHeader("Pragma", "no-cache").putHeader("Expires", "0")
          .putHeader("X-Content-Type-Options", "nosniff");
      requestHandler.next();
    });

    router.route().handler(BodyHandler.create());

    FailureHandler validationsFailureHandler = new FailureHandler();
    ValidationHandler postEntitiesValidationHandler =
        new ValidationHandler(RequestType.ENTITY);
    Api apis = Api.getInstance(dxApiBasePath);


    router.post(apis.getEntitiesEndpoint()).consumes(APPLICATION_JSON)
        .handler(postEntitiesValidationHandler)
        .handler(AuthHandler.create(vertx, apis))
        .handler(this::handleEntitiesPostQuery).failureHandler(validationsFailureHandler);

    ValidationHandler postIngestionValidationHandler =
        new ValidationHandler(RequestType.INGEST);

    ValidationHandler deleteIngestionValidationHandler =
        new ValidationHandler(RequestType.INGEST_DELETE);

    router.post(apis.getIngestionEndpoint()).consumes(APPLICATION_JSON)
        .handler(postIngestionValidationHandler)
        .handler(AuthHandler.create(vertx, apis))
        .handler(this::handleIngestPostQuery).handler(validationsFailureHandler);

    router.delete(apis.getIngestionEndpoint()).consumes(APPLICATION_JSON)
        .handler(deleteIngestionValidationHandler)
        .handler(AuthHandler.create(vertx, apis))
        .handler(this::handleIngestDeleteQuery)
        .handler(validationsFailureHandler);

    /* Static Resource Handler */
    /* Get openapiv3 spec */
    router.get(ROUTE_STATIC_SPEC).produces(MIME_APPLICATION_JSON).handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.sendFile("docs/openapi.yaml");
    });
    /* Get redoc */
    router.get(ROUTE_DOC).produces(MIME_TEXT_HTML).handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.sendFile("docs/apidoc.html");
    });


    /* Read ssl configuration. */
    isSecureConnection = config().getBoolean("ssl");


    HttpServerOptions serverOptions = new HttpServerOptions();

    if (isSecureConnection) {
      LOGGER.debug("Info: Starting HTTPs server");

      /* Read the configuration and set the HTTPs server properties. */

      keystore = config().getString("keystore");
      keystorePassword = config().getString("keystorePassword");

      /*
       * Default port when ssl is enabled is 8443. If set through config, then that value is taken
       */
      port = config().getInteger("httpPort") == null ? 8443
          : config().getInteger("httpPort");

      /* Setup the HTTPs server properties, APIs and port. */

      serverOptions.setSsl(true)
          .setKeyStoreOptions(new JksOptions().setPath(keystore).setPassword(keystorePassword));

    } else {
      LOGGER.debug("Info: Starting HTTP server");

      /* Setup the HTTP server properties, APIs and port. */

      serverOptions.setSsl(false);
      /*
       * Default port when ssl is disabled is 8080. If set through config, then that value is taken
       */
      port = config().getInteger("httpPort") == null ? 8080
          : config().getInteger("httpPort");
    }
    serverOptions.setCompressionSupported(true).setCompressionLevel(5);
    server = vertx.createHttpServer(serverOptions);
    server.requestHandler(router).listen(port);

    databroker = DataBrokerService.createProxy(vertx, BROKER_SERVICE_ADDRESS);
    meteringService = MeteringService.createProxy(vertx, METERING_SERVICE_ADDRESS);
    catalogueService = new CatalogueService(vertx, config());
    printDeployedEndpoints(router);

  }

  /**
   * This method is used to handle all data publication for endpoint /ngsi-ld/v1/entities.
   *
   * @param routingContext RoutingContext Object
   */
  private void handleEntitiesPostQuery(RoutingContext routingContext) {
    LOGGER.debug("Info: handleEntitiesPostQuery method started.");

    JsonObject requestJson = routingContext.getBodyAsJson();
    String id = requestJson.getString(ID);
    HttpServerResponse response = routingContext.response();

    catalogueService.getCatItem(id).onComplete(catRsp -> {
      if (catRsp.succeeded()) {
        LOGGER.info("Success: ID Found in Catalogue.");
        JsonObject catItemJson = catRsp.result();
        requestJson.put("catItem", catItemJson);

        String publishID = UUID.randomUUID().toString();
        requestJson.put("publishID", publishID);

        databroker.publishData(requestJson, handler -> {
          if (handler.succeeded()) {
            LOGGER.info("Success: Ingestion Success");

            JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
            authInfo.mergeIn(catItemJson);

            Future.future(fu -> updateAuditTable(authInfo)); // fire-and-forget audit

            JsonObject responseJson = new JsonObject()
                    .put(JSON_TYPE, SUCCESS.getUrn())
                    .put(JSON_TITLE, SUCCESS.getMessage())
                    .put(RESULTS, new JsonArray().add(
                            new JsonObject()
                                    .put("detail", "message published successfully, ingestion success")
                                    .put("publishID", handler.result().getString("publishID"))
                    ));

            handleSuccessResponse(response, 201, responseJson.encode());

          } else {
            LOGGER.error("Fail: Ingestion Fail - {}", handler.cause().getMessage());

            handleFailedResponse(
                    response,
                    HttpStatusCode.getByValue(400),
                    ResponseUrn.INVALID_PAYLOAD_FORMAT,
                    handler.cause().getMessage());
          }
        });

      } else {
        LOGGER.error("Fail: ID does not exist in Catalogue - {}", catRsp.cause().getMessage());

        handleFailedResponse(
                response,
                HttpStatusCode.getByValue(404),
                ResponseUrn.RESOURCE_NOT_FOUND,
                "ID does not exist in catalogue");
      }
    });
  }

  public void handleIngestPostQuery(RoutingContext routingContext) {
    LOGGER.debug("Info:handleIngestPostQuery method started.");
    JsonObject requestJson = routingContext.getBodyAsJson();
    LOGGER.debug("Info: request Json :: ;" + requestJson);

    String id = requestJson.getString(ID);
    LOGGER.info("ID " + id);
    /* Handles HTTP response from server to client */
    HttpServerResponse response = routingContext.response();
    Future<JsonObject> catItem = catalogueService.getCatItem(id);
    catItem.onComplete(catRsp -> {
      if (catRsp.succeeded()) {
        LOGGER.info("Success: ID Found in Catalogue.");
        JsonObject catItemJson = catRsp.result();
        requestJson.put("catItem", catItemJson);
        databroker.ingestDataPost(requestJson, handler -> {
          if (handler.succeeded()) {
            LOGGER.info("Success: Ingestion Success");
            JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
            authInfo.mergeIn(catItemJson);
            Future.future(fu -> updateAuditTable(authInfo));
            JsonObject responseJson = new JsonObject()
                .put(JSON_TYPE, SUCCESS.getUrn())
                .put(JSON_TITLE, SUCCESS.getMessage())
                .put(RESULTS, new JsonArray().add(new JsonObject().put("detail",
                    "Creation of resource group and queue successful,Ingest data operation successful")));
            handleSuccessResponse(response, 201, responseJson.toString());
          } else if (handler.failed()) {
            LOGGER.error("Fail: Ingestion Fail");
            handleFailedResponse(response, 400, ResponseUrn.INVALID_PAYLOAD_FORMAT);
          }
        });
      } else {
        LOGGER.error("Fail: ID does not exist. ");
        handleFailedResponse(response, 404, ResponseUrn.RESOURCE_NOT_FOUND);
      }
    });

  }

  public void handleIngestDeleteQuery(RoutingContext routingContext) {
    LOGGER.debug("Info:handleIngestPostQuery method started.");
    JsonObject requestJson = routingContext.getBodyAsJson();
    LOGGER.debug("Info: request Json :: ;" + requestJson);

    String id = requestJson.getString(ID);
    LOGGER.info("ID " + id);
    /* Handles HTTP response from server to client */
    HttpServerResponse response = routingContext.response();
    Future<JsonObject> catItem = catalogueService.getCatItem(id);
    catItem.onComplete(catRsp -> {
      if (catRsp.succeeded()) {
        LOGGER.info("Success: ID Found in Catalogue.");
        JsonObject catItemJson = catRsp.result();
        requestJson.put("catItem", catItemJson);
        databroker.ingestDataDelete(requestJson, handler -> {
          if (handler.succeeded()) {
            LOGGER.info("Success: Ingestion Success");
            JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
            authInfo.mergeIn(catItemJson);
            Future.future(fu -> updateAuditTable(authInfo));
            JsonObject responseJson = new JsonObject()
                .put(JSON_TYPE, SUCCESS.getUrn())
                .put(JSON_TITLE, SUCCESS.getMessage())
                .put(RESULTS, new JsonArray().add(new JsonObject().put("detail",
                    "Deletion of resource group and queue successful")));
            handleSuccessResponse(response, 200, responseJson.toString());
          } else if (handler.failed()) {
            LOGGER.error("Fail: Ingestion Fail");
            handleFailedResponse(response, 400, ResponseUrn.INVALID_PAYLOAD_FORMAT);
          }
        });
      } else {
        LOGGER.error("Fail: ID does not exist. ");
        handleFailedResponse(response, 404, ResponseUrn.RESOURCE_NOT_FOUND);
      }
    });
  }

  /**
   * handle HTTP response.
   *
   * @param response   response object
   * @param statusCode Http status for response
   * @param result     response
   */

  private void handleSuccessResponse(HttpServerResponse response, int statusCode, String result) {
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode)
        .end(result);
  }

  private void handleFailedResponse(HttpServerResponse response, int statusCode,
                                    ResponseUrn failureType) {
    HttpStatusCode status = HttpStatusCode.getByValue(statusCode);
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(status.getValue())
        .end(generateResponse(failureType, status).toString());
  }

  private void handleFailedResponse(HttpServerResponse response, HttpStatusCode statusCode,
                                    ResponseUrn ur, String message) {
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode.getValue())
            .end(   new JsonObject().put(JSON_TYPE, ur.getUrn())
                    .put(JSON_TITLE, statusCode.getDescription())
                    .put(JSON_DETAIL, message).encode());
  }




  private JsonObject generateResponse(ResponseUrn urn, HttpStatusCode statusCode) {
    return new JsonObject().put(JSON_TYPE, urn.getUrn()).put(JSON_DETAIL,
        statusCode.getDescription());
  }


  private Future<Void> updateAuditTable(JsonObject auditJson) {
    final Promise<Void> promise = Promise.promise();
    ZonedDateTime zst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    long time = zst.toInstant().toEpochMilli();
    String isoTime =
        LocalDateTime.now()
            .atZone(ZoneId.of("Asia/Kolkata"))
            .truncatedTo(ChronoUnit.SECONDS)
            .toString();

    JsonObject request = new JsonObject();
    String resourceId = auditJson.getString(ID);
    String primaryKey = UUID.randomUUID().toString().replace("-", "");

    request.put(PRIMARY_KEY, primaryKey);
    request.put(PROVIDER_ID, auditJson.getString(PROVIDER));
    request.put(EPOCH_TIME, time);
    request.put(ISO_TIME, isoTime);
    request.put(USER_ID, auditJson.getValue(USER_ID));
    request.put(ID, resourceId);
    request.put(API, auditJson.getValue(API_ENDPOINT));
    request.put(ORIGIN, ORIGIN_SERVER);
    request.put(RESPONSE_SIZE, 0);
    meteringService.insertMeteringValuesInRmq(
        request,
        handler -> {
          if (handler.succeeded()) {
            LOGGER.info("message published into rmq.");
            promise.complete();
          } else {
            LOGGER.error("failed to publish msg into rmq.");
            promise.complete();
          }
        });

    return promise.future();
  }

  private void printDeployedEndpoints(Router router) {
    for (Route route : router.getRoutes()) {
      if (route.getPath() != null) {
        LOGGER.info("API Endpoints deployed :" + route.methods() + ":" + route.getPath());
      }
    }
  }

}
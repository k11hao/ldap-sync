package com.tqls;

import com.tqls.entity.Conf;
import com.tqls.entity.Server;
import com.unboundid.ldap.sdk.*;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 */
public class App {

    private Conf conf;

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    static {
        try {
            InputStream inputStream = App.class.getResourceAsStream("/log4j2.xml");
            ConfigurationSource source = new ConfigurationSource(inputStream);
            Configurator.initialize(null, source);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) throws FileNotFoundException {

        App app = new App();
        app.initConf();
        Timer timer;
        try {
            long period = app.conf.getPeriod() * 1000 * 60;
            final String date = null;
            timer = new Timer();
            if (app.conf.isStartEnable()) {
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        app.sync(date, null);
                    }
                }, 1000, period);
            }

        } catch (Exception err) {
            LOGGER.error("", err);
        }


        Vertx vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        ThymeleafTemplateEngine thymeleafTemplateEngine = ThymeleafTemplateEngine.create(vertx);

        //配置Router解析url
        router.route("/").handler(
                req -> {
                    Map obj = new HashMap();
                    obj.put("name", "Hello World from backend");

                    //第三步 ThymeleafTemplateEngine 直接 render
                    thymeleafTemplateEngine.render(obj,
                            "templates/index.html",
                            bufferAsyncResult -> {
                                if (bufferAsyncResult.succeeded()) {
                                    req.response()
                                            .putHeader("content-type", "text/html")
                                            .end(bufferAsyncResult.result());
                                } else {
                                    LOGGER.error("失败");
                                }
                            });
                }
        );


        router.route("/api/log").handler(ctx -> {
            HttpServerResponse response = ctx.response();
            HttpServerRequest request = ctx.request();
            System.out.println(request.getParam("test"));
        });


        router.route("/api/sync").handler(ctx -> {
            HttpServerResponse response = ctx.response();
            HttpServerRequest request = ctx.request();
            String name = request.getParam("name");
            response.setChunked(true);


            WorkerExecutor executor = vertx.createSharedWorkerExecutor("my-worker-sync");

            executor.executeBlocking(future -> {
                int count = app.sync("1997-01-01", name);
                future.complete(count);
            }, res -> {
                response.write("<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "<meta charset=\"UTF-8\">\n" +
                        "<title>更新条数</title>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<p>更新条数！" + res.result() + "</p>\n" +
                        "</body>\n" +
                        "</html>");

                ctx.response().end();
            });

        });

        server.requestHandler(router).listen(8080);

    }

    public static void login() {
        LDAPAuthentication ldapAuthentication = new LDAPAuthentication();
        ldapAuthentication.authenricate("025032", "");
    }

    public int sync(String dateStr, String name) {

        int updateCount = 0;
        try {

            String dn = "ou=Internal,ou=People,dc=tqls,dc=cn";
            Calendar c = Calendar.getInstance();
            if (dateStr != null && !dateStr.equals("")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                Date date = sdf.parse(dateStr);
                c.setTime(date);
            }
            //设置成0点,也就是查1天的
            c.set(Calendar.HOUR_OF_DAY, 0);
            SimpleDateFormat sdf_ldap = new SimpleDateFormat("yyyyMMddHHmmss");
            String syncDate = sdf_ldap.format(c.getTime()) + "Z";
            Filter filter;
            Filter datefilter = Filter.createGreaterOrEqualFilter("modifytimestamp", syncDate);
            if (name != null && !"".equals(name)) {
                Filter userFilter = Filter.createEqualityFilter("uid", name);
                filter = Filter.createANDFilter(userFilter, datefilter);
            } else {
                filter = Filter.createANDFilter(datefilter);
            }

            LDAPConnection connection_prd = new LDAPConnection(conf.getSource().getHost(), conf.getSource().getPort(), conf.getSource().getDn(), conf.getSource().getPassword());

            List<LDAPConnection> targetConnList = new ArrayList<>();

            for (Server server : conf.getTarget()) {
                LDAPConnection targetConn = new LDAPConnection(server.getHost(), server.getPort(), server.getDn(), server.getPassword());
                targetConnList.add(targetConn);
            }

            SearchScope scope = SearchScope.SUB;
            /**
             *
             * LDAPSearchResults res = ld.search( MY_SEARCHBASE, LDAPConnection.SCOPE_SUB, MY_FILTER, new String[]{"*","createTimestamp","modifyTimestamp","creatorsName","modifiersName","subschemaSubentry"}, false );
             */
            SearchRequest searchRequest = new SearchRequest(dn, scope, filter, new String[]{"*", "createTimestamp", "modifyTimestamp", "creatorsName", "modifiersName"});
            SearchResult searchResult = connection_prd.search(searchRequest);

            System.out.println("查询结果数:" + searchResult.getEntryCount());

            if (searchResult.getEntryCount() <= 0) {
                return 0;
            }
            List<String> skip = Arrays.asList("createTimestamp", "modifyTimestamp", "creatorsName", "modifiersName");

            for (SearchResultEntry searchResultEntry : searchResult.getSearchEntries()) {

                Collection<Attribute> attributesOld = searchResultEntry.getAttributes();

                List<Attribute> attributes = new ArrayList<>();

                for (Attribute attribute : attributesOld) {
                    if (skip.contains(attribute.getName())) {
                        continue;
                    }
                    attributes.add(attribute);
                }

                Filter user_filter = Filter.createEqualityFilter("uid", searchResultEntry.getAttributeValue("uid"));
                SearchRequest user_searchRequest = new SearchRequest(dn, scope, user_filter);

                for (LDAPConnection targetConn : targetConnList) {
                    SearchResult user_searchResult = targetConn.search(user_searchRequest);
                    if (user_searchResult.getEntryCount() > 0) {
                        //如果有要提前删除
                        targetConn.delete(searchResultEntry.getDN());
                    }
                    try {
                        LDAPResult result = targetConn.add(searchResultEntry.getDN(), attributes);
                        LOGGER.info(searchResultEntry.getDN() + "更新结果" + result.getResultString());
                        updateCount++;
                    } catch (Exception err) {
                        System.out.println("更新失败:" + searchResultEntry.getDN());
                        err.printStackTrace();
                    }
                }

            }
            LOGGER.info("更新条数:" + updateCount);

        } catch (Exception e) {
            LOGGER.error("同步失败", e);
        }
        return updateCount;
    }

    private void initConf() {
        try {
            InputStream inputStream;
            String path = System.getProperty("user.dir") + "/app.yml";
            if (new File(path).exists()) {
                inputStream = new FileInputStream(path);
            } else {
                inputStream = App.class.getResourceAsStream("/app.yml");
            }
            Yaml yaml = new Yaml(new Constructor(Conf.class));
            conf = yaml.load(inputStream);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

}

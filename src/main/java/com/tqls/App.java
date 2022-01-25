package com.tqls;

import com.tqls.entity.Conf;
import com.unboundid.ldap.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 */
public class App {

    private Conf conf;

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        App app = new App();
        app.initConf();
        long period  = app.conf.getPeriod()*1000*60;
        final String date = null;
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sync(date);
            }
        }, 1000,period);


    }

    public static void login() {
        LDAPAuthentication ldapAuthentication = new LDAPAuthentication();
        ldapAuthentication.authenricate("025032", "");
    }

    public static void sync(String dateStr) {

//        List<SearchResultEntry> result = new ArrayList<>();
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
            Filter filter = Filter.createGreaterOrEqualFilter("modifytimestamp", syncDate);


            LDAPConnection connection_prd = new LDAPConnection("*", 1389, "cn=Directory Manager", "");

            LDAPConnection connection = new LDAPConnection("*", 389, "cn=Directory Manager", "");

            SearchScope scope = SearchScope.SUB;
            /**
             *
             * LDAPSearchResults res = ld.search( MY_SEARCHBASE, LDAPConnection.SCOPE_SUB, MY_FILTER, new String[]{"*","createTimestamp","modifyTimestamp","creatorsName","modifiersName","subschemaSubentry"}, false );
             */
            SearchRequest searchRequest = new SearchRequest(dn, scope, filter, new String[]{"*", "createTimestamp", "modifyTimestamp", "creatorsName", "modifiersName"});
            SearchResult searchResult = connection_prd.search(searchRequest);
            System.out.println("查询结果数:" + searchResult.getEntryCount());
            int updateCount = 0;
            if (searchResult.getEntryCount() <= 0) {
                return;
            }
            List<String> skip = Arrays.asList("createTimestamp", "modifyTimestamp", "creatorsName", "modifiersName");

            for (SearchResultEntry searchResultEntry : searchResult.getSearchEntries()) {

                Collection<Attribute> attributesOld = searchResultEntry.getAttributes();

                List<Attribute> attributes = new ArrayList<>();

                for (Attribute attribute : attributesOld) {
                    if (skip.contains(attribute.getName())) continue;
                    attributes.add(attribute);
                }
                //Attribute(name=modifyTimestamp, values={'20220119052735Z'})


                Filter user_filter = Filter.createEqualityFilter("uid", searchResultEntry.getAttributeValue("uid"));
                SearchRequest user_searchRequest = new SearchRequest(dn, scope, user_filter);
                SearchResult user_searchResult = connection.search(user_searchRequest);
                if (user_searchResult.getEntryCount() > 0) {
                    //如果有要提前删除
                    connection.delete(searchResultEntry.getDN());
                }
                try {
                    LDAPResult result = connection.add(searchResultEntry.getDN(), attributes);
                    System.out.println(searchResultEntry.getDN() + "更新结果" + result.getResultString());
                    updateCount++;
                } catch (Exception err) {
                    System.out.println("更新失败:" + searchResultEntry.getDN());
                    err.printStackTrace();

                }

            }
            System.out.println("更新条数:" + updateCount);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initConf() {
        try (InputStream inputStream = App.class.getResourceAsStream("/app.yml")) {
            Yaml yaml = new Yaml(new Constructor(Conf.class));
            conf = yaml.load(inputStream);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

}

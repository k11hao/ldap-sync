package com.tqls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

public class LDAPAuthentication {

    private static final Logger logger = LoggerFactory.getLogger(LDAPAuthentication.class);

    private final String URL = "ldap://192.168.50.135:1389/";
    private final String BASEDN = "ou=People,dc=tqls,dc=cn";
    private final String FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private LdapContext ctx = null;
    private final Control[] connCtls = null;

    private void LDAP_connect() {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
        env.put(Context.PROVIDER_URL, URL + BASEDN);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");

        String root = "cn=Directory Manager";// root
        env.put(Context.SECURITY_PRINCIPAL, root);
        env.put(Context.SECURITY_CREDENTIALS, "tqls");
        // 此处若不指定用户名和密码,则自动转换为匿名登录
        try {
            ctx = new InitialLdapContext(env, connCtls);
        } catch (javax.naming.AuthenticationException e) {
            System.out.println("验证失败：" + e.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getUserDN(String uid) {
        String userDN = "";
        LDAP_connect();
        try {
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            NamingEnumeration<SearchResult> en = ctx.search("", "uid=" + uid, constraints);
            if (en == null || !en.hasMoreElements()) {
                System.out.println("未找到该用户");
            }
            // maybe more than one element
            while (en != null && en.hasMoreElements()) {
                Object obj = en.nextElement();
                if (obj instanceof SearchResult) {
                    SearchResult si = (SearchResult) obj;
                    userDN += si.getName();
                    userDN += "," + BASEDN;
                } else {
                    logger.info(obj.toString());
                }
            }
        } catch (Exception e) {
            logger.error("查找用户时产生异常。", e);
            e.printStackTrace();
        }

        return userDN;
    }

    public boolean authenricate(String UID, String password) {
        boolean valide = false;
        String userDN = getUserDN(UID);

        try {
            ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, userDN);
            ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
            ctx.reconnect(connCtls);
            System.out.println(userDN + " 验证通过");
            valide = true;
        } catch (AuthenticationException e) {
            System.out.println(userDN + " 验证失败");
            System.out.println(e.toString());
            valide = false;
        } catch (NamingException e) {
            System.out.println(userDN + " 验证失败");
            valide = false;
        }

        return valide;
    }
}
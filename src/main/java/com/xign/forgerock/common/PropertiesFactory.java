/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.xign.forgerock.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;

/**
 *
 * @author palle
 */
public class PropertiesFactory {

    private static Properties properties;
    private static String xignInScript;
    private static ByteArrayOutputStream outputStream;

    public static Properties getProperties(String path) throws FileNotFoundException, IOException {
        return loadProperties(path);
    }

    public static InputStream getPropertiesAsInputStream(String path) throws IOException {
        
        if (outputStream == null) {
            outputStream = new ByteArrayOutputStream();
            loadProperties(path).store(outputStream, "");
        }

        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    public static String getScript(String managerUrl, String redirectUri, String clientId) throws IOException {
        if (xignInScript == null) {
            xignInScript = loadScript(managerUrl, redirectUri, clientId);
        }
        return xignInScript;
    }

    private static Properties loadProperties(String path) throws IOException {
        if (properties == null) {
            InputStream in = new FileInputStream(path);
            properties = new Properties();
            properties.load(in);
            in.close();
        }
        return properties;
    }

    private static String loadScript(String managerUrl, String redirectUri, String clientId) throws IOException {
        HttpClientBuilder builder = HttpClients.custom();
        builder.setRedirectStrategy(new LaxRedirectStrategy());
        CloseableHttpClient client = builder.build();
        HttpGet get = new HttpGet(managerUrl + "/js/v3/xignin-v3-forgerock.js");
        CloseableHttpResponse response = client.execute(get);
        byte[] scr = IOUtils.toByteArray(response.getEntity().getContent());
        String script = new String(scr, "UTF-8");
        return script.replace("###manager.url###", managerUrl)
                .replace("###redirectUri###", redirectUri)
                .replace("###clientId###", clientId);

    }
}

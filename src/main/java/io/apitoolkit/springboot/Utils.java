package io.apitoolkit.springboot;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

public class Utils {

    public static HashMap<String, Object> redactHeaders(HashMap<String, Object> headers, List<String> redactedHeaders) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }
        HashMap<String, Object> redactedHeadersMap = new HashMap<>(headers);
        if (redactedHeaders == null || redactedHeaders.isEmpty()) {
            return headers;
        }
        for (String headerName : headers.keySet()) {
            if (redactedHeaders.contains(headerName) || redactedHeaders.contains(headerName.toLowerCase())) {
                redactedHeadersMap.put(headerName, "[CLIENT_REDACTED]");
            }
        }
        return redactedHeadersMap;
    }

    public static byte[] redactJson(byte[] data, List<String> jsonPaths, Boolean debug) {
        if (jsonPaths == null || jsonPaths.isEmpty() || data.length == 0) {
            return data;
        }
        try {

            String jsonData = new String(data, StandardCharsets.UTF_8);
            DocumentContext jsonObject = JsonPath.parse(jsonData);
            for (String path : jsonPaths) {
                try {
                    jsonObject = jsonObject.set(path, "[CLIENT_REDACTED]");
                } catch (Exception e) {
                    if (debug) {
                        e.printStackTrace();
                    }
                }
            }
            String redactedJson = jsonObject.jsonString();
            return redactedJson.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (debug) {
                e.printStackTrace();
            }
            return data;
        }

    }

    public static HashMap<String, Object> getPathParamsFromPattern(String pattern, String path) {
        HashMap<String, Object> pathParams = new HashMap<>();
        String[] pathParts = path.split("/");
        for (int i = 0; i < pathParts.length; i++) {
            String pathPart = pathParts[i];
            String patternPart = pattern.split("/")[i];
            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                String paramName = patternPart.substring(1, patternPart.length() - 1);
                pathParams.put(paramName, pathPart);
            }
        }
        return pathParams;
    }
}

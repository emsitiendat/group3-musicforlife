package com.example.musicforlife;

public class Utils {
    public static String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty() || rawUrl.equals("null")) {
            return "";
        }

        String cleanedUrl = rawUrl.replace("\\", "/").replaceAll(" ", "%20");
        String baseUrl = RetrofitClient.BASE_URL;

        if (cleanedUrl.contains("127.0.0.1") || cleanedUrl.contains("localhost")) {
            cleanedUrl = cleanedUrl.replaceFirst("http://127\\.0\\.0\\.1(:\\d+)?/", baseUrl);
            cleanedUrl = cleanedUrl.replaceFirst("http://localhost(:\\d+)?/", baseUrl);
        }
        else if (cleanedUrl.startsWith("http")) {
            if (!cleanedUrl.contains("10.0.2.2") && !cleanedUrl.contains("192.168.")) {
                cleanedUrl = cleanedUrl.replace("http://", "https://");
            }
        }
        else {
            if (cleanedUrl.startsWith("/")) {
                cleanedUrl = cleanedUrl.substring(1);
            }
            cleanedUrl = baseUrl + cleanedUrl;
        }

        return cleanedUrl;
    }
}
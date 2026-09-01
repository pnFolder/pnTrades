package ru.privatenull.update;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubUpdateClient {
    static final String DEFAULT_DOWNLOAD_URL = "https://github.com/pnFolder/pnTrades/releases/latest";
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"v?([^\"]+)\"");
    private static final Pattern DOWNLOAD = Pattern.compile("\"downloadUrl\"\\s*:\\s*\"([^\"]+)\"");

    UpdateInfo fetchLatest() throws Exception {
        String manifest = fetch("https://raw.githubusercontent.com/pnFolder/pnTrades/master/update-manifest.json");
        Matcher version = VERSION.matcher(manifest);
        if (!version.find()) return new UpdateInfo(null, DEFAULT_DOWNLOAD_URL);
        Matcher download = DOWNLOAD.matcher(manifest);
        return new UpdateInfo(version.group(1), download.find() ? download.group(1).replace("\\/", "/") : DEFAULT_DOWNLOAD_URL);
    }

    private String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "pnTrades UpdateChecker");
        try {
            if (connection.getResponseCode() / 100 != 2) {
                throw new IllegalStateException("GitHub вернул HTTP " + connection.getResponseCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append('\n');
                return body.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
}

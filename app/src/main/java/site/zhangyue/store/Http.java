package site.zhangyue.store;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Http — 极简 HTTP 工具。 */
public final class Http {
    private Http() {}

    public static byte[] bytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "LetianStore/3.0 (Android)");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** 静默拉取文本：任何失败返回 null，不抛异常（后台探测用）。 */
    public static String quiet(String url) {
        try {
            return new String(bytes(url), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}

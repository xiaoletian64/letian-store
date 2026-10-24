package site.zhangyue.store;

/**
 * StoreConfig — 构建时注入的商店配置（这是本工程唯一的"私有"文件）。
 *
 * 本工程以开源形态发布：开源仓库里所有部署相关的地址、账号均为占位符。
 * 真实值由 build_store.sh 在打包阶段注入（见 repo 根的 build_store.sh 与
 * config/store.properties.example）：
 *
 *   sed -e "s|__STORE_INDEX__|https://your.host/apps/apps.json|" ...
 *
 * 这样任何人都可以 fork 框架 + 一份自己的 properties 打出完全自己的商店，
 * 而仓库里不留任何个人服务器信息。
 */
public final class StoreConfig {
    /** 应用目录清单（apps.json）地址。 */
    public static final String INDEX_URL = "__STORE_INDEX__";
    /** 网页版商店（详情页里"网页打开"），留空则隐藏入口。 */
    public static final String WEB_URL = "__STORE_WEB__";
    /** 商店自身的大文件下载域（提示与分享用），留空则用 INDEX_URL 推导。 */
    public static final String CDN_BASE = "__STORE_CDN__";
    /** 商店显示名。 */
    public static final String STORE_NAME = "__STORE_NAME__";
    /** 自更新清单 slug：apps.json 里代表商店自己的那个应用。 */
    public static final String SELF_SLUG = "__SELF_SLUG__";

    private StoreConfig() {}

    /** 兜底：任何字段没被注入（保持占位符）时返回 null，让代码走运行时覆盖逻辑。 */
    public static String clean(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty() || v.startsWith("__") && v.endsWith("__")) return null;
        return v;
    }
}

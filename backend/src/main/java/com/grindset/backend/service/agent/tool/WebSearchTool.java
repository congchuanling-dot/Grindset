package com.grindset.backend.service.agent.tool;

import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网搜索。抓取 Bing 搜索结果页并解析出标题、链接、摘要。
 *
 * <p>不依赖第三方搜索 API key;代价是解析基于页面结构,对方改版时需要同步调整正则。
 * 搜索结果只作为参考资料回填给模型,不落库。</p>
 */
@Component
public class WebSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    private static final String SEARCH_ENDPOINT = "https://cn.bing.com/search?setlang=zh-CN&q=";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** 单次最多回填给模型的结果条数 */
    private static final int MAX_LIMIT = 8;
    /** 摘要截断长度,避免整页文本撑爆上下文 */
    private static final int MAX_SNIPPET = 300;

    private static final Pattern BLOCK_SPLIT = Pattern.compile("class=\"b_algo\"");
    private static final Pattern TITLE_LINK =
            Pattern.compile("<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern SNIPPET = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final HttpClient httpClient;

    public WebSearchTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String label() {
        return "联网搜索";
    }

    @Override
    public String description() {
        return "在互联网上搜索资料。参数:query(搜索关键词,必填)、limit(返回条数,默认5,最多8)。"
                + "返回网页标题、链接和摘要。适合查官方文档、版本号、报错原因、技术方案等站外信息;"
                + "查用户自己的学习数据请用其他工具。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索关键词"),
                        "limit", Map.of("type", "integer",
                                "description", "返回条数,默认 5,最多 8")),
                "required", List.of("query"));
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        String query = AgentToolArgs.string(args, "query");
        if (query == null) {
            return new AgentToolResult(false, "缺少搜索关键词",
                    Map.of("hint", "请提供 query 参数后重试"));
        }
        int limit = AgentToolArgs.integer(args, "limit", 5, 1, MAX_LIMIT);

        List<Map<String, String>> items;
        try {
            items = search(query, limit);
        } catch (IOException e) {
            log.warn("联网搜索失败 query={}", query, e);
            return new AgentToolResult(false, "联网搜索失败",
                    Map.of("hint", "网络不可用或搜索被限流,请如实告知用户暂时无法联网"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentToolResult(false, "联网搜索被中断", Map.of("hint", "请重试"));
        }

        if (items.isEmpty()) {
            return new AgentToolResult(false, "没有搜到相关网页",
                    Map.of("hint", "可以换更具体的关键词再搜一次,或直接告知用户未找到"));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("items", items);
        return new AgentToolResult(true, String.format("搜到 %d 条网页结果", items.size()), data);
    }

    private List<Map<String, String>> search(String query, int limit)
            throws IOException, InterruptedException {
        String url = SEARCH_ENDPOINT + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("搜索服务返回 " + response.statusCode());
        }
        return parse(response.body(), limit);
    }

    private List<Map<String, String>> parse(String html, int limit) {
        List<Map<String, String>> items = new ArrayList<>();
        String[] blocks = BLOCK_SPLIT.split(html);
        for (int i = 1; i < blocks.length && items.size() < limit; i++) {
            Matcher title = TITLE_LINK.matcher(blocks[i]);
            if (!title.find()) {
                continue;
            }
            String link = htmlDecode(title.group(1)).strip();
            String name = plainText(title.group(2), 120);
            if (link.isEmpty() || name.isEmpty()) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("title", name);
            item.put("url", link);
            Matcher snippet = SNIPPET.matcher(blocks[i]);
            item.put("snippet", snippet.find() ? plainText(snippet.group(1), MAX_SNIPPET) : "");
            items.add(item);
        }
        return items;
    }

    /** 去标签、解实体、压缩空白 */
    private static String plainText(String html, int maxLength) {
        String text = TAG.matcher(html).replaceAll(" ");
        text = SPACES.matcher(htmlDecode(text)).replaceAll(" ").strip();
        return text.length() > maxLength ? text.substring(0, maxLength) + "…" : text;
    }

    /** 只处理搜索摘要里常见的实体,够用即可 */
    static String htmlDecode(String text) {
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }
}

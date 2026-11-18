package com.grindset.backend.service.agent.tool;

import com.grindset.backend.service.agent.AgentTool;
import com.grindset.backend.service.agent.AgentToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取指定网页正文。通常配合 web_search 使用:先搜到链接,再挑一两条细读。
 *
 * <p>只做纯文本抽取,不执行 JS,因此对前端渲染型页面只能拿到骨架内容。
 * 网络层限制:超时 15 秒、最多读 1.5MB、拒绝内网地址。</p>
 */
@Component
public class FetchUrlTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FetchUrlTool.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_BYTES = 1_500_000;
    /** 回填给模型的正文长度上限 */
    private static final int MAX_CHARS = 4000;

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script.*?</script>|<style.*?</style>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final HttpClient httpClient;

    public FetchUrlTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "fetch_url";
    }

    @Override
    public String label() {
        return "读取网页";
    }

    @Override
    public String description() {
        return "读取指定网页的正文纯文本(最多 4000 字)。参数:url(必须是以 http:// 或 https:// 开头的完整链接)。"
                + "用于在搜索后深入了解某篇文档或文章;只返回文字,不含图片和脚本。";
    }

    @Override
    public AgentToolResult execute(long userId, Map<String, Object> args) {
        String raw = AgentToolArgs.string(args, "url");
        if (raw == null) {
            return new AgentToolResult(false, "缺少网页链接", Map.of("hint", "请提供 url 参数"));
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            return new AgentToolResult(false, "链接格式不正确", Map.of("hint", "请提供完整的 http(s) 链接"));
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return new AgentToolResult(false, "只支持 http/https 链接", Map.of("hint", "跳过该链接"));
        }
        if (isInternal(uri.getHost())) {
            return new AgentToolResult(false, "不允许访问内网地址", Map.of("hint", "换一个公开网页"));
        }

        String html;
        try {
            html = fetch(uri);
        } catch (UnknownHostException e) {
            return new AgentToolResult(false, "域名无法解析", Map.of("hint", "链接可能失效,换一条结果"));
        } catch (IOException e) {
            log.warn("读取网页失败 url={}", raw, e);
            return new AgentToolResult(false, "网页读取失败", Map.of("hint", "可能超时或被目标站拦截,换一条结果"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentToolResult(false, "网页读取被中断", Map.of("hint", "请重试"));
        }

        String text = extractText(html);
        if (text.length() < 40) {
            return new AgentToolResult(false, "页面没有可读正文",
                    Map.of("hint", "该页可能是前端渲染或需要登录,换一条搜索结果"));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", raw);
        data.put("title", extractTitle(html));
        data.put("text", text);
        return new AgentToolResult(true, String.format("读取网页成功,正文 %d 字", text.length()), data);
    }

    private String fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .GET()
                .build();
        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() >= 400) {
                throw new IOException("目标页返回 " + response.statusCode());
            }
            return new String(in.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
        }
    }

    /** 拦截 localhost、私有网段、链路本地地址,避免被诱导访问内网服务 */
    private static boolean isInternal(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String lower = host.toLowerCase();
        if (lower.equals("localhost") || lower.endsWith(".local") || lower.endsWith(".internal")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            // 解析不了交给 fetch 阶段报错,这里不提前拦截
            return false;
        }
        return false;
    }

    private static String extractTitle(String html) {
        Matcher matcher = TITLE.matcher(html);
        return matcher.find() ? plainText(matcher.group(1), 120) : "";
    }

    private static String extractText(String html) {
        String body = SCRIPT.matcher(html).replaceAll(" ");
        String text = TAG.matcher(body).replaceAll(" ");
        text = WebSearchTool.htmlDecode(text);
        text = SPACES.matcher(text).replaceAll(" ").strip();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "…" : text;
    }

    private static String plainText(String html, int maxLength) {
        String text = SPACES.matcher(WebSearchTool.htmlDecode(TAG.matcher(html).replaceAll(" ")))
                .replaceAll(" ").strip();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}

#!/usr/bin/env python3
"""抓取 xiaolincoding 面试题正文 + 配图，输出 Markdown 与 JSON。

用法:
    python scripts/crawl_xiaolincoding.py
    python scripts/crawl_xiaolincoding.py <url> <输出目录>
    python scripts/crawl_xiaolincoding.py <url> <输出目录> --no-images

仅供个人学习离线阅读使用。
"""

import hashlib
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup
from markdownify import markdownify

DEFAULT_URL = "https://www.xiaolincoding.com/interview/java.html"
DEFAULT_OUT = Path(__file__).resolve().parent / "output" / "xiaolincoding-java"
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
EXT_BY_CT = {
    "image/png": ".png",
    "image/jpeg": ".jpg",
    "image/gif": ".gif",
    "image/webp": ".webp",
    "image/svg+xml": ".svg",
    "image/avif": ".avif",
}


def safe_name(text, limit=60):
    cleaned = re.sub(r'[\\/:*?"<>|\s]+', "_", text).strip("_")
    return (cleaned or "untitled")[:limit]


def fetch(session, url, **kwargs):
    last_error = None
    for attempt in range(3):
        try:
            resp = session.get(url, timeout=25, **kwargs)
            resp.raise_for_status()
            if "charset" not in resp.headers.get("Content-Type", "").lower():
                # 服务端没声明 charset 时 requests 会退回 ISO-8859-1，导致中文乱码
                resp.encoding = "utf-8"
            return resp
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            time.sleep(1.5 * (attempt + 1))
    raise last_error


def save_image(session, abs_url, referer, img_dir):
    """下载图片到本地，失败时原样返回远程地址。"""
    path = urlparse(abs_url).path
    stem = Path(path).stem or "img"
    ext = Path(path).suffix
    digest = hashlib.md5(abs_url.encode()).hexdigest()[:8]
    try:
        resp = fetch(session, abs_url, headers={"Referer": referer})
    except Exception:  # noqa: BLE001
        print(f"  [warn] 图片下载失败，保留远程地址: {abs_url}")
        return abs_url
    if not ext:
        content_type = resp.headers.get("Content-Type", "").split(";")[0].strip()
        ext = EXT_BY_CT.get(content_type, ".png")
    filename = f"{safe_name(stem)}_{digest}{ext}"
    (img_dir / filename).write_bytes(resp.content)
    time.sleep(0.15)
    return f"images/{filename}"


def find_content(soup):
    for selector in (
        ".theme-default-content",
        "#markdown-content",
        ".markdown-content",
        ".markdown-body",
        "[class*='vp-doc']",
        "article",
        "main",
        "#app",
    ):
        node = soup.select_one(selector)
        if node is not None:
            return node
    return None


def prepare_content(content):
    """去掉锚点、行号列、面包屑、导航等噪音，并把代码块语言搬到 <code> 上。"""
    for selector in (
        ".vp-page-title",
        ".page-info",
        ".vp-breadcrumb",
        ".vp-page-meta",
        ".vp-page-nav",
        ".page-nav",
        ".page-edit",
        ".line-numbers-wrapper",
        "script",
        "style",
        "noscript",
    ):
        for node in content.select(selector):
            node.decompose()

    # Mermaid 图表由浏览器懒加载渲染，静态 HTML 里只有占位符，标注出来避免误读
    for node in content.select(".mermaid-lazy-placeholder"):
        node.replace_with("> （Mermaid 图表，静态页面不含源码，未抓取）")

    # xiaolincoding 的锚点是独立 "#"，直接删；JavaGuide 把标题文字包在锚点里，需拆壳
    for heading in content.find_all(["h1", "h2", "h3", "h4", "h5", "h6"]):
        for anchor in heading.select("a.header-anchor"):
            if anchor.get_text(strip=True) in ("", "#"):
                anchor.decompose()
            else:
                anchor.unwrap()

    for block in content.select("div[class*='language-']"):
        match = re.search(r"language-([\w+#.-]+)", " ".join(block.get("class", [])))
        if not match:
            continue
        language = match.group(1)
        for code in block.find_all("code"):
            code["class"] = [f"language-{language}"]
        for pre in block.find_all("pre"):
            pre["class"] = [f"language-{language}"]


def download_images(session, content, referer, img_dir):
    img_dir.mkdir(parents=True, exist_ok=True)
    cache = {}
    for img in content.find_all("img"):
        src = img.get("data-src") or img.get("src") or ""
        if not src or src.startswith("data:"):
            continue
        img.attrs.pop("data-src", None)
        abs_url = urljoin(referer, src)
        if abs_url not in cache:
            cache[abs_url] = save_image(session, abs_url, referer, img_dir)
        img["src"] = cache[abs_url]
    return cache


def split_sections(markdown_text, page_title):
    """按 h2（分类）/ h3（题目）切分。"""
    sections = []
    category, question, buffer = "前言", page_title, []

    def push():
        body = "\n".join(buffer).strip()
        if body or question:
            sections.append((category, question, body))

    for line in markdown_text.splitlines():
        if line.startswith("## "):
            push()
            category, question, buffer = line[3:].strip(), None, []
        elif line.startswith("### "):
            push()
            question, buffer = line[4:].strip(), []
        elif line.startswith("# "):
            continue
        else:
            buffer.append(line)
    push()
    return sections


def code_language(pre):
    """markdownify 只接受回调，从 <pre>/父级 class 里取 language-xxx。"""
    for node in (pre, pre.parent):
        if node is None:
            continue
        for name in node.get("class") or []:
            match = re.search(r"language-([\w+#.-]+)", name)
            if match:
                return match.group(1)
    return None


def main(argv):
    url = DEFAULT_URL
    out_dir = DEFAULT_OUT
    with_images = True

    args = [a for a in argv[1:] if not a.startswith("--")]
    flags = {a for a in argv[1:] if a.startswith("--")}
    if args:
        url = args[0]
    if len(args) > 1:
        out_dir = Path(args[1])
    if "--no-images" in flags:
        with_images = False

    out_dir = out_dir.resolve()
    img_dir = out_dir / "images"

    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Referer": url})

    print(f"抓取: {url}")
    html = fetch(session, url).text
    soup = BeautifulSoup(html, "html.parser")

    content = find_content(soup)
    if content is None:
        sys.exit("未找到正文容器，页面结构可能已变化")

    prepare_content(content)

    cache = {}
    if with_images:
        print("下载图片 ...")
        cache = download_images(session, content, url, img_dir)
    else:
        for img in content.find_all("img"):
            src = img.get("data-src") or img.get("src") or ""
            if src:
                img["src"] = urljoin(url, src)
                img.attrs.pop("data-src", None)

    title_node = content.find("h1") or soup.find("h1")
    page_title = title_node.get_text(strip=True) if title_node else "未命名"
    markdown_text = markdownify(
        str(content), heading_style="ATX", code_language_callback=code_language
    )

    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "all.md").write_text(
        f"# {page_title}\n\n{markdown_text}\n", encoding="utf-8"
    )

    sections = split_sections(markdown_text, page_title)
    records = []
    for index, (category, question, body) in enumerate(sections, 1):
        if not body and not question:
            continue
        filename = f"{index:03d}_{safe_name(question or category)}.md"
        heading = (
            f"# {question}\n\n> 分类：{category}\n\n" if question else f"# {category}\n\n"
        )
        (out_dir / filename).write_text(heading + body + "\n", encoding="utf-8")
        records.append(
            {
                "index": index,
                "category": category,
                "question": question,
                "file": filename,
                "markdown": body,
            }
        )

    (out_dir / "questions.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print(
        f"完成: {len(records)} 个条目, {len(cache)} 张图片, 正文 {len(markdown_text)} 字符"
    )
    print(f"输出目录: {out_dir}")


if __name__ == "__main__":
    main(sys.argv)

#!/usr/bin/env python3
"""抓取小林coding全部面试题并导入 Grindset 题库。

流程:
  1. 逐页抓取 /interview/*.html，按 h2/h3 切出「题目 + 参考答案」
  2. 配图下载后上传到 OSS（走后端 /api/admin/questions/images），正文图片链接改写为后端代理地址
  3. 分批调用 /api/admin/questions/import 写入题库，按题干哈希幂等

用法:
  python scripts/import_xiaolincoding.py --admin-token <token>
  python scripts/import_xiaolincoding.py --admin-token <token> --pages java,mysql
  python scripts/import_xiaolincoding.py --dry-run
  python scripts/import_xiaolincoding.py --admin-token <token> --no-images

依赖: requests / beautifulsoup4 / markdownify（与 crawl_xiaolincoding.py 相同）。
"""

import argparse
import json
import mimetypes
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from markdownify import markdownify

sys.path.insert(0, str(Path(__file__).resolve().parent))
from crawl_xiaolincoding import (  # noqa: E402
    code_language,
    download_images,
    fetch,
    find_content,
    prepare_content,
    split_sections,
)

SITE = "https://www.xiaolincoding.com"
OUT_DIR = Path(__file__).resolve().parent / "output" / "xiaolincoding-import"
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

# (领域, 页面 slug, 题型, 难度)
PAGES = [
    ("Java基础", "java", "BASICS", 2),
    ("Java集合", "collections", "BASICS", 2),
    ("Java并发", "juc", "BASICS", 2),
    ("JVM", "jvm", "BASICS", 2),
    ("Spring", "spring", "BASICS", 2),
    ("C++", "cpp", "BASICS", 2),
    ("Golang", "golang", "BASICS", 2),
    ("Python", "python", "BASICS", 2),
    ("MySQL", "mysql", "BASICS", 2),
    ("Redis", "redis", "BASICS", 2),
    ("计算机网络", "network", "BASICS", 2),
    ("操作系统", "os", "BASICS", 2),
    ("数据结构与算法", "data", "BASICS", 2),
    ("消息队列", "mq", "SCENARIO", 2),
    ("分布式", "cap", "SCENARIO", 3),
    ("系统设计", "systemdesign", "SCENARIO", 3),
    ("Linux", "linux", "BASICS", 1),
    ("Git", "git", "BASICS", 1),
    ("Docker", "docker", "BASICS", 1),
    ("业务测试", "business_testing", "BASICS", 2),
    ("Python自动化测试", "python_automation", "BASICS", 2),
    ("Java自动化测试", "java_automation", "BASICS", 2),
    ("性能测试", "performance_testing", "BASICS", 2),
    ("后端进阶", "win", "SCENARIO", 3),
]

SKIP_QUESTIONS = {"写在最后", "参考", "参考资料", "前言", "总结", "目录"}


def clean_question(text):
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text or "")
    text = text.replace("`", "").replace("**", "").strip("*_ ")
    text = re.sub(r"^\d+[.、]\s*", "", text)
    return re.sub(r"\s+", " ", text).strip()


def truncate_bytes(text, limit=60000):
    """question_bank.reference_answer 是 TEXT，按 UTF-8 字节截断，避免插入失败。"""
    data = text.encode("utf-8")
    if len(data) <= limit:
        return text
    return data[:limit].decode("utf-8", "ignore") + "\n\n> （内容过长，已截断）"


def image_paths(body):
    return set(re.findall(r"!\[[^\]]*\]\((images/[^)]+)\)", body)) | set(
        re.findall(r'src="(images/[^"]+)"', body)
    )


def should_skip(question, category):
    if not question:
        return True
    if category == "前言":
        return True
    return question in SKIP_QUESTIONS


def split_by_h2(markdown_text):
    """没有 h3 的页面：每个 h2 本身就是一道题。"""
    sections = []
    question, buffer = None, []

    def push():
        body = "\n".join(buffer).strip()
        if question and body:
            sections.append((question, body))

    for line in markdown_text.splitlines():
        if line.startswith("## "):
            push()
            question, buffer = line[3:].strip(), []
        elif line.startswith("# "):
            continue
        else:
            buffer.append(line)
    push()
    return sections


def rewrite_images(body, local_to_url):
    def md_repl(match):
        alt, local = match.group(1), match.group(2)
        return f"![{alt}]({local_to_url.get(local, local)})"

    def html_repl(match):
        local = match.group(1)
        return f'src="{local_to_url.get(local, local)}"'

    body = re.sub(r"!\[([^\]]*)\]\((images/[^)]+)\)", md_repl, body)
    return re.sub(r'src="(images/[^"]+)"', html_repl, body)


def crawl_page(session, domain, slug, qtype, difficulty, images_dir, with_images):
    url = f"{SITE}/interview/{slug}.html"
    html = fetch(session, url).text
    content = find_content(BeautifulSoup(html, "html.parser"))
    if content is None:
        raise RuntimeError(f"{slug}: 未找到正文容器")

    prepare_content(content)
    has_h3 = bool(content.find_all("h3"))

    cache = {}
    if with_images:
        cache = download_images(session, content, url, images_dir)
    else:
        for img in content.find_all("img"):
            src = img.get("data-src") or img.get("src") or ""
            if src:
                img["src"] = urljoin(url, src)
                img.attrs.pop("data-src", None)

    title_node = content.find("h1")
    page_title = title_node.get_text(strip=True) if title_node else domain
    markdown_text = markdownify(
        str(content), heading_style="ATX", code_language_callback=code_language
    )

    if has_h3:
        raw = [(category, question, body) for category, question, body in
               split_sections(markdown_text, page_title) if question]
    else:
        raw = [(domain, question, body) for question, body in split_by_h2(markdown_text)]

    local_to_remote = {local: remote for remote, local in cache.items()}
    records = []
    for category, question, body in raw:
        question = clean_question(question)
        body = (body or "").strip()
        if should_skip(question, category) or not body:
            continue
        context = f"> 分类：{domain}" + (f" · {category}" if category and category != domain else "")
        records.append(
            {
                "title": question[:200],
                "content": f"{question}\n\n{context}",
                "type": qtype,
                "difficulty": difficulty,
                "domain": domain,
                "referenceAnswer": truncate_bytes(body),
                "keyPoints": [],
                "kpNames": [],
                "source": "CRAWL",
                "sourceUrl": url,
            }
        )
    return records, local_to_remote, len(cache)


def upload_image(api, base_url, token, path, remote_fallback):
    content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    try:
        with path.open("rb") as handle:
            resp = api.post(
                f"{base_url}/api/admin/questions/images",
                headers={"X-Admin-Token": token},
                files={"file": (path.name, handle, content_type)},
                timeout=90,
            )
        resp.raise_for_status()
        payload = resp.json()
        if payload.get("code") != 0:
            raise RuntimeError(payload.get("message") or "上传失败")
        return payload["data"]["url"]
    except Exception as exc:  # noqa: BLE001
        print(f"    [warn] 图片上传失败，保留远程地址: {path.name} ({exc})")
        return remote_fallback


def import_batch(api, base_url, token, items):
    last_error = None
    for attempt in range(3):
        try:
            resp = api.post(
                f"{base_url}/api/admin/questions/import",
                headers={"X-Admin-Token": token},
                json={"questions": items},
                timeout=180,
            )
            resp.raise_for_status()
            payload = resp.json()
            if payload.get("code") != 0:
                raise RuntimeError(payload.get("message") or "导入失败")
            return payload["data"]
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"导入失败: {last_error}")


def main(argv):
    parser = argparse.ArgumentParser(description="抓取小林coding面试题并导入题库")
    parser.add_argument("--admin-token", default=os.environ.get("ADMIN_TOKEN", ""))
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--pages", default="all", help="逗号分隔的 slug，默认 all")
    parser.add_argument("--dry-run", action="store_true", help="只抓取生成 JSON，不传图不导入")
    parser.add_argument("--no-images", action="store_true", help="不下载/上传图片，保留远程地址")
    parser.add_argument("--output", default=str(OUT_DIR))
    parser.add_argument("--batch-size", type=int, default=40)
    args = parser.parse_args(argv[1:])

    base_url = args.base_url.rstrip("/")
    out_dir = Path(args.output).resolve()
    images_dir = out_dir / "images"
    out_dir.mkdir(parents=True, exist_ok=True)

    selected = {x.strip() for x in args.pages.split(",") if x.strip()}
    pages = [p for p in PAGES if args.pages == "all" or p[1] in selected]
    if not pages:
        sys.exit(f"没有匹配的页面: {args.pages}")

    if not args.dry_run and not args.admin_token:
        sys.exit("缺少管理令牌：请传 --admin-token 或设置 ADMIN_TOKEN")

    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Referer": f"{SITE}/interview/"})
    api = requests.Session()
    api.headers.update({"User-Agent": UA})

    all_records = []
    page_summaries = []
    seen_content = set()
    image_urls = {}  # path.name -> url
    with_images = not (args.dry_run or args.no_images)

    for index, (domain, slug, qtype, difficulty) in enumerate(pages, 1):
        print(f"[{index}/{len(pages)}] 抓取 {domain} ({slug}) ...")
        try:
            records, local_to_remote, image_count = crawl_page(
                session, domain, slug, qtype, difficulty, images_dir, with_images
            )
        except Exception as exc:  # noqa: BLE001
            print(f"  [error] {domain} 抓取失败: {exc}")
            page_summaries.append({"domain": domain, "slug": slug, "error": str(exc)})
            continue

        # 图片上传（同一次运行内按文件名去重）
        local_to_url = {}
        if with_images and not args.dry_run:
            for local in {local for rec in records for local in image_paths(rec["referenceAnswer"])}:
                path = out_dir / local
                if not path.exists():
                    continue
                name = path.name
                if name not in image_urls:
                    image_urls[name] = upload_image(
                        api, base_url, args.admin_token, path, local_to_remote.get(local, "")
                    )
                    time.sleep(0.05)
                local_to_url[local] = image_urls[name]

        imported = []
        for record in records:
            if local_to_url:
                record["referenceAnswer"] = rewrite_images(record["referenceAnswer"], local_to_url)
            if record["content"] in seen_content:
                continue
            seen_content.add(record["content"])
            imported.append(record)

        all_records.extend(imported)
        page_summaries.append(
            {
                "domain": domain,
                "slug": slug,
                "questions": len(imported),
                "images": image_count,
            }
        )
        print(f"  题目 {len(imported)} 条，图片 {image_count} 张")
        time.sleep(0.3)

    payload_path = out_dir / "questions.json"
    payload_path.write_text(
        json.dumps({"questions": all_records}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"已生成: {payload_path}（{len(all_records)} 条）")

    if args.dry_run:
        summary_path = out_dir / "summary.json"
        summary_path.write_text(
            json.dumps(page_summaries, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        print(f"dry-run 完成，未上传图片、未写题库。汇总: {summary_path}")
        return

    total_imported = 0
    total_skipped = 0
    for start in range(0, len(all_records), args.batch_size):
        batch = all_records[start:start + args.batch_size]
        result = import_batch(api, base_url, args.admin_token, batch)
        total_imported += int(result.get("imported") or 0)
        total_skipped += int(result.get("skipped") or 0)
        print(
            f"  导入进度 {min(start + args.batch_size, len(all_records))}/{len(all_records)}"
            f"，新增 {total_imported}，跳过 {total_skipped}"
        )
        time.sleep(0.1)

    summary = {
        "pages": page_summaries,
        "questions": len(all_records),
        "imported": total_imported,
        "skipped": total_skipped,
        "images": len(image_urls),
    }
    summary_path = out_dir / "summary.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        f"完成: {len(pages)} 个页面，{len(all_records)} 道题，"
        f"新增 {total_imported}，跳过 {total_skipped}，图片 {len(image_urls)} 张"
    )
    print(f"汇总: {summary_path}")


if __name__ == "__main__":
    main(sys.argv)

#!/usr/bin/env python3
"""本地模拟 DeepSeek 接口 + 测试网页（仅用于开发测试，不随 APK 发布）。
  POST /chat/completions   Key 必须为 sk-mock-good；模型 bad-model 返回 400；/slow 模拟超时
  GET  /models
  GET  /page/ok            含多种链接的测试网页
  GET  /page/blocked       返回 403
日志写入 tools/mock_requests.log（不记录 Authorization 头）。"""
import json, sys, time, re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
LOG = __file__.replace("mock_server.py", "mock_requests.log")
PAGE = """<html><head><title>browser-use：让 AI 操作浏览器</title><meta name="description" content="开源 Python 库"></head><body>
<nav><a href="/pricing">Pricing</a> <a href="https://twitter.com/intent/tweet?url=x">分享</a></nav>
<article><h1>browser-use</h1><p>""" + ("browser-use 是一个开源 Python 库，能把大模型接到真实浏览器上，自动打开网页、点击按钮、填写表单和提取信息。" * 4) + """</p>
<p>仓库：<a href="https://github.com/browser-use/browser-use?tab=readme-ov-file#quickstart">GitHub</a>，
在线体验：<a href="https://cloud.browser-use.com/?ref=gh_readme&amp;lang=zh-CN">Cloud</a>，
文档：<a href="../docs/quickstart?v=2&amp;q=%E4%B8%AD%E6%96%87">Docs</a>，
镜像下载：<a href="http://example-mirror.cn/bu_v0.5.zip?t=17%3A02">zip</a></p></article>
<footer><a href="https://browser-use.com/privacy">Privacy</a></footer></body></html>"""

XPOST = """<html><head><title>Geek Share on X: "browser-use 让 AI 替你点网页" / X</title></head><body>
<nav><a href="https://x.com/home">Home</a> <a href="https://x.com/explore">Explore</a></nav>
<article><p><a href="https://x.com/geekshare">@geekshare</a> 分享：""" + ("browser-use 是一个开源 Python 库，能把大模型接到真实浏览器上，自动打开网页、点击按钮、填写表单。" * 4) + """</p>
<p>仓库 <a href="https://github.com/browser-use/browser-use">GitHub</a>，文档 <a href="https://docs.browser-use.com/quickstart">Docs</a></p></article></body></html>"""

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def send(self, code, body, ctype="application/json"):
        b = body.encode() if isinstance(body, str) else body
        self.send_response(code); self.send_header("Content-Type", ctype + "; charset=utf-8"); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        if self.path.startswith("/models"):
            if self.headers.get("Authorization") != "Bearer sk-mock-good": return self.send(401, json.dumps({"error": {"message": "Authentication Fails, Your api key is invalid"}}))
            return self.send(200, json.dumps({"object": "list", "data": [{"id": "deepseek-flash"}, {"id": "deepseek-v4-pro"}]}))
        if self.path.startswith("/page/ok"): return self.send(200, PAGE, "text/html")
        if self.path.startswith("/page/xpost"): return self.send(200, XPOST, "text/html")
        if self.path.startswith("/page/blocked"): return self.send(403, "<html>Forbidden</html>", "text/html")
        self.send(404, "{}")
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0)); req = json.loads(self.rfile.read(n) or b"{}")
        msgs = req.get("messages", []); system = msgs[0]["content"] if msgs else ""; user = msgs[-1]["content"] if msgs else ""
        with open(LOG, "a") as f:
            f.write(json.dumps({"t": time.strftime("%H:%M:%S"), "path": self.path, "model": req.get("model"), "auth_ok": self.headers.get("Authorization") == "Bearer sk-mock-good",
                                "system_head": system[:80], "user_len": len(user),
                                "user_has_author_link": "x.com/geekshare" in user, "user_has_source_line": "原始来源链接" in user, "user_link_lines": re.findall(r"^L\d+ \|.*$", user, re.M)}, ensure_ascii=False) + "\n")
        if self.headers.get("Authorization") != "Bearer sk-mock-good": return self.send(401, json.dumps({"error": {"message": "Authentication Fails, Your api key is invalid"}}))
        if req.get("model") == "bad-model": return self.send(400, json.dumps({"error": {"message": "Model Not Exist"}}))
        if req.get("model") == "slow-model": time.sleep(200)
        ids = re.findall(r"^(L\d+) \|", user, re.M)
        if "OUTPUT_PLAIN_TEST" in system:
            content = "【自定义结构】\n第一行：这是按新提示词输出的纯文本\n用到的链接：" + " ".join("[%s]" % i for i in ids[:2])
        else:
            content = json.dumps({
                "titles": ["GitHub 爆火！让 AI 替你点网页", "别再手动填表了，这个开源项目帮你上网", "我试了 browser-use：一句话操作浏览器"],
                "body": "最近挖到一个宝藏开源项目 browser-use 🧭\n\n它能把大模型接到真实浏览器上，自动打开网页、点击按钮、填写表单。\n\n✅ 适合：想做自动化但不会写爬虫的人\n✅ 上手：项目地址见 [%s]\n\n（模型擅自写的链接：https://github.com/browser-use）\n\n#AI工具 #开源项目 #效率神器" % (ids[1] if len(ids) > 1 else "L1"),
                "links": [{"id": i, "name": "资源 %s" % i, "purpose": "模拟用途说明", "category": "resource"} for i in ids[1:3]] + [{"id": "L999", "name": "不存在", "category": "resource"}],
                "fact_checks": ["Star 数等数据为抓取时信息，发布前请核对"], "uncertain": ["原文未说明是否收费"],
            }, ensure_ascii=False)
        self.send(200, json.dumps({"id": "mock", "model": req.get("model"), "choices": [{"index": 0, "message": {"role": "assistant", "content": content}, "finish_reason": "stop"}]}, ensure_ascii=False))

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8787
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()

package com.scarlett.geekradar

import com.scarlett.geekradar.core.DeepSeekClient
import com.scarlett.geekradar.core.LinkLocation
import com.scarlett.geekradar.core.PageFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DeepSeekClientTest {
    private lateinit var server: MockWebServer
    private val fastClient = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()
    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }
    private fun cfg(key: String = "sk-test-123") = DeepSeekClient.Config(server.url("/").toString(), key, "deepseek-flash")

    @Test fun successSendsPromptAndAuth() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"model":"deepseek-flash","choices":[{"message":{"role":"assistant","content":"{\"titles\":[\"a\"],\"body\":\"b\"}"},"finish_reason":"stop"}]}"""))
        val r = DeepSeekClient(fastClient).chat(cfg(), "SYS-PROMPT-XYZ", "USER-DATA")
        assertTrue(r is DeepSeekClient.Result.Success)
        val req = server.takeRequest()
        assertEquals("/chat/completions", req.path)
        assertEquals("Bearer sk-test-123", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("deepseek-flash", body.getString("model"))
        assertEquals("SYS-PROMPT-XYZ", body.getJSONArray("messages").getJSONObject(0).getString("content"))
    }
    private fun fail(code: Int, body: String): DeepSeekClient.Result.Failure = runBlocking {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        DeepSeekClient(fastClient).chat(cfg(), "s", "u") as DeepSeekClient.Result.Failure
    }
    @Test fun errorMapping() {
        assertEquals(DeepSeekClient.ErrorKind.INVALID_KEY, fail(401, """{"error":{"message":"Authentication Fails"}}""").kind)
        assertEquals(DeepSeekClient.ErrorKind.INSUFFICIENT_BALANCE, fail(402, """{"error":{"message":"Insufficient Balance"}}""").kind)
        assertEquals(DeepSeekClient.ErrorKind.RATE_LIMIT, fail(429, """{"error":{"message":"Rate limit"}}""").kind)
        val m = fail(400, """{"error":{"message":"Model Not Exist"}}""")
        assertEquals(DeepSeekClient.ErrorKind.MODEL_NOT_FOUND, m.kind); assertTrue(m.message.contains("Model Not Exist"))
        assertEquals(DeepSeekClient.ErrorKind.SERVER, fail(503, "busy").kind)
        assertEquals(DeepSeekClient.ErrorKind.BAD_FORMAT, fail(200, "<html>oops</html>").kind)
        assertEquals(DeepSeekClient.ErrorKind.EMPTY, fail(200, """{"choices":[{"message":{"content":""},"finish_reason":"stop"}]}""").kind)
    }
    @Test fun missingKeyNoRequest() = runBlocking {
        val r = DeepSeekClient(fastClient).chat(cfg(""), "s", "u") as DeepSeekClient.Result.Failure
        assertEquals(DeepSeekClient.ErrorKind.NO_KEY, r.kind); assertEquals(0, server.requestCount)
    }
    @Test fun timeout() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setHeadersDelay(3, TimeUnit.SECONDS))
        val r = DeepSeekClient(fastClient).chat(cfg(), "s", "u") as DeepSeekClient.Result.Failure
        assertEquals(DeepSeekClient.ErrorKind.TIMEOUT, r.kind)
    }
    @Test fun connectionFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val r = DeepSeekClient(fastClient).chat(cfg(), "s", "u") as DeepSeekClient.Result.Failure
        assertEquals(DeepSeekClient.ErrorKind.NETWORK, r.kind)
    }
    @Test fun testConnectionChecksModel() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"deepseek-flash"},{"id":"deepseek-v4-pro"}]}"""))
        assertTrue(DeepSeekClient(fastClient).testConnection(cfg()) is DeepSeekClient.Result.Success)
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"deepseek-v4-pro"}]}"""))
        val r = DeepSeekClient(fastClient).testConnection(cfg()) as DeepSeekClient.Result.Failure
        assertEquals(DeepSeekClient.ErrorKind.MODEL_NOT_FOUND, r.kind)
        assertEquals("/models", server.takeRequest().path)
    }
}

class PageFetcherTest {
    private lateinit var server: MockWebServer
    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private val longP = "这是一段足够长的正文内容，用来模拟网页文章。".repeat(20)

    private fun dispatcher(pageCode: Int, pageBody: String, jinaCode: Int = 200, jinaBody: String = "") = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val p = request.path ?: ""
            return if (p.startsWith("/jina/")) MockResponse().setResponseCode(jinaCode).setBody(jinaBody)
            else MockResponse().setResponseCode(pageCode).setHeader("Content-Type", "text/html; charset=utf-8").setBody(pageBody)
        }
    }

    @Test fun localSuccess() = runBlocking {
        server.dispatcher = dispatcher(200, "<html><head><title>T</title></head><body><article><p>$longP</p><a href='/r?a=1'>r</a></article></body></html>")
        val f = PageFetcher(jinaBase = server.url("/jina/").toString())
        val o = f.fetch(server.url("/post").toString(), true)
        assertEquals(PageFetcher.Status.OK, o.status); assertEquals("local", o.channel); assertEquals("T", o.title)
        assertEquals("/r?a=1", o.links[0].raw); assertTrue(o.links[0].resolved.endsWith("/r?a=1"))
        assertEquals(1, server.requestCount)
    }

    @Test fun blockedThenJinaFallbackKeepsRawLinks() = runBlocking {
        val jina = "Title: 一条推文\nURL Source: https://x.com/a/status/1\n\nMarkdown Content:\n$longP\n项目 [GitHub](https://github.com/acme/tool?ref=x&y=1) 文档 https://docs.acme.com/a?b=c ![img](https://pbs.twimg.com/a.jpg)"
        server.dispatcher = dispatcher(403, "denied", 200, jina)
        val f = PageFetcher(jinaBase = server.url("/jina/").toString())
        val target = server.url("/x/status/1?s=20").toString()
        val o = f.fetch(target, true)
        assertEquals(PageFetcher.Status.OK, o.status); assertEquals("jina", o.channel); assertEquals("一条推文", o.title)
        assertTrue(o.localError.contains("403"))
        val raws = o.links.map { it.raw }
        assertTrue(raws.contains("https://github.com/acme/tool?ref=x&y=1"))
        assertTrue(raws.contains("https://docs.acme.com/a?b=c"))
        assertFalse(raws.any { it.endsWith(".jpg") })
        assertTrue(o.links.all { it.location == LinkLocation.JINA })
        // 原始 URL 原样拼接在 Jina 前缀之后
        server.takeRequest(); val jr = server.takeRequest()
        assertEquals("/jina/$target", jr.path)
    }

    @Test fun jinaDisabledFailsWithMessage() = runBlocking {
        server.dispatcher = dispatcher(403, "denied")
        val o = PageFetcher(jinaBase = server.url("/jina/").toString()).fetch(server.url("/p").toString(), false)
        assertEquals(PageFetcher.Status.FAILED, o.status); assertTrue(o.error.contains("拒绝访问")); assertEquals(1, server.requestCount)
    }

    @Test fun shortPageTriesJinaAndBothFail() = runBlocking {
        server.dispatcher = dispatcher(200, "<html><body><p>登录后查看</p></body></html>", 451, "blocked")
        val o = PageFetcher(jinaBase = server.url("/jina/").toString()).fetch(server.url("/p").toString(), true)
        assertEquals(PageFetcher.Status.PARTIAL, o.status); assertTrue(o.jinaTried); assertTrue(o.jinaError.contains("451"))
        assertEquals(2, server.requestCount)
    }

    @Test fun invalidUrl() = runBlocking {
        val o = PageFetcher().fetch("not a url", false)
        assertEquals(PageFetcher.Status.FAILED, o.status)
    }
}

package pro.xiangyu.cashierhelper.api

import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pro.xiangyu.cashierhelper.config.AppConfig

class CashierClientTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var httpsClient: OkHttpClient

    @Before
    fun setUp() {
        // The client only accepts HTTPS, so the fake server speaks TLS and the client trusts its certificate.
        val held = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        httpsClient = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `uploads the images with date and idempotency key and accepts the 201`() = runBlocking {
        server.enqueue(accepted("doc-1"))
        val first = jpeg("a", byteArrayOf(1, 2, 3, 4))
        val second = jpeg("b", ByteArray(1000) { it.toByte() })

        val outcome = client().create(config(), listOf(first, second), "2026-10-03", KEY)

        assertEquals(UploadOutcome.Accepted("doc-1"), outcome)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/source-documents", request.path)
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
        assertEquals(KEY, request.getHeader("Idempotency-Key"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))

        val raw = request.body.readUtf8()
        // The announced length is exact, so the upload is not chunked.
        assertEquals(raw.toByteArray().size.toLong(), request.getHeader("Content-Length")!!.toLong())
        val json = Json.parseToJsonElement(raw).jsonObject
        assertEquals(setOf("images", "entryDate"), json.keys)
        assertEquals("2026-10-03", json.getValue("entryDate").jsonPrimitive.content)
        val images = json.getValue("images").jsonArray.map { it.jsonObject }
        assertEquals(2, images.size)
        images.forEach { assertEquals(setOf("data", "mimeType"), it.keys) }
        assertEquals(Base64.getEncoder().encodeToString(first.readBytes()), images[0].getValue("data").jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(second.readBytes()), images[1].getValue("data").jsonPrimitive.content)
        assertEquals("image/jpeg", images[1].getValue("mimeType").jsonPrimitive.content)
    }

    @Test
    fun `base64 padding is correct for every remainder`() = runBlocking {
        for (size in 1..4) {
            server.enqueue(accepted("doc"))
            val file = jpeg("f$size", ByteArray(size) { (it + 7).toByte() })
            client().create(config(), listOf(file), "2026-10-03", KEY)
            val json = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val data = json.getValue("images").jsonArray.single().jsonObject.getValue("data").jsonPrimitive.content
            assertEquals(file.readBytes().toList(), Base64.getDecoder().decode(data).toList())
        }
    }

    @Test
    fun `a replayed upload with the same key is accepted like the first one`() = runBlocking {
        // Cashier answers a replay with the same 201 shape, always reporting "processing".
        val body = """{"sourceDocumentId":"doc-1","revisionId":"r1","revisionState":"processing","status":"processing"}"""
        repeat(2) { server.enqueue(MockResponse().setResponseCode(201).setBody(body)) }
        val file = jpeg("a", byteArrayOf(9))

        val first = client().create(config(), listOf(file), "2026-10-03", KEY)
        val second = client().create(config(), listOf(file), "2026-10-03", KEY)

        assertEquals(first, second)
        assertEquals(KEY, server.takeRequest().getHeader("Idempotency-Key"))
        assertEquals(KEY, server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `maps upload status codes to outcomes`() = runBlocking {
        val file = jpeg("a", byteArrayOf(1))
        fun respond(code: Int, body: String = "", vararg headers: Pair<String, String>) {
            val response = MockResponse().setResponseCode(code).setBody(body)
            headers.forEach { (name, value) -> response.addHeader(name, value) }
            server.enqueue(response)
        }
        suspend fun upload() = client().create(config(), listOf(file), "2026-10-03", KEY)

        respond(401)
        assertEquals(UploadOutcome.Unauthorized, upload())
        respond(403)
        assertEquals(UploadOutcome.Unauthorized, upload())
        respond(409, """{"error":{"code":"CONFLICT","message":"x"}}""")
        assertEquals(UploadOutcome.Conflict, upload())
        respond(500)
        assertEquals(UploadOutcome.Transient(TransientKind.SERVER), upload())
        respond(503, "", "Retry-After" to "7")
        assertEquals(UploadOutcome.Transient(TransientKind.SERVER, 7_000), upload())
        respond(429, "", "Retry-After" to "3")
        assertEquals(UploadOutcome.Transient(TransientKind.SERVER, 3_000), upload())
        respond(400, """{"error":{"code":"VALIDATION_FAILED","message":"图片格式不支持"}}""")
        assertEquals(UploadOutcome.Rejected(400, "VALIDATION_FAILED", "图片格式不支持"), upload())
        respond(413, "<html>too large</html>")
        assertEquals(UploadOutcome.Rejected(413, null, null), upload())
    }

    @Test
    fun `an accepted response without a document id is retried not trusted`() = runBlocking {
        val file = jpeg("a", byteArrayOf(1))
        listOf("""{"revisionState":"processing"}""", "not-json", "[]").forEach { body ->
            server.enqueue(MockResponse().setResponseCode(201).setBody(body))
            assertEquals(
                UploadOutcome.Transient(TransientKind.MALFORMED),
                client().create(config(), listOf(file), "2026-10-03", KEY),
            )
        }
    }

    @Test
    fun `network failure and timeout are transient with their own kind`() = runBlocking {
        val file = jpeg("a", byteArrayOf(1))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val noRetry = OkHttpClient.Builder()
            .sslSocketFactory(httpsClient.sslSocketFactory, httpsClient.x509TrustManager!!)
            .retryOnConnectionFailure(false)
            .build()
        assertEquals(
            UploadOutcome.Transient(TransientKind.NETWORK),
            CashierClient(noRetry).create(config(), listOf(file), "2026-10-03", KEY),
        )

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(
            UploadOutcome.Transient(TransientKind.TIMEOUT),
            CashierClient(httpsClient, createTimeoutMillis = 300)
                .create(config(), listOf(file), "2026-10-03", KEY),
        )
    }

    @Test
    fun `cancelling the caller cancels the request instead of reporting a network error`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val file = jpeg("a", byteArrayOf(1))
        var outcome: UploadOutcome? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            outcome = client().create(config(), listOf(file), "2026-10-03", KEY)
        }
        server.takeRequest(5, TimeUnit.SECONDS)
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertNull(outcome)
    }

    @Test
    fun `parses every document status`() = runBlocking {
        val completed = """
            {"sourceDocumentId":"d","status":"completed","result":{"title":"星巴克","total":"35.00","totalCurrency":"CNY",
            "entries":[{"name":"拿铁","description":null,"amount":"35.00","currency":"CNY","category":"餐饮"}]},"error":null}
        """.trimIndent()
        val cases = listOf(
            """{"status":"processing","result":null,"error":null}""" to
                QueryOutcome.Status(DocumentStatus.Processing, 5_000),
            completed to QueryOutcome.Status(
                DocumentStatus.Completed(
                    DocumentResult(
                        "星巴克", "35.00", "CNY",
                        listOf(DocumentEntry("拿铁", null, "35.00", "CNY", "餐饮")),
                    ),
                ),
                null,
            ),
            """{"status":"completed","result":{"title":null,"total":null,"totalCurrency":"CNY","entries":[]}}""" to
                QueryOutcome.Status(DocumentStatus.Completed(DocumentResult(null, null, "CNY", emptyList())), null),
            """{"status":"invalid","error":{"code":"VALIDATION_FAILED","message":"这是一张退款单据"}}""" to
                QueryOutcome.Status(DocumentStatus.Invalid("这是一张退款单据"), null),
            """{"status":"invalid","error":{"code":"VALIDATION_FAILED","message":null}}""" to
                QueryOutcome.Status(DocumentStatus.Invalid(null), null),
            """{"status":"failed","error":{"code":"processing_timeout","message":null}}""" to
                QueryOutcome.Status(DocumentStatus.Failed("processing_timeout", null), null),
            """{"status":"cancelled","error":null}""" to QueryOutcome.Status(DocumentStatus.Cancelled, null),
        )
        for ((body, expected) in cases) {
            val response = MockResponse().setResponseCode(200).setBody(body)
            if (expected.status == DocumentStatus.Processing) response.addHeader("Retry-After", "5")
            server.enqueue(response)
            assertEquals(expected, client().get(config(), "d"))
        }
        assertEquals("/api/v1/source-documents/d", server.takeRequest().path)
    }

    @Test
    fun `unknown or broken status bodies are transient`() = runBlocking {
        listOf(
            """{"status":"queued"}""",
            """{"status":"completed"}""",
            """{"status":"completed","result":{"entries":[{"name":"x"}]}}""",
            "not-json",
        ).forEach { body ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            assertEquals(QueryOutcome.Transient(TransientKind.MALFORMED), client().get(config(), "d"))
        }
    }

    @Test
    fun `maps query status codes`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(QueryOutcome.NotFound, client().get(config(), "d"))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(QueryOutcome.Unauthorized, client().get(config(), "d"))
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(QueryOutcome.Transient(TransientKind.SERVER), client().get(config(), "d"))
    }

    @Test
    fun `the document id is path encoded`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        client().get(config(), "a/b c")
        assertEquals("/api/v1/source-documents/a%2Fb%20c", server.takeRequest().path)
    }

    @Test
    fun `connection test tells a good key from a bad key and a wrong server`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":"NOT_FOUND","message":"x"}}"""))
        assertEquals(ConnectionTest.OK, client().testConnection(base, "secret-key"))
        val probe = server.takeRequest()
        assertEquals("GET", probe.method)
        assertTrue(probe.path!!.startsWith("/api/v1/source-documents/"))
        assertEquals("Bearer secret-key", probe.getHeader("Authorization"))

        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(ConnectionTest.BAD_KEY, client().testConnection(base, "wrong"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>nginx</html>"))
        assertEquals(ConnectionTest.NOT_CASHIER, client().testConnection(base, "secret-key"))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(ConnectionTest.UNREACHABLE, client().testConnection(base, "secret-key"))
        assertEquals(ConnectionTest.UNREACHABLE, client().testConnection("not a url", "secret-key"))
    }

    @Test
    fun `connection test reports certificate and host problems`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        // A default client does not trust the test server's certificate.
        assertEquals(ConnectionTest.BAD_CERT, CashierClient().testConnection(base, "secret-key"))
        assertEquals(
            ConnectionTest.UNKNOWN_HOST,
            CashierClient().testConnection("https://no-such-host.invalid", "secret-key"),
        )
    }

    @Test
    fun `retry after accepts seconds and dates and is capped`() {
        val now = Instant.parse("2026-10-03T10:00:00Z")
        assertEquals(5_000L, CashierClient.parseRetryAfter("5", now))
        assertEquals(60_000L, CashierClient.parseRetryAfter("3600", now))
        assertEquals(0L, CashierClient.parseRetryAfter("0", now))
        assertEquals(10_000L, CashierClient.parseRetryAfter("Sat, 03 Oct 2026 10:00:10 GMT", now))
        assertEquals(0L, CashierClient.parseRetryAfter("Sat, 03 Oct 2026 09:00:00 GMT", now))
        assertNull(CashierClient.parseRetryAfter("soon", now))
        assertNull(CashierClient.parseRetryAfter(null, now))
    }

    private fun client() = CashierClient(httpsClient)

    private fun config() = AppConfig(server.url("/").toString().trimEnd('/'), "secret-key")

    private fun accepted(id: String) = MockResponse().setResponseCode(201)
        .setBody("""{"sourceDocumentId":"$id","revisionId":"r","revisionState":"processing","status":"processing"}""")

    private fun jpeg(name: String, bytes: ByteArray): File =
        temp.newFile("$name.jpg").also { it.writeBytes(bytes) }

    private companion object {
        const val KEY = "11111111-2222-3333-4444-555555555555"
    }
}

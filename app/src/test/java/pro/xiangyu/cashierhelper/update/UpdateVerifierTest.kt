package pro.xiangyu.cashierhelper.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateVerifierTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val installed = ApkInfo("pro.xiangyu.cashierhelper", 1000004, setOf("key-a"))

    private fun apk(content: String = "apk-bytes"): File = temp.newFile().also { it.writeText(content) }

    private fun feedFor(file: File, versionCode: Int = 1000005) = UpdateFeed(
        versionName = "1.0.5",
        versionCode = versionCode,
        apkUrl = "${UpdateFeedParser.DOWNLOAD_PREFIX}v1.0.5/x.apk",
        sha256 = UpdateVerifier.sha256(file),
    )

    private fun inspect(info: ApkInfo?) = ApkInspector { info }

    private val good = ApkInfo("pro.xiangyu.cashierhelper", 1000005, setOf("key-a"))

    @Test
    fun `a matching file from the same signer is accepted`() {
        val file = apk()

        assertEquals(Verification.Ok, UpdateVerifier.verify(file, feedFor(file), installed, inspect(good)))
    }

    @Test
    fun `the checksum is compared without regard to case`() {
        val file = apk()
        val feed = feedFor(file).let { it.copy(sha256 = it.sha256.uppercase()) }

        assertEquals(Verification.Ok, UpdateVerifier.verify(file, feed, installed, inspect(good)))
    }

    @Test
    fun `a file that differs from the feed is rejected`() {
        val file = apk()
        val feed = feedFor(file)
        file.writeText("tampered")

        assertEquals(
            Verification.Rejected(Rejection.CHECKSUM),
            UpdateVerifier.verify(file, feed, installed, inspect(good)),
        )
    }

    @Test
    fun `an unreadable file is rejected`() {
        val file = apk()

        assertEquals(
            Verification.Rejected(Rejection.UNREADABLE),
            UpdateVerifier.verify(file, feedFor(file), installed, inspect(null)),
        )
    }

    @Test
    fun `another application is rejected`() {
        val file = apk()

        assertEquals(
            Verification.Rejected(Rejection.WRONG_PACKAGE),
            UpdateVerifier.verify(file, feedFor(file), installed, inspect(good.copy(packageName = "other.app"))),
        )
    }

    @Test
    fun `a version that does not match the feed or is not newer is rejected`() {
        val file = apk()

        assertEquals(
            Verification.Rejected(Rejection.WRONG_VERSION),
            UpdateVerifier.verify(file, feedFor(file), installed, inspect(good.copy(versionCode = 1000009))),
        )
        assertEquals(
            Verification.Rejected(Rejection.WRONG_VERSION),
            UpdateVerifier.verify(file, feedFor(file, 1000004), installed, inspect(good.copy(versionCode = 1000004))),
        )
    }

    @Test
    fun `a different signing key is rejected`() {
        val file = apk()

        assertEquals(
            Verification.Rejected(Rejection.WRONG_SIGNER),
            UpdateVerifier.verify(file, feedFor(file), installed, inspect(good.copy(signers = setOf("key-b")))),
        )
        assertEquals(
            Verification.Rejected(Rejection.WRONG_SIGNER),
            UpdateVerifier.verify(file, feedFor(file), installed, inspect(good.copy(signers = emptySet()))),
        )
    }

    @Test
    fun `the digest is the usual lowercase sha-256`() {
        val file = temp.newFile().also { it.writeText("abc") }

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateVerifier.sha256(file))
        assertTrue(Rejection.entries.all { it.message.isNotBlank() })
    }
}

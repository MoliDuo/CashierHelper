package pro.xiangyu.cashierhelper.update

import java.io.File
import java.security.MessageDigest

/** What the package manager says about an APK file. [signers] are SHA-256 digests of the signing certificates. */
data class ApkInfo(val packageName: String, val versionCode: Long, val signers: Set<String>)

fun interface ApkInspector {
    /** Null when the file is not a readable APK. */
    fun inspect(file: File): ApkInfo?
}

enum class Rejection(val message: String) {
    CHECKSUM("下载的安装包不完整，已丢弃"),
    UNREADABLE("下载的安装包无法读取，已丢弃"),
    WRONG_PACKAGE("下载的安装包不是 Cashier 记账，已丢弃"),
    WRONG_VERSION("下载的安装包版本与更新信息不符，已丢弃"),
    WRONG_SIGNER("下载的安装包签名与当前版本不一致，已丢弃"),
}

sealed interface Verification {
    data object Ok : Verification
    data class Rejected(val reason: Rejection) : Verification
}

/**
 * Accepts an update only if it is the file the feed describes and was signed by the
 * same key as the installed app. Android would refuse a different signer at install
 * time anyway; checking first keeps a bad file from ever being offered.
 */
object UpdateVerifier {
    fun verify(file: File, feed: UpdateFeed, installed: ApkInfo, inspector: ApkInspector): Verification {
        if (!sha256(file).equals(feed.sha256, ignoreCase = true)) return Verification.Rejected(Rejection.CHECKSUM)
        val candidate = inspector.inspect(file) ?: return Verification.Rejected(Rejection.UNREADABLE)
        if (candidate.packageName != installed.packageName) return Verification.Rejected(Rejection.WRONG_PACKAGE)
        if (candidate.versionCode != feed.versionCode.toLong() || candidate.versionCode <= installed.versionCode) {
            return Verification.Rejected(Rejection.WRONG_VERSION)
        }
        if (candidate.signers.isEmpty() || candidate.signers != installed.signers) {
            return Verification.Rejected(Rejection.WRONG_SIGNER)
        }
        return Verification.Ok
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

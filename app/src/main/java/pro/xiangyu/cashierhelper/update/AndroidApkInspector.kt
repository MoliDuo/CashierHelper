package pro.xiangyu.cashierhelper.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/** Reads package name, version and signing certificates through the package manager. */
class AndroidApkInspector(context: Context) : ApkInspector {
    private val packageManager = context.packageManager
    private val ownPackage = context.packageName

    override fun inspect(file: File): ApkInfo? =
        packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)?.let(::describe)

    /** The installed app, to compare an update against. */
    fun installed(): ApkInfo =
        describe(packageManager.getPackageInfo(ownPackage, PackageManager.GET_SIGNING_CERTIFICATES))

    private fun describe(info: PackageInfo): ApkInfo {
        val signing = info.signingInfo
        val certificates = when {
            signing == null -> emptyArray()
            signing.hasMultipleSigners() -> signing.apkContentsSigners
            else -> signing.signingCertificateHistory ?: emptyArray()
        }
        val signers = certificates.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        return ApkInfo(info.packageName, info.longVersionCode, signers)
    }
}

package pro.xiangyu.cashierhelper.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import java.io.IOException

/** Hands the verified APK to the system installer, which asks for confirmation itself. */
class PackageInstallerUpdateInstaller(private val context: Context) : UpdateInstaller {
    override fun start(file: File): InstallStart {
        val packageManager = context.packageManager
        if (!packageManager.canRequestPackageInstalls()) return InstallStart.NEEDS_PERMISSION

        val installer = packageManager.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(context.packageName) }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("update.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val result = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(UpdateActions.INSTALL_RESULT).setPackage(context.packageName),
                    // The system adds the confirmation intent to the broadcast, so it must be mutable.
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(result.intentSender)
            }
            InstallStart.STARTED
        } catch (_: IOException) {
            abandon(installer, sessionId)
            InstallStart.FAILED
        } catch (_: SecurityException) {
            abandon(installer, sessionId)
            InstallStart.FAILED
        } catch (_: IllegalStateException) {
            abandon(installer, sessionId)
            InstallStart.FAILED
        }
    }

    private fun abandon(installer: PackageInstaller, sessionId: Int) {
        if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
    }
}

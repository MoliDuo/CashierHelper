package pro.xiangyu.cashierhelper.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallFailureTest {
    @Test
    fun `every failure says the current version is fine or what to do`() {
        val statuses = listOf(
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE,
        )
        statuses.forEach { assertTrue(InstallFailure.describe(it).isNotBlank()) }
    }
}

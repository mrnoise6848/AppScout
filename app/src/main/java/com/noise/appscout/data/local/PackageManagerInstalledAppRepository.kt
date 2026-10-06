package com.noise.appscout.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.noise.appscout.core.common.IoDispatcher
import com.noise.appscout.domain.model.InstalledApp
import com.noise.appscout.domain.repository.InstalledAppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Reads launcher visible applications through `PackageManager`.
 *
 * `QUERY_ALL_PACKAGES` is intentionally not requested: restricted package visibility with a
 * MAIN/LAUNCHER intent query is enough to show the apps a user can actually launch.
 */
class PackageManagerInstalledAppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : InstalledAppRepository {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())

    override fun observeInstalledApps(): Flow<List<InstalledApp>> = installedApps.asStateFlow()

    override suspend fun refresh() = withContext(io) {
        installedApps.value = loadLauncherApps()
    }

    override suspend fun getInstalledApp(packageName: String): InstalledApp? = withContext(io) {
        loadLauncherApps().firstOrNull { it.packageName == packageName }
            ?: runCatching { readApp(packageName) }.getOrNull()
    }

    private fun loadLauncherApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        val seen = HashSet<String>()
        val apps = ArrayList<InstalledApp>(resolveInfos.size)

        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue
            if (!seen.add(packageName)) continue

            val packageInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
            }.getOrNull() ?: continue

            if (packageInfo.applicationInfo?.enabled == false) continue

            val label = resolveInfo.loadLabel(packageManager).toString().trim()
            apps += InstalledApp(
                packageName = packageName,
                appName = label.ifEmpty { packageName },
                versionName = packageInfo.versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            )
        }

        return apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
    }

    private fun readApp(packageName: String): InstalledApp {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return InstalledApp(
            packageName = packageName,
            appName = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                .ifEmpty { packageName },
            versionName = packageInfo.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        )
    }
}

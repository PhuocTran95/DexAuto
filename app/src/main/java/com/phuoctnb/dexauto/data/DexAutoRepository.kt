package com.phuoctnb.dexauto.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.phuoctnb.dexauto.payment.PaymentQrConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class DexAutoRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSavedLayouts(): List<SavedLayout> {
        val raw = prefs.getString(PREF_LAYOUTS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val packages = item.getJSONArray("packages")
                val type = layoutTypeFromPrefs(item.getString("type"))
                val ratios = item.optJSONArray("ratios")
                SavedLayout(
                    id = item.getString("id"),
                    type = type,
                    packages = List(packages.length()) { packageIndex -> packages.getString(packageIndex) }
                        .take(type.slotCount),
                    ratios = if (ratios == null) {
                        defaultRatiosFor(type)
                    } else {
                        List(ratios.length()) { ratioIndex -> ratios.optDouble(ratioIndex, 0.5).toFloat() }
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveLayouts(layouts: List<SavedLayout>) {
        val array = JSONArray()
        layouts.forEach { layout ->
            array.put(JSONObject().apply {
                put("id", layout.id)
                put("type", layout.type.name)
                put("packages", JSONArray(layout.packages))
                put("ratios", JSONArray(layout.ratios))
            })
        }
        prefs.edit { putString(PREF_LAYOUTS, array.toString()) }
    }

    fun loadBatteryFixEnabled(): Boolean = prefs.getBoolean(PREF_BATTERY_ENABLED, true)

    fun saveBatteryFixEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(PREF_BATTERY_ENABLED, enabled) }
    }

    fun loadBatteryLevel(): Int = prefs.getInt(PREF_BATTERY_LEVEL, 100)

    fun saveBatteryLevel(level: Int) {
        prefs.edit { putInt(PREF_BATTERY_LEVEL, level.coerceIn(1, 100)) }
    }

    fun loadPrivilegedBackend(): PrivilegedBackend {
        val raw = prefs.getString(PREF_PRIVILEGED_BACKEND, PrivilegedBackend.None.name)
        return runCatching { PrivilegedBackend.valueOf(raw ?: PrivilegedBackend.None.name) }
            .getOrDefault(PrivilegedBackend.None)
    }

    fun savePrivilegedBackend(backend: PrivilegedBackend) {
        prefs.edit { putString(PREF_PRIVILEGED_BACKEND, backend.name) }
    }

    fun loadPanelPosition(): PanelPosition {
        val raw = prefs.getString(PREF_PANEL_POSITION, PanelPosition.Left.name)
        return runCatching { PanelPosition.valueOf(raw ?: PanelPosition.Left.name) }
            .getOrDefault(PanelPosition.Left)
    }

    fun savePanelPosition(position: PanelPosition) {
        prefs.edit { putString(PREF_PANEL_POSITION, position.name) }
    }

    fun loadPaymentQrConfig(): PaymentQrConfig = PaymentQrConfig(
        bankCode = prefs.getString(PREF_PAYMENT_BANK_CODE, "").orEmpty(),
        accountNumber = prefs.getString(PREF_PAYMENT_ACCOUNT_NUMBER, "").orEmpty()
    )

    fun savePaymentQrConfig(config: PaymentQrConfig) {
        prefs.edit {
            putString(PREF_PAYMENT_BANK_CODE, config.bankCode)
            putString(PREF_PAYMENT_ACCOUNT_NUMBER, config.accountNumber)
        }
    }

    fun loadPanelAutoStartSuppressed(): Boolean =
        prefs.getBoolean(PREF_PANEL_AUTO_START_SUPPRESSED, false)

    fun savePanelAutoStartSuppressed(suppressed: Boolean) {
        prefs.edit { putBoolean(PREF_PANEL_AUTO_START_SUPPRESSED, suppressed) }
    }

    fun loadDexSessionActive(): Boolean =
        prefs.getBoolean(PREF_DEX_SESSION_ACTIVE, false)

    fun saveDexSessionActive(active: Boolean) {
        prefs.edit { putBoolean(PREF_DEX_SESSION_ACTIVE, active) }
    }

    fun loadPreserveDexLayoutEnabled(): Boolean =
        prefs.getBoolean(PREF_PRESERVE_DEX_LAYOUT_ENABLED, false)

    fun savePreserveDexLayoutEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(PREF_PRESERVE_DEX_LAYOUT_ENABLED, enabled) }
    }

    fun loadPendingDexSessionSnapshot(): DexSessionSnapshot? {
        if (!prefs.getBoolean(PREF_DEX_SESSION_PENDING, false)) return null
        return loadDexSessionSnapshot()
    }

    fun hasDexSessionSnapshot(): Boolean =
        prefs.contains(PREF_DEX_SESSION_SNAPSHOT)

    private fun loadDexSessionSnapshot(): DexSessionSnapshot? {
        val raw = prefs.getString(PREF_DEX_SESSION_SNAPSHOT, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val workArea = root.getJSONObject("workArea").toRect()
            val appsJson = root.getJSONArray("apps")
            val apps = List(appsJson.length()) { index ->
                val app = appsJson.getJSONObject(index)
                DexSessionApp(
                    packageName = app.getString("packageName"),
                    bounds = app.getJSONObject("bounds").toRect()
                )
            }.filter { it.packageName.isNotBlank() && it.bounds.width() > 0 && it.bounds.height() > 0 }
            DexSessionSnapshot(workArea, apps)
        }.getOrNull()?.takeIf { it.apps.isNotEmpty() }
    }

    fun saveDexSessionSnapshot(snapshot: DexSessionSnapshot, pendingRestore: Boolean) {
        val root = JSONObject().apply {
            put("workArea", snapshot.workArea.toJson())
            put("apps", JSONArray().apply {
                snapshot.apps.forEach { app ->
                    put(JSONObject().apply {
                        put("packageName", app.packageName)
                        put("bounds", app.bounds.toJson())
                    })
                }
            })
        }
        prefs.edit {
            putString(PREF_DEX_SESSION_SNAPSHOT, root.toString())
            putBoolean(PREF_DEX_SESSION_PENDING, pendingRestore && snapshot.apps.isNotEmpty())
        }
    }

    fun markDexSessionSnapshotPending(): Boolean {
        val hasSnapshot = loadDexSessionSnapshot() != null
        prefs.edit { putBoolean(PREF_DEX_SESSION_PENDING, hasSnapshot) }
        return hasSnapshot
    }

    fun consumeDexSessionSnapshot() {
        prefs.edit {
            remove(PREF_DEX_SESSION_SNAPSHOT)
            putBoolean(PREF_DEX_SESSION_PENDING, false)
        }
    }

    fun clearDexSessionSnapshot() {
        prefs.edit {
            remove(PREF_DEX_SESSION_SNAPSHOT)
            putBoolean(PREF_DEX_SESSION_PENDING, false)
        }
    }

    fun loadLaunchableApps(): List<LaunchableApp> {
        val packageManager = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(mainIntent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.applicationInfo.packageName
                if (packageName == appContext.packageName) return@mapNotNull null
                LaunchableApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun layoutTypeFromPrefs(raw: String): LayoutType {
        return if (raw == "SplitThreeRows") {
            LayoutType.SplitFourColumns
        } else {
            LayoutType.valueOf(raw)
        }
    }

    private fun android.graphics.Rect.toJson() = JSONObject().apply {
        put("left", left)
        put("top", top)
        put("right", right)
        put("bottom", bottom)
    }

    private fun JSONObject.toRect() = android.graphics.Rect(
        getInt("left"),
        getInt("top"),
        getInt("right"),
        getInt("bottom")
    )

    private companion object {
        const val PREFS_NAME = "dex_auto_prefs"
        const val PREF_LAYOUTS = "saved_layouts"
        const val PREF_BATTERY_ENABLED = "battery_fix_enabled"
        const val PREF_BATTERY_LEVEL = "battery_fix_level"
        const val PREF_PRIVILEGED_BACKEND = "privileged_backend"
        const val PREF_PANEL_POSITION = "panel_position"
        const val PREF_PAYMENT_BANK_CODE = "payment_bank_code"
        const val PREF_PAYMENT_ACCOUNT_NUMBER = "payment_account_number"
        const val PREF_PANEL_AUTO_START_SUPPRESSED = "panel_auto_start_suppressed"
        const val PREF_DEX_SESSION_ACTIVE = "dex_session_active"
        const val PREF_PRESERVE_DEX_LAYOUT_ENABLED = "preserve_dex_layout_enabled"
        const val PREF_DEX_SESSION_SNAPSHOT = "dex_session_snapshot"
        const val PREF_DEX_SESSION_PENDING = "dex_session_pending"
    }
}

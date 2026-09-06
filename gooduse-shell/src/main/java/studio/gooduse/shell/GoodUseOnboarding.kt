package studio.gooduse.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

enum class GoodUseOnboardingProfile {
    SIMPLE_LOCAL_UTILITY,
    PROFESSIONAL_WORKBENCH,
    AD_SUPPORTED_UTILITY,
    OFFICIAL_REFERENCE_OR_BENEFIT,
    SENSITIVE_LOCAL_TRACKER,
    PERMISSION_DEPENDENT_CAPTURE,
    MULTI_REGION_REFERENCE,
    PAID_UPFRONT_OR_NO_ADS,
}

enum class GoodUseOnboardingKind {
    LANGUAGE,
    WELCOME,
    REGION,
    SCOPE,
    QUICK_START,
    TRUST_PRIVACY,
    LEGAL_ACK,
    PROVIDER_CONSENT,
    PERMISSION_RATIONALE,
}

data class GoodUseOnboardingScreen(
    val id: String,
    val kind: GoodUseOnboardingKind,
    val titleKey: String,
    val bodyKeys: List<String>,
    val primaryActionKey: String,
    val secondaryActionKey: String?,
    val required: Boolean,
    val showsLogo: Boolean,
)

data class GoodUseOnboardingManifest(
    val contractVersion: String,
    val appId: String,
    val onboardingVersion: String,
    val profile: GoodUseOnboardingProfile,
    val sourceRevision: String,
    val codeEvidenceHash: String,
    val adsAllowedDuringAppOwnedOnboarding: Boolean,
    val screens: List<GoodUseOnboardingScreen>,
) {
    init {
        require(contractVersion == "1.0.0") { "Unsupported APP_ONBOARDING_MANIFEST version: $contractVersion" }
        require(appId.isNotBlank()) { "onboarding appId must not be blank" }
        require(onboardingVersion.isNotBlank()) { "onboardingVersion must not be blank" }
        require(sourceRevision.length >= 7) { "sourceRevision must identify the inspected source revision" }
        require(codeEvidenceHash.length >= 12) { "codeEvidenceHash must identify CODE_ONBOARDING_EVIDENCE_MAP" }
        require(!adsAllowedDuringAppOwnedOnboarding) { "Ads are forbidden during app-owned onboarding" }
        require(screens.isNotEmpty()) { "At least one onboarding screen is required" }
        require(screens.size <= GOODUSE_ONBOARDING_KIND_ORDER.size) { "Too many onboarding screens" }
        require(screens.map { it.id }.distinct().size == screens.size) { "onboarding screen IDs must be unique" }
        require(screens.count { it.kind == GoodUseOnboardingKind.WELCOME } == 1) { "Exactly one WELCOME screen is required" }
        require(screens.all { it.id.isNotBlank() && it.titleKey.isNotBlank() && it.primaryActionKey.isNotBlank() }) {
            "onboarding screen id/titleKey/primaryActionKey must not be blank"
        }
        require(screens.all { it.bodyKeys.size <= 3 && it.bodyKeys.all(String::isNotBlank) }) {
            "onboarding bodyKeys must contain at most three non-blank semantic keys"
        }
        val order = screens.map { GOODUSE_ONBOARDING_KIND_ORDER.getValue(it.kind) }
        require(order.zipWithNext().all { (a, b) -> a < b }) {
            "onboarding screens must follow the canonical GoodUse order without duplicate kinds"
        }
    }

    fun screen(id: String): GoodUseOnboardingScreen? = screens.firstOrNull { it.id == id }
}

private val GOODUSE_ONBOARDING_KIND_ORDER = mapOf(
    GoodUseOnboardingKind.LANGUAGE to 0,
    GoodUseOnboardingKind.WELCOME to 1,
    GoodUseOnboardingKind.REGION to 2,
    GoodUseOnboardingKind.SCOPE to 3,
    GoodUseOnboardingKind.QUICK_START to 4,
    GoodUseOnboardingKind.TRUST_PRIVACY to 5,
    GoodUseOnboardingKind.LEGAL_ACK to 6,
    GoodUseOnboardingKind.PROVIDER_CONSENT to 7,
    GoodUseOnboardingKind.PERMISSION_RATIONALE to 8,
)

object GoodUseOnboardingManifestLoader {
    fun fromJson(json: String): GoodUseOnboardingManifest {
        val root = JSONObject(json)
        val screens = root.getJSONArray("screens").let { array ->
            List(array.length()) { index ->
                array.getJSONObject(index).let { screen ->
                    GoodUseOnboardingScreen(
                        id = screen.getString("id"),
                        kind = GoodUseOnboardingKind.valueOf(screen.getString("kind")),
                        titleKey = screen.getString("titleKey"),
                        bodyKeys = screen.getJSONArray("bodyKeys").let { body ->
                            List(body.length()) { bodyIndex -> body.getString(bodyIndex) }
                        },
                        primaryActionKey = screen.getString("primaryActionKey"),
                        secondaryActionKey = if (screen.has("secondaryActionKey") && !screen.isNull("secondaryActionKey")) {
                            screen.getString("secondaryActionKey")
                        } else {
                            null
                        },
                        required = screen.getBoolean("required"),
                        showsLogo = screen.getBoolean("showsLogo"),
                    )
                }
            }
        }

        return GoodUseOnboardingManifest(
            contractVersion = root.getString("contractVersion"),
            appId = root.getString("appId"),
            onboardingVersion = root.getString("onboardingVersion"),
            profile = GoodUseOnboardingProfile.valueOf(root.getString("profile")),
            sourceRevision = root.getString("sourceRevision"),
            codeEvidenceHash = root.getString("codeEvidenceHash"),
            adsAllowedDuringAppOwnedOnboarding = root.getBoolean("adsAllowedDuringAppOwnedOnboarding"),
            screens = screens,
        )
    }
}

/**
 * Shared app-owned first-run renderer. Call inside GoodUseAppShell so the
 * exact same theme/type/spacing contract drives onboarding and core UI.
 *
 * Language/region/legal/provider/permission screens may inject specialist
 * native content through customContent; the shell still owns page geometry,
 * copy hierarchy and action placement.
 */
@Composable
fun GoodUseOnboardingHost(
    manifest: GoodUseOnboardingManifest,
    currentScreenId: String = manifest.screens.first().id,
    label: (String) -> String,
    onPrimary: (GoodUseOnboardingScreen) -> Unit,
    modifier: Modifier = Modifier,
    onSecondary: ((GoodUseOnboardingScreen) -> Unit)? = null,
    logo: (@Composable () -> Unit)? = null,
    customContent: (@Composable (GoodUseOnboardingScreen) -> Unit)? = null,
) {
    val screen = requireNotNull(manifest.screen(currentScreenId)) {
        "Unknown onboarding screen id: $currentScreenId"
    }
    val runtime = LocalGoodUseShell.current
    val spacing = runtime.config.theme.spacing

    GoodUseStack(
        modifier = modifier.fillMaxWidth(),
        scrollable = true,
    ) {
        if (screen.showsLogo && logo != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.small.dp),
                contentAlignment = Alignment.Center,
            ) { logo() }
        }

        GoodUseText(
            text = label(screen.titleKey),
            role = GoodUseTextRoleName.TITLE,
        )

        screen.bodyKeys.forEach { key ->
            GoodUseText(
                text = label(key),
                role = GoodUseTextRoleName.BODY,
            )
        }

        customContent?.invoke(screen)

        Spacer(modifier = Modifier.height(spacing.medium.dp))

        GoodUsePrimaryButton(
            text = label(screen.primaryActionKey),
            onClick = { onPrimary(screen) },
        )

        val secondaryKey = screen.secondaryActionKey
        if (secondaryKey != null) {
            GoodUseSecondaryButton(
                text = label(secondaryKey),
                onClick = { onSecondary?.invoke(screen) },
                enabled = onSecondary != null,
            )
        }
    }
}


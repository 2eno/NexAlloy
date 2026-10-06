package io.github.nexalloy.quiet.spotify.misc

import de.robv.android.xposed.XC_MethodReplacement
import io.github.nexalloy.patch
import io.github.nexalloy.quiet.spotify.canBindAppWidgetPermissionMethod

val FixThirdPartyLaunchersWidgets = patch(
    name = "Fix third party launchers widgets",
    description = "Allows the Spotify widgets to be added to third party launchers.",
) {
    // Only the stock launcher has the BIND_APPWIDGET permission Spotify checks for.
    ::canBindAppWidgetPermissionMethod.hookMethod(XC_MethodReplacement.returnConstant(true))
}

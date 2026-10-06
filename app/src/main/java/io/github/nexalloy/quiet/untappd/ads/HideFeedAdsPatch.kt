package io.github.nexalloy.quiet.untappd.ads

import app.quiet.extension.untappd.HideFeedAdsPatch
import de.robv.android.xposed.XposedHelpers
import io.github.nexalloy.hookMethod
import io.github.nexalloy.patch

val HideFeedAds = patch(
    name = "Hide feed ads",
    description = "Removes the ad slots from the activity feed.",
) {
    XposedHelpers.findMethodExact(
        "io.invertase.firebase.config.UniversalFirebaseConfigModule", classLoader,
        "getAllValuesForApp", String::class.java,
    ).hookMethod {
        after { HideFeedAdsPatch.overrideConfigValues(it.result) }
    }
}

package io.github.nexalloy.quiet.kleinanzeigen

import io.github.nexalloy.quiet.kleinanzeigen.ads.HideAds
import io.github.nexalloy.quiet.kleinanzeigen.privacy.SanitizeSharingLinks
import io.github.nexalloy.quiet.kleinanzeigen.pur.HidePur

val KleinanzeigenPatches = arrayOf(HideAds, HidePur, SanitizeSharingLinks)

package io.github.nexalloy.quiet.spotify

import io.github.nexalloy.quiet.spotify.ads.BlockPopupAds
import io.github.nexalloy.quiet.spotify.ads.HideAdSections
import io.github.nexalloy.quiet.spotify.ads.HideAdViews
import io.github.nexalloy.quiet.spotify.ads.HideContextMenuUpsells
import io.github.nexalloy.quiet.spotify.ads.HidePlaylistAds
import io.github.nexalloy.quiet.spotify.ads.HideVideoAds
import io.github.nexalloy.quiet.spotify.ads.MuteAudioAds
import io.github.nexalloy.quiet.spotify.layout.HidePremiumTab
import io.github.nexalloy.quiet.spotify.misc.FixThirdPartyLaunchersWidgets
import io.github.nexalloy.quiet.spotify.privacy.SanitizeSharingLinks
import io.github.nexalloy.quiet.spotify.theme.MaterialYouTheme
import io.github.nexalloy.quiet.spotify.theme.RoundedCorners

val SpotifyPatches = arrayOf(
    MuteAudioAds,
    HideAdSections,
    HideAdViews,
    HideContextMenuUpsells,
    BlockPopupAds,
    HidePlaylistAds,
    HideVideoAds,
    HidePremiumTab,
    FixThirdPartyLaunchersWidgets,
    SanitizeSharingLinks,
    MaterialYouTheme,
    RoundedCorners,
)

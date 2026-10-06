package io.github.nexalloy.twoeno.kleinanzeigen.ads

import io.github.nexalloy.morphe.findMethodListDirect

internal const val LIBERTY_PACKAGE = "de.kleinanzeigen.liberty"

/**
 * Ad placement lookups of the "Liberty" ad SDK, by page type (e.g. `LibertyPageType.PAGE_ATTR_HOME`):
 * `(LibertyPageType, position: Int, String, Boolean): AdPlacement?` and `(LibertyPageType, String): Map`.
 */
val adLookupMethods = findMethodListDirect {
    val pageType = findClass {
        searchPackages(LIBERTY_PACKAGE)
        matcher {
            superClass("java.lang.Enum")
            addField { name = "PAGE_ATTR_HOME" }
        }
    }.single().name

    findMethod {
        searchPackages(LIBERTY_PACKAGE)
        matcher { paramTypes(pageType, "int", "java.lang.String", "boolean") }
    } + findMethod {
        searchPackages(LIBERTY_PACKAGE)
        matcher { paramTypes(pageType, "java.lang.String") }
    }
}

package com.moyue.reader.source

data class CustomCatalogItem(
    val title: String? = null,
    val author: String? = null,
    val intro: String? = null,
    val category: String? = null,
    val url: String? = null
)

data class CustomSource(
    val id: String? = null,
    val name: String? = null,
    val tagline: String? = null,
    val baseUrl: String? = null,
    val searchable: Boolean = true,
    val explore: List<CustomCatalogItem>? = null,
    val exploreUrl: String? = null,
    val exploreList: String? = null,
    val exploreTitle: String? = null,
    val searchUrl: String? = null,
    val searchMethod: String? = "get",
    val searchBody: String? = null,
    val searchList: String? = null,
    val searchTitle: String? = "a",
    val searchAuthor: String? = null,
    val searchIntro: String? = null,
    val searchLink: String? = "a@href",
    val tocUrl: String? = "{bookUrl}",
    val tocLinkSelector: String? = "a[href]",
    val tocLinkRegex: String? = null,
    val tocNextSelector: String? = null,
    val tocSort: String? = "auto",
    val contentSelector: String? = null,
    val contentRemove: String? = null,
    val contentNextSelector: String? = null,
    val builtIn: Boolean = false
)

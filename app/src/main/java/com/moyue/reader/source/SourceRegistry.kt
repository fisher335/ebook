package com.moyue.reader.source

class SourceRegistry(
    private val builtInSources: List<BookSource>,
    private val customStore: CustomSourceStore,
    private val api: ApiClient
) {
    val customSources = customStore.sources

    fun allSources(): List<BookSource> {
        val custom = customStore.sources.value.map { RuleBookSource(it, api) }
        return builtInSources + custom
    }

    fun get(id: String): BookSource? =
        allSources().firstOrNull { it.id == id }

    fun addCustomSource(json: String): CustomSource = customStore.add(json)

    fun removeCustomSource(id: String) = customStore.remove(id)
}

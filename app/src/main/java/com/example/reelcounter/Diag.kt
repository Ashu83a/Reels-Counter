package com.example.reelcounter

// What the counter last saw on screen. The diagnostic screen shows this so a problem
// on a new phone can be fixed from a screenshot.
object Diag {
    @Volatile var time = 0L
    @Volatile var pkg = ""
    @Volatile var kind = 0
    @Volatile var ids = ""
    @Volatile var selected = ""
    @Volatile var items = ""
    @Volatile var pager = false
    @Volatile var fallback = false
    @Volatile var evals = 0
    @Volatile var counted = 0

    fun update(
        pkg: String, kind: Int, ids: Set<String>, selected: Set<String>,
        items: Set<String>, pager: Boolean, fallback: Boolean
    ) {
        this.time = System.currentTimeMillis()
        this.pkg = pkg
        this.kind = kind
        this.ids = ids.take(40).joinToString(", ")
        this.selected = selected.take(10).joinToString(", ")
        this.items = items.take(8).joinToString(" | ")
        this.pager = pager
        this.fallback = fallback
        this.evals = this.evals + 1
    }
}

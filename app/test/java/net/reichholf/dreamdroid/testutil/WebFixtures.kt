package net.reichholf.dreamdroid.testutil

/** Load XML fixtures packaged on the unit-test classpath (`test/resources/web`). */
fun loadWebFixture(name: String): String {
    val loader = checkNotNull(Thread.currentThread().contextClassLoader) {
        "No context ClassLoader for test fixture web/$name"
    }
    val stream = checkNotNull(
        loader.getResourceAsStream("web/$name")
            ?: object {}.javaClass.getResourceAsStream("/web/$name"),
    ) { "Missing test fixture web/$name" }
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

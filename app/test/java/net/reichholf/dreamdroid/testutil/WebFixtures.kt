package net.reichholf.dreamdroid.testutil

/** Load XML fixtures packaged on the unit-test classpath (`test/resources/web`). */
fun loadWebFixture(name: String): String = loadFixture("web/$name")

/** Load OpenWebif fixtures (`test/resources/owif`); each cites the OpenWebif source it follows. */
fun loadOwifFixture(name: String): String = loadFixture("owif/$name")

private fun loadFixture(path: String): String {
    val loader = checkNotNull(Thread.currentThread().contextClassLoader) {
        "No context ClassLoader for test fixture $path"
    }
    val stream = checkNotNull(
        loader.getResourceAsStream(path)
            ?: object {}.javaClass.getResourceAsStream("/$path")
    ) { "Missing test fixture $path" }
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

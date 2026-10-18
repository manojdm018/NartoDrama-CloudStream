version = 37

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

cloudstream {
    description = "Search and browse public Narto Drama listings"
    authors = listOf("manojdm018")
    status = 3 // Beta: playback has not yet been verified.
    tvTypes = listOf("TvSeries")
}

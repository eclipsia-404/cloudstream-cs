import org.jetbrains.kotlin.konan.properties.Properties

version = 438
android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TMDB_KEY", "\"${properties.getProperty("TMDB_KEY")}\"")
        buildConfigField("String", "CC_COOKIE", "\"${properties.getProperty("CC_COOKIE")}\"")
    }
}

cloudstream {
    language = "en"
    description = "A Curated Collection of Providers for Reliability and Performance"
    authors = listOf("eclipsia")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama",
        "Anime",
        "Torrent"
    )

    iconUrl = "https://codeberg.org/eclipsia/eclipsia-cloudstream/raw/branch/main/icons/cs.png"
}

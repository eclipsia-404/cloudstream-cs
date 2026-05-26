import org.jetbrains.kotlin.konan.properties.Properties

version = 438
android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API")}\"")
        buildConfigField("String", "TMDB_KEY", "\"${properties.getProperty("TMDB_KEY")}\"")
    }
}

cloudstream {
    language = "en"
    description = "Movies, Series and Anime"
    authors = listOf("eclipsia")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime"
    )

    iconUrl = "https://codeberg.org/eclipsia/eclipsia-cloudstream/raw/branch/main/icons/cs.png"
}

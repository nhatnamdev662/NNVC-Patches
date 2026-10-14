extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.nnvc.chess.extension"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "PATCH_VERSION", "\"${project.version}\"")
    }
}

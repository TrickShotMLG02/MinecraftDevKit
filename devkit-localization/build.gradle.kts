dependencies {
    api(project(":devkit-text"))
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.0")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.26.1")
}

dependencies {
    api(platform("org.incendo:cloud-bom:2.1.0"))
    api("org.incendo:cloud-core")
    api(project(":devkit-core"))
    api(project(":devkit-text"))
    api(project(":devkit-permissions"))
    implementation("net.kyori:adventure-text-serializer-plain:4.26.1")
}

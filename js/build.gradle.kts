plugins {
    id("com.github.node-gradle.node") version "3.2.0"
}

val runWebpack by tasks.creating(com.github.gradle.node.npm.task.NpxTask::class) {
    dependsOn(tasks.getByName("yarn_install"))
    command.set("webpack")
}

tasks.create("build") {
    dependsOn(runWebpack)
}
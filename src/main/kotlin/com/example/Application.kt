package com.example

import io.ktor.client.plugins.websocket.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureSockets()
    configureNegotation()
    configureDatabases()
    configureAdministration()
    configureMonitoring()
    configureRouting()

}

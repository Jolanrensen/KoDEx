package nl.jolanrensen.example

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

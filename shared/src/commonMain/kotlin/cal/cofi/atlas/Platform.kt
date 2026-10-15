package cal.cofi.atlas

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
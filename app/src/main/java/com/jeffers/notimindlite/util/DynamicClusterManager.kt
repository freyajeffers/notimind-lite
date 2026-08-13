package com.jeffers.notimindlite.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamically discovers, expands, and builds comprehensive semantic category clusters based on
 * installed applications, Android's ApplicationInfo.CATEGORY_* metadata, package classifications,
 * and high-dimensional semantic domain vocabularies.
 */
object DynamicClusterManager {
    private const val TAG = "DynamicClusterManager"

    // Maps category names to dynamically expanded keyword and app name clusters
    private val dynamicClusters = ConcurrentHashMap<String, MutableSet<String>>()

    // Comprehensive base vocabulary per domain category (50+ keywords per cluster)
    private val BASE_CATEGORY_VOCABULARY: Map<String, List<String>> = mapOf(
        "finance" to listOf(
            "finance", "banking", "money", "payment", "receipt", "transfer", "balance", "credit",
            "debit", "card", "wallet", "cash", "bill", "invoice", "charge", "crypto", "bitcoin",
            "ethereum", "paypal", "venmo", "zelle", "chase", "revolut", "cashapp", "transaction",
            "deposit", "withdrawal", "refund", "budget", "invest", "stock", "dividend", "interest",
            "loan", "atm", "statement", "wire", "autopay", "overdraft", "savings", "checking",
            "capital", "fidelity", "vanguard", "schwab", "coinbase", "binance", "stripe", "square",
            "monzo", "sofi", "mint", "quickbooks", "robinhood", "wealth", "forex", "ledger"
        ),
        "delivery" to listOf(
            "delivery", "food", "order", "package", "track", "courier", "uber", "eats", "doordash",
            "grubhub", "postmates", "instacart", "amazon", "fedex", "ups", "dhl", "usps", "shipment",
            "shipped", "arriving", "driver", "restaurant", "takeout", "groceries", "dispatch", "parcel",
            "carrier", "mailbox", "dropoff", "transit", "out for delivery", "delivered", "logistics",
            "tracking", "freight", "cargo", "order status", "meal", "pizza", "burger", "coffee",
            "starbucks", "seamless", "deliveroo", "caviar", "ontrac", "lasership", "prime", "fresh"
        ),
        "security" to listOf(
            "code", "otp", "password", "2fa", "mfa", "security", "verify", "verification", "login",
            "auth", "authentication", "pin", "token", "alert", "suspicious", "passcode", "biometric",
            "fingerprint", "key", "unauthorized", "confirm", "session", "credential", "identity",
            "reset", "duo", "authenticator", "yubikey", "access", "permission", "lockout", "breach",
            "secure", "shield", "protect", "antivirus", "malware", "firewall", "vpn", "sign in",
            "temporary code", "verification code", "one-time", "passkey", "authenticating"
        ),
        "social" to listOf(
            "social", "chat", "message", "dm", "text", "conversation", "talk", "post", "friend",
            "reply", "typing", "sms", "mms", "whatsapp", "telegram", "signal", "messenger",
            "discord", "slack", "instagram", "facebook", "twitter", "threads", "tiktok", "snapchat",
            "reddit", "linkedin", "group", "call", "voice", "channel", "mention", "reaction",
            "comment", "like", "share", "retweet", "follower", "story", "feed", "wechat", "viber",
            "line", "skype", "teams", "status", "streamer", "subscriber", "unread", "direct message"
        ),
        "travel" to listOf(
            "travel", "flight", "ride", "trip", "hotel", "uber", "lyft", "airline", "train",
            "bus", "ticket", "gate", "boarding", "terminal", "booking", "reservation", "itinerary",
            "airport", "checkin", "delay", "departure", "arrival", "luggage", "transit", "commute",
            "cab", "driver", "airbnb", "expedia", "delta", "united", "american airlines", "southwest",
            "amtrak", "subway", "metro", "passport", "visa", "rental", "hertz", "enterprise",
            "hostel", "destination", "layover", "seat", "route", "mileage", "frequent flyer"
        ),
        "shopping" to listOf(
            "shopping", "deal", "discount", "sale", "coupon", "promo", "cart", "checkout", "store",
            "purchase", "bought", "item", "price", "offer", "clearance", "bogo", "ebay", "walmart",
            "target", "etsy", "shop", "retail", "receipt", "wishlist", "buy", "merchant", "savings",
            "reward", "points", "cashback", "mall", "black friday", "cyber monday", "voucher",
            "haul", "outlet", "bestbuy", "costco", "aliexpress", "shein", "temu", "order placed"
        ),
        "productivity" to listOf(
            "productivity", "work", "office", "task", "todo", "calendar", "document", "sheet",
            "note", "meeting", "reminder", "agenda", "schedule", "alarm", "appointment", "deadline",
            "zoom", "teams", "meet", "conference", "due", "sync", "planner", "organizer", "memo",
            "notion", "trello", "asana", "jira", "docs", "excel", "powerpoint", "word", "workspace",
            "evernote", "obsidian", "github", "gitlab", "bitbucket", "linear", "basecamp", "monday"
        ),
        "media" to listOf(
            "media", "audio", "music", "song", "track", "podcast", "radio", "sound", "stream",
            "video", "movie", "film", "tv", "clip", "watch", "show", "play", "pause", "spotify",
            "youtube", "netflix", "hulu", "disney", "prime video", "hbo", "max", "twitch", "apple music",
            "soundcloud", "pandora", "deezer", "tidal", "vimeo", "audible", "episode", "playlist",
            "album", "artist", "playing", "now playing", "headphones", "bluetooth audio", "equalizer"
        ),
        "health" to listOf(
            "health", "fitness", "workout", "gym", "run", "steps", "heart", "pulse", "sleep",
            "calories", "water", "doctor", "medicine", "prescription", "pharmacy", "appointment",
            "hospital", "clinic", "diet", "weight", "exercise", "walk", "activity", "fitbit",
            "garmin", "strava", "myfitnesspal", "headspace", "calm", "meditation", "blood pressure",
            "glucose", "hydration", "cycle", "wellness", "cardio", "nutrition", "vitamins", "dosage"
        ),
        "smarthome" to listOf(
            "home", "smart", "thermostat", "light", "lock", "doorbell", "camera", "motion",
            "sensor", "nest", "ring", "alexa", "google home", "hue", "alarm", "garage", "temperature",
            "plug", "device", "security cam", "smart life", "tuya", "matter", "zigbee", "z-wave",
            "automation", "scene", "bulb", "switch", "vacuum", "roborock", "roomba", "appliance"
        ),
        "game" to listOf(
            "game", "gaming", "play", "player", "score", "level", "quest", "steam", "xbox",
            "playstation", "nintendo", "achievement", "trophy", "multiplayer", "match", "tournament",
            "guild", "clan", "raid", "respawn", "battle pass", "season", "arcade", "rpg", "fps",
            "mmo", "gamer", "esports", "epic games", "roblox", "minecraft", "fortnite", "genshin"
        ),
        "system" to listOf(
            "system", "battery", "update", "charging", "charger", "wifi", "bluetooth", "network",
            "storage", "download", "install", "memory", "cpu", "reboot", "os", "signal", "hotspot",
            "usb", "connected", "disconnected", "performance", "cleaner", "backup", "restore",
            "firmware", "patch", "data usage", "airplane mode", "low battery", "fully charged",
            "android", "hardware", "diagnostic", "permission", "security patch", "developer"
        )
    )

    // Mapping Android OS ApplicationInfo.CATEGORY_* constants to our semantic category names
    private val OS_CATEGORY_MAPPING: Map<Int, String> = mapOf(
        ApplicationInfo.CATEGORY_GAME to "game",
        ApplicationInfo.CATEGORY_AUDIO to "media",
        ApplicationInfo.CATEGORY_VIDEO to "media",
        ApplicationInfo.CATEGORY_IMAGE to "media",
        ApplicationInfo.CATEGORY_SOCIAL to "social",
        ApplicationInfo.CATEGORY_NEWS to "social",
        ApplicationInfo.CATEGORY_MAPS to "travel",
        ApplicationInfo.CATEGORY_PRODUCTIVITY to "productivity",
        ApplicationInfo.CATEGORY_ACCESSIBILITY to "system"
    )

    @Volatile
    private var isInitialized = false

    /**
     * Initializes dynamic clusters by combining base vocabularies with real-time
     * package and application metadata from Android's PackageManager.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                // 1. Populate base vocabularies for all categories
                for ((category, words) in BASE_CATEGORY_VOCABULARY) {
                    val cluster = dynamicClusters.getOrPut(category) { ConcurrentHashMap.newKeySet() }
                    cluster.addAll(words)
                }

                // 2. Discover installed applications and assign them dynamically
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)

                for (app in apps) {
                    val appLabel = try {
                        pm.getApplicationLabel(app).toString().lowercase()
                    } catch (e: Exception) {
                        null
                    }
                    val pkgName = app.packageName.lowercase()
                    val catId = app.category

                    // Check OS category mapping
                    val mappedCategory = if (catId != ApplicationInfo.CATEGORY_UNDEFINED) {
                        OS_CATEGORY_MAPPING[catId]
                    } else {
                        // Keyword-based package inference for non-tagged apps
                        inferCategoryFromPackageName(pkgName, appLabel)
                    }

                    if (mappedCategory != null && dynamicClusters.containsKey(mappedCategory)) {
                        val cluster = dynamicClusters[mappedCategory]!!
                        if (!appLabel.isNullOrBlank()) {
                            cluster.add(appLabel)
                            cluster.addAll(appLabel.split("\\s+".toRegex()).filter { it.length > 2 })
                        }
                        val pkgParts = pkgName.split(".")
                        val meaningfulParts = pkgParts.filter { 
                            it != "com" && it != "android" && it != "app" && it != "mobile" && it != "apps" && it.length > 2 
                        }
                        cluster.addAll(meaningfulParts)
                    }
                }

                isInitialized = true
                Log.d(TAG, "Initialized ${dynamicClusters.size} dynamic clusters with over ${dynamicClusters.values.sumOf { it.size }} words.")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing dynamic clusters from PackageManager", e)
            }
        }
    }

    private fun inferCategoryFromPackageName(pkgName: String, appLabel: String?): String? {
        val combined = "$pkgName ${appLabel ?: ""}".lowercase()
        return when {
            combined.contains("bank") || combined.contains("pay") || combined.contains("wallet") || combined.contains("money") || combined.contains("finance") || combined.contains("crypto") -> "finance"
            combined.contains("food") || combined.contains("eat") || combined.contains("delivery") || combined.contains("order") || combined.contains("courier") || combined.contains("transit") -> "delivery"
            combined.contains("auth") || combined.contains("otp") || combined.contains("pass") || combined.contains("secure") || combined.contains("vpn") -> "security"
            combined.contains("flight") || combined.contains("hotel") || combined.contains("ride") || combined.contains("airline") || combined.contains("trip") || combined.contains("travel") -> "travel"
            combined.contains("shop") || combined.contains("store") || combined.contains("deal") || combined.contains("cart") || combined.contains("mall") -> "shopping"
            combined.contains("fit") || combined.contains("health") || combined.contains("gym") || combined.contains("run") || combined.contains("doctor") || combined.contains("med") -> "health"
            combined.contains("home") || combined.contains("smart") || combined.contains("camera") || combined.contains("iot") || combined.contains("nest") || combined.contains("ring") -> "smarthome"
            combined.contains("game") || combined.contains("play") || combined.contains("arcade") || combined.contains("rpg") -> "game"
            combined.contains("chat") || combined.contains("message") || combined.contains("social") || combined.contains("talk") || combined.contains("mail") -> "social"
            combined.contains("task") || combined.contains("note") || combined.contains("cal") || combined.contains("office") || combined.contains("doc") || combined.contains("meet") -> "productivity"
            combined.contains("music") || combined.contains("video") || combined.contains("audio") || combined.contains("stream") || combined.contains("tube") || combined.contains("tv") -> "media"
            else -> null
        }
    }

    /**
     * Retrieves all discovered dynamic clusters and their associated vocabularies.
     */
    fun getDynamicClusters(): Map<String, Set<String>> {
        if (!isInitialized) {
            // Provide base vocabularies even before async initialization finishes
            for ((category, words) in BASE_CATEGORY_VOCABULARY) {
                val cluster = dynamicClusters.getOrPut(category) { ConcurrentHashMap.newKeySet() }
                cluster.addAll(words)
            }
        }
        return dynamicClusters
    }

    /**
     * Finds matching cluster names for a given set of query tokens.
     */
    fun findMatchingClusters(queryTokens: List<String>): Set<String> {
        val clusters = getDynamicClusters()
        val matched = mutableSetOf<String>()
        for (token in queryTokens) {
            val lowerToken = token.lowercase()
            for ((clusterName, keywords) in clusters) {
                if (lowerToken == clusterName || keywords.contains(lowerToken)) {
                    matched.add(clusterName)
                }
            }
        }
        return matched
    }
}

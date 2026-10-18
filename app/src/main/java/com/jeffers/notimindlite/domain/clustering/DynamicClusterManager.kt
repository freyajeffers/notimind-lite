package com.jeffers.notimindlite.domain.clustering

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class ClusterNode(
    val name: String,
    val keywords: MutableSet<String> = mutableSetOf(),
    val subClusters: MutableMap<String, ClusterNode> = ConcurrentHashMap()
)

object DynamicClusterManager {
    private const val TAG = "DynamicClusterManager"

    private val rootClusters = ConcurrentHashMap<String, ClusterNode>()

    private val HIERARCHICAL_VOCABULARY: Map<String, Map<String, List<String>>> = mapOf(
        "finance" to mapOf(
            "banking" to listOf(
                "banking",
                "money",
                "payment",
                "receipt",
                "transfer",
                "balance",
                "credit",
                "debit",
                "card",
                "wallet",
                "cash",
                "bill",
                "invoice",
                "charge",
                "chase",
                "revolut",
                "cashapp",
                "transaction",
                "deposit",
                "withdrawal",
                "refund",
                "budget",
                "atm",
                "statement",
                "wire",
                "autopay",
                "overdraft",
                "savings",
                "checking",
                "monzo",
                "sofi",
                "mint",
                "quickbooks",
            ),
            "crypto" to listOf(
                "crypto", "bitcoin", "ethereum", "coinbase", "binance", "ledger",
                "forex", "web3", "wallet", "token", "nft", "blockchain"
            ),
            "investing" to listOf(
                "invest", "stock", "dividend", "interest", "capital", "fidelity",
                "vanguard", "schwab", "robinhood", "wealth", "apr", "bonus",
                "cashback", "payout", "settlement", "remittance", "tax", "irs", "yield"
            )
        ),
        "delivery" to mapOf(
            "food" to listOf(
                "food", "restaurant", "takeout", "groceries", "meal", "pizza", "burger", "coffee",
                "starbucks", "seamless", "deliveroo", "caviar", "doordash", "uber eats", "grubhub", "postmates"
            ),
            "logistics" to listOf(
                "delivery", "package", "track", "courier", "amazon", "fedex", "ups", "dhl", "usps",
                "shipment", "shipped", "arriving", "driver", "dispatch", "parcel", "carrier", "mailbox",
                "dropoff", "transit", "out for delivery", "delivered", "logistics", "tracking", "freight",
                "cargo", "order status", "ontrac", "lasership", "prime", "fresh", "shipt", "gopuff",
                "cart", "fulfilled", "estimated arrival", "in transit", "locker"
            )
        ),
        "security" to mapOf(
            "authentication" to listOf(
                "code", "otp", "password", "2fa", "mfa", "verify", "verification", "login",
                "auth", "authentication", "pin", "token", "passcode", "biometric", "fingerprint",
                "key", "confirm", "session", "credential", "identity", "reset", "duo",
                "authenticator", "yubikey", "temporary code", "verification code", "one-time",
                "passkey", "authenticating"
            ),
            "protection" to listOf(
                "security", "alert", "suspicious", "unauthorized", "breach", "secure", "shield",
                "protect", "antivirus", "malware", "firewall", "vpn", "sign in", "compromised",
                "detected", "unrecognized device", "security key", "vault", "encryption", "trusted device"
            )
        ),
        "social" to mapOf(
            "messaging" to listOf(
                "chat", "message", "dm", "text", "conversation", "talk", "sms", "mms",
                "whatsapp", "telegram", "signal", "messenger", "discord", "slack", "direct message",
                "ping", "huddle", "voice note", "inbox message", "sent a photo"
            ),
            "networking" to listOf(
                "social", "post", "friend", "reply", "typing", "instagram", "facebook",
                "twitter", "threads", "tiktok", "snapchat", "reddit", "linkedin",
                "group", "call", "voice", "channel", "mention", "reaction", "comment",
                "like", "share", "retweet", "follower", "story", "feed", "wechat",
                "viber", "line", "skype", "teams", "status", "streamer", "subscriber",
                "unread"
            )
        ),
        "travel" to mapOf(
            "transport" to listOf(
                "ride", "trip", "uber", "lyft", "train", "bus", "ticket", "cab",
                "driver", "amtrak", "subway", "metro", "rental", "hertz", "enterprise"
            ),
            "aviation" to listOf(
                "travel", "flight", "airline", "gate", "boarding", "terminal", "airport", "checkin",
                "delay", "departure", "arrival", "luggage", "delta", "united", "american airlines",
                "southwest", "boarding pass", "tsa", "baggage claim"
            ),
            "accommodation" to listOf(
                "hotel", "booking", "reservation", "itinerary", "airbnb", "expedia", "hostel",
                "destination", "layover", "seat", "route", "mileage", "frequent flyer", "kayak",
                "priceline", "hopper", "cruise"
            )
        ),
        "shopping" to mapOf(
            "retail" to listOf(
                "shopping", "deal", "discount", "sale", "coupon", "promo", "cart", "checkout",
                "store", "purchase", "bought", "item", "price", "offer", "clearance", "bogo",
                "ebay", "walmart", "target", "etsy", "shop", "retail", "receipt", "wishlist",
                "buy", "merchant", "savings", "reward", "points", "cashback", "mall",
                "black friday", "cyber monday", "voucher", "haul", "outlet", "bestbuy",
                "costco", "aliexpress", "shein", "temu", "order placed", "promo code",
                "rebate", "gift card", "price drop", "flash sale", "back in stock"
            )
        ),
        "productivity" to mapOf(
            "organization" to listOf(
                "productivity", "work", "office", "task", "todo", "calendar", "document", "sheet",
                "note", "meeting", "reminder", "agenda", "schedule", "alarm", "appointment",
                "deadline", "planner", "organizer", "memo", "kanban", "sprint", "milestone", "project"
            ),
            "collaboration" to listOf(
                "zoom", "teams", "meet", "conference", "due", "sync", "notion", "trello", "asana",
                "jira", "docs", "excel", "powerpoint", "word", "workspace", "evernote", "obsidian",
                "github", "gitlab", "bitbucket", "linear", "basecamp", "monday", "presentation",
                "spreadsheet", "confluence"
            )
        ),
        "media" to mapOf(
            "audio" to listOf(
                "media", "audio", "music", "song", "track", "podcast", "radio", "sound", "stream",
                "spotify", "soundcloud", "pandora", "deezer", "tidal", "audible", "playlist", "album",
                "artist", "playing", "now playing", "headphones", "bluetooth audio", "equalizer"
            ),
            "video" to listOf(
                "video", "movie", "film", "tv", "clip", "watch", "show", "play", "pause", "youtube",
                "netflix", "hulu", "disney", "prime video", "hbo", "max", "twitch", "apple music",
                "vimeo", "episode", "trailer", "season", "premiere", "broadcast", "channel", "curated",
                "soundtrack"
            )
        ),
        "health" to mapOf(
            "fitness" to listOf(
                "health", "fitness", "workout", "gym", "run", "steps", "heart", "pulse", "sleep",
                "calories", "water", "exercise", "walk", "activity", "fitbit", "garmin", "strava",
                "myfitnesspal", "peloton", "nike run", "whoop", "cardio", "nutrition"
            ),
            "medical" to listOf(
                "doctor", "medicine", "prescription", "pharmacy", "appointment", "hospital", "clinic",
                "diet", "weight", "headspace", "calm", "meditation", "blood pressure", "glucose",
                "hydration", "cycle", "wellness", "vitamins", "dosage", "apple health", "telehealth",
                "refill", "dentist"
            )
        ),
        "smarthome" to mapOf(
            "control" to listOf(
                "home", "smart", "thermostat", "light", "lock", "doorbell", "camera",
                "motion", "sensor", "nest", "ring", "alexa", "google home", "hue",
                "alarm", "garage", "temperature", "plug", "device", "security cam",
                "smart life", "tuya", "matter", "zigbee", "z-wave", "automation",
                "scene", "bulb", "switch", "vacuum", "roborock", "roomba", "appliance",
                "ecobee", "simplisafe", "arlo", "nanoleaf", "smartthings", "lutron",
                "refrigerator"
            )
        ),
        "game" to mapOf(
            "platform" to listOf(
                "game", "gaming", "play", "player", "score", "level", "quest", "steam",
                "xbox", "playstation", "nintendo", "achievement", "trophy", "multiplayer",
                "match", "tournament", "guild", "clan", "raid", "respawn", "battle pass",
                "season", "arcade", "rpg", "fps", "mmo", "gamer", "esports", "epic games",
                "roblox", "minecraft", "fortnite", "genshin", "valorant", "league of legends",
                "discord gaming", "controller", "leaderboard", "inventory"
            )
        ),
        "system" to mapOf(
            "os" to listOf(
                "system", "battery", "update", "charging", "charger", "wifi", "bluetooth", "network",
                "storage", "download", "install", "memory", "cpu", "reboot", "os", "signal",
                "hotspot", "usb", "connected", "disconnected", "performance", "cleaner",
                "backup", "restore", "firmware", "patch", "data usage", "airplane mode",
                "low battery", "fully charged", "android", "hardware", "diagnostic", "permission",
                "security patch", "developer", "thermal"
            )
        ),
        "education" to mapOf(
            "learning" to listOf(
                "education", "learning", "course", "class", "school", "student", "university",
                "college", "lesson", "study", "homework", "assignment", "exam", "grade",
                "duolingo", "canvas", "blackboard", "coursera", "udemy", "khan", "quizlet",
                "tutor", "lecture", "textbook", "academic", "degree", "syllabus", "flashcards",
                "practice", "vocabulary", "math", "science", "language", "edx", "skillshare",
                "classroom", "semester", "diploma", "gpa", "quiz"
            )
        ),
        "news" to mapOf(
            "press" to listOf(
                "news", "article", "headline", "breaking", "journal", "magazine", "newspaper",
                "press", "editor", "broadcast", "report", "edition", "nytimes", "wsj", "bbc",
                "cnn", "reuters", "bloomberg", "apnews", "feed", "newsletter", "politics",
                "world", "local", "weather", "forecast", "radar", "temperature", "storm",
                "rain", "snow", "climate", "huffpost", "washington post", "economist",
                "guardian", "daily", "breaking news", "front page"
            )
        ),
        "food" to mapOf(
            "culinary" to listOf(
                "recipe", "cooking", "kitchen", "baking", "ingredients", "dinner", "lunch",
                "breakfast", "snack", "restaurant", "reservation", "opentable", "yelp",
                "menu", "dining", "coffee", "barista", "cocktail", "wine", "beer", "brewery",
                "tasting", "dish", "grocery", "supermarket", "produce", "bakery", "deli",
                "culinary", "flavor", "cookbook", "tasty", "allrecipes", "nytcars",
                "bon appetit", "roast", "grill", "dessert", "appetizer"
            )
        ),
        "realestate" to mapOf(
            "property" to listOf(
                "real estate", "housing", "apartment", "house", "home", "rent", "lease",
                "mortgage", "tenant", "landlord", "zillow", "redfin", "realtor", "trulia",
                "property", "listing", "inspection", "closing", "broker", "condo", "townhouse",
                "sublet", "escrow", "hoa", "realty", "move", "moving", "neighborhood",
                "open house", "square feet", "land", "appraisal"
            )
        ),
        "automotive" to mapOf(
            "vehicle" to listOf(
                "auto", "car", "vehicle", "truck", "motorcycle", "drive", "driving", "gas",
                "fuel", "ev", "charging", "tesla", "supercharger", "service", "maintenance",
                "oil", "tire", "mechanic", "dealer", "mileage", "odometer", "engine",
                "battery", "brake", "parking", "toll", "speed", "traffic", "accident",
                "insurance", "geico", "progressive", "statefarm", "waze", "gas station",
                "carplay", "android auto", "transmission", "recall", "registration"
            )
        ),
        "dating" to mapOf(
            "romance" to listOf(
                "dating", "match", "tinder", "bumble", "hinge", "okcupid", "coffee meets bagel",
                "relationship", "single", "date", "crush", "swipe", "profile", "meetup",
                "flirt", "romance", "anniversary", "partner", "couple", "marriage", "wedding",
                "engaged", "matchmaking", "sparks", "speed date"
            )
        ),
        "events" to mapOf(
            "entertainment" to listOf(
                "tickets", "concert", "festival", "event", "show", "theater", "broadway", "ticketmaster",
                "stubhub", "seatgeek", "livenation", "venue", "stadium", "arena", "vip", "pass",
                "wristband", "lineup", "performer", "stage", "curtain", "seating", "box office",
                "tour", "showtime", "general admission", "doors open", "headliner", "merch",
                "amphitheater"
            )
        ),
        "utilities" to mapOf(
            "services" to listOf(
                "utility", "electric", "power", "water", "gas", "internet", "wifi", "broadband",
                "cable", "mobile", "cellular", "sim", "verizon", "att", "tmobile", "sprint",
                "vodafone", "billing", "meter", "outage", "telecom", "provider", "hotspot",
                "roaming", "data plan", "carrier", "spectrum", "xfinity", "comcast",
                "conedison", "pge", "energy", "kilowatt", "fiber"
            )
        ),
        "pets" to mapOf(
            "care" to listOf(
                "pet", "dog", "cat", "puppy", "kitten", "vet", "veterinary", "clinic", "vaccine",
                "chewy", "rover", "barkbox", "grooming", "walk", "walker", "foster", "adoption",
                "shelter", "rescue", "collar", "microchip", "leash", "paws", "litter", "pet food",
                "kennel", "boarding", "flea", "tick", "rabies", "petco", "petsmart", "aquarium",
                "bird", "reptile"
            )
        ),
        "career" to mapOf(
            "employment" to listOf(
                "job", "career", "recruiter", "interview", "hiring", "resume", "cv", "application",
                "applied", "position", "salary", "offer", "linkedin", "indeed", "glassdoor", "monster",
                "ziprecruiter", "employment", "employer", "candidate", "promotion", "workplace", "hr",
                "onboarding", "headhunter", "job alert", "requisition", "cover letter", "internship",
                "benefits"
            )
        )
    )

    internal val PACKAGE_DOMAIN_MARKERS: List<Pair<String, List<String>>> = listOf(
        "finance" to listOf("bank", "pay", "wallet", "money", "finance", "crypto"),
        "delivery" to listOf("food", "eat", "delivery", "order", "courier", "transit"),
        "security" to listOf("auth", "otp", "pass", "secure", "vpn"),
        "travel" to listOf("flight", "hotel", "ride", "airline", "trip", "travel"),
        "shopping" to listOf(
            "shopping", "deal", "discount", "sale", "coupon"
        ),
        "health" to listOf("fit", "health", "gym", "run", "doctor", "med"),
        "smarthome" to listOf("home", "smart", "camera", "iot", "nest", "ring"),
        "game" to listOf("game", "play", "arcade", "rpg"),
        "social" to listOf("chat", "message", "social", "talk", "mail"),
        "productivity" to listOf("task", "note", "cal", "office", "doc", "meet"),
        "media" to listOf("music", "video", "audio", "stream", "tube", "tv"),
        "education" to listOf("learn", "edu", "course", "school", "study"),
        "news" to listOf("news", "press", "weather"),
        "food" to listOf("recipe", "cook", "dine"),
        "realestate" to listOf("zillow", "rent", "estate"),
        "automotive" to listOf("car", "auto", "drive", "gas"),
        "dating" to listOf("date", "match", "tinder"),
        "events" to listOf("ticket", "event", "concert"),
        "utilities" to listOf("telecom", "mobile", "utility"),
        "pets" to listOf("pet", "vet", "dog", "cat"),
        "career" to listOf("job", "career", "hire", "workplace")
    )

    internal val OS_CATEGORY_MAPPING: Map<Int, String> = mapOf(
        ApplicationInfo.CATEGORY_GAME to "game",
        ApplicationInfo.CATEGORY_AUDIO to "media",
        ApplicationInfo.CATEGORY_VIDEO to "media",
        ApplicationInfo.CATEGORY_IMAGE to "media",
        ApplicationInfo.CATEGORY_SOCIAL to "social",
        ApplicationInfo.CATEGORY_NEWS to "news",
        ApplicationInfo.CATEGORY_MAPS to "travel",
        ApplicationInfo.CATEGORY_PRODUCTIVITY to "productivity",
        ApplicationInfo.CATEGORY_ACCESSIBILITY to "system"
    )

    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                initializeVocabulary()
                enrichInstalledApplications(context.packageManager)
                isInitialized = true
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Unable to resolve an installed application", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Unable to inspect installed applications", e)
            }
        }
    }

    private fun initializeVocabulary() {
        HIERARCHICAL_VOCABULARY.forEach { (domain, subMap) ->
            val domainNode = rootClusters.getOrPut(domain) { ClusterNode(domain) }
            subMap.forEach { (subCategory, words) ->
                val subNode = domainNode.subClusters.getOrPut(subCategory) { ClusterNode(subCategory) }
                subNode.keywords.addAll(words)
                domainNode.keywords.addAll(words)
            }
        }
    }

    private fun enrichInstalledApplications(packageManager: PackageManager) {
        packageManager
            .getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .forEach { app -> enrichApplication(packageManager, app) }
    }

    private fun enrichApplication(packageManager: PackageManager, app: ApplicationInfo) {
        val appLabel = applicationLabel(packageManager, app)
        val packageName = app.packageName.lowercase()
        val domain = appDomain(app, packageName, appLabel) ?: return
        val domainNode = rootClusters[domain] ?: return
        val meaningfulParts = meaningfulPackageParts(packageName)
        domainNode.keywords.addAll(appKeywords(appLabel, meaningfulParts))
        val combined = "$packageName ${appLabel ?: ""}".lowercase()
        addMatchingSubcategoryKeywords(domainNode, combined, appLabel, meaningfulParts)
    }

    // Helper functions moved to DynamicClusterHelpers.kt to reduce object size

    fun getDynamicClusters(): Map<String, Set<String>> {
        val flatMap = mutableMapOf<String, Set<String>>()
        for ((domain, node) in rootClusters) {
            flatMap[domain] = node.keywords
            for ((subName, subNode) in node.subClusters) {
                flatMap[subName] = subNode.keywords
            }
        }
        return flatMap
    }

    fun getHierarchicalClusters(): Map<String, ClusterNode> {
        return rootClusters
    }

    fun getSubClusters(domain: String): Map<String, ClusterNode>? {
        return rootClusters[domain]?.subClusters
    }

    fun findMatchingClusters(queryTokens: List<String>): Map<String, Set<ClusterNode>> {
        return queryTokens.asSequence()
            .flatMap { token -> matchesForToken(token) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, nodes) -> nodes.toSet() }
    }

    private fun matchesForToken(token: String): Sequence<Pair<String, ClusterNode>> {
        val lowerToken = token.lowercase()
        return rootClusters.asSequence().flatMap { (domainName, domainNode) ->
            val domainMatch = if (lowerToken == domainName || domainNode.keywords.contains(lowerToken)) {
                sequenceOf(domainName to domainNode)
            } else {
                emptySequence()
            }
            val subMatches = domainNode.subClusters.asSequence()
                .filter { (subName, subNode) ->
                    lowerToken == subName || subNode.keywords.contains(lowerToken)
                }
                .map { (_, subNode) -> domainName to subNode }
            domainMatch + subMatches
        }
    }
}

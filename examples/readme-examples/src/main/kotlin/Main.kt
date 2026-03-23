@file:Suppress("unused", "RedundantSuspendModifier", "UNUSED_VARIABLE")

import applicative.*
import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ═══════════════════════════════════════════════════════════════════════
//  Shared domain types & simulated services
// ═══════════════════════════════════════════════════════════════════════

data class UserProfile(val name: String, val id: Long)
data class ShoppingCart(val items: Int, val total: Double)
data class PromotionBundle(val code: String, val discountPct: Int)
data class InventorySnapshot(val allInStock: Boolean)
data class StockConfirmation(val confirmed: Boolean)
data class ShippingQuote(val amount: Double, val method: String)
data class TaxBreakdown(val amount: Double, val rate: Double)
data class DiscountSummary(val amount: Double, val promoApplied: String)
data class PaymentAuth(val cardLast4: String, val authorized: Boolean)
data class OrderConfirmation(val orderId: String)
data class EmailReceipt(val sentTo: String, val orderId: String)

data class CheckoutResult(
    val user: UserProfile,
    val cart: ShoppingCart,
    val promos: PromotionBundle,
    val inventory: InventorySnapshot,
    val stock: StockConfirmation,
    val shipping: ShippingQuote,
    val tax: TaxBreakdown,
    val discounts: DiscountSummary,
    val payment: PaymentAuth,
    val confirmation: OrderConfirmation,
    val email: EmailReceipt,
)

suspend fun fetchUser(): UserProfile { delay(50); return UserProfile("Alice", 42) }
suspend fun fetchCart(): ShoppingCart { delay(40); return ShoppingCart(3, 147.50) }
suspend fun fetchPromos(): PromotionBundle { delay(30); return PromotionBundle("SUMMER20", 20) }
suspend fun fetchInventory(): InventorySnapshot { delay(50); return InventorySnapshot(allInStock = true) }
suspend fun validateStock(): StockConfirmation { delay(20); return StockConfirmation(confirmed = true) }
suspend fun calcShipping(): ShippingQuote { delay(30); return ShippingQuote(5.99, "standard") }
suspend fun calcTax(): TaxBreakdown { delay(20); return TaxBreakdown(12.38, 0.08) }
suspend fun calcDiscounts(): DiscountSummary { delay(15); return DiscountSummary(29.50, "SUMMER20") }
suspend fun reservePayment(): PaymentAuth { delay(40); return PaymentAuth("4242", authorized = true) }
suspend fun generateConfirmation(): OrderConfirmation { delay(30); return OrderConfirmation("order-#90142") }
suspend fun sendEmail(): EmailReceipt { delay(20); return EmailReceipt("alice@example.com", "order-#90142") }

suspend fun fetchName(): String { delay(30); return "Alice" }
suspend fun fetchAge(): Int { delay(20); return 30 }

// ═══════════════════════════════════════════════════════════════════════
//  NEW: "A constructor is a function"
// ═══════════════════════════════════════════════════════════════════════

data class Greeting(val text: String, val target: String)

suspend fun constructorIsAFunction() {
    println("=== A constructor is a function ===\n")

    // lift works with ANY function. A data class constructor IS a function:
    //   ::Greeting  has type  (String, String) -> Greeting
    // So lift2(::Greeting) is the same as lift2 { a, b -> Greeting(a, b) }

    // With a constructor reference:
    val g1 = Async {
        lift2(::Greeting)
            .ap { fetchName() }
            .ap { "hello" }
    }
    println("  Constructor ref: $g1")

    // With a lambda — any (A, B) -> R function works:
    val greet: (String, Int) -> String = { name, age -> "Hi $name, you're $age" }
    val g2: String = Async {
        lift2(greet)
            .ap { fetchName() }
            .ap { fetchAge() }
    }
    println("  Lambda function: $g2")

    // With a regular function reference:
    fun buildSummary(name: String, items: Int): String = "$name has $items items"

    val g3: String = Async {
        lift2(::buildSummary)
            .ap { fetchName() }
            .ap { 5 }
    }
    println("  Function ref:   $g3")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  NEW: "Nothing runs until Async {}"
// ═══════════════════════════════════════════════════════════════════════

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun fetchDashUser(): String { delay(30); return "Alice" }
suspend fun fetchDashCart(): String { delay(20); return "3 items" }
suspend fun fetchDashPromos(): String { delay(10); return "SAVE20" }

suspend fun nothingRunsUntilAsync() {
    println("=== Nothing runs until Async {} ===\n")

    // This builds a plan — nothing runs yet
    val plan: Computation<Dashboard> = lift3(::Dashboard)
        .ap { fetchDashUser() }
        .ap { fetchDashCart() }
        .ap { fetchDashPromos() }

    println("  Plan built. Nothing has executed yet.")
    println("  plan is: ${plan::class.simpleName}")

    // NOW it runs — all three in parallel
    val result: Dashboard = Async { plan }
    println("  After Async: $result")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  NEW: "All vals, no nulls"
// ═══════════════════════════════════════════════════════════════════════

data class DashboardView(val user: String, val cart: String)

suspend fun allValsNoNulls() {
    println("=== All vals, no nulls ===\n")

    // Raw coroutines: you end up with vars or nulls
    var user: String? = null
    var cart: String? = null
    coroutineScope {
        launch { user = fetchDashUser() }
        launch { cart = fetchDashCart() }
    }
    // Now you need null checks everywhere: user!!, cart!!
    val rawResult = DashboardView(user!!, cart!!)
    println("  Raw (var/null!!): $rawResult")

    // KAP: constructor receives everything at once. All val. No nulls. No !!.
    val kapResult: DashboardView = Async {
        lift2(::DashboardView)
            .ap { fetchDashUser() }
            .ap { fetchDashCart() }
    }
    println("  KAP (val, safe):  $kapResult")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  NEW: Phase Dependencies with flatMap (3 → flatMap → 4 → flatMap → 2)
// ═══════════════════════════════════════════════════════════════════════

data class UserContext(val profile: String, val prefs: String, val tier: String)
data class EnrichedContent(val recs: String, val promos: String, val trending: String, val history: String)
data class FinalDashboard(val layout: String, val analytics: String)

suspend fun fetchProfile(id: String): String { delay(50); return "profile-$id" }
suspend fun fetchPreferences(id: String): String { delay(30); return "prefs-dark" }
suspend fun fetchLoyaltyTier(id: String): String { delay(40); return "gold" }
suspend fun fetchRecommendations(profile: String): String { delay(40); return "recs-for-$profile" }
suspend fun fetchPromotions(tier: String): String { delay(30); return "promos-$tier" }
suspend fun fetchTrending(prefs: String): String { delay(20); return "trending-$prefs" }
suspend fun fetchHistory(profile: String): String { delay(35); return "history-$profile" }
suspend fun renderLayout(ctx: UserContext, content: EnrichedContent): String {
    delay(25); return "layout(${ctx.profile}, ${content.recs})"
}
suspend fun trackAnalytics(ctx: UserContext, content: EnrichedContent): String {
    delay(15); return "tracked(${ctx.tier})"
}

suspend fun phasedFlatMapRaw() {
    println("=== Phase Dependencies: Raw Coroutines ===\n")
    val userId = "user-42"

    val ctx = coroutineScope {
        val dProfile = async { fetchProfile(userId) }
        val dPrefs = async { fetchPreferences(userId) }
        val dTier = async { fetchLoyaltyTier(userId) }
        UserContext(dProfile.await(), dPrefs.await(), dTier.await())
    }

    val enriched = coroutineScope {
        val dRecs = async { fetchRecommendations(ctx.profile) }
        val dPromos = async { fetchPromotions(ctx.tier) }
        val dTrending = async { fetchTrending(ctx.prefs) }
        val dHistory = async { fetchHistory(ctx.profile) }
        EnrichedContent(dRecs.await(), dPromos.await(), dTrending.await(), dHistory.await())
    }

    val dashboard = coroutineScope {
        val dLayout = async { renderLayout(ctx, enriched) }
        val dTrack = async { trackAnalytics(ctx, enriched) }
        FinalDashboard(dLayout.await(), dTrack.await())
    }

    println("  Raw result: $dashboard")
    println()
}

suspend fun phasedFlatMapKap() {
    println("=== Phase Dependencies: KAP flatMap ===\n")
    val userId = "user-42"

    val dashboard: FinalDashboard = Async {
        lift3(::UserContext)
            .ap { fetchProfile(userId) }
            .ap { fetchPreferences(userId) }
            .ap { fetchLoyaltyTier(userId) }
            .flatMap { ctx ->
                lift4(::EnrichedContent)
                    .ap { fetchRecommendations(ctx.profile) }
                    .ap { fetchPromotions(ctx.tier) }
                    .ap { fetchTrending(ctx.prefs) }
                    .ap { fetchHistory(ctx.profile) }
                    .flatMap { enriched ->
                        lift2(::FinalDashboard)
                            .ap { renderLayout(ctx, enriched) }
                            .ap { trackAnalytics(ctx, enriched) }
                    }
            }
    }

    println("  KAP result: $dashboard")
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Hero: lift11 checkout
// ═══════════════════════════════════════════════════════════════════════

suspend fun heroCheckout11() {
    println("=== Hero: lift11 Checkout ===\n")

    val checkout: CheckoutResult = Async {
        lift11(::CheckoutResult)
            .ap { fetchUser() }
            .ap { fetchCart() }
            .ap { fetchPromos() }
            .ap { fetchInventory() }
            .followedBy { validateStock() }
            .ap { calcShipping() }
            .ap { calcTax() }
            .ap { calcDiscounts() }
            .followedBy { reservePayment() }
            .ap { generateConfirmation() }
            .ap { sendEmail() }
    }

    println("  Result: $checkout")
    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Three Primitives examples
// ═══════════════════════════════════════════════════════════════════════

suspend fun fetchA(): String { delay(30); return "A" }
suspend fun fetchB(): String { delay(20); return "B" }
suspend fun validate(): String { delay(10); return "valid" }

data class R3(val a: String, val b: String, val c: String)

suspend fun threePrimitives() {
    println("=== Three Primitives ===\n")

    // .ap — parallel: both launch at t=0
    data class AB(val a: String, val b: String)
    val pair = Async {
        lift2(::AB)
            .ap { fetchA() }
            .ap { fetchB() }
    }
    println("  ap (parallel):     $pair")

    // .followedBy — barrier: validate waits for A and B
    val withBarrier = Async {
        lift3(::R3)
            .ap { fetchA() }
            .ap { fetchB() }
            .followedBy { validate() }
    }
    println("  followedBy:        $withBarrier")

    // .flatMap — barrier that passes data
    data class UserCtx(val profile: String, val prefs: String, val tier: String, val orders: String)
    data class PersonalizedDashboard(val recs: String, val promos: String, val trending: String)

    val userId = "user-1"
    val dashboard = Async {
        lift4(::UserCtx)
            .ap { fetchProfile(userId) }
            .ap { fetchPreferences(userId) }
            .ap { fetchLoyaltyTier(userId) }
            .ap { "recent-orders" }
            .flatMap { ctx ->
                lift3(::PersonalizedDashboard)
                    .ap { fetchRecommendations(ctx.profile) }
                    .ap { fetchPromotions(ctx.tier) }
                    .ap { fetchTrending(ctx.prefs) }
            }
    }
    println("  flatMap (phased):  $dashboard")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Quick Start
// ═══════════════════════════════════════════════════════════════════════

suspend fun quickStart() {
    println("=== Quick Start ===\n")

    val result = Async {
        lift3(::Dashboard)
            .ap { fetchDashUser() }
            .ap { fetchDashCart() }
            .ap { fetchDashPromos() }
    }
    println("  Dashboard: $result")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Quick Start: Resilience
// ═══════════════════════════════════════════════════════════════════════

suspend fun fetchFromSlowApi(): String { delay(300); return "slow-result" }
suspend fun fetchFromCache(): String { delay(20); return "cached-result" }

suspend fun quickStartResilience() {
    println("=== Quick Start: Resilience ===\n")

    val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)
    val retryPolicy = Schedule.recurs<Throwable>(3) and Schedule.exponential(10.milliseconds)

    val result = Async {
        lift3(::Dashboard)
            .ap(Computation { fetchDashUser() }
                .withCircuitBreaker(breaker)
                .retry(retryPolicy))
            .ap(Computation { fetchFromSlowApi() }
                .timeoutRace(100.milliseconds, Computation { fetchFromCache() }))
            .ap { fetchDashPromos() }
    }
    println("  Resilient dashboard: $result")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Quick Start: Validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

sealed class RegError(val message: String) {
    class NameTooShort(msg: String) : RegError(msg)
    class InvalidEmail(msg: String) : RegError(msg)
    class AgeTooLow(msg: String) : RegError(msg)
    class UsernameTaken(msg: String) : RegError(msg)
}

data class ValidName(val value: String)
data class ValidEmail(val value: String)
data class ValidAge(val value: Int)
data class ValidUsername(val value: String)
data class User(val name: ValidName, val email: ValidEmail, val age: ValidAge, val username: ValidUsername)

suspend fun validateName(name: String): Either<NonEmptyList<RegError>, ValidName> {
    delay(20)
    return if (name.length >= 2) Either.Right(ValidName(name))
    else Either.Left(nonEmptyListOf(RegError.NameTooShort("Name must be >= 2 chars")))
}

suspend fun validateEmail(email: String): Either<NonEmptyList<RegError>, ValidEmail> {
    delay(15)
    return if ("@" in email) Either.Right(ValidEmail(email))
    else Either.Left(nonEmptyListOf(RegError.InvalidEmail("Invalid email: $email")))
}

suspend fun validateAge(age: Int): Either<NonEmptyList<RegError>, ValidAge> {
    delay(10)
    return if (age >= 18) Either.Right(ValidAge(age))
    else Either.Left(nonEmptyListOf(RegError.AgeTooLow("Must be >= 18, got $age")))
}

suspend fun checkUsername(username: String): Either<NonEmptyList<RegError>, ValidUsername> {
    delay(25)
    return if (username.length >= 3) Either.Right(ValidUsername(username))
    else Either.Left(nonEmptyListOf(RegError.UsernameTaken("Username too short")))
}

suspend fun quickStartValidation() {
    println("=== Quick Start: Validation ===\n")

    val registration: Either<NonEmptyList<RegError>, User> = Async {
        liftV4<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .apV { validateName("Al") }
            .apV { validateEmail("alice@example.com") }
            .apV { validateAge(25) }
            .apV { checkUsername("alice") }
    }

    when (registration) {
        is Either.Right -> println("  Valid: ${registration.value}")
        is Either.Left -> println("  Errors: ${registration.value.map { it.message }}")
    }

    // With failures accumulated
    val invalid: Either<NonEmptyList<RegError>, User> = Async {
        liftV4<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
            .apV { validateName("A") }
            .apV { validateEmail("bad") }
            .apV { validateAge(10) }
            .apV { checkUsername("al") }
    }

    when (invalid) {
        is Either.Right -> println("  Valid: ${invalid.value}")
        is Either.Left -> println("  ${invalid.value.size} errors: ${invalid.value.map { it.message }}")
    }

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Choose Your Style
// ═══════════════════════════════════════════════════════════════════════

suspend fun chooseYourStyle() {
    println("=== Choose Your Style ===\n")

    // Style 1: lift + ap
    val s1 = Async {
        lift3(::Dashboard)
            .ap { fetchDashUser() }
            .ap { fetchDashCart() }
            .ap { fetchDashPromos() }
    }
    println("  lift+ap:  $s1")

    // Style 2: liftA
    val s2 = Async {
        liftA3(
            { fetchDashUser() },
            { fetchDashCart() },
            { fetchDashPromos() },
        ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
    }
    println("  liftA3:   $s2")

    // Style 3: mapN / zip
    val s3 = Async {
        mapN(
            Computation { fetchDashUser() },
            Computation { fetchDashCart() },
            Computation { fetchDashPromos() },
        ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
    }
    println("  mapN:     $s3")

    // Bonus: product
    val (user, cart) = Async { product({ fetchDashUser() }, { fetchDashCart() }) }
    println("  product:  ($user, $cart)")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 3: Retry with Schedule (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRetrySchedule() {
    println("=== Feature: Retry with Schedule ===\n")

    var attempts = 0
    suspend fun flakyService(): String {
        attempts++
        if (attempts <= 2) throw RuntimeException("flake #$attempts")
        return "success on attempt $attempts"
    }

    val policy = Schedule.recurs<Throwable>(5) and
        Schedule.exponential(10.milliseconds) and
        Schedule.doWhile<Throwable> { it is RuntimeException }

    val result = Async {
        Computation { flakyService() }.retry(policy)
    }
    println("  Result: $result")

    // Inline retry with the simple core overload
    attempts = 0
    data class RetryResult(val user: String, val service: String)
    val result2 = Async {
        lift2(::RetryResult)
            .ap { fetchDashUser() }
            .ap(Computation { flakyService() }
                .retry(3, delay = 10.milliseconds))
    }
    println("  Inline:  $result2")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 4: Resource Safety (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

class MockConnection(val name: String) {
    var closed = false
    suspend fun query(q: String): String { delay(20); return "$name:result-of-$q" }
    suspend fun get(key: String): String { delay(15); return "$name:$key" }
    fun close() { closed = true }
}

suspend fun openDbConnection(): MockConnection { delay(10); return MockConnection("db") }
suspend fun openCacheConnection(): MockConnection { delay(10); return MockConnection("cache") }
suspend fun openHttpClient(): MockConnection { delay(10); return MockConnection("http") }

suspend fun featureResourceSafety() {
    println("=== Feature: Resource Safety ===\n")

    // bracket
    val result = Async {
        lift3 { db: String, cache: String, api: String -> "$db|$cache|$api" }
            .ap(bracket(
                acquire = { openDbConnection() },
                use = { conn -> Computation { conn.query("SELECT 1") } },
                release = { conn -> conn.close() },
            ))
            .ap(bracket(
                acquire = { openCacheConnection() },
                use = { conn -> Computation { conn.get("key") } },
                release = { conn -> conn.close() },
            ))
            .ap(bracket(
                acquire = { openHttpClient() },
                use = { client -> Computation { client.get("/api") } },
                release = { client -> client.close() },
            ))
    }
    println("  bracket: $result")

    // Resource monad
    data class DashboardData(val db: String, val cache: String, val http: String)

    val infra = Resource.zip(
        Resource({ openDbConnection() }, { it.close() }),
        Resource({ openCacheConnection() }, { it.close() }),
        Resource({ openHttpClient() }, { it.close() }),
    ) { db, cache, http -> Triple(db, cache, http) }

    val result2 = Async {
        infra.useComputation { (db, cache, http) ->
            lift3(::DashboardData)
                .ap { db.query("SELECT 1") }
                .ap { cache.get("user:prefs") }
                .ap { http.get("/recommendations") }
        }
    }
    println("  Resource: $result2")

    // bracketCase
    val result3 = Async {
        bracketCase(
            acquire = { openDbConnection() },
            use = { tx -> Computation { tx.query("INSERT 1") } },
            release = { tx, case ->
                when (case) {
                    is ExitCase.Completed<*> -> println("    bracketCase: commit")
                    else -> println("    bracketCase: rollback")
                }
                tx.close()
            },
        )
    }
    println("  bracketCase: $result3")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 5: Racing (kap-core + kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRacing() {
    println("=== Feature: Racing ===\n")

    suspend fun fetchFromRegionUS(): String { delay(100); return "US-data" }
    suspend fun fetchFromRegionEU(): String { delay(30); return "EU-data" }
    suspend fun fetchFromRegionAP(): String { delay(60); return "AP-data" }

    val fastest = Async {
        raceN(
            Computation { fetchFromRegionUS() },
            Computation { fetchFromRegionEU() },
            Computation { fetchFromRegionAP() },
        )
    }
    println("  raceN winner: $fastest")

    // raceEither with different types
    val raceResult: Either<String, Int> = Async {
        raceEither(
            fa = Computation { delay(30); "fast-string" },
            fb = Computation { delay(100); 42 },
        )
    }
    println("  raceEither: $raceResult")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 6: Bounded Parallel Collection Processing
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTraverse() {
    println("=== Feature: Traverse ===\n")

    val userIds = (1..10).toList()

    val results = Async {
        userIds.traverse(concurrency = 3) { id ->
            Computation { delay(20); "user-$id" }
        }
    }
    println("  traverse(c=3): ${results.size} users fetched")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 7: Settled — partial failure tolerance
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureSettled() {
    println("=== Feature: Settled ===\n")

    suspend fun fetchUserMayFail(): String { throw RuntimeException("user service down") }
    suspend fun fetchCartAlways(): String { delay(20); return "cart-ok" }
    suspend fun fetchConfigAlways(): String { delay(15); return "config-ok" }

    data class PartialDashboard(val user: String, val cart: String, val config: String)

    val dashboard = Async {
        lift3 { user: Result<String>, cart: String, config: String ->
            PartialDashboard(user.getOrDefault("anonymous"), cart, config)
        }
            .ap(Computation { fetchUserMayFail() }.settled())
            .ap { fetchCartAlways() }
            .ap { fetchConfigAlways() }
    }
    println("  settled: $dashboard")

    // traverseSettled
    val ids = listOf(1, 2, 3, 4, 5)
    val results: List<Result<String>> = Async {
        ids.traverseSettled { id ->
            Computation {
                if (id % 2 == 0) throw RuntimeException("fail-$id")
                "user-$id"
            }
        }
    }
    val successes = results.filter { it.isSuccess }.map { it.getOrThrow() }
    val failures = results.filter { it.isFailure }.map { it.exceptionOrNull()!!.message }
    println("  traverseSettled: successes=$successes, failures=$failures")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 8: Timeout with Parallel Fallback (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureTimeoutRace() {
    println("=== Feature: timeoutRace ===\n")

    suspend fun fetchFromPrimary(): String { delay(200); return "primary-data" }
    suspend fun fetchFromFallback(): String { delay(30); return "fallback-data" }

    val start = System.currentTimeMillis()
    val result = Async {
        Computation { fetchFromPrimary() }
            .timeoutRace(100.milliseconds, Computation { fetchFromFallback() })
    }
    val elapsed = System.currentTimeMillis() - start
    println("  timeoutRace: $result (${elapsed}ms — fallback won)")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 9: Quorum Consensus (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureRaceQuorum() {
    println("=== Feature: raceQuorum ===\n")

    suspend fun fetchReplicaA(): String { delay(50); return "replica-A" }
    suspend fun fetchReplicaB(): String { delay(20); return "replica-B" }
    suspend fun fetchReplicaC(): String { delay(80); return "replica-C" }

    val quorum: List<String> = Async {
        raceQuorum(
            required = 2,
            Computation { fetchReplicaA() },
            Computation { fetchReplicaB() },
            Computation { fetchReplicaC() },
        )
    }
    println("  raceQuorum(2 of 3): $quorum")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 10: Circuit Breaker (kap-resilience)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureCircuitBreaker() {
    println("=== Feature: Circuit Breaker ===\n")

    val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

    val result = Async {
        Computation { fetchDashUser() }
            .timeout(500.milliseconds)
            .withCircuitBreaker(breaker)
            .retry(Schedule.recurs<Throwable>(3) and Schedule.exponential(10.milliseconds))
            .recover { "cached-user" }
    }
    println("  Composable chain: $result")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 12: Memoization
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureMemoize() {
    println("=== Feature: Memoize ===\n")

    var callCount = 0
    val fetchOnce = Computation { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

    val a = Async { fetchOnce }
    val b = Async { fetchOnce }
    println("  memoizeOnSuccess: a=$a, b=$b, callCount=$callCount")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature 13: Phased Validation (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

data class Identity(val name: ValidName, val email: ValidEmail, val age: ValidAge)
data class Clearance(val notBlocked: Boolean, val available: Boolean)
data class Registration(val identity: Identity, val clearance: Clearance)

suspend fun checkNotBlacklisted(id: Identity): Either<NonEmptyList<RegError>, Boolean> {
    delay(20); return Either.Right(true)
}

suspend fun checkUsernameAvailable(email: String): Either<NonEmptyList<RegError>, Boolean> {
    delay(15); return Either.Right(true)
}

suspend fun featurePhasedValidation() {
    println("=== Feature: Phased Validation ===\n")

    val result: Either<NonEmptyList<RegError>, Registration> = Async {
        accumulate {
            val identity = zipV(
                { validateName("Alice") },
                { validateEmail("alice@example.com") },
                { validateAge(25) },
            ) { name, email, age -> Identity(name, email, age) }
                .bindV()

            val cleared = zipV(
                { checkNotBlacklisted(identity) },
                { checkUsernameAvailable(identity.email.value) },
            ) { a, b -> Clearance(a, b) }
                .bindV()

            Registration(identity, cleared)
        }
    }

    when (result) {
        is Either.Right -> println("  Phased: ${result.value}")
        is Either.Left -> println("  Errors: ${result.value.map { it.message }}")
    }

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Execution Model: followedBy vs thenValue
// ═══════════════════════════════════════════════════════════════════════

fun combineThree(a: String, b: String, c: String): String = "$a+$b+$c"

suspend fun executionModel() {
    println("=== Execution Model ===\n")

    val graph = lift3(::combineThree)
        .ap { fetchA() }
        .ap { fetchB() }

    println("  graph built, not executed")
    val result = Async { graph.ap { "C" } }
    println("  Async { graph }: $result")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: attempt() (kap-arrow)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureAttempt() {
    println("=== Feature: attempt() ===\n")

    val success: Either<Throwable, String> = Async {
        Computation { "hello" }.attempt()
    }
    println("  attempt success: $success")

    val failure: Either<Throwable, String> = Async {
        Computation<String> { throw RuntimeException("boom") }.attempt()
    }
    println("  attempt failure: $failure")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: firstSuccessOf & orElse (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureFallbacks() {
    println("=== Feature: firstSuccessOf & orElse ===\n")

    val result = Async {
        Computation<String> { throw RuntimeException("fail-1") }
            .orElse(Computation { "fallback-ok" })
    }
    println("  orElse: $result")

    val result2 = Async {
        firstSuccessOf(
            Computation { throw RuntimeException("fail-1") },
            Computation { throw RuntimeException("fail-2") },
            Computation { "third-wins" },
        )
    }
    println("  firstSuccessOf: $result2")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Feature: computation { } builder (kap-core)
// ═══════════════════════════════════════════════════════════════════════

suspend fun featureComputationBuilder() {
    println("=== Feature: computation {} builder ===\n")

    val result = Async {
        computation {
            val user = Computation { fetchDashUser() }.bind()
            val cart = Computation { fetchDashCart() }.bind()
            "$user has $cart"
        }
    }
    println("  computation {}: $result")

    println()
}

// ═══════════════════════════════════════════════════════════════════════
//  Main
// ═══════════════════════════════════════════════════════════════════════

suspend fun main() {
    println("╔══════════════════════════════════════════════╗")
    println("║  KAP README Examples — All Compilable        ║")
    println("╚══════════════════════════════════════════════╝\n")

    // NEW teaching sections
    constructorIsAFunction()
    nothingRunsUntilAsync()
    allValsNoNulls()
    phasedFlatMapRaw()
    phasedFlatMapKap()

    // Existing README sections
    heroCheckout11()
    threePrimitives()
    quickStart()
    quickStartResilience()
    quickStartValidation()
    chooseYourStyle()

    // Feature Showcase
    featureRetrySchedule()
    featureResourceSafety()
    featureRacing()
    featureTraverse()
    featureSettled()
    featureTimeoutRace()
    featureRaceQuorum()
    featureCircuitBreaker()
    featureMemoize()
    featurePhasedValidation()
    featureAttempt()
    featureFallbacks()
    featureComputationBuilder()
    executionModel()

    println("All README examples passed!")
}

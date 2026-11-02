package com.budgetapp.voiceexpense

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseSource
import com.budgetapp.voiceexpense.quick.QuickCaptureActivity
import com.budgetapp.voiceexpense.widget.refreshBudgetWidgets
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Takes the README's screenshots from made-up demo data, on Friday 30 Oct 2026 at 13:05.
 * Skipped unless run with `-e screenshots true`, and the widget must already be on the home screen.
 * The images land in [DIR] on the device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots {
    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val mic: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val zone = ZoneId.of("Asia/Singapore")
    private val now = LocalDateTime.of(2026, 10, 30, 13, 5)
    private val app get() = ApplicationProvider.getApplicationContext<VoiceExpenseApp>()
    private val recognizer = FakeVoiceRecognizer()

    private lateinit var database: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        // Skipping each test, not the class, lets the runner count them all as skipped.
        assumeTrue(screenshotsAsked)
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        app.containerOverride = AppContainer(
            database = database,
            themeSettings = testThemeSettings(app),
            clock = Clock.fixed(now.atZone(zone).toInstant(), zone),
            createVoiceRecognizer = { recognizer },
            onDataChanged = { refreshBudgetWidgets(app) },
            savedCardMillis = 60_000,
        )
        seedDemoData()
    }

    @After
    fun tearDown() {
        if (!screenshotsAsked) return
        scenario?.close()
        app.containerOverride = null
        database.close()
    }

    @Test
    fun widgetAndQuickCapture() {
        shell("input keyevent KEYCODE_HOME")
        // The emulator's launcher put the widget on its second page, since the first was full.
        shell("input swipe 900 1300 150 1300 300")
        refreshBudgetWidgets(app)
        capture("widget", settleMillis = 2_000)

        app.startActivity(QuickCaptureActivity.intent(app))
        compose.waitUntil(5_000) { recognizer.isListening }
        recognizer.hearPartial("lunch chicken rice")
        waitFor("lunch chicken rice")
        capture("quick-listening")

        recognizer.hear("lunch chicken rice 5.50")
        waitFor("Saved")
        capture("quick-saved", settleMillis = 2_000) // Long enough for the widget behind to update.
        shell("input keyevent KEYCODE_BACK")
    }

    @Test
    fun home() {
        addTodaysLunch()
        openApp()
        capture("home")
    }

    @Test
    fun voice() {
        addTodaysLunch()
        openApp()
        compose.onNodeWithContentDescription("Log by voice").performClick()
        compose.waitUntil(5_000) { recognizer.isListening }
        recognizer.hearPartial("grab to client meeting")
        waitFor("grab to client meeting")
        capture("voice-listening")

        recognizer.hear("grab to client meeting 14.20")
        waitFor("Confirm expense")
        capture("voice-confirm")
    }

    @Test
    fun trends() {
        addTodaysLunch()
        openApp()
        compose.onNodeWithContentDescription("Trends").performClick()
        waitFor("Last 12 weeks")
        capture("trends")

        // Two swipes reach the bottom, so the meal averages show in full below the categories.
        repeat(2) { compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() } }
        capture("trends-categories")
    }

    @Test
    fun history() {
        addTodaysLunch()
        openApp()
        compose.onNodeWithText("All expenses").performClick()
        waitFor("Yesterday")
        capture("history")
    }

    @Test
    fun settings() {
        openApp()
        compose.onNodeWithContentDescription("Budgets and settings").performClick()
        waitFor("Weekly budget")
        capture("settings")
    }

    @Test
    fun darkMode() {
        addTodaysLunch()
        shell("cmd uimode night yes")
        try {
            openApp()
            capture("home-dark")
            compose.onNodeWithContentDescription("Trends").performClick()
            waitFor("Last 12 weeks")
            capture("trends-dark")
        } finally {
            shell("cmd uimode night no")
        }
    }

    private fun openApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("This week")
    }

    private fun waitFor(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), 5_000)
    }

    /** Waits out ripples and screen transitions, which the compose clock doesn't cover. */
    private fun capture(name: String, settleMillis: Long = 1_000) {
        compose.waitForIdle()
        Thread.sleep(settleMillis)
        shell("screencap -p $DIR/$name.png")
    }

    private fun addTodaysLunch() = runBlocking {
        database.expenseDao().insert(
            demoExpense(now.withHour(12).withMinute(41), 550, Category.LUNCH, "Chicken rice", voice = true),
        )
    }

    private fun demoExpense(
        at: LocalDateTime,
        cents: Long,
        category: Category,
        note: String,
        voice: Boolean = false,
    ) = Expense(
        timestamp = at.atZone(zone).toInstant(),
        amountCents = cents,
        category = category,
        note = note,
        source = if (voice) ExpenseSource.VOICE else ExpenseSource.MANUAL,
    )

    /** Twelve weeks of everyday spending, the same every run, then this morning's breakfast and train. */
    private fun seedDemoData() = runBlocking {
        val today = now.toLocalDate()
        val random = Random(2026)
        var day = today.with(DayOfWeek.MONDAY).minusWeeks(11)
        while (day < today) {
            demoDay(day, random).forEach { database.expenseDao().insert(it) }
            day = day.plusDays(1)
        }
        database.expenseDao().insert(
            demoExpense(today.atTime(8, 5), 380, Category.BREAKFAST, "Kopi and kaya toast", voice = true),
        )
        database.expenseDao().insert(demoExpense(today.atTime(8, 32), 189, Category.TRANSPORT, "MRT"))
        val longAgo = today.minusMonths(6)
        database.budgetDao().setOverall(BudgetPeriod.WEEK, 25_000, longAgo)
        database.budgetDao().setOverall(BudgetPeriod.MONTH, 130_000, longAgo)
    }

    private fun demoDay(day: LocalDate, random: Random): List<Expense> = buildList {
        val weekend = day.dayOfWeek >= DayOfWeek.SATURDAY
        fun chance(odds: Float) = random.nextFloat() < odds
        fun spend(hour: Int, minute: Int, item: Pair<String, Long>, category: Category) {
            add(demoExpense(day.atTime(hour, minute), item.second, category, item.first))
        }

        if (chance(if (weekend) 0.5f else 0.85f)) spend(8, random.nextInt(40), BREAKFASTS.random(random), Category.BREAKFAST)
        if (!weekend) spend(8, 30 + random.nextInt(20), "MRT" to 189L, Category.TRANSPORT)
        if (chance(if (weekend) 0.7f else 0.95f)) spend(12, 10 + random.nextInt(45), LUNCHES.random(random), Category.LUNCH)
        if (chance(0.65f)) spend(19, random.nextInt(55), DINNERS.random(random), Category.DINNER)
        if (chance(0.12f)) spend(22, 10 + random.nextInt(40), SUPPERS.random(random), Category.SUPPER)
        if (day.dayOfWeek == DayOfWeek.SATURDAY) {
            spend(11, random.nextInt(50), "FairPrice" to 3_500L + random.nextInt(4_000), Category.GROCERIES)
        }
        if (day.dayOfWeek == DayOfWeek.WEDNESDAY && chance(0.5f)) {
            spend(18, 50, "Sheng Siong" to 900L + random.nextInt(1_600), Category.GROCERIES)
        }
        if (chance(0.15f)) spend(21, random.nextInt(50), "Grab home" to 1_100L + random.nextInt(1_100), Category.TRANSPORT)
        if (chance(0.08f)) spend(20, 30, SHOPPING.random(random), Category.SHOPPING)
        if (chance(0.04f)) spend(15, 20, OTHERS.random(random), Category.OTHER)
    }

    companion object {
        private const val DIR = "/data/local/tmp/readme-screenshots"
        private var inDemoMode = false

        private val BREAKFASTS = listOf(
            "Kopi and kaya toast" to 380L, "Kopi C" to 160L, "Nasi lemak" to 450L,
            "Prata and teh" to 420L, "Bread and coffee" to 520L,
        )
        private val LUNCHES = listOf(
            "Chicken rice" to 550L, "Fishball noodles" to 480L, "Economy rice" to 620L, "Laksa" to 700L,
            "Ban mian" to 600L, "Duck rice" to 650L, "Wonton mee" to 550L, "Salad bowl" to 1_290L,
            "Poke bowl" to 1_450L,
        )
        private val DINNERS = listOf(
            "Hokkien mee" to 700L, "Yong tau foo" to 740L, "Fried rice" to 650L, "Bak kut teh" to 1_150L,
            "Ramen" to 1_680L, "Thai food" to 1_890L, "Sushi" to 2_400L, "Zi char with friends" to 2_800L,
        )
        private val SUPPERS = listOf("Bubble tea" to 420L, "Prata" to 360L, "Ice cream" to 550L)
        private val SHOPPING = listOf(
            "Shopee order" to 2_390L, "Uniqlo" to 2_990L, "Phone case" to 1_280L, "Paperback" to 1_890L,
        )
        private val OTHERS = listOf("Haircut" to 1_800L, "Movie tickets" to 2_700L, "Birthday gift" to 3_000L)

        /** Runs as the shell user, which can write to [DIR] and drive System UI's demo mode. */
        private fun shell(command: String) {
            val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        }

        private fun demo(command: String) = shell("am broadcast -a com.android.systemui.demo -e command $command")

        private val screenshotsAsked: Boolean
            get() = InstrumentationRegistry.getArguments().getString("screenshots") == "true"

        @JvmStatic
        @BeforeClass
        fun enterDemoMode() {
            if (!screenshotsAsked) return
            shell("mkdir -p $DIR")
            // A tidy status bar: a fixed clock, full signal and battery, no notification icons.
            shell("settings put global sysui_demo_allowed 1")
            demo("enter")
            demo("clock -e hhmm 1305")
            demo("battery -e level 100 -e plugged false")
            demo("network -e wifi show -e level 4 -e fully true")
            demo("network -e mobile hide")
            demo("notifications -e visible false")
            inDemoMode = true
        }

        @JvmStatic
        @AfterClass
        fun leaveDemoMode() {
            if (inDemoMode) demo("exit")
        }
    }
}

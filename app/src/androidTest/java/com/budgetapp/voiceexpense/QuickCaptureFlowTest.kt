package com.budgetapp.voiceexpense

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseSource
import com.budgetapp.voiceexpense.domain.LatLng
import com.budgetapp.voiceexpense.quick.QuickCaptureActivity
import com.budgetapp.voiceexpense.voice.VoiceFailure
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The widget and tile's flow: what's heard saves straight away, with no confirm card. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickCaptureFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val mic: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val zone = ZoneId.of("Asia/Singapore")
    private val now = LocalDateTime.of(2026, 11, 4, 12, 30)
    private val app get() = ApplicationProvider.getApplicationContext<VoiceExpenseApp>()
    private val recognizer = FakeVoiceRecognizer()
    private val dataChanges = AtomicInteger()
    private val here = LatLng(1.2840, 103.8514)

    private lateinit var database: AppDatabase
    private lateinit var scenario: ActivityScenario<QuickCaptureActivity>

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        scenario.close()
        app.containerOverride = null
        database.close()
    }

    /** The saved card stays up long enough to look at unless a test wants it gone sooner. */
    private fun launch(savedCardMillis: Long = 60_000) {
        app.containerOverride = AppContainer(
            database = database,
            themeSettings = testThemeSettings(app),
            clock = Clock.fixed(now.atZone(zone).toInstant(), zone),
            createVoiceRecognizer = { recognizer },
            onDataChanged = { dataChanges.incrementAndGet() },
            savedCardMillis = savedCardMillis,
            locationSource = FakeLocationSource(here),
        )
        scenario = ActivityScenario.launch(Intent(app, QuickCaptureActivity::class.java))
        compose.waitUntil(5_000) { recognizer.isListening }
    }

    private fun waitFor(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), 5_000)
    }

    private fun savedExpenses(): List<Expense> =
        runBlocking { database.expenseDao().observeAll().first() }

    private fun waitUntilClosed() {
        val deadline = System.currentTimeMillis() + 5_000
        while (scenario.state != Lifecycle.State.DESTROYED) {
            check(System.currentTimeMillis() < deadline) { "Still open: ${scenario.state}" }
            compose.waitForIdle() // Tests only draw new frames when asked to.
            Thread.sleep(50)
        }
    }

    private fun waitUntilSavedCount(expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (savedExpenses().size != expected) {
            check(System.currentTimeMillis() < deadline) { "Saved: ${savedExpenses().size}" }
            compose.waitForIdle()
            Thread.sleep(50)
        }
    }

    @Test
    fun whatIsHeardIsSavedWithoutAnyConfirming() {
        launch()
        recognizer.hear("lunch chicken rice 5.50")

        waitFor("Saved")
        waitFor("Lunch · $5.50")
        waitFor("Chicken rice")
        waitUntilSavedCount(1)
        val saved = savedExpenses().single()
        assertEquals(550L, saved.amountCents)
        assertEquals(Category.LUNCH, saved.category)
        assertEquals("Chicken rice", saved.note)
        assertEquals("lunch chicken rice 5.50", saved.rawTranscript)
        assertEquals(ExpenseSource.VOICE, saved.source)
        assertEquals(now.atZone(zone).toInstant(), saved.timestamp)
    }

    @Test
    fun aSavedExpenseRemembersWhereItWasLogged() {
        launch()
        recognizer.hear("lunch chicken rice 5.50")
        waitUntilSavedCount(1)
        compose.waitUntil(5_000) {
            runBlocking { database.expenseDao().withLocation() }.singleOrNull()?.let {
                it.latitude == here.latitude && it.longitude == here.longitude
            } ?: false
        }
    }

    @Test
    fun widgetIsToldAboutTheNewExpense() {
        launch()
        recognizer.hear("lunch 5")
        waitUntilSavedCount(1)
        compose.waitUntil(5_000) { dataChanges.get() == 1 }
    }

    @Test
    fun categoryFollowsTheTimeWhenNoneIsSaid() {
        launch()
        recognizer.hear("toast 3")
        waitFor("Lunch · $3.00")
    }

    @Test
    fun closesItselfAfterSaving() {
        launch(savedCardMillis = 300)
        recognizer.hear("lunch 5")
        waitUntilClosed()
        assertEquals(1, savedExpenses().size)
    }

    @Test
    fun undoRemovesTheExpenseAndCloses() {
        launch()
        recognizer.hear("lunch 5")
        waitFor("Undo")
        waitUntilSavedCount(1)

        compose.onNodeWithText("Undo").performClick()

        waitUntilClosed()
        assertTrue(savedExpenses().isEmpty())
        compose.waitUntil(5_000) { dataChanges.get() == 2 }
    }

    @Test
    fun undoStillWorksIfTappedTheMomentTheCardAppears() {
        launch()
        recognizer.hear("lunch 5")
        waitFor("Undo")
        compose.onNodeWithText("Undo").performClick()

        waitUntilClosed()
        assertTrue(savedExpenses().isEmpty())
    }

    @Test
    fun beingCutOffKeepsListeningUntilThePriceThenSaves() {
        launch()
        recognizer.hear("lunch")
        compose.waitUntil(5_000) { recognizer.starts == 2 && recognizer.isListening }
        waitFor("Keep going and say the price, or tap Done")
        assertTrue(savedExpenses().isEmpty())

        recognizer.hear("five fifty")

        waitFor("Lunch · $5.50")
        waitUntilSavedCount(1)
        assertEquals("lunch five fifty", savedExpenses().single().rawTranscript)
    }

    @Test
    fun noPriceSavesNothingAndCanTryAgain() {
        launch()
        recognizer.hear("lunch chicken rice")
        compose.waitUntil(5_000) { recognizer.starts == 2 && recognizer.isListening }
        recognizer.fail(VoiceFailure.NOT_HEARD)

        waitFor("I didn't hear a price, so nothing was saved.")
        waitFor("Heard: “lunch chicken rice”")
        assertTrue(savedExpenses().isEmpty())

        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(5_000) { recognizer.starts == 3 && recognizer.isListening }
        recognizer.hear("dinner 9")

        waitFor("Dinner · $9.00")
        waitUntilSavedCount(1)
        assertEquals("dinner 9", savedExpenses().single().rawTranscript)
    }

    @Test
    fun hearingNothingOffersTryAgain() {
        launch()
        recognizer.fail(VoiceFailure.NOT_HEARD)

        waitFor("I didn't catch that.")
        assertTrue(savedExpenses().isEmpty())
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(5_000) { recognizer.starts == 2 && recognizer.isListening }
    }

    @Test
    fun cancelClosesAndSavesNothing() {
        launch()
        waitFor("Cancel")
        compose.onNodeWithText("Cancel").performClick()

        waitUntilClosed()
        assertTrue(savedExpenses().isEmpty())
    }
}

package com.budgetapp.voiceexpense

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseSource
import com.budgetapp.voiceexpense.voice.VoiceFailure
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against an in-memory database, on a Wednesday at 12:30, with speech faked. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class VoiceFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val mic: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val zone = ZoneId.of("Asia/Singapore")
    private val now = LocalDateTime.of(2026, 11, 4, 12, 30)
    private val app get() = ApplicationProvider.getApplicationContext<VoiceExpenseApp>()
    private val recognizer = FakeVoiceRecognizer()

    private lateinit var database: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        useRecognizer(recognizer)
    }

    @After
    fun tearDown() {
        scenario?.close()
        app.containerOverride = null
        database.close()
    }

    private fun useRecognizer(fake: FakeVoiceRecognizer) {
        app.containerOverride = AppContainer(
            database = database,
            themeSettings = testThemeSettings(app),
            clock = Clock.fixed(now.atZone(zone).toInstant(), zone),
            createVoiceRecognizer = { fake },
        )
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), 5_000)
    }

    private fun tapSpeak() {
        waitFor("This week")
        compose.onNodeWithContentDescription("Log by voice").performClick()
        waitFor("Listening…")
        compose.waitUntil(5_000) { recognizer.isListening }
    }

    private fun savedExpenses(): List<Expense> =
        runBlocking { database.expenseDao().observeAll().first() }

    @Test
    fun spokenExpenseIsConfirmedAndSavedWithItsTranscript() {
        launch()
        tapSpeak()
        recognizer.hearPartial("lunch chicken")
        waitFor("lunch chicken")
        recognizer.hear("lunch chicken rice 5.50")

        waitFor("Confirm expense")
        waitFor("Heard: “lunch chicken rice 5.50”")
        compose.onNodeWithText("5.50").assertExists()
        compose.onNode(hasText("Lunch") and isSelected()).assertExists()
        compose.onNodeWithText("Chicken rice").assertExists()
        compose.onNodeWithText("Save").performClick()

        waitFor("Chicken rice", substring = true)
        waitFor("$5.50")
        val saved = savedExpenses().single()
        assertEquals(550L, saved.amountCents)
        assertEquals(Category.LUNCH, saved.category)
        assertEquals("Chicken rice", saved.note)
        assertEquals("lunch chicken rice 5.50", saved.rawTranscript)
        assertEquals(ExpenseSource.VOICE, saved.source)
    }

    @Test
    fun confirmCardCanBeCorrectedBeforeSaving() {
        launch()
        tapSpeak()
        recognizer.hear("lunch chicken rice 5.50")
        waitFor("Confirm expense")

        compose.onNodeWithText("Amount").performTextClearance()
        compose.onNodeWithText("Amount").performTextInput("6")
        compose.onNodeWithText("Dinner").performClick()
        compose.onNodeWithText("Save").performClick()

        waitFor("$6.00")
        val saved = savedExpenses().single()
        assertEquals(600L, saved.amountCents)
        assertEquals(Category.DINNER, saved.category)
        assertEquals("lunch chicken rice 5.50", saved.rawTranscript)
    }

    private fun waitForNextListen(startsSoFar: Int) {
        compose.waitUntil(5_000) { recognizer.starts > startsSoFar && recognizer.isListening }
    }

    @Test
    fun beingCutOffAfterOneWordKeepsListeningAndJoinsTheWords() {
        launch()
        tapSpeak()
        recognizer.hear("lunch")

        waitForNextListen(startsSoFar = 1)
        waitFor("lunch")
        waitFor("Keep going and say the price, or tap Done")
        compose.onNodeWithText("Confirm expense").assertDoesNotExist()

        recognizer.hearPartial("chicken")
        waitFor("lunch chicken")
        recognizer.hear("chicken rice")
        waitForNextListen(startsSoFar = 2)
        recognizer.hear("five fifty")

        waitFor("Heard: “lunch chicken rice five fifty”")
        compose.onNodeWithText("5.50").assertExists()
        compose.onNode(hasText("Lunch") and isSelected()).assertExists()
        compose.onNodeWithText("Chicken rice").assertExists()
        compose.onNodeWithText("Save").performClick()

        waitFor("$5.50")
        assertEquals("lunch chicken rice five fifty", savedExpenses().single().rawTranscript)
    }

    @Test
    fun aQuantityIsNotMistakenForThePrice() {
        launch()
        tapSpeak()
        recognizer.hear("2 kopi")
        waitForNextListen(startsSoFar = 1)
        recognizer.hear("3.60")

        waitFor("Heard: “2 kopi 3.60”")
        compose.onNodeWithText("3.60").assertExists()
    }

    @Test
    fun doneEndsListeningWithWhatWasSaid() {
        launch()
        tapSpeak()
        recognizer.hear("lunch")
        waitForNextListen(startsSoFar = 1)

        compose.onNodeWithText("Done").performClick()
        recognizer.hear("chicken rice")

        waitFor("Heard: “lunch chicken rice”")
        waitFor("No amount heard. Type it here.")
        assertEquals(2, recognizer.starts)
    }

    @Test
    fun failureAfterSomeWordsStillReachesTheConfirmCard() {
        launch()
        tapSpeak()
        recognizer.hear("lunch")
        waitForNextListen(startsSoFar = 1)
        recognizer.fail(VoiceFailure.NO_INTERNET)

        waitFor("Heard: “lunch”")
        waitFor("No amount heard. Type it here.")
    }

    @Test
    fun phraseWithNoAmountAsksForIt() {
        launch()
        tapSpeak()
        recognizer.hear("lunch chicken rice")
        waitForNextListen(startsSoFar = 1)
        recognizer.fail(VoiceFailure.NOT_HEARD)

        waitFor("No amount heard. Type it here.")
        compose.onNodeWithText("Save").performClick()
        waitFor("Enter an amount above zero, like 5.50")
        assertTrue(savedExpenses().isEmpty())

        compose.onNodeWithText("Amount").performTextInput("7")
        compose.onNodeWithText("Save").performClick()
        waitFor("$7.00")
        assertEquals(700L, savedExpenses().single().amountCents)
    }

    @Test
    fun specialCharactersInTheTranscriptSurvive() {
        launch()
        tapSpeak()
        recognizer.hear("kopi & toast at 100% off? #1 $2.50")

        waitFor("Heard: “kopi & toast at 100% off? #1 $2.50”")
        compose.onNodeWithText("2.50").assertExists()
    }

    @Test
    fun cancellingSavesNothing() {
        launch()
        tapSpeak()
        compose.onNodeWithText("Cancel").performClick()

        waitFor("Nothing logged today.")
        assertTrue(savedExpenses().isEmpty())
        compose.waitUntil(5_000) { !recognizer.isListening }
    }

    @Test
    fun backFromConfirmCardReturnsHomeAndSavesNothing() {
        launch()
        tapSpeak()
        recognizer.hear("lunch 5")
        waitFor("Confirm expense")

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Nothing logged today.")
        assertTrue(savedExpenses().isEmpty())
    }

    @Test
    fun sayItAgainListensAgain() {
        launch()
        tapSpeak()
        recognizer.hear("lunch 5")
        waitFor("Confirm expense")

        compose.onNodeWithContentDescription("Say it again").performClick()
        waitFor("Listening…")
        compose.waitUntil(5_000) { recognizer.isListening }
        recognizer.hear("dinner 9")

        waitFor("Heard: “dinner 9”")
        compose.onNode(hasText("Dinner") and isSelected()).assertExists()
    }

    @Test
    fun notHearingAnythingOffersRetryAndTyping() {
        launch()
        tapSpeak()
        recognizer.fail(VoiceFailure.NOT_HEARD)

        waitFor("I didn't catch that.")
        compose.onNodeWithText("Try again").performClick()
        waitFor("Listening…")
        compose.waitUntil(5_000) { recognizer.isListening }
        assertEquals(2, recognizer.starts)

        recognizer.fail(VoiceFailure.NO_INTERNET)
        waitFor("Voice couldn't connect", substring = true)
        compose.onNodeWithText("Type it instead").performClick()
        waitFor("Add expense")
        compose.onNodeWithText("Amount").assertExists()
    }

    @Test
    fun phoneWithoutSpeechServiceOffersTypingOnly() {
        useRecognizer(FakeVoiceRecognizer(isAvailable = false))
        launch()
        waitFor("This week")
        compose.onNodeWithContentDescription("Log by voice").performClick()

        waitFor("Voice input isn't available on this phone.")
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("Type it instead").assertExists()
    }

    @Test
    fun editingAVoiceExpenseKeepsItsTranscript() {
        runBlocking {
            database.expenseDao().insert(
                Expense(
                    timestamp = now.atZone(zone).toInstant(),
                    amountCents = 550,
                    category = Category.LUNCH,
                    rawTranscript = "lunch 5.50",
                    source = ExpenseSource.VOICE,
                ),
            )
        }
        launch()
        waitFor("Lunch")
        compose.onNodeWithText("Lunch").performClick()
        waitFor("Edit expense")
        compose.onNodeWithText("Amount").performTextClearance()
        compose.onNodeWithText("Amount").performTextInput("6")
        compose.onNodeWithText("Save").performClick()

        waitFor("$6.00")
        val saved = savedExpenses().single()
        assertEquals("lunch 5.50", saved.rawTranscript)
        assertEquals(ExpenseSource.VOICE, saved.source)
    }
}

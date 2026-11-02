package com.budgetapp.voiceexpense.ui.meals

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.domain.LatLng
import com.budgetapp.voiceexpense.domain.NearbyPlace
import com.budgetapp.voiceexpense.domain.OwnPlace
import com.budgetapp.voiceexpense.domain.PriceLevel
import com.budgetapp.voiceexpense.domain.formatMoney
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.ui.labelRes
import java.util.Locale
import kotlin.math.roundToInt

private const val MAPS_PACKAGE = "com.google.android.apps.maps"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealFinderScreen(
    onBack: () -> Unit,
    viewModel: MealFinderViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state = viewModel.state
    val context = LocalContext.current
    val askForLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.meal_finder)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { BudgetCard(state.budget) }
            when {
                state.needsPermission -> item {
                    LocationPrompt(
                        onAllow = {
                            askForLocation.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                    )
                }
                state.locating -> item { Progress(R.string.meal_finder_locating) }
                state.noLocation -> item {
                    Column {
                        Note(stringResource(R.string.meal_finder_no_location))
                        TextButton(onClick = viewModel::refresh, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
                else -> {
                    item { SectionTitle(R.string.meal_finder_your_places) }
                    if (state.ownPlaces.isEmpty()) item { Note(stringResource(R.string.meal_finder_no_own_places)) }
                    items(state.ownPlaces) { place ->
                        val name = place.name.ifBlank { stringResource(place.category.labelRes()) }
                        OwnPlaceRow(place, name) { openDirections(context, place.location, name) }
                    }
                    item { SectionTitle(R.string.meal_finder_nearby) }
                    nearbySection(
                        result = state.nearby,
                        perMealCents = state.budget?.perMealCents,
                        onSearchAgain = viewModel::searchAgain,
                        onOpen = { place -> openDirections(context, place.location, place.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(budget: MealBudget?) {
    if (budget == null) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val perMeal = budget.perMealCents
            val remaining = budget.remainingCents
            when {
                perMeal == null || remaining == null -> Text(stringResource(R.string.meal_finder_no_budget))
                remaining <= 0 -> Text(stringResource(R.string.meal_finder_budget_used))
                else -> {
                    Text(
                        stringResource(R.string.meal_finder_per_meal, formatMoney(perMeal)),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = if (budget.mealsLeft > 0) {
                            pluralStringResource(
                                R.plurals.meal_finder_left_for_meals,
                                budget.mealsLeft,
                                formatMoney(remaining),
                                budget.mealsLeft,
                            )
                        } else {
                            stringResource(R.string.meal_finder_left_no_meals, formatMoney(remaining))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationPrompt(onAllow: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.meal_finder_location_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.meal_finder_location_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAllow) { Text(stringResource(R.string.allow_location)) }
    }
}

private fun LazyListScope.nearbySection(
    result: NearbyResult,
    perMealCents: Long?,
    onSearchAgain: () -> Unit,
    onOpen: (NearbyPlace) -> Unit,
) {
    when (result) {
        NearbyResult.NoKey -> item { Note(stringResource(R.string.meal_finder_no_key)) }
        NearbyResult.Searching -> item { Progress(R.string.meal_finder_searching) }
        is NearbyResult.Failed -> item {
            Column {
                Note(stringResource(R.string.meal_finder_search_failed, result.reason.orEmpty()))
                SearchAgain(onSearchAgain)
            }
        }
        is NearbyResult.Found -> {
            if (result.places.isEmpty() && result.hiddenOver == 0) {
                item { Note(stringResource(R.string.meal_finder_nothing_nearby)) }
            }
            items(result.places) { item ->
                NearbyRow(item) { onOpen(item.place) }
            }
            item {
                Column {
                    if (result.hiddenOver > 0 && perMealCents != null) {
                        Note(
                            pluralStringResource(
                                R.plurals.meal_finder_hidden_over,
                                result.hiddenOver,
                                result.hiddenOver,
                                formatMoney(perMealCents),
                            ),
                        )
                    }
                    // Places data shown without a map has to say where it came from.
                    Text(
                        stringResource(R.string.meal_finder_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SearchAgain(onSearchAgain)
                }
            }
        }
    }
}

@Composable
private fun OwnPlaceRow(place: OwnPlace, name: String, onClick: () -> Unit) {
    val details = listOf(
        stringResource(R.string.meal_finder_last_price, formatMoney(place.lastCents)),
        pluralStringResource(R.plurals.meal_finder_visits, place.visits, place.visits),
        formatDistance(place.distanceMeters),
    )
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details.joinToString(" · ")) },
    )
}

@Composable
private fun NearbyRow(item: NearbyItem, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(item.place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${priceText(item.place)} · ${formatDistance(item.distanceMeters)}") },
    )
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Progress(@StringRes label: Int) {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchAgain(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(R.string.meal_finder_search_again))
    }
}

@Composable
private fun priceText(place: NearbyPlace): String {
    val from = place.priceFromCents
    val to = place.priceToCents
    val level = place.priceLevel
    return when {
        from != null && to != null -> "${shortMoney(from)}–${shortMoney(to)}"
        from != null -> stringResource(R.string.meal_finder_price_from, shortMoney(from))
        level != null -> stringResource(level.labelRes())
        else -> stringResource(R.string.meal_finder_no_price)
    }
}

/** Google's price ranges are in whole dollars, so ".00" would only add noise. */
private fun shortMoney(cents: Long): String = formatMoney(cents).removeSuffix(".00")

private fun formatDistance(meters: Double): String =
    if (meters < 1_000) {
        "${(meters / 10).roundToInt() * 10} m"
    } else {
        String.format(Locale.US, "%.1f km", meters / 1_000)
    }

@StringRes
private fun PriceLevel.labelRes(): Int = when (this) {
    PriceLevel.FREE -> R.string.price_free
    PriceLevel.INEXPENSIVE -> R.string.price_inexpensive
    PriceLevel.MODERATE -> R.string.price_moderate
    PriceLevel.EXPENSIVE -> R.string.price_expensive
    PriceLevel.VERY_EXPENSIVE -> R.string.price_very_expensive
}

/** Walking directions in Google Maps, or any map app's pin when Maps isn't installed. */
private fun openDirections(context: Context, to: LatLng, label: String) {
    val coordinates = "${to.latitude},${to.longitude}"
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$coordinates&mode=w")).setPackage(MAPS_PACKAGE),
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$coordinates(${Uri.encode(label)})")),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            continue
        }
    }
}

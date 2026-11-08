package com.chai.aiskin.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
sealed interface Route {
    @Serializable data object Splash : Route
    @Serializable data object Onboarding : Route
    @Serializable data object Login : Route
    @Serializable data object Register : Route
    @Serializable data object Home : Route
    @Serializable data object Camera : Route
    @Serializable data object Preview : Route
    @Serializable data object Scanning : Route
    /** [fresh] = just produced by a scan (vs. opened from history). */
    @Serializable data class Result(val analysisId: String, val fresh: Boolean) : Route
    /** AI product suggestions for one analysis. */
    @Serializable data class Products(val analysisId: String) : Route
    @Serializable data object History : Route
    @Serializable data class Compare(val firstId: String, val secondId: String) : Route
    @Serializable data object Profile : Route
    @Serializable data object Settings : Route
    @Serializable data object Privacy : Route
}

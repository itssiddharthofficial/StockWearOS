package com.siddharth.stockticker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.siddharth.stockticker.ui.detail.DetailScreen
import com.siddharth.stockticker.ui.home.HomeScreen
import com.siddharth.stockticker.ui.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{symbol}"
    const val SEARCH = "search"

    fun detail(symbol: String) = "detail/" + java.net.URLEncoder.encode(symbol, "UTF-8")
}

/**
 * Navigation is swipe-based, as it is everywhere else on Wear: back is a swipe
 * from the left edge, handled by [SwipeDismissableNavHost]. There is no back
 * button widget anywhere in the app.
 *
 * The swipe-to-dismiss state is hoisted here and handed to the detail screen so
 * its horizontal pager can reserve only the left edge for "back" — otherwise
 * paging and dismissing fight over the same gesture.
 */
@Composable
fun StockTickerNavGraph() {
    val navController = rememberSwipeDismissableNavController()
    val swipeState = rememberSwipeToDismissBoxState()
    val navHostState = rememberSwipeDismissableNavHostState(swipeState)

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.HOME,
        state = navHostState
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onStockClick = { symbol -> navController.navigate(Routes.detail(symbol)) },
                onSearchClick = { navController.navigate(Routes.SEARCH) }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { entry ->
            val encoded = entry.arguments?.getString("symbol").orEmpty()
            val symbol = runCatching {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            }.getOrDefault(encoded)
            DetailScreen(
                initialSymbol = symbol,
                swipeToDismissBoxState = swipeState
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(onDone = { navController.popBackStack() })
        }
    }
}

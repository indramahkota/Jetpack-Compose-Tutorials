package com.smarttoolfactory.tutorial3_1navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.random.Random


@Composable
fun Tutorial5_1Screen() {
    /*
        In this example added ViewModel's scoped to NavBackStackEntry via
        hiltViewModel()
     */
    MainContainer()
}

@Composable
private fun MainContainer() {

    val navController = rememberNavController()

    NavHost(
        modifier = Modifier.fillMaxSize(),
        navController = navController,
        startDestination = Splash

    ) {
        addNavGraph(navController)
    }
}

private fun NavGraphBuilder.addNavGraph(
    navController: NavHostController,
) {
    composable<Splash> { navBackStackEntry: NavBackStackEntry ->

        SplashScreen {
            navController.navigate(Home) {
                popUpTo<Splash> {
                    inclusive = true
                }
            }
        }
    }

    navigation<HomeGraph>(
        startDestination = Home
    ) {

        composable<Home> { navBackStackEntry: NavBackStackEntry ->

            // 🔥Passing this NavBackStackEntry to hiltViewModel is useful
            // for creating shared ViewModels between screens in same graph
            val parentBackStackEntry: NavBackStackEntry = navController.getBackStackEntry(HomeGraph)
            println("HomeScreen parentBackStackEntry: $parentBackStackEntry")

            HomeScreen(
                // 🔥 hiltViewModel() creates ViewModel scoped this navBackStackEntry
                // NavBackStackEntry implements LifecycleOwner,
                // ViewModelStoreOwner, SavedStateRegistryOwner
                homeViewModel = hiltViewModel<HomeViewModel>()
            ) { userProfile: UserProfile ->

                // 🔥🔥 Navigates to to route with UserProfile
                navController.navigate(userProfile)
            }
        }

        composable<UserProfile> { navBackStackEntry: NavBackStackEntry ->
            val parentBackStackEntry: NavBackStackEntry = navController.getBackStackEntry(HomeGraph)
            println("ProfileScreen parentBackStackEntry: $parentBackStackEntry")

            ProfileScreen(
                // 🔥 hiltViewModel() creates ViewModel scoped this navBackStackEntry
                profileViewModel = hiltViewModel<ProfileViewModel>()
            )
        }
    }
}

@Composable
private fun SplashScreen(
    onNavigateHome: () -> Unit,
) {
    val updatedLambda = rememberUpdatedState(onNavigateHome)

    LaunchedEffect(Unit) {
        delay(500)
        updatedLambda.value.invoke()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Splash Screen", fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeScreen(
    homeViewModel: HomeViewModel,
    onProfileClick: (UserProfile) -> Unit,
) {

    val userList by homeViewModel.profileFlow.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {

        items(items = userList) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable {
                        onProfileClick(it)
                    }
                    .padding(16.dp),
                text = "name: ${it.name}, id: ${it.id}"
            )
        }
    }
}

@Composable
private fun ProfileScreen(
    profileViewModel: ProfileViewModel,
) {

    val profile: UserProfile? by profileViewModel.profileFlow.collectAsStateWithLifecycle()

    profile?.let {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Text("Profile Screen", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("name: ${it.name}, id: ${it.id}", fontSize = 30.sp)
        }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _profileFlow = MutableStateFlow<List<UserProfile>>(listOf())
    val profileFlow = _profileFlow.asStateFlow()

    init {
        _profileFlow.value = List(Random.nextInt(15, 50)) {
            UserProfile(id = "$it", name = "User $it")
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _profileFlow = MutableStateFlow<UserProfile?>(null)
    val profileFlow = _profileFlow.asStateFlow()

    init {
        // 🔥 toRoute gets UserProfile object from NavBackStackEntry
        _profileFlow.value = savedStateHandle.toRoute<UserProfile>()
    }
}

@Serializable
data class UserProfile(val id: String, val name: String)

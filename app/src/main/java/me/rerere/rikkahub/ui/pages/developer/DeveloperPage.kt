package me.rerere.rikkahub.ui.pages.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.AILogging
import me.rerere.rikkahub.ui.components.nav.AppCompactTopBar
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppFloatingControlsOverlay
import me.rerere.rikkahub.ui.components.ui.AppFloatingOverlayContentBottomPadding
import me.rerere.rikkahub.ui.components.ui.AppFloatingTabBar
import me.rerere.rikkahub.ui.components.ui.AppFloatingTabButton
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeveloperPage(vm: DeveloperVM = koinViewModel()) {
    val pager = rememberPagerState { 1 }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            AppCompactTopBar(
                title = {
                    Text(
                        text = "Developer Page",
                        maxLines = 1,
                    )
                },
                navigationIcon = { BackButton() }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                contentPadding = innerPadding
            ) { page ->
                when (page) {
                    0 -> {
                        LoggingPaging(
                            vm = vm,
                            contentPadding = PaddingValues(bottom = AppFloatingOverlayContentBottomPadding)
                        )
                    }
                }
            }

            AppFloatingControlsOverlay {
                AppFloatingTabBar(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                ) {
                    AppFloatingTabButton(
                        selected = pager.currentPage == 0,
                        onClick = { scope.launch { pager.animateScrollToPage(0) } },
                        icon = Icons.Rounded.Description,
                        contentDescription = "Developer"
                    )
                }
            }
        }
    }
}

@Composable
fun LoggingPaging(
    vm: DeveloperVM,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(logs) { log ->
            when (log) {
                is AILogging.Generation -> {
                    Card {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                        }
                    }
                }
            }
        }
    }
}

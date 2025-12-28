package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
import kotlinx.coroutines.launch
import kotlin.collections.forEach

@Preview
@Composable
fun LazyColumnInfoTest() {

    var text by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()

    val density = LocalDensity.current

    val padding = with(density) {
        100.toDp()
    }

    val iteHeight = with(density) {
        100.toDp()
    }

    val height = with(density) {
        1000.toDp()
    }

    var index by remember { mutableIntStateOf(3) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
        }.collect { lazyListItemInfos: List<LazyListItemInfo> ->
            val viewportEndOffset = listState.layoutInfo.viewportEndOffset
            val height = listState.layoutInfo.viewportSize.height

            text = "viewportEndOffset: $viewportEndOffset, height:$height\n"
            lazyListItemInfos.forEach { item: LazyListItemInfo ->
                text += "i: ${item.index}, s: ${item.size}, offset: ${item.offset}\n"
            }
        }
    }

    Column {

        Text(text)
        Button(
            onClick = {
                scope.launch {
                    listState.scrollToItem(index)
                }
            }
        ) {
            Text("Scroll to $index")
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth().height(height),
            contentPadding = PaddingValues(padding)
        ) {
            items(30) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color.Red)
                        .height(iteHeight)
                ) {
                    Text("Index: $it")
                }
            }
        }
    }
}

@Preview
@Composable
fun LazyColumnMarkdownRecompositionTest() {

    var text by remember {
        mutableStateOf("")
    }

    val listState = rememberLazyListState()

    val density = LocalDensity.current

    val padding = with(density) {
        100.toDp()
    }


    val height = with(density) {
        1000.toDp()
    }

    val messages = remember {
        mutableStateListOf<String>()
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
        }.collect {

            val layoutInfo = listState.layoutInfo
            val viewportEndOffset = layoutInfo.viewportEndOffset
            val height = layoutInfo.viewportSize.height

            text = "viewportEndOffset: $viewportEndOffset, " +
                    "height:$height\n"

            var tempText = ""

            println("START NEW INFO")
            layoutInfo.visibleItemsInfo.forEach { info: LazyListItemInfo ->
                tempText += "index: ${info.index}, height: ${info.size}, offset: ${info.offset}\n"
                println(tempText)
            }

            text += tempText
        }
    }

    Column {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth().height(height),
            contentPadding = PaddingValues(padding)
        ) {
            items(messages) {
                Box(
                    modifier = Modifier.fillMaxWidth().drawBehind {
                        println("Draw height: ${size.height}")
                    }
                ) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            BasicRichText(
                                modifier = Modifier
                            ) {
                                Markdown(it)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(text)
        Button(
            onClick = {
                messages.add("Write 3 words")
            }
        ) {
            Text("Add message")
        }

    }
}

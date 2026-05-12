package io.aequicor.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private val THUMBNAIL_PANEL_WIDTH = 120.dp
private val WIDE_BREAKPOINT = 900.dp

@Composable
fun PdfViewerScreen(modifier: Modifier = Modifier) {
    val controller = rememberPdfViewerState()
    Surface(modifier = modifier.fillMaxSize()) {
        when (val state = controller.state) {
            is PdfViewerState.Idle -> IdleScreen(
                onOpenFile = controller::openPicker,
                onFileDrop = controller::loadFrom,
            )
            is PdfViewerState.Loading -> LoadingScreen()
            is PdfViewerState.Error -> ErrorScreen(message = state.message, onRetry = controller::openPicker)
            is PdfViewerState.Loaded -> LoadedScreen(controller = controller, state = state)
        }
    }
}

@Composable
private fun IdleScreen(onOpenFile: () -> Unit, onFileDrop: (ByteArray) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .dropPdfFiles(onFileDrop)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Drop a PDF here", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenFile) { Text("Open file") }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Open another file") }
    }
}

@Composable
private fun LoadedScreen(controller: PdfViewerController, state: PdfViewerState.Loaded) {
    Column(modifier = Modifier.fillMaxSize()) {
        NavigationToolbar(
            currentPage = state.currentPage,
            totalPages = state.doc.pageCount,
            viewMode = controller.viewMode,
            onNavigate = controller::navigateTo,
            onViewModeChange = { controller.viewMode = it },
            onClose = controller::close,
        )
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= WIDE_BREAKPOINT
            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ThumbnailPanel(
                        doc = state.doc,
                        currentPage = state.currentPage,
                        onSelect = controller::navigateTo,
                        modifier = Modifier.width(THUMBNAIL_PANEL_WIDTH).fillMaxHeight(),
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        MainViewer(controller = controller, state = state)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainViewer(controller = controller, state = state)
                    FloatingActionButton(
                        onClick = controller::openPicker,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) { Text("+") }
                }
            }
        }
    }
}

@Composable
private fun MainViewer(controller: PdfViewerController, state: PdfViewerState.Loaded) {
    when (controller.viewMode) {
        ViewMode.SinglePage -> PdfPageCanvas(
            doc = state.doc,
            pageIndex = state.currentPage,
            modifier = Modifier.fillMaxSize(),
        )
        ViewMode.ContinuousScroll -> ContinuousScrollViewer(
            doc = state.doc,
            currentPage = state.currentPage,
            onPageChange = controller::navigateTo,
            modifier = Modifier.fillMaxSize(),
        )
        ViewMode.TwoPage -> TwoPageViewer(
            doc = state.doc,
            currentPage = state.currentPage,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ThumbnailPanel(
    doc: PdfDocument,
    currentPage: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(count = doc.pageCount, key = { it }) { pageIndex ->
            ThumbnailItem(
                doc = doc,
                pageIndex = pageIndex,
                isSelected = pageIndex == currentPage,
                onClick = { onSelect(pageIndex) },
            )
        }
    }
}

@Composable
private fun ThumbnailItem(
    doc: PdfDocument,
    pageIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var bitmap by remember(doc, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, pageIndex) {
        bitmap = withContext(Dispatchers.Default) { doc.renderPage(pageIndex, 0.3f) }
    }
    Surface(
        tonalElevation = if (isSelected) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun ContinuousScrollViewer(
    doc: PdfDocument,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onPageChange(it) }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(count = doc.pageCount, key = { it }) { pageIndex ->
            LazyPageItem(doc = doc, pageIndex = pageIndex)
        }
    }
}

@Composable
private fun LazyPageItem(doc: PdfDocument, pageIndex: Int) {
    var bitmap by remember(doc, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, pageIndex) {
        bitmap = withContext(Dispatchers.Default) { doc.renderPage(pageIndex, 1.5f) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(400.dp),
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun TwoPageViewer(
    doc: PdfDocument,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val leftPage = if (currentPage == 0) 0 else if (currentPage % 2 == 1) currentPage else currentPage - 1
    val rightPage = if (currentPage == 0) -1 else leftPage + 1

    Row(modifier = modifier) {
        PdfPageCanvas(
            doc = doc,
            pageIndex = leftPage,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        if (rightPage in 0 until doc.pageCount) {
            PdfPageCanvas(
                doc = doc,
                pageIndex = rightPage,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun NavigationToolbar(
    currentPage: Int,
    totalPages: Int,
    viewMode: ViewMode,
    onNavigate: (Int) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onClose: () -> Unit,
) {
    var pageInput by remember(currentPage) { mutableStateOf((currentPage + 1).toString()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = { onNavigate(currentPage - 1) }, enabled = currentPage > 0) { Text("<") }

        OutlinedTextField(
            value = pageInput,
            onValueChange = { pageInput = it },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                val page = pageInput.trim().toIntOrNull()?.minus(1)
                if (page != null) onNavigate(page)
            }),
            singleLine = true,
            modifier = Modifier.width(72.dp),
        )

        Text("/ $totalPages", modifier = Modifier.padding(end = 4.dp))

        IconButton(onClick = { onNavigate(currentPage + 1) }, enabled = currentPage < totalPages - 1) { Text(">") }

        Box(modifier = Modifier.weight(1f))

        ViewModeToggle(current = viewMode, onChange = onViewModeChange)

        Button(onClick = onClose, modifier = Modifier.padding(start = 4.dp)) { Text("Close") }
    }
}

@Composable
private fun ViewModeToggle(current: ViewMode, onChange: (ViewMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { onChange(ViewMode.SinglePage) }) {
            Text(
                text = "1",
                color = if (current == ViewMode.SinglePage) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { onChange(ViewMode.ContinuousScroll) }) {
            Text(
                text = "≡",
                color = if (current == ViewMode.ContinuousScroll) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { onChange(ViewMode.TwoPage) }) {
            Text(
                text = "2",
                color = if (current == ViewMode.TwoPage) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

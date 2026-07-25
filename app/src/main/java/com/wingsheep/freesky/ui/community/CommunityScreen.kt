package com.wingsheep.freesky.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingsheep.freesky.ui.theme.TerminalAccent
import com.wingsheep.freesky.ui.theme.TerminalBackground
import com.wingsheep.freesky.ui.theme.TerminalBorder
import com.wingsheep.freesky.ui.theme.TerminalCursor
import com.wingsheep.freesky.ui.theme.TerminalDim
import com.wingsheep.freesky.ui.theme.TerminalError
import com.wingsheep.freesky.ui.theme.TerminalPrimary
import com.wingsheep.freesky.ui.theme.TerminalSuccess
import com.wingsheep.freesky.ui.theme.TerminalSurface
import com.wingsheep.freesky.ui.theme.TerminalTitle
import com.wingsheep.freesky.ui.theme.TerminalWarning

@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val postAction by viewModel.postAction.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            CommunityHeader(connectionState = connectionState)

            when (connectionState) {
                is CommunityUiState.Connecting -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    ConnectingPanel()
                }
                is CommunityUiState.Error -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    ErrorPanel(
                        message = (connectionState as CommunityUiState.Error).message,
                        onRetry = { viewModel.connect() }
                    )
                }
                is CommunityUiState.Connected -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    FeedList(
                        posts = posts,
                        isLoadingMore = isLoadingMore,
                        hasMore = hasMore,
                        onLoadMore = { viewModel.loadMore() },
                        modifier = Modifier.weight(1f)
                    )
                    PostInputBar(
                        postAction = postAction,
                        onSend = { content ->
                            viewModel.sendPost(content)
                        },
                        onResetAction = { viewModel.resetPostAction() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityHeader(connectionState: CommunityUiState) {
    val statusText = when (connectionState) {
        is CommunityUiState.Connecting -> "connecting..."
        is CommunityUiState.Connected -> "connected"
        is CommunityUiState.Error -> "disconnected"
    }
    val statusColor = when (connectionState) {
        is CommunityUiState.Connecting -> TerminalWarning
        is CommunityUiState.Connected -> TerminalSuccess
        is CommunityUiState.Error -> TerminalError
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurface)
            .border(1.dp, TerminalBorder, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "freesky",
            color = TerminalTitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = "  ·  community",
            color = TerminalDim,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "● $statusText",
            color = statusColor,
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun ConnectingPanel() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = TerminalAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            TerminalText("  establishing noise session...", color = TerminalDim, modifier = Modifier.padding(start = 8.dp))
        }
        TerminalText("> noise IK handshake [secp256r1 + ChaChaPoly + BLAKE2s]", color = TerminalDim)
        TerminalText("> loading group key...", color = TerminalDim)
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TerminalText("! session error: $message", color = TerminalError)
        TerminalText("> noise channel unavailable", color = TerminalDim)
        Spacer(modifier = Modifier.height(8.dp))
        TerminalPromptButton(
            text = "RECONNECT",
            enabled = true,
            onClick = onRetry
        )
    }
}

@Composable
private fun FeedList(
    posts: List<DecryptedPost>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Infinite-scroll: trigger loadMore when approaching the end of the list.
    val lastVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset } }
    val shouldLoadMore = remember(isLoadingMore, hasMore, posts.size) {
        derivedStateOf {
            hasMore &&
                !isLoadingMore &&
                posts.isNotEmpty() &&
                listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size >= posts.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onLoadMore()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalBackground)
            .border(1.dp, TerminalBorder, shape = RoundedCornerShape(4.dp))
    ) {
        if (posts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TerminalText("> no posts yet", color = TerminalDim)
                TerminalText("> be the first to say something", color = TerminalDim)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    PostItem(post = post)
                }
                if (isLoadingMore) {
                    item(key = "loading_more") {
                        LoadingMoreItem()
                    }
                }
                if (!hasMore && posts.isNotEmpty()) {
                    item(key = "end_of_feed") {
                        EndOfFeedItem()
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingMoreItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = TerminalAccent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(12.dp)
        )
        TerminalText("  fetching older posts...", color = TerminalDim, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EndOfFeedItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        TerminalText("── end of feed ──", color = TerminalDim)
    }
}

@Composable
private fun PostItem(post: DecryptedPost) {
    val colorHex = "#${post.authorIdentity.color.toString(16).padStart(2, '0')}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurface.copy(alpha = 0.4f))
            .border(1.dp, TerminalBorder.copy(alpha = 0.5f), shape = RoundedCornerShape(3.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = post.authorIdentity.name,
                color = TerminalAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = "·c$colorHex·e${post.mlsEpoch}",
                color = TerminalDim,
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatTime(post.timestamp),
                color = TerminalDim,
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = post.content,
            color = TerminalPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun PostInputBar(
    postAction: PostActionState,
    onSend: (String) -> Unit,
    onResetAction: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "cursor_blink"
    )

    val isSending = postAction is PostActionState.Sending
    val borderColor = when (postAction) {
        is PostActionState.Sent -> TerminalSuccess
        is PostActionState.Failed -> TerminalError
        else -> TerminalBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        // Status line
        AnimatedVisibility(visible = postAction !is PostActionState.Idle) {
            val statusText = when (postAction) {
                is PostActionState.Sending -> "> encrypting + signing + sending..."
                is PostActionState.Sent -> "> [ok] post sent via noise channel"
                is PostActionState.Failed -> "! failed: ${postAction.message}"
                is PostActionState.Idle -> ""
            }
            val statusColor = when (postAction) {
                is PostActionState.Sent -> TerminalSuccess
                is PostActionState.Failed -> TerminalError
                else -> TerminalDim
            }
            TerminalText(statusText, color = statusColor, modifier = Modifier.padding(bottom = 4.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface, shape = RoundedCornerShape(4.dp))
                .border(1.dp, borderColor, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ">",
                color = if (inputText.isNotBlank()) TerminalAccent else TerminalDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                enabled = !isSending,
                singleLine = false,
                maxLines = 3,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TerminalPrimary,
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TerminalCursor),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )
            val canSend = inputText.isNotBlank() && !isSending
            Text(
                text = "SEND",
                color = if (canSend) TerminalAccent else TerminalDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = canSend
                    ) {
                        onSend(inputText)
                        inputText = ""
                        onResetAction()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            if (canSend) {
                Text(
                    text = "█",
                    color = TerminalCursor,
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.alpha(cursorAlpha)
                )
            }
        }
    }
}

@Composable
private fun TerminalText(
    text: String,
    color: Color = TerminalPrimary,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = modifier
    )
}

@Composable
private fun TerminalPromptButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (enabled) TerminalAccent.copy(alpha = 0.1f) else TerminalSurface
    val borderColor = if (enabled) TerminalAccent else TerminalBorder
    val textColor = if (enabled) TerminalAccent else TerminalDim

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, shape = RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, shape = RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("▸", color = textColor, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

private fun formatTime(timestampMs: Long): String {
    val date = java.util.Date(timestampMs)
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

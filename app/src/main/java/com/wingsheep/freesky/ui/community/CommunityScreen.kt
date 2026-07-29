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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import com.wingsheep.freesky.ui.theme.terminalColor

@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val postAction by viewModel.postAction.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(contentPadding)
            .imePadding()
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

                    val grouped = remember(posts) {
                        val roots = posts.filter { it.parentId == null }
                        val repliesByParent = posts
                            .filter { it.parentId != null }
                            .groupBy { it.parentId!! }
                        roots.map { root -> root to (repliesByParent[root.id] ?: emptyList()) }
                    }

                    FeedList(
                        groupedPosts = grouped,
                        isLoadingMore = isLoadingMore,
                        hasMore = hasMore,
                        onLoadMore = { viewModel.loadMore() },
                        onReply = { viewModel.setReplyingTo(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PostInputBar(
                        postAction = postAction,
                        replyingTo = replyingTo,
                        onSend = { content ->
                            if (replyingTo != null) {
                                viewModel.sendReply(content)
                            } else {
                                viewModel.sendPost(content)
                            }
                        },
                        onCancelReply = { viewModel.clearReplyingTo() },
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
            text = "\u25cf $statusText",
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
        TerminalText("> noise NK handshake [secp256r1 + ChaChaPoly + BLAKE2s]", color = TerminalDim)
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
    groupedPosts: List<Pair<DecryptedPost, List<DecryptedPost>>>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onReply: (DecryptedPost) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val lastVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset } }
    val shouldLoadMore = remember(isLoadingMore, hasMore, groupedPosts.size) {
        derivedStateOf {
            hasMore &&
                !isLoadingMore &&
                groupedPosts.isNotEmpty() &&
                listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size >= groupedPosts.size - 3
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
        if (groupedPosts.isEmpty()) {
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
                items(groupedPosts, key = { it.first.id }) { (root, replies) ->
                    PostItem(
                        post = root,
                        replies = replies,
                        onReply = { onReply(root) }
                    )
                }
                if (isLoadingMore) {
                    item(key = "loading_more") {
                        LoadingMoreItem()
                    }
                }
                if (!hasMore && groupedPosts.isNotEmpty()) {
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
        TerminalText("\u2500\u2500 end of feed \u2500\u2500", color = TerminalDim)
    }
}

@Composable
private fun PostItem(
    post: DecryptedPost,
    replies: List<DecryptedPost> = emptyList(),
    onReply: () -> Unit = {}
) {
    val authorColor = terminalColor(post.authorIdentity.color)
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
                text = "0x${post.authorIdentity.name}",
                color = authorColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (post.isMine) {
                Text(
                    text = "(me)",
                    color = TerminalAccent,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(TerminalAccent.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 0.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(post.timestamp),
                color = TerminalDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
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
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "\u21a9 reply",
                color = TerminalAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(
                        TerminalAccent.copy(alpha = 0.08f),
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        TerminalAccent.copy(alpha = 0.3f),
                        RoundedCornerShape(3.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReply
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Nested replies
        if (replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalSurface.copy(alpha = 0.2f))
                    .padding(start = 8.dp, top = 6.dp, bottom = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    replies.forEach { reply ->
                        ReplyItem(reply = reply)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyItem(reply: DecryptedPost) {
    val authorColor = terminalColor(reply.authorIdentity.color)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "\u2517",
                color = TerminalDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "0x${reply.authorIdentity.name}",
                color = authorColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (reply.isMine) {
                Text(
                    text = "(me)",
                    color = TerminalAccent,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(TerminalAccent.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 0.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(reply.timestamp),
                color = TerminalDim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2503",
                color = TerminalDim.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = reply.content,
                color = TerminalPrimary.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PostInputBar(
    postAction: PostActionState,
    replyingTo: DecryptedPost?,
    onSend: (String) -> Unit,
    onCancelReply: () -> Unit,
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
        else -> if (replyingTo != null) TerminalAccent else TerminalBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        // Reply context
        AnimatedVisibility(visible = replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u21a9 replying to 0x${replyingTo?.authorIdentity?.name}",
                    color = TerminalAccent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "[cancel]",
                    color = TerminalDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCancelReply
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

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
                    text = "\u2588",
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
        fontFamily = FontFamily.Monospace,
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
        Text("\u25b8", color = textColor, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

private fun formatTime(timestampMs: Long): String {
    val date = java.util.Date(timestampMs)
    val sdf = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

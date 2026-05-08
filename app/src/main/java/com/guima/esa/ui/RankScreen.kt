package com.guima.esa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil.compose.rememberAsyncImagePainter
import com.guima.esa.R
import com.guima.esa.data.CloudSyncRepository
import com.guima.esa.data.RankRepository
import com.guima.esa.data.RankingUser
import com.guima.esa.data.UserRepository
import com.guima.esa.util.AvatarAssetResolver
import com.guima.esa.util.formatPointsCompact
import kotlinx.coroutines.delay

private const val ONLINE_WINDOW_MS = 90_000L

@Composable
fun RankScreen() {
    val repository = remember { RankRepository() }
    val rankingList by repository.getTop100Flow().collectAsState(initial = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val localUser = RankingUser(
        userId = UserRepository.getCloudUserId(),
        nickname = UserRepository.getNickname(),
        pontos = UserRepository.getRankPoints(),
        avatarId = UserRepository.getAvatarId(),
        isPremium = UserRepository.isPremium(),
        isOnline = UserRepository.isPresenceOnline(),
        lastSeenAt = UserRepository.getLastSeenAt().takeIf { it > 0L } ?: now,
        onlineSinceAt = UserRepository.getOnlineSinceAt(),
        studyTimeMs = UserRepository.getAccumulatedStudyTimeMs(now),
        platform = "android"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(localUser.userId, localUser.isPremium) {
        if (localUser.userId.isNotBlank()) {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Top 100",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (rankingList == null && localUser.nickname.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val users = mergeLocalUserIntoRanking(
                usersFromCloud = rankingList.orEmpty(),
                localUser = localUser
            )

            if (users.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum dado no ranking ainda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val top3 = users.take(3)
                val rest = users.drop(3)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (top3.size > 1) {
                        TopRankItem(
                            user = top3[1],
                            positionLabel = "2\u00BA",
                            isCurrentUser = isCurrentUser(top3[1], localUser),
                            now = now,
                            modifier = Modifier
                                .weight(1f)
                                .offset(y = 16.dp)
                        )
                    }
                    if (top3.isNotEmpty()) {
                        TopRankItem(
                            user = top3[0],
                            positionLabel = "1\u00BA",
                            isCurrentUser = isCurrentUser(top3[0], localUser),
                            now = now
                            ,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (top3.size > 2) {
                        TopRankItem(
                            user = top3[2],
                            positionLabel = "3\u00BA",
                            isCurrentUser = isCurrentUser(top3[2], localUser),
                            now = now,
                            modifier = Modifier
                                .weight(1f)
                                .offset(y = 16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(rest) { index, user ->
                        RankListItem(
                            user = user,
                            rank = index + 4,
                            isCurrentUser = isCurrentUser(user, localUser),
                            now = now
                        )
                    }
                }
            }
        }
    }
}

private fun mergeLocalUserIntoRanking(
    usersFromCloud: List<RankingUser>,
    localUser: RankingUser
): List<RankingUser> {
    if (localUser.nickname.isBlank()) return usersFromCloud

    val withoutDuplicate = usersFromCloud.filterNot { user ->
        user.userId.isNotBlank() && user.userId == localUser.userId
    }

    return (withoutDuplicate + localUser)
        .distinctBy { user -> user.userId.ifBlank { user.nickname } }
        .sortedByDescending { it.pontos }
        .take(100)
}

private fun isCurrentUser(user: RankingUser, localUser: RankingUser): Boolean {
    return when {
        user.userId.isNotBlank() && localUser.userId.isNotBlank() -> user.userId == localUser.userId
        else -> user.nickname.isNotBlank() && user.nickname == localUser.nickname
    }
}

private fun isUserOnline(user: RankingUser, now: Long): Boolean {
    if (user.lastSeenAt <= 0L) return false
    if (user.isOnline == false) return false
    return now - user.lastSeenAt <= ONLINE_WINDOW_MS
}

private fun formatPresenceLabel(user: RankingUser, now: Long): String {
    return "${formatPresenceStatusLabel(user, now)} / ${formatStudyDurationLabel(user)}"
}

private fun formatPresenceStatusLabel(user: RankingUser, now: Long): String {
    return if (isUserOnline(user, now)) {
        "online"
    } else if (user.lastSeenAt > 0L) {
        "${formatCompactDuration((now - user.lastSeenAt).coerceAtLeast(0L))} offline"
    } else {
        "offline"
    }
}

private fun formatStudyDurationLabel(user: RankingUser): String {
    return "${formatCompactDuration(user.studyTimeMs.coerceAtLeast(0L), allowZero = true)} study"
}

private fun formatCompactDuration(durationMs: Long, allowZero: Boolean = false): String {
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    val month = 30 * day
    val year = 365 * day

    if (durationMs < minute) {
        return if (allowZero) "0m" else "1m"
    }

    return when {
        durationMs < hour -> "${durationMs / minute}m"
        durationMs < day -> "${durationMs / hour}h"
        durationMs < month -> "${durationMs / day}d"
        durationMs < year -> "${durationMs / month}mo"
        else -> "${durationMs / year}a"
    }
}

@Composable
fun TopRankItem(
    user: RankingUser,
    positionLabel: String,
    isCurrentUser: Boolean,
    now: Long,
    modifier: Modifier = Modifier
) {
    val premiumAccent = premiumGold()
    val currentUserAccent = currentUserBorder()
    val online = isUserOnline(user, now)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Text(
            text = positionLabel,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        RankUserAvatar(
            avatarId = user.avatarId,
            size = 64.dp,
            borderColor = when {
                user.isPremium -> premiumAccent
                isCurrentUser -> currentUserAccent
                else -> Color.Transparent
            },
            borderWidth = when {
                user.isPremium -> 2.dp
                isCurrentUser -> 1.5.dp
                else -> 0.dp
            },
            isOnline = online
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = user.nickname,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (user.isPremium) premiumAccent else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = formatPresenceStatusLabel(user, now),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (online) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatStudyDurationLabel(user),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${formatPointsCompact(user.pontos)} pts",
            fontSize = 14.sp,
            color = if (user.isPremium) premiumAccent else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun RankListItem(
    user: RankingUser,
    rank: Int,
    isCurrentUser: Boolean,
    now: Long
) {
    val premiumAccent = premiumGold()
    val premiumContainer = premiumGoldContainer()
    val currentUserAccent = currentUserBorder()
    val online = isUserOnline(user, now)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (user.isPremium) {
                premiumContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = when {
            user.isPremium -> BorderStroke(2.dp, premiumAccent.copy(alpha = 0.9f))
            isCurrentUser -> BorderStroke(1.5.dp, currentUserAccent)
            else -> null
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(40.dp),
                color = if (user.isPremium) premiumAccent else MaterialTheme.colorScheme.onSurface
            )
            RankUserAvatar(
                avatarId = user.avatarId,
                size = 40.dp,
                borderColor = when {
                    user.isPremium -> premiumAccent
                    isCurrentUser -> currentUserAccent
                    else -> Color.Transparent
                },
                borderWidth = when {
                    user.isPremium -> 2.dp
                    isCurrentUser -> 1.dp
                    else -> 0.dp
                },
                isOnline = online
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.nickname,
                    fontWeight = FontWeight.Medium,
                    color = if (user.isPremium) premiumAccent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatPresenceLabel(user, now),
                    fontSize = 12.sp,
                    color = if (online) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (user.isPremium) {
                PremiumSeal(modifier = Modifier.padding(end = 10.dp))
            }
            Text(
                text = "${formatPointsCompact(user.pontos)} pts",
                fontWeight = FontWeight.SemiBold,
                color = if (user.isPremium) premiumAccent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PremiumSeal(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.premium_seal),
        contentDescription = "Premium",
        modifier = modifier.size(34.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun RankUserAvatar(
    avatarId: Int,
    size: Dp,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    isOnline: Boolean = false
) {
    val context = LocalContext.current
    val avatarPath = AvatarAssetResolver.getAvatarAssetPath(context, avatarId)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(borderWidth, borderColor)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = avatarPath,
                    error = painterResource(id = android.R.drawable.ic_menu_report_image)
                ),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size((size * 0.22f).coerceAtLeast(10.dp))
                .background(
                    color = if (isOnline) Color(0xFF22C55E) else Color(0xFF64748B),
                    shape = CircleShape
                )
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

@Composable
private fun premiumGold(): Color {
    return if (isLightTheme()) {
        Color(0xFF7A4A00)
    } else {
        Color(0xFFFFD54F)
    }
}

@Composable
private fun premiumGoldContainer(): Color {
    return if (isLightTheme()) {
        Color(0xFFFFF1BF)
    } else {
        Color(0xFFFFD54F).copy(alpha = 0.16f)
    }
}

@Composable
private fun currentUserBorder(): Color {
    return if (isLightTheme()) {
        Color(0xFF8A93A3)
    } else {
        Color(0xFF9CA3AF)
    }
}

@Composable
private fun isLightTheme(): Boolean {
    return ColorUtils.calculateLuminance(MaterialTheme.colorScheme.background.toArgb()) > 0.5
}

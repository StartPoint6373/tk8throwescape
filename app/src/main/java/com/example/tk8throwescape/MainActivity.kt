package com.example.tk8throwescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

// ---------- ENUM ----------

enum class KeyInput { LEFT, RIGHT, BOTH }

enum class JudgeResult(val text: String, val color: Color) {
    SUCCESS("성공!", Color.Green),
    FAILURE("실패", Color.Red)
}

enum class AppState {
    READY,      // 시작 버튼 화면
    PLAYING,    // 영상 재생 중
    BLACKOUT    // 0.3초 전환
}

// ---------- ACTIVITY ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThrowPracticeScreen()
        }
    }
}

// ---------- UI ----------

@Composable
fun ThrowPracticeScreen() {
    val context = LocalContext.current

    val videos = remember {
        listOf(
            "leftthrow_60fps",
            "rightthrow_60fps",
            "allthrow_60fps"
        )
    }

    var appState by remember { mutableStateOf(AppState.READY) }
    var currentVideo by remember { mutableStateOf("") }

    var judgeResult by remember { mutableStateOf<JudgeResult?>(null) }
    var comboCount by remember { mutableStateOf(0) }

    var waitingForEnd by remember { mutableStateOf(false) }

    // -------- ExoPlayer --------

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && waitingForEnd) {
                    waitingForEnd = false
                    appState = AppState.BLACKOUT
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // -------- 영상 로드 --------

    LaunchedEffect(currentVideo) {
        if (currentVideo.isEmpty()) return@LaunchedEffect

        val resId = context.resources.getIdentifier(
            currentVideo, "raw", context.packageName
        )
        if (resId == 0) return@LaunchedEffect

        val mediaItem =
            MediaItem.fromUri("android.resource://${context.packageName}/$resId")

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    // -------- 블랙아웃 --------

    LaunchedEffect(appState) {
        if (appState == AppState.BLACKOUT) {
            delay(300)
            judgeResult = null
            appState = AppState.PLAYING
            currentVideo = videos.random()
        }
    }

    // -------- 현재 시간 --------

    var currentTimeMs by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying) {
                currentTimeMs = player.currentPosition
            }
            delay(16)
        }
    }
    val currentTimeSec = currentTimeMs / 1000.0

    // ================= UI =================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        when (appState) {

            // ---------- 시작 화면 ----------
            AppState.READY -> {
                Text(
                    text = "TK8 Throw Escape Trainer",
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                Button(onClick = {
                    comboCount = 0
                    judgeResult = null
                    currentVideo = videos.random()
                    appState = AppState.PLAYING
                }) {
                    Text("시작")
                }
            }

            // ---------- 영상 재생 ----------
            AppState.PLAYING -> {

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "연속 성공: $comboCount",
                    color = Color.Cyan,
                    fontSize = 18.sp
                )

                Text(
                    text = judgeResult?.text ?: "",
                    color = judgeResult?.color ?: Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    fun onPress(input: KeyInput) {
                        if (judgeResult != null) return

                        judgeResult = judge(input, currentVideo, currentTimeSec)

                        if (judgeResult == JudgeResult.SUCCESS) {
                            comboCount++
                        } else {
                            comboCount = 0
                        }

                        waitingForEnd = true
                    }

                    Button(onClick = { onPress(KeyInput.LEFT) }) { Text("1") }
                    Button(onClick = { onPress(KeyInput.BOTH) }) { Text("1+2") }
                    Button(onClick = { onPress(KeyInput.RIGHT) }) { Text("2") }
                }
            }

            // ---------- 블랙아웃 ----------
            AppState.BLACKOUT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }
}

// ---------- 판정 로직 ----------

fun judge(
    input: KeyInput,
    videoName: String,
    timeSec: Double
): JudgeResult {

    val fps = 60
    val startFrame = (2.016 * fps).toInt()
    val endFrame = startFrame + 14
    val currentFrame = (timeSec * fps).toInt()

    val correctKey = when {
        videoName.contains("left", true) -> KeyInput.LEFT
        videoName.contains("right", true) -> KeyInput.RIGHT
        videoName.contains("all", true) -> KeyInput.BOTH
        else -> null
    }

    val timingOk = currentFrame in startFrame..endFrame
    val inputOk = input == correctKey

    return if (timingOk && inputOk) {
        JudgeResult.SUCCESS
    } else {
        JudgeResult.FAILURE
    }
}

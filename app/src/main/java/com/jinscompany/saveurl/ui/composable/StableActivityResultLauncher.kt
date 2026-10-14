package com.jinscompany.saveurl.ui.composable

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.util.WeakHashMap

/**
 * 고정 [key] 로 등록하는 ActivityResult 런처.
 *
 * rememberLauncherForActivityResult 는 키를 rememberSaveable 로 만들기 때문에, 다른 화면(파일 선택 등)에 다녀오는 동안
 * 회전으로 한 화면 ↔ 2분할이 바뀌어 컴포지션 구조가 달라지면 키가 돌아오지 않아 결과가 버려진다
 * (CSV 백업이 빈 파일로 남음. 38번 리뷰 2번 재검토).
 *
 * 화면 전환 애니메이션 동안 같은 화면이 잠깐 둘 있을 수 있다(나갔다 바로 다시 들어옴). 같은 키는 레지스트리에 하나라
 * 나중에 등록한 쪽이 결과를 받고, 마지막 사용처가 사라질 때만 등록을 푼다 (먼저 사라지는 쪽이 새 등록을 풀어
 * launch 에서 IllegalStateException 이 나지 않게).
 *
 * Activity 가 다시 만들어지는 동안 도착한 결과는 레지스트리가 들고 있다가, 새 화면이 같은 키로 등록할 때 넘겨준다.
 */
@Composable
fun <I, O> rememberStableActivityResultLauncher(
    key: String,
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> {
    val registry = checkNotNull(LocalActivityResultRegistryOwner.current) {
        "ActivityResultRegistryOwner 가 없습니다"
    }.activityResultRegistry
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = remember(registry, key) {
        registry.register(key, contract) { result -> currentOnResult(result) }
    }
    DisposableEffect(registry, key) {
        val users = registrationCounts.getOrPut(registry) { mutableMapOf() }
        users[key] = (users[key] ?: 0) + 1
        onDispose {
            val left = (users[key] ?: 1) - 1
            if (left <= 0) {
                users.remove(key)
                launcher.unregister()
            } else {
                users[key] = left
            }
        }
    }
    return launcher
}

/** 레지스트리(Activity)별 키 사용처 수. 메인 스레드에서만 쓴다 */
private val registrationCounts = WeakHashMap<ActivityResultRegistry, MutableMap<String, Int>>()

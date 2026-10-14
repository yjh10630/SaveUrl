package com.jinscompany.saveurl.ui.composable

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * 고정 [key] 로 등록하는 ActivityResult 런처.
 *
 * rememberLauncherForActivityResult 는 키를 rememberSaveable 로 만들기 때문에, 다른 화면(파일 선택 등)에 다녀오는 동안
 * 회전으로 한 화면 ↔ 2분할이 바뀌어 컴포지션 구조가 달라지면 키가 돌아오지 않아 결과가 버려진다
 * (CSV 백업이 빈 파일로 남음. 38번 리뷰 2번 재검토). 같은 [key] 를 쓰는 런처는 한 번에 하나만 있어야 한다.
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
    DisposableEffect(launcher) {
        onDispose { launcher.unregister() }
    }
    return launcher
}

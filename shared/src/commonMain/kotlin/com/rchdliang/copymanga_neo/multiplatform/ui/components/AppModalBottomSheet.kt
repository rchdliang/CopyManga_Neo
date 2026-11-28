package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalWithHostDefaultOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventDispatcherOwnerHostDefaultKey
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

// The NavHost supplies its own owner. The dialog window receives system gestures on its host owner.
private val LocalSheetWindowOwner = compositionLocalWithHostDefaultOf(NavigationEventDispatcherOwnerHostDefaultKey)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val commitOnly = LocalUiPlatform.current?.supportsPredictiveBack == true &&
        !LocalPredictiveBackEnabled.current && LocalSheetWindowOwner.current != null
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    var closing by remember { mutableStateOf(false) }
    val closeOnCommit = {
        if (!closing) {
            closing = true
            scope.launch {
                try {
                    sheetState.hide()
                    if (!sheetState.isVisible) latestDismiss()
                } finally {
                    if (sheetState.isVisible) closing = false
                }
            }
        }
    }
    if (commitOnly) BackHandler(isTransient = true, onBack = closeOnCommit)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !commitOnly),
        contentWindowInsets = contentWindowInsets,
    ) {
        if (commitOnly) {
            val windowOwner = LocalSheetWindowOwner.current
            if (windowOwner != null) CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides windowOwner) {
                NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = closeOnCommit)
            }
        }
        content()
    }
}

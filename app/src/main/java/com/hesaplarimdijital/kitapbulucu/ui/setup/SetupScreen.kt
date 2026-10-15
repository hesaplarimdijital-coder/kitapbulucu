package com.hesaplarimdijital.kitapbulucu.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hesaplarimdijital.kitapbulucu.R
import com.hesaplarimdijital.kitapbulucu.model.Owner
import com.hesaplarimdijital.kitapbulucu.store.AppSettings
import com.hesaplarimdijital.kitapbulucu.store.LibraryStore
import com.hesaplarimdijital.kitapbulucu.store.LoadStatus
import com.hesaplarimdijital.kitapbulucu.ui.PhoneForm
import com.hesaplarimdijital.kitapbulucu.ui.BottomPrimaryButton
import com.hesaplarimdijital.kitapbulucu.ui.FilePicker
import com.hesaplarimdijital.kitapbulucu.ui.PhoneHeader
import com.hesaplarimdijital.kitapbulucu.ui.isPhone
import desktopkit.components.DeskButton
import desktopkit.components.SegmentedChoice
import desktopkit.screens.PageHeader
import desktopkit.settings.SettingsSection
import desktopkit.theme.DeskTheme

/**
 * First start: whose device this is, then the two files (chosen or created). [onDone] once this
 * device's own list reads; the other list may follow later from Settings.
 */
@Composable
fun SetupScreen(
    store: LibraryStore,
    settings: AppSettings,
    picker: FilePicker,
    onMap: (Owner) -> Unit,
    onDone: () -> Unit,
) {
    val states by store.states.collectAsState()
    val previews by store.previews.collectAsState()
    val colors = DeskTheme.colors
    val type = DeskTheme.type
    val dimens = DeskTheme.dimens
    val me = settings.me
    val phone = isPhone()
    val ownReady = me != null && states.getValue(me).status == LoadStatus.READY
    val order = if (me == Owner.ME) listOf(Owner.ME, Owner.DAD) else listOf(Owner.DAD, Owner.ME)
    val body: @Composable () -> Unit = {
        if (phone) PhoneHeader(stringResource(R.string.setup_title)) else PageHeader(stringResource(R.string.setup_title))
        Text(
            stringResource(R.string.setup_intro),
            style = type.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = dimens.pagePadding).widthIn(max = 760.dp),
        )
        Spacer(Modifier.height(dimens.gapS))
        SettingsSection(stringResource(R.string.setup_who_title)) {
            Text(stringResource(R.string.setup_who_text), style = type.body, color = colors.textSecondary)
            Spacer(Modifier.height(dimens.gapM))
            SegmentedChoice(
                options = listOf<Pair<Owner?, String>>(Owner.DAD to stringResource(R.string.setup_who_dad), Owner.ME to stringResource(R.string.setup_who_me)),
                selected = me,
                onSelect = { owner -> if (owner != null) settings.updateMe(owner) },
            )
        }
        for (owner in order) {
            LibraryFileCard(
                owner = owner,
                state = states.getValue(owner),
                isMine = me?.let { it == owner },
                selectedSheet = previews[owner]?.selected,
                picker = picker,
                onChooseSheet = { store.chooseSheet(owner, it) },
                onMap = { onMap(owner) },
                onRetry = store::refresh,
            )
        }
    }
    val needText = stringResource(if (me == null) R.string.setup_need_who else R.string.setup_need_own)
    if (phone) {
        // phone: the cards scroll, "Bitir" stays at the bottom (in the scroll while the keyboard is open)
        PhoneForm(
            bar = {
                if (!ownReady) Text(needText, style = type.body, color = colors.textSecondary)
                BottomPrimaryButton(stringResource(R.string.setup_done), onDone, enabled = ownReady)
                Text(stringResource(R.string.setup_later), style = type.labelSmall, color = colors.textTertiary)
            },
        ) {
            body()
            Spacer(Modifier.height(dimens.gapL))
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        body()
        Column(
            Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.gapM).widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.gapS),
        ) {
            if (!ownReady) {
                Text(needText, style = type.body, color = colors.textSecondary)
            }
            DeskButton(stringResource(R.string.setup_done), onDone, primary = true, enabled = ownReady)
            Text(stringResource(R.string.setup_later), style = type.labelSmall, color = colors.textTertiary)
        }
        Spacer(Modifier.height(dimens.gapL))
    }
}

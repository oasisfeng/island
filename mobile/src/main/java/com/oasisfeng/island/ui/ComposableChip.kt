package com.oasisfeng.island.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable fun MutexChipGroup(chips: List<Pair<String, Int>>, selectedIdState: MutableState<Int?>,
                   arrangement: Arrangement.Horizontal = Arrangement.Start) {
    val selectedId = selectedIdState.value
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
        chips.forEach { chip ->
            val id = chip.second
            ToggleableChip(chip.first, id == selectedId) { selectedIdState.value = if (it) id else null }}}
}

@Composable fun ToggleableChip(label: String, checked: Boolean, onValueChange: (Boolean) -> Unit) {
    val fillColor = if (checked) MaterialTheme.colors.primary else Color.Transparent
    val textColor = contentColorFor(if (checked) fillColor else MaterialTheme.colors.background) // contentColorFor() does not work for Color.Transparent

    Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, color = MaterialTheme.colors.primary),
        modifier = Modifier.padding(8.dp), color = fillColor) {

        Row(modifier = Modifier/*.background(fillColor)*/.toggleable(value = checked, onValueChange = onValueChange)) {
            Text(text = label, color = textColor, style = MaterialTheme.typography.body1, modifier = Modifier.padding(12.dp)) }}
}

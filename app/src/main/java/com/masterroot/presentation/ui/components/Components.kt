package com.masterroot.presentation.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootType

// ─── Section Card ─────────────────────────────────────────────────────────────

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MasterRootColors.CardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MasterRootColors.Card)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        content = content
    )
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MasterRootType.Label,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

// ─── Status Indicator (dot + label) ──────────────────────────────────────────

@Composable
fun StatusIndicator(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MasterRootType.StatusChip, color = color)
    }
}

// ─── Info Row ────────────────────────────────────────────────────────────────

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = MasterRootColors.OnSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MasterRootType.Label)
        Text(value, style = MasterRootType.Body, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

// ─── Primary Action Button ────────────────────────────────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MasterRootColors.Primary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = MasterRootColors.OnPrimary,
            disabledContainerColor = MasterRootColors.SurfaceVariant,
            disabledContentColor = MasterRootColors.OnSurfaceDim
        )
    ) {
        Text(
            text = text,
            style = MasterRootType.TitleMedium,
            color = if (enabled) MasterRootColors.OnPrimary else MasterRootColors.OnSurfaceDim
        )
    }
}

// ─── Secondary Button ─────────────────────────────────────────────────────────

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MasterRootColors.OnSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) MasterRootColors.CardBorder else MasterRootColors.SurfaceVariant
        )
    ) {
        Text(text, style = MasterRootType.TitleMedium)
    }
}

// ─── Danger Button ────────────────────────────────────────────────────────────

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MasterRootColors.Error,
            contentColor = Color.White
        )
    ) {
        Text(text, style = MasterRootType.TitleMedium, color = Color.White)
    }
}

// ─── Progress Section ─────────────────────────────────────────────────────────

@Composable
fun OperationProgress(
    label: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MasterRootType.Body)
            Text("${(progress * 100).toInt()}%", style = MasterRootType.Body,
                color = MasterRootColors.Primary)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MasterRootColors.Primary,
            trackColor = MasterRootColors.SurfaceVariant
        )
    }
}

// ─── Connection Method Button ─────────────────────────────────────────────────

@Composable
fun ConnectionMethodButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MasterRootColors.Primary else MasterRootColors.CardBorder
    val bgColor = if (selected) MasterRootColors.PrimaryContainer else MasterRootColors.Card

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp, borderColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MasterRootColors.Primary else MasterRootColors.OnSurfaceDim,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MasterRootType.TitleMedium,
            color = if (selected) MasterRootColors.Primary else MasterRootColors.OnSurface
        )
    }
}

// ─── Warning Banner ───────────────────────────────────────────────────────────

@Composable
fun WarningBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MasterRootColors.WarningDim)
            .border(1.dp, MasterRootColors.Warning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("⚠", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(message, style = MasterRootType.Body, color = MasterRootColors.Warning)
    }
}

// ─── Error Banner ─────────────────────────────────────────────────────────────

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MasterRootColors.ErrorDim)
            .border(1.dp, MasterRootColors.Error.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("✕", fontSize = 16.sp, color = MasterRootColors.Error)
        Spacer(Modifier.width(8.dp))
        Text(message, style = MasterRootType.Body, color = MasterRootColors.Error)
    }
}

// ─── Divider ─────────────────────────────────────────────────────────────────

@Composable
fun MRDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MasterRootColors.CardBorder,
        thickness = 1.dp
    )
}

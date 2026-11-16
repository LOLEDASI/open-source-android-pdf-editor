package com.mobile.pdfeditor.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.pdfeditor.ui.theme.SectionLabel

private data class Tool(val id: ToolId, val name: String, val desc: String, val icon: ImageVector)
private data class ToolGroup(val title: String, val tint: Color, val ink: Color, val tools: List<Tool>)

private val groups = listOf(
    ToolGroup(
        "Organize", Color(0xFFE8EEFC), Color(0xFF1E47B0),
        listOf(
            Tool(ToolId.Merge, "Merge", "Combine PDFs", Icons.Outlined.Layers),
            Tool(ToolId.Split, "Split", "By page or range", Icons.AutoMirrored.Outlined.CallSplit),
            Tool(ToolId.Compress, "Compress", "Shrink file size", Icons.Outlined.Compress),
            Tool(ToolId.Reorder, "Reorder", "Move, rotate pages", Icons.Outlined.SwapVert),
        ),
    ),
    ToolGroup(
        "Create & convert", Color(0xFFE3F4EC), Color(0xFF13693A),
        listOf(
            Tool(ToolId.Scan, "Scan to PDF", "Camera, auto-crop", Icons.Outlined.DocumentScanner),
            Tool(ToolId.ImagesToPdf, "Images to PDF", "From gallery", Icons.Outlined.AddPhotoAlternate),
            Tool(ToolId.PdfToImages, "PDF to images", "PNG or JPG", Icons.Outlined.Image),
            Tool(ToolId.Ocr, "OCR", "Make text searchable", Icons.Outlined.FindInPage),
        ),
    ),
    ToolGroup(
        "Protect", Color(0xFFFDEDE3), Color(0xFF9A3D0B),
        listOf(
            Tool(ToolId.Password, "Password", "Lock or unlock", Icons.Outlined.Lock),
            Tool(ToolId.Redact, "Redact", "Remove text for good", Icons.Outlined.VisibilityOff),
            Tool(ToolId.Watermark, "Watermark", "Text on every page", Icons.Outlined.WaterDrop),
            Tool(ToolId.Flatten, "Flatten", "Lock annotations", Icons.Outlined.LayersClear),
        ),
    ),
)

@Composable
fun ToolsScreen(onTool: (ToolId) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
                Text("Tools", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Everything runs on your phone. Files never leave the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    group.title.uppercase(),
                    style = SectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp),
                )
            }
            items(group.tools) { tool ->
                ToolCard(tool, group, onClick = { onTool(tool.id) })
            }
        }
    }
}

@Composable
private fun ToolCard(tool: Tool, group: ToolGroup, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(group.tint),
            ) {
                Icon(tool.icon, contentDescription = null, tint = group.ink, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(tool.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(tool.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

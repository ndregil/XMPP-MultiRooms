package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.XmppMessage

@Composable
fun FileMessageBubbleContent(
    message: XmppMessage,
    textColor: Color
) {
    val context = LocalContext.current
    val isImage = message.fileMimeType?.startsWith("image/") == true
    val isVideo = message.fileMimeType?.startsWith("video/") == true

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        if (isImage && message.fileUrl != null) {
            AsyncImage(
                model = message.fileUrl,
                contentDescription = "Image preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        // Open in browser or gallery
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(message.fileUrl))
                        context.startActivity(intent)
                    },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(textColor.copy(alpha = 0.1f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isImage -> Icons.Default.Image
                    isVideo -> Icons.Default.PlayCircle
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.fileName ?: "File",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(message.fileSize ?: 0L),
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = {
                // Open URL to "download"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(message.fileUrl))
                context.startActivity(intent)
            }) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = textColor
                )
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

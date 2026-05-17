package montafra.beam.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import montafra.beam.R

data class ChangelogEntry(val versionCode: Int, val versionName: String, val body: String)

fun loadChangelogs(context: Context, sinceVersionCode: Int, currentVersionCode: Int): List<ChangelogEntry> {
    val assetMgr = context.assets
    val locale = context.resources.configuration.locales[0].language
    val lang = listOf(locale, "en").firstOrNull { tag ->
        runCatching { assetMgr.list("changelogs/$tag") }.getOrNull()?.isNotEmpty() == true
    } ?: return emptyList()
    val files = assetMgr.list("changelogs/$lang") ?: return emptyList()
    return files
        .mapNotNull { name ->
            val code = name.removeSuffix(".txt").toIntOrNull() ?: return@mapNotNull null
            if (code <= sinceVersionCode || code > currentVersionCode) return@mapNotNull null
            val raw = runCatching {
                assetMgr.open("changelogs/$lang/$name").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            val lines = raw.lines()
            ChangelogEntry(
                versionCode = code,
                versionName = lines.firstOrNull()?.trim().orEmpty(),
                body = lines.drop(1).joinToString("\n").trim(),
            )
        }
        .sortedByDescending { it.versionCode }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ico_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.whatsNew),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            entries.forEach { entry ->
                if (entry.versionName.isNotEmpty()) {
                    Text(
                        text = "Beam ${entry.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = entry.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

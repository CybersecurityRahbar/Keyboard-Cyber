package com.cybersecurityrahbar.keyboardcyber

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybersecurityrahbar.keyboardcyber.data.ClipRecord
import com.cybersecurityrahbar.keyboardcyber.data.ClipStore
import com.cybersecurityrahbar.keyboardcyber.data.KeyboardPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = darkColorScheme(
                primary = Color(0xFF35E0BE),
                secondary = Color(0xFF5AA9FF),
                background = Color(0xFF07111E),
                surface = Color(0xFF101E30),
                onPrimary = Color(0xFF06131B),
                onBackground = Color(0xFFE8F1FA),
                onSurface = Color(0xFFE8F1FA)
            )
            MaterialTheme(colorScheme = scheme) {
                ArchiveScreen()
            }
        }
    }
}

@Composable
private fun ArchiveScreen() {
    val context = LocalContext.current
    val store = remember { ClipStore.get(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var records by remember { mutableStateOf(emptyList<ClipRecord>()) }
    var totalCount by remember { mutableStateOf(0L) }
    var favoriteCount by remember { mutableStateOf(0L) }
    var suggestionsEnabled by remember {
        mutableStateOf(KeyboardPreferences.archiveSuggestionsEnabled(context))
    }

    LaunchedEffect(query) {
        val snapshot = withContext(Dispatchers.IO) {
            Triple(store.search(query), store.count(), store.favoriteCount())
        }
        records = snapshot.first
        totalCount = snapshot.second
        favoriteCount = snapshot.third
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF07111E)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0B1A2B), Color(0xFF07111E), Color(0xFF050C15))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "KEYBOARD CYBER",
                            color = Color(0xFF35E0BE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "مستودع الحافظة",
                            color = Color(0xFFEAF4FF),
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF102C3A),
                        modifier = Modifier.border(1.dp, Color(0x5535E0BE), RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            "LOCAL ARCHIVE",
                            color = Color(0xFF35E0BE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102236)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "ابدأ بتفعيل لوحة Cyber Keyboard",
                            color = Color(0xFFEAF4FF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "الحفظ التلقائي يعمل عندما تكون هذه اللوحة هي لوحة الإدخال الافتراضية. لا يكفي تثبيت التطبيق فقط.",
                            color = Color(0xFF9FB3C8),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF35E0BE),
                                    contentColor = Color(0xFF06131B)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("تفعيل اللوحة", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val manager = context.getSystemService(
                                        Context.INPUT_METHOD_SERVICE
                                    ) as InputMethodManager
                                    manager.showInputMethodPicker()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF19334A),
                                    contentColor = Color(0xFFEAF4FF)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("اختيار الافتراضية")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("إجمالي السجلات", totalCount.toString(), Modifier.weight(1f))
                    StatCard("المفضلة", favoriteCount.toString(), Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ابحث داخل النصوص المحفوظة") },
                    leadingIcon = { Text("⌕", fontSize = 23.sp, color = Color(0xFF35E0BE)) },
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF102236))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "اقتراحات الأرشيف داخل اللوحة",
                            color = Color(0xFFEAF4FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "تعرض نصوصاً نسختها سابقاً فقط، ولا توجد اقتراحات كتابة تلقائية.",
                            color = Color(0xFF91A7BD),
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = suggestionsEnabled,
                        onCheckedChange = {
                            suggestionsEnabled = it
                            KeyboardPreferences.setArchiveSuggestionsEnabled(context, it)
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("آخر عمليات النسخ", color = Color(0xFFEAF4FF), fontWeight = FontWeight.Bold)
                    Text("لا حذف تلقائي", color = Color(0xFF35E0BE), fontSize = 11.sp)
                }

                Spacer(Modifier.height(6.dp))

                if (records.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF0D1B2B))
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⌁", fontSize = 36.sp, color = Color(0xFF35E0BE))
                        Text(
                            if (query.isBlank()) "الأرشيف جاهز لاستقبال أول نص" else "لا توجد نتائج مطابقة",
                            color = Color(0xFFEAF4FF),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "بعد تفعيل اللوحة ونسخ نص من أي تطبيق سيظهر هنا.",
                            color = Color(0xFF91A7BD),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(records, key = { it.id }) { record ->
                            ClipCard(
                                record = record,
                                onCopy = {
                                    val clipboard = context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Keyboard Cyber internal", record.content)
                                    )
                                    Toast.makeText(context, "تم نسخ النص كاملاً", Toast.LENGTH_SHORT).show()
                                },
                                onFavorite = {
                                    scope.launch {
                                        val snapshot = withContext(Dispatchers.IO) {
                                            store.setFavorite(record.id, !record.isFavorite)
                                            Triple(store.search(query), store.count(), store.favoriteCount())
                                        }
                                        records = snapshot.first
                                        totalCount = snapshot.second
                                        favoriteCount = snapshot.third
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102236)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(value, color = Color(0xFF35E0BE), fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color(0xFF9FB3C8), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ClipCard(
    record: ClipRecord,
    onCopy: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101E30)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x2235E0BE), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(
                record.content,
                color = Color(0xFFE4EDF7),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                SimpleDateFormat("EEEE، yyyy/MM/dd • HH:mm:ss", Locale.getDefault())
                    .format(Date(record.copiedAt)),
                color = Color(0xFF8CA5BC),
                fontSize = 10.sp
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF173A4A),
                        contentColor = Color(0xFF53E5C4)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("نسخ النص كاملاً", fontSize = 11.sp)
                }
                Button(
                    onClick = onFavorite,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF202B40),
                        contentColor = if (record.isFavorite) Color(0xFFFFD166) else Color(0xFFB4C4D5)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(if (record.isFavorite) "★ مفضلة" else "☆ أضف للمفضلة", fontSize = 11.sp)
                }
            }
        }
    }
}

package com.example.gdl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun SettingsScreenPreview() {
    val bgColor = Color(0xFFF5F5FA)
    val cardBg = Color.White
    val line = Color(0xFFE3E3EC)
    val primary = Color(0xFF007AFF)
    val primaryBg = Color(0xFFEBF3FF)
    val textColor = Color(0xFF1B1B1F)
    val muted = Color(0xFF6B6B7B)
    val okBg = Color(0xFFDFF5E6)
    val okFg = Color(0xFF15803D)
    val errFg = Color(0xFFB3261E)
    val errLine = Color(0xFFF0C6C2)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Üst Başlık (Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(cardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, line, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Ayarlar",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // KAYDETME BÖLÜMÜ
            Text(
                text = "Kaydetme",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, line, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Galeri klasörü adı", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = "GalleryDl",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        "Pictures/… ve Movies/… altında oluşturulur",
                        fontSize = 12.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Dosya adı şablonu", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        placeholder = { Text("örn. {category}_{id}_{num}.{extension}", color = Color(0xFF9A9AAA)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        "Boş bırakırsan varsayılan kullanılır (yalnızca gallery-dl)",
                        fontSize = 12.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White)
                    ) {
                        Text("Kaydet", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ÇEREZ BÖLÜMÜ
            Text(
                text = "Çerez",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, line, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Giriş isteyen siteler için cookies.txt (Netscape biçimi)", fontSize = 12.sp, color = muted)
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .background(okBg, RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Kayıtlı · 1240 bayt", fontSize = 12.sp, color = okFg, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        placeholder = { Text("cookies.txt içeriğini buraya yapıştırın", color = Color(0xFF9A9AAA)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBg, contentColor = primary)
                        ) {
                            Text("Kaydet", fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {},
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBg, contentColor = primary)
                        ) {
                            Text("Dosyadan aktar", fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBg, contentColor = errFg),
                        border = BorderStroke(1.dp, errLine)
                    ) {
                        Text("Çerezi sil", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // GEÇMİŞ BÖLÜMÜ
            Text(
                text = "Geçmiş",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, line, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Ana ekrandaki indirme listesini siler. Galerideki dosyalara dokunmaz.",
                        fontSize = 12.sp,
                        color = muted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBg, contentColor = errFg),
                        border = BorderStroke(1.dp, errLine)
                    ) {
                        Text("Geçmişi temizle", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

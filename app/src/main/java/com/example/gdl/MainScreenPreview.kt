package com.example.gdl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val InterFontFamily = FontFamily(Font(R.font.inter))

@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
fun MainScreenPreview() {
    val bgColor = Color(0xFFF5F5FA)
    val cardBg = Color.White
    val line = Color(0xFFE3E3EC)
    val primary = Color(0xFF007AFF)
    val primaryBg = Color(0xFFEBF3FF)
    val textColor = Color(0xFF1B1B1F)
    val muted = Color(0xFF6B6B7B)
    val okBg = Color(0xFFDFF5E6)
    val okFg = Color(0xFF15803D)

    val typography = Typography(
        bodyLarge = TextStyle(fontFamily = InterFontFamily),
        bodyMedium = TextStyle(fontFamily = InterFontFamily),
        titleLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
    )

    MaterialTheme(typography = typography) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Üst Başlık (Header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "İNDİR",
                        fontSize = 20.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Link Kartı (Input Card)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, line, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            placeholder = { Text("Gönderi linkini yapıştırın", fontFamily = InterFontFamily, color = Color(0xFF9A9AAA)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color(0xFFD3D3E0),
                                focusedBorderColor = primary
                            )
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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryBg,
                                    contentColor = primary
                                )
                            ) {
                                Text("Yapıştır", fontFamily = InterFontFamily, fontWeight = FontWeight.Medium)
                            }

                            Button(
                                onClick = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("İndir", fontFamily = InterFontFamily, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Son İndirmeler
                Text(
                    text = "Son indirmeler",
                    fontSize = 13.sp,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = muted
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Örnek Geçmiş Öğesi (History Row Example)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, line, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(okBg, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = okFg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "instagram.com",
                                fontSize = 15.sp,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "/p/C123456789/",
                                fontSize = 13.sp,
                                fontFamily = InterFontFamily,
                                color = muted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(okBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("5 dosya", fontSize = 12.sp, fontFamily = InterFontFamily, color = okFg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Floating Liquid Glass Bottom Navigation Bar (Compact Pill)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .wrapContentWidth()
                    .shadow(10.dp, RoundedCornerShape(999.dp))
                    .background(Color(0xE6FFFFFF), RoundedCornerShape(999.dp))
                    .border(1.dp, Color(0xA0FFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 0: İndir (Selected)
                    Box(
                        modifier = Modifier
                            .background(primaryBg, RoundedCornerShape(999.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⬇️", fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("İndir", fontSize = 11.sp, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, color = primary)
                        }
                    }

                    // Tab 1: Galeri
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🖼️", fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Galeri", fontSize = 11.sp, fontFamily = InterFontFamily, color = muted)
                        }
                    }

                    // Tab 2: Ayarlar
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚙️", fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Ayarlar", fontSize = 11.sp, fontFamily = InterFontFamily, color = muted)
                        }
                    }
                }
            }
        }
    }
}

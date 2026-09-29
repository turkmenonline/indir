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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
fun MainScreenPreview() {
    val bgColor = Color(0xFFF5F5FA)
    val cardBg = Color.White
    val line = Color(0xFFE3E3EC)
    val primary = Color(0xFF4338CA)
    val primaryBg = Color(0xFFE8E7FB)
    val textColor = Color(0xFF1B1B1F)
    val muted = Color(0xFF6B6B7B)
    val okBg = Color(0xFFDFF5E6)
    val okFg = Color(0xFF15803D)

    val typography = Typography(
        bodyLarge = TextStyle(fontFamily = SpaceGroteskFontFamily),
        bodyMedium = TextStyle(fontFamily = SpaceGroteskFontFamily),
        titleLarge = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold)
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "İNDİR",
                        fontSize = 24.sp,
                        fontFamily = SpaceGroteskFontFamily,
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
                            placeholder = { Text("Gönderi linkini yapıştırın", fontFamily = SpaceGroteskFontFamily, color = Color(0xFF9A9AAA)) },
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
                                Text("Yapıştır", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Medium)
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
                                Text("İndir", fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Son İndirmeler
                Text(
                    text = "Son indirmeler",
                    fontSize = 13.sp,
                    fontFamily = SpaceGroteskFontFamily,
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
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "/p/C123456789/",
                                fontSize = 13.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                color = muted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(okBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("5 dosya", fontSize = 12.sp, fontFamily = SpaceGroteskFontFamily, color = okFg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Floating Liquid Glass Bottom Navigation Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(32.dp))
                    .background(Color(0xE6FFFFFF), RoundedCornerShape(32.dp))
                    .border(2.dp, Color(0xA0FFFFFF), RoundedCornerShape(32.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 0: İndir (Selected)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(primaryBg, RoundedCornerShape(22.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⬇️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("İndir", fontSize = 13.sp, fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, color = primary)
                        }
                    }

                    // Tab 1: Galeri
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🖼️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galeri", fontSize = 13.sp, fontFamily = SpaceGroteskFontFamily, color = muted)
                        }
                    }

                    // Tab 2: Ayarlar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚙️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ayarlar", fontSize = 13.sp, fontFamily = SpaceGroteskFontFamily, color = muted)
                        }
                    }
                }
            }
        }
    }
}

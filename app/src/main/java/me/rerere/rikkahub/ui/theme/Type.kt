@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package me.rerere.rikkahub.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R

/**
 * Material 3 Expressive Typography using Google Sans Flex variable font.
 * 
 * Google Sans Flex axes:
 * - wght (weight): 100-1000, standard weight control
 * - wdth (width): 75-125, condensed to expanded
 * - ROND (roundness): 0-100, sharp to rounded letterforms
 * - GRAD (grade): -50 to 150, adjusts visual weight without changing size
 * - slnt (slant): -10 to 0, italic angle
 * - opsz (optical size): auto-adjusts based on font size
 * 
 * M3 Expressive uses:
 * - High roundness (ROND=100) for friendly, approachable feel
 * - Wider width for display text hierarchy
 * - Varied weights for emphasis
 */

// Create font with specific variation settings for M3 Expressive mode
@Composable
fun rememberGoogleSansFlexExpressive(
    weight: FontWeight = FontWeight.Normal,
    width: Float = 100f,  // 75-125, 100 = normal
    roundness: Float = 100f, // 0-100, 100 = fully rounded (M3E style)
    grade: Float = 0f,    // -50 to 150
): FontFamily {
    return remember(weight, width, roundness, grade) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FontFamily(
                Font(
                    R.font.google_sans_flex,
                    weight = weight,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight.weight),
                        FontVariation.width(width),
                        FontVariation.Setting("ROND", roundness),
                        FontVariation.Setting("GRAD", grade)
                    )
                )
            )
        } else {
            // Fallback for older Android versions
            FontFamily(Font(R.font.google_sans_flex, weight = weight))
        }
    }
}

// Create font for Normal mode (no roundness, standard settings)
@Composable
fun rememberGoogleSansFlexNormal(
    weight: FontWeight = FontWeight.Normal,
    width: Float = 100f,
    grade: Float = 0f,
): FontFamily {
    return remember(weight, width, grade) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FontFamily(
                Font(
                    R.font.google_sans_flex,
                    weight = weight,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight.weight),
                        FontVariation.width(width),
                        FontVariation.Setting("ROND", 0f), // No roundness
                        FontVariation.Setting("GRAD", grade)
                    )
                )
            )
        } else {
            FontFamily(Font(R.font.google_sans_flex, weight = weight))
        }
    }
}

// Static font families for non-composable contexts
private fun createGoogleSansFlex(roundness: Float, wideForExpressive: Boolean = false): FontFamily {
    // Use wider width (110) for expressive display/headline text
    val displayWidth = if (wideForExpressive) 110f else 100f
    val normalWidth = 100f
    
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        FontFamily(
            // Light
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.Light,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(300),
                    FontVariation.width(normalWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
            // Normal
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.Normal,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(400),
                    FontVariation.width(normalWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
            // Medium
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.Medium,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(500),
                    FontVariation.width(normalWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
            // SemiBold - used for headlines, slightly wider when expressive
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.SemiBold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(600),
                    FontVariation.width(displayWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
            // Bold - used for display titles, wider when expressive
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.Bold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(700),
                    FontVariation.width(displayWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
            // ExtraBold - even wider for maximum impact
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.ExtraBold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(800),
                    FontVariation.width(displayWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            )
        )
    } else {
        // Fallback for older Android
        FontFamily(
            Font(R.font.google_sans_flex, FontWeight.Light),
            Font(R.font.google_sans_flex, FontWeight.Normal),
            Font(R.font.google_sans_flex, FontWeight.Medium),
            Font(R.font.google_sans_flex, FontWeight.SemiBold),
            Font(R.font.google_sans_flex, FontWeight.Bold),
            Font(R.font.google_sans_flex, FontWeight.ExtraBold)
        )
    }
}

// M3 Expressive font family - rounded, friendly, wider display text
val GoogleSansFlexExpressive = createGoogleSansFlex(roundness = 100f, wideForExpressive = true)

// Normal font family - standard, no roundness, normal width
val GoogleSansFlexNormal = createGoogleSansFlex(roundness = 0f, wideForExpressive = false)

/**
 * Creates M3 Expressive Typography with the specified font family.
 * 
 * M3E Guidelines:
 * - Display: Bold, wider for hero text
 * - Headlines: SemiBold for section headers  
 * - Titles: Medium weight for cards and items
 * - Body: Normal weight for content
 * - Labels: Medium weight for buttons and chips
 */
fun createTypography(fontFamily: FontFamily): Typography = Typography(
    // Display styles - hero text, large headlines (wider for impact)
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    
    // Headline styles - section headers
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    
    // Title styles - cards, dialogs, list items
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    
    // Body styles - paragraphs, descriptions (Medium weight for better readability)
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    
    // Label styles - buttons, tabs, chips
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Default Typography uses M3 Expressive (rounded)
val Typography = createTypography(GoogleSansFlexExpressive)

// Normal Typography (non-expressive)
val TypographyNormal = createTypography(GoogleSansFlexNormal)

/**
 * Create a FontFamily from FontConfig with proper variation settings.
 */
@Composable
fun rememberFontFamilyFromConfig(config: me.rerere.rikkahub.data.datastore.FontConfig): FontFamily {
    return remember(
        config.fontSource,
        config.customFontPath,
        config.weight,
        config.width,
        config.roundness,
        config.grade,
        config.customAxes
    ) {
        when (config.fontSource) {
            me.rerere.rikkahub.data.datastore.FontSource.System -> {
                // Use Google Sans Flex with roundness control
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createDynamicFontFamily(
                        resourceId = R.font.google_sans_flex,
                        weight = config.weight,
                        width = config.width,
                        roundness = config.roundness,
                        grade = config.grade
                    )
                } else {
                    GoogleSansFlexExpressive
                }
            }
            me.rerere.rikkahub.data.datastore.FontSource.SystemCode -> {
                // Use Google Sans Code (monospace)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    FontFamily(
                        Font(
                            R.font.google_sans_code,
                            weight = FontWeight.Light,
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(300)
                            )
                        ),
                        Font(
                            R.font.google_sans_code,
                            weight = FontWeight.Normal,
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(400)
                            )
                        ),
                        Font(
                            R.font.google_sans_code,
                            weight = FontWeight.Medium,
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(500)
                            )
                        ),
                        Font(
                            R.font.google_sans_code,
                            weight = FontWeight.SemiBold,
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(600)
                            )
                        ),
                        Font(
                            R.font.google_sans_code,
                            weight = FontWeight.Bold,
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(700)
                            )
                        )
                    )
                } else {
                    FontFamily(Font(R.font.google_sans_code))
                }
            }
            me.rerere.rikkahub.data.datastore.FontSource.Custom -> {
                // Custom font from file
                config.customFontPath?.let { path ->
                    try {
                        val fontFile = java.io.File(path)
                        if (fontFile.exists()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config.customAxes.isNotEmpty()) {
                                val settings = mutableListOf<FontVariation.Setting>()
                                config.customAxes.forEach { axis ->
                                    settings.add(FontVariation.Setting(axis.tag, axis.currentValue))
                                }
                                FontFamily(
                                    Font(fontFile, weight = FontWeight.Light, variationSettings = FontVariation.Settings(*settings.toTypedArray())),
                                    Font(fontFile, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(*settings.toTypedArray())),
                                    Font(fontFile, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(*settings.toTypedArray())),
                                    Font(fontFile, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(*settings.toTypedArray())),
                                    Font(fontFile, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(*settings.toTypedArray()))
                                )
                            } else if (fontFile.exists()) {
                                FontFamily(
                                    Font(fontFile, weight = FontWeight.Light),
                                    Font(fontFile, weight = FontWeight.Normal),
                                    Font(fontFile, weight = FontWeight.Medium),
                                    Font(fontFile, weight = FontWeight.SemiBold),
                                    Font(fontFile, weight = FontWeight.Bold)
                                )
                            } else {
                                GoogleSansFlexExpressive
                            }
                        } else {
                            GoogleSansFlexExpressive
                        }
                    } catch (e: Exception) {
                        GoogleSansFlexExpressive
                    }
                } ?: GoogleSansFlexExpressive
            }
        }
    }
}

/**
 * Create a dynamic font family with variable font settings.
 * Creates multiple weight variants for proper font styling.
 */
private fun createDynamicFontFamily(
    resourceId: Int,
    weight: Float,
    width: Float,
    roundness: Float,
    grade: Float
): FontFamily {
    return FontFamily(
        Font(
            resourceId,
            weight = FontWeight.Light,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(300),
                FontVariation.width(width),
                FontVariation.Setting("ROND", roundness),
                FontVariation.Setting("GRAD", grade)
            )
        ),
        Font(
            resourceId,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(400),
                FontVariation.width(width),
                FontVariation.Setting("ROND", roundness),
                FontVariation.Setting("GRAD", grade)
            )
        ),
        Font(
            resourceId,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(500),
                FontVariation.width(width),
                FontVariation.Setting("ROND", roundness),
                FontVariation.Setting("GRAD", grade)
            )
        ),
        Font(
            resourceId,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(600),
                FontVariation.width(width),
                FontVariation.Setting("ROND", roundness),
                FontVariation.Setting("GRAD", grade)
            )
        ),
        Font(
            resourceId,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(700),
                FontVariation.width(width),
                FontVariation.Setting("ROND", roundness),
                FontVariation.Setting("GRAD", grade)
            )
        ),
        Font(
            resourceId,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(800),
                FontVariation.width(width),
                FontVariation.Setting("ROND", roundness),
                FontVariation.Setting("GRAD", grade)
            )
        )
    )
}

/**
 * Create Typography from FontSettings.
 * Uses header font for display/headline/title styles, content font for body/label styles.
 */
@Composable
fun rememberTypographyFromFontSettings(fontSettings: me.rerere.rikkahub.data.datastore.FontSettings): Typography {
    val headerFont = rememberFontFamilyFromConfig(fontSettings.headerFont)
    val contentFont = rememberFontFamilyFromConfig(
        if (fontSettings.useSameFontForHeadersAndContent) fontSettings.headerFont else fontSettings.contentFont
    )
    
    return remember(headerFont, contentFont, fontSettings) {
        Typography(
            // Display styles - use header font
            displayLarge = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.Bold,
                fontSize = (57 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (64 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (-0.25 + fontSettings.headerFont.letterSpacing).sp
            ),
            displayMedium = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.Bold,
                fontSize = (45 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (52 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0 + fontSettings.headerFont.letterSpacing).sp
            ),
            displaySmall = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = (36 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (44 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0 + fontSettings.headerFont.letterSpacing).sp
            ),
            
            // Headline styles - use header font
            headlineLarge = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = (32 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (40 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0 + fontSettings.headerFont.letterSpacing).sp
            ),
            headlineMedium = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = (28 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (36 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0 + fontSettings.headerFont.letterSpacing).sp
            ),
            headlineSmall = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = (24 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (32 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0 + fontSettings.headerFont.letterSpacing).sp
            ),
            
            // Title styles - use header font
            titleLarge = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = (22 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (28 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0 + fontSettings.headerFont.letterSpacing).sp
            ),
            titleMedium = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.Medium,
                fontSize = (16 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (24 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0.15 + fontSettings.headerFont.letterSpacing).sp
            ),
            titleSmall = TextStyle(
                fontFamily = headerFont,
                fontWeight = FontWeight.Medium,
                fontSize = (14 * fontSettings.headerFont.fontSize).sp,
                lineHeight = (20 * fontSettings.headerFont.lineHeight).sp,
                letterSpacing = (0.1 + fontSettings.headerFont.letterSpacing).sp
            ),
            
            // Body styles - use content font
            bodyLarge = TextStyle(
                fontFamily = contentFont,
                fontWeight = FontWeight.Medium,
                fontSize = (16 * fontSettings.contentFont.fontSize).sp,
                lineHeight = (24 * fontSettings.contentFont.lineHeight).sp,
                letterSpacing = (0.5 + fontSettings.contentFont.letterSpacing).sp
            ),
            bodyMedium = TextStyle(
                fontFamily = contentFont,
                fontWeight = FontWeight.Medium,
                fontSize = (14 * fontSettings.contentFont.fontSize).sp,
                lineHeight = (20 * fontSettings.contentFont.lineHeight).sp,
                letterSpacing = (0.25 + fontSettings.contentFont.letterSpacing).sp
            ),
            bodySmall = TextStyle(
                fontFamily = contentFont,
                fontWeight = FontWeight.Medium,
                fontSize = (12 * fontSettings.contentFont.fontSize).sp,
                lineHeight = (16 * fontSettings.contentFont.lineHeight).sp,
                letterSpacing = (0.4 + fontSettings.contentFont.letterSpacing).sp
            ),
            
            // Label styles - use content font
            labelLarge = TextStyle(
                fontFamily = contentFont,
                fontWeight = FontWeight.Medium,
                fontSize = (14 * fontSettings.contentFont.fontSize).sp,
                lineHeight = (20 * fontSettings.contentFont.lineHeight).sp,
                letterSpacing = (0.1 + fontSettings.contentFont.letterSpacing).sp
            ),
            labelMedium = TextStyle(
                fontFamily = contentFont,
                fontWeight = FontWeight.Medium,
                fontSize = (12 * fontSettings.contentFont.fontSize).sp,
                lineHeight = (16 * fontSettings.contentFont.lineHeight).sp,
                letterSpacing = (0.5 + fontSettings.contentFont.letterSpacing).sp
            ),
            labelSmall = TextStyle(
                fontFamily = contentFont,
                fontWeight = FontWeight.Medium,
                fontSize = (11 * fontSettings.contentFont.fontSize).sp,
                lineHeight = (16 * fontSettings.contentFont.lineHeight).sp,
                letterSpacing = (0.5 + fontSettings.contentFont.letterSpacing).sp
            )
        )
    }
}

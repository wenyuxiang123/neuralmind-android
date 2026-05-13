package com.neuralmind.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== Background Colors ====================
// Primary dark background - deepest blue
val BackgroundPrimary = Color(0xFF0D1B2A)
// Secondary background - cards and panels
val BackgroundSecondary = Color(0xFF1A2332)
// Tertiary background - input fields and tags
val BackgroundTertiary = Color(0xFF243447)

// ==================== Gradient Accent Colors ====================
// Gradient start - cyan blue
val GradientStart = Color(0xFF00BCD4)
// Gradient end - blue
val GradientEnd = Color(0xFF2196F3)
// Accent - teal
val GradientAccent = Color(0xFF00897B)

// ==================== Text Colors ====================
// Primary text - white
val TextPrimary = Color(0xFFFFFFFF)
// Secondary text - light gray
val TextSecondary = Color(0xFFB0BEC5)
// Tertiary text - lighter gray for hints
val TextTertiary = Color(0xFF78909C)

// ==================== Status Colors ====================
// Online status - green
val StatusOnline = Color(0xFF4CAF50)
// Offline status - red
val StatusOffline = Color(0xFFF44336)
// Warning status - orange
val StatusWarning = Color(0xFFFF9800)
// Info status - blue
val StatusInfo = Color(0xFF2196F3)

// ==================== Card Colors ====================
// Card background - dark blue gray
val CardBackground = Color(0xFF1A2332)
// Card border
val CardBorder = Color(0xFF2A3A4D)
// Card highlight when selected
val CardHighlight = Color(0xFF2A4A6D)

// ==================== Component Colors ====================
// User message bubble gradient
val UserBubbleStart = Color(0xFF00BCD4)
val UserBubbleEnd = Color(0xFF2196F3)
// AI message bubble
val AIBubbleBackground = Color(0xFF1A2332)
// Navigation drawer
val DrawerBackground = Color(0xFF0D1B2A)
val DrawerItemSelected = Color(0xFF1A3A5C)
val DrawerItemText = Color(0xFFB0BEC5)
val DrawerItemTextSelected = Color(0xFF00BCD4)

// ==================== Button Colors ====================
// Primary button gradient
val ButtonGradientStart = Color(0xFF00BCD4)
val ButtonGradientEnd = Color(0xFF2196F3)
// Secondary button
val ButtonSecondary = Color(0xFF243447)
// Input field
val InputBackground = Color(0xFF1A2332)
val InputBorder = Color(0xFF2A3A4D)
val InputCursor = Color(0xFF00BCD4)

// ==================== Memory Layer Colors ====================
// 9 memory layer gradient colors (light to dark)
val MemoryLayer1 = Color(0xFF1E3A5F)
val MemoryLayer2 = Color(0xFF1A3555)
val MemoryLayer3 = Color(0xFF16304B)
val MemoryLayer4 = Color(0xFF122B41)
val MemoryLayer5 = Color(0xFF0E2637)
val MemoryLayer6 = Color(0xFF0A212D)
val MemoryLayer7 = Color(0xFF061C23)
val MemoryLayer8 = Color(0xFF031719)
val MemoryLayer9 = Color(0xFF01120F)

// ==================== Model Category Colors ====================
// General model - blue gradient
val ModelCategoryGeneral = Color(0xFF1565C0)
// Mobile optimized - green gradient
val ModelCategoryMobile = Color(0xFF2E7D32)
// Code model - purple gradient
val ModelCategoryCode = Color(0xFF7B1FA2)
// Vision model - orange gradient
val ModelCategoryVision = Color(0xFFE65100)
// Audio model - teal gradient
val ModelCategoryAudio = Color(0xFF00796B)

// ==================== Skill Category Colors ====================
// Productivity - blue
val SkillCategoryProductivity = Color(0xFF1976D2)
// Utility - green
val SkillCategoryUtility = Color(0xFF388E3C)
// Creative - purple
val SkillCategoryCreative = Color(0xFF7B1FA2)
// Learning - orange
val SkillCategoryLearning = Color(0xFFE64A19)
// Lifestyle - teal
val SkillCategoryLifestyle = Color(0xFF00796B)

// ==================== Legacy Color Mappings ====================
// These maintain compatibility with existing code
val Primary = GradientStart
val PrimaryVariant = GradientEnd
val Secondary = GradientAccent
val Background = BackgroundPrimary
val Surface = BackgroundSecondary
val Error = StatusOffline
val OnPrimary = TextPrimary
val OnSecondary = TextPrimary
val OnBackground = TextPrimary
val OnSurface = TextPrimary
val OnError = TextPrimary

val DarkPrimary = GradientStart
val DarkPrimaryVariant = GradientEnd
val DarkSecondary = GradientAccent
val DarkBackground = BackgroundPrimary
val DarkSurface = BackgroundSecondary
val DarkError = StatusOffline
val DarkOnPrimary = BackgroundPrimary
val DarkOnSecondary = BackgroundPrimary
val DarkOnBackground = TextPrimary
val DarkOnSurface = TextPrimary
val DarkOnError = TextPrimary

val Success = StatusOnline
val Warning = StatusWarning
val Info = StatusInfo

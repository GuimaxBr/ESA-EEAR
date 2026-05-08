package com.guima.esa.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.UserRepository
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val CENTRAL_TAB_ASSET_DIR = "avatars"
private const val CENTRAL_TAB_ASSET_NAME = "botao_central"
private const val GUIDE_CITY_GATE_ASSET_NAME = "Imagem final 2"
private const val GUIDE_CITY_MONUMENT_ASSET_NAME = "Imagem final 3"
private const val GUIDE_BASIC_PERIOD_ASSET_NAME =
    "comocar acima de  No Período Básico da ESA, o aluno aprende a se tornar militar. Resumidamente, ele aprende"
private const val GUIDE_QUALIFICATION_ASSET_NAME =
    "abaixo de  Após o período básico ano, o aluno segue para qualificação"
private const val GUIDE_ADAPTATION_ASSET_NAME = "Imagem final 4"
private const val GUIDE_AREAS_ASSET_NAME = "imagem final"

private val MilitaryGreen = Color(0xFF1B2A1B)
private val MilitaryGreenDark = Color(0xFF0D170D)
private val MilitaryGreenLight = Color(0xFF2D4630)
private val TacticalGold = Color(0xFFC8A94A)
private val LedRed = Color(0xFFFF4B4B)
private val DisplayBlack = Color(0xFF040404)
private val ScrewLight = Color(0xFFD7D8D4)
private val ScrewDark = Color(0xFF666A66)

private data class GuideSection(
    val title: String,
    val body: String = "",
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val bulletLines: List<String> = emptyList(),
    val imageAssetName: String? = null,
    val imagePlacement: GuideImagePlacement = GuideImagePlacement.Top,
    val imageContentScale: ContentScale = ContentScale.Crop
)

private data class AreaCardData(
    val title: String,
    val description: String,
    val accent: Color
)

private data class GuideChecklistBlock(
    val title: String,
    val accent: Color,
    val lines: List<String>
)

private enum class GuideImagePlacement {
    Top,
    Bottom
}

private data class SargentometroUiState(
    val daysRemaining: Long,
    val todaysQuestions: Int,
    val overallAccuracyPercent: Int,
    val studyStreakDays: Int
)

private data class SargentometroPalette(
    val screenTop: Color,
    val screenBottom: Color,
    val frameBase: Color,
    val frameBorder: Color,
    val panelBase: Color,
    val panelBottom: Color,
    val titleColor: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val bodyText: Color,
    val mutedText: Color,
    val metricLabel: Color,
    val metricValue: Color
)

@Composable
fun CentralScreen() {
    val palette = rememberSargentometroPalette()
    val today = LocalDate.now()
    val targetDateKey = UserRepository.getSargentometroTargetDate()
    val examDate = remember(targetDateKey) {
        runCatching { LocalDate.parse(targetDateKey) }.getOrElse { LocalDate.of(2026, 9, 15) }
    }
    val todaysQuestions = ProgressRepository.getTodaysAnswerAttempts()
    val accuracyPercent = ProgressRepository.getOverallAccuracyPercent()
    val studyStreakDays = UserRepository.getCurrentStudyStreakDays()
    val uiState = remember(today, todaysQuestions, accuracyPercent, studyStreakDays, examDate) {
        SargentometroUiState(
            daysRemaining = ChronoUnit.DAYS.between(today, examDate).coerceAtLeast(0L),
            todaysQuestions = todaysQuestions,
            overallAccuracyPercent = accuracyPercent,
            studyStreakDays = studyStreakDays
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.screenTop,
                        palette.screenBottom
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SargentometroHeroCard(uiState = uiState, palette = palette)
        SargentometroGuide(palette = palette)
    }
}

@Composable
private fun rememberSargentometroPalette(): SargentometroPalette {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.background.luminance() < 0.5f

    return remember(scheme, darkTheme) {
        if (darkTheme) {
            SargentometroPalette(
                screenTop = Color(0xFF07111E),
                screenBottom = Color(0xFF12253B),
                frameBase = Color(0xFF1A2018),
                frameBorder = Color(0xFF67796B),
                panelBase = Color(0xFF122032),
                panelBottom = Color(0xFF0B1624),
                titleColor = Color(0xFFF4F7FC),
                cardSurface = scheme.surface.copy(alpha = 0.96f),
                cardBorder = scheme.outline.copy(alpha = 0.55f),
                bodyText = scheme.onSurface,
                mutedText = scheme.onSurfaceVariant,
                metricLabel = Color(0xFFAFC9FF),
                metricValue = Color(0xFFF4F7FC)
            )
        } else {
            SargentometroPalette(
                screenTop = Color(0xFFF1F4ED),
                screenBottom = Color(0xFFE0E7DA),
                frameBase = Color(0xFFE5E6DE),
                frameBorder = Color(0xFF20241B),
                panelBase = Color(0xFFFFFEFB),
                panelBottom = Color(0xFFF3EFE4),
                titleColor = Color(0xFF101010),
                cardSurface = Color.White.copy(alpha = 0.95f),
                cardBorder = MilitaryGreen.copy(alpha = 0.14f),
                bodyText = scheme.onSurface,
                mutedText = scheme.onSurfaceVariant,
                metricLabel = MilitaryGreenLight,
                metricValue = MilitaryGreenDark
            )
        }
    }
}

@Composable
private fun SargentometroHeroCard(
    uiState: SargentometroUiState,
    palette: SargentometroPalette
) {
    CamoFrame(
        modifier = Modifier.fillMaxWidth(),
        palette = palette
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(2.dp),
            colors = CardDefaults.cardColors(containerColor = palette.panelBase)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.panelBase,
                                palette.panelBottom
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "SARGENTÔMETRO",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        letterSpacing = 1.1.sp,
                        color = palette.titleColor
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LeftSargentometroAssetBadge(modifier = Modifier.weight(1f))

                    DigitalCountdownDisplay(
                        daysRemaining = uiState.daysRemaining,
                        modifier = Modifier.weight(1.8f)
                    )

                    RightSargentometroAssetBadge(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CamoFrame(
    modifier: Modifier = Modifier,
    palette: SargentometroPalette,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.frameBase)
            .border(1.dp, palette.frameBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CamoBand(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                content()
            }

            CamoBand(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 32.dp)
        ) { ScrewHead() }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 18.dp, top = 32.dp)
        ) { ScrewHead() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 32.dp)
        ) { ScrewHead() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 32.dp)
        ) { ScrewHead() }
    }
}

@Composable
private fun CamoBand(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF60472F),
                        0.08f to Color(0xFF283724),
                        0.16f to Color(0xFF8C7554),
                        0.26f to Color(0xFF3B4E33),
                        0.36f to Color(0xFF734F39),
                        0.48f to Color(0xFF172317),
                        0.60f to Color(0xFF846B4D),
                        0.72f to Color(0xFF2A3C28),
                        0.84f to Color(0xFF967C5A),
                        1.00f to Color(0xFF503D2B)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 18.dp)
                .width(74.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF2E412C))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(102.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF72513A))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-20).dp)
                .width(78.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF2A3B28))
        )
    }
}

@Composable
private fun SargentometroGuide(palette: SargentometroPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GuideHeroCard(palette)
        SargentometroGuideContent(
            bodyText = palette.bodyText,
            mutedText = palette.mutedText,
            cardSurface = palette.cardSurface,
            cardBorder = palette.cardBorder
        )
    }
    return

    val sections = remember {
        listOf(
            GuideSection(
                title = "Onde fica a ESA",
                body = "A Escola de Sargentos das Armas fica em Três Corações, no sul de Minas Gerais. A cidade costuma ser associada a uma rotina mais focada, tradição militar e um ritmo que ajuda quem precisa mergulhar no objetivo.",
                accent = Color(0xFF40653E),
                icon = Icons.Default.Place
            ),
            GuideSection(
                title = "O que acontece depois da aprovação",
                body = "Depois da classificação, o candidato entra em uma fase de apresentação, conferência documental, inspeções exigidas e adaptação à vida militar. A mudança é real: o estudo passa a caminhar junto com disciplina, preparo físico e rotina intensa.",
                accent = TacticalGold,
                icon = Icons.Default.CheckCircle
            ),
            GuideSection(
                title = "Como funciona o período básico",
                body = "O período básico é a base da transformação. Ali entram ordem unida, instruções iniciais, condicionamento físico, organização pessoal, postura militar e amadurecimento emocional. É a fase em que o aluno aprende a sustentar constância mesmo sob pressão.",
                accent = Color(0xFF42596F),
                icon = Icons.Default.School
            ),
            GuideSection(
                title = "O que é um sargento",
                body = "O sargento é uma liderança de proximidade. Ele orienta, cobra padrão, organiza atividades, ajuda a formar a tropa e faz a ligação entre o comando e a execução diária. É uma função que mistura técnica, exemplo e responsabilidade humana.",
                accent = Color(0xFF6A4F39),
                icon = Icons.Default.Groups
            )
        )
    }
    val areas = remember {
        listOf(
            AreaCardData("Infantaria", "Contato mais direto com o terreno, patrulhas, deslocamentos e liderança na linha de frente.", TacticalGold),
            AreaCardData("Cavalaria", "Mobilidade, reconhecimento e leitura rápida do cenário operacional.", Color(0xFF6A4F39)),
            AreaCardData("Artilharia", "Precisão técnica, fogos e apoio decisivo ao combate.", Color(0xFF7A5E3A)),
            AreaCardData("Engenharia", "Obstáculos, pontes, mobilidade da tropa e apoio técnico.", Color(0xFF4D6B47)),
            AreaCardData("Comunicações", "Rádios, redes, enlaces e suporte ao comando e controle.", Color(0xFF3D5D73)),
            AreaCardData("Material Bélico", "Armamento, munição, manutenção e cuidado com materiais sensíveis.", Color(0xFF6A3F3F)),
            AreaCardData("Intendência", "Suprimento, alimentação, fardamento, gestão e apoio logístico.", Color(0xFF556B2F))
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GuideHeroCard(palette)
        CityProsConsCard(palette)
        sections.forEach { section -> GuideSectionCard(section, palette) }
        TrainingTimelineCard(palette)
        AreasOverviewCard(areas, palette)
    }
}

@Composable
private fun GuideHeroCard(palette: SargentometroPalette) {
    GuideHeroCardStyled(bodyText = palette.bodyText, mutedText = palette.mutedText)
    return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
        border = BorderStroke(1.dp, TacticalGold.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Guia do futuro sargento",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = palette.bodyText
            )
            Text(
                text = "A ideia aqui é transformar ansiedade em visão de futuro. Quando o caminho fica claro, a motivação deixa de ser só emoção e vira direção.",
                color = palette.mutedText,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun CityProsConsCard(palette: SargentometroPalette) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Três Corações: o que costuma pesar na balança",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.bodyText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProsConsBlock(
                    title = "Pontos positivos",
                    lines = listOf(
                        "Cidade menor e mais focada.",
                        "Menos distração que grandes capitais.",
                        "Ambiente fortemente ligado à rotina militar."
                    ),
                    accent = Color(0xFF3F7A49),
                    mutedText = palette.mutedText,
                    modifier = Modifier.weight(1f)
                )
                ProsConsBlock(
                    title = "Pontos de atenção",
                    lines = listOf(
                        "Saudade da família pesa.",
                        "A disciplina exige adaptação rápida.",
                        "A rotina pode parecer dura no início."
                    ),
                    accent = Color(0xFF9B6A2B),
                    mutedText = palette.mutedText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProsConsBlock(
    title: String,
    lines: List<String>,
    accent: Color,
    mutedText: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = accent, fontWeight = FontWeight.Bold)
            lines.forEach { line ->
                Text("• $line", color = mutedText, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun GuideSectionCard(
    section: GuideSection,
    palette: SargentometroPalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
        border = BorderStroke(1.dp, section.accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = CircleShape, color = section.accent.copy(alpha = 0.12f)) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = section.accent,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.bodyText
                )
            }
            Text(
                text = section.body,
                color = palette.mutedText,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun TrainingTimelineCard(palette: SargentometroPalette) {
    val steps = remember {
        listOf(
            "Aprovação e convocação: começa a fase documental e a preparação prática para a incorporação.",
            "Adaptação inicial: horários, padrão pessoal, disciplina e resistência emocional passam a contar muito.",
            "Período básico: formação militar, instrução física, campo e amadurecimento de postura.",
            "Especialização e consolidação: o aluno aprofunda a área em que vai servir até chegar à formatura."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MilitaryGreenLight)
                Text(
                    "Da prova até a formação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.bodyText
                )
            }
            steps.forEachIndexed { index, step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(shape = CircleShape, color = MilitaryGreen.copy(alpha = 0.12f)) {
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (palette.bodyText.luminance() < 0.5f) Color.White else MilitaryGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = step,
                        modifier = Modifier.weight(1f),
                        color = palette.mutedText,
                        lineHeight = 21.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AreasOverviewCard(
    areas: List<AreaCardData>,
    palette: SargentometroPalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = MilitaryGreenLight)
                Text(
                    "Áreas e perfis de atuação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.bodyText
                )
            }
            Text(
                text = "Cada área pede um tipo de energia. Algumas puxam mais liderança no terreno; outras, organização, técnica, apoio logístico e precisão.",
                color = palette.mutedText,
                lineHeight = 21.sp
            )
            areas.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { area ->
                        AreaProfileCard(area = area, bodyColor = palette.mutedText, modifier = Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaProfileCard(
    area: AreaCardData,
    bodyColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = area.accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, area.accent.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = area.title, color = area.accent, fontWeight = FontWeight.Bold)
            Text(
                text = area.description,
                color = bodyColor,
                lineHeight = 20.sp,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ScrewHead() {
    Canvas(modifier = Modifier.size(14.dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ScrewLight,
                    Color(0xFFB7BAB6),
                    ScrewDark
                )
            )
        )
        drawCircle(
            color = Color(0x88000000),
            radius = size.minDimension * 0.42f,
            style = Stroke(width = 1.3f)
        )
        val centerY = size.height / 2f
        drawLine(
            color = Color(0xFF4E524E),
            start = Offset(size.width * 0.28f, centerY),
            end = Offset(size.width * 0.72f, centerY),
            strokeWidth = 1.8f
        )
    }
}

@Composable
private fun DigitalCountdownDisplay(
    daysRemaining: Long,
    modifier: Modifier = Modifier
) {
    val clampedDays = daysRemaining.coerceIn(0L, 9999L)
    val displayText = remember(clampedDays) { "%04d".format(clampedDays) }

    Box(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141414),
                        DisplayBlack
                    )
                )
            )
            .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayText,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = LedRed,
                    shadow = Shadow(
                        color = LedRed.copy(alpha = 0.65f),
                        blurRadius = 20f
                    )
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "DIAS",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFFB8B8B8),
                    letterSpacing = 1.4.sp
                )
            )
        }
    }
}

@Composable
private fun LeftSargentometroBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(72.dp)
                .height(72.dp)
        ) {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.84f, h * 0.25f)
                lineTo(w * 0.84f, h * 0.67f)
                quadraticTo(w * 0.84f, h * 0.90f, w * 0.5f, h * 0.97f)
                quadraticTo(w * 0.16f, h * 0.90f, w * 0.16f, h * 0.67f)
                lineTo(w * 0.16f, h * 0.25f)
                close()
            }

            drawPath(path = shield, color = Color(0xFFB7A076))
            drawPath(path = shield, color = Color(0xFF5C584A), style = Stroke(width = w * 0.035f))

            val stripeColor = Color(0xFF4A5D59)
            val stripeStroke = Stroke(
                width = w * 0.085f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )

            drawPath(
                path = Path().apply {
                    moveTo(w * 0.25f, h * 0.39f)
                    lineTo(w * 0.5f, h * 0.18f)
                    lineTo(w * 0.75f, h * 0.39f)
                },
                color = stripeColor,
                style = stripeStroke
            )
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.28f, h * 0.52f)
                    lineTo(w * 0.5f, h * 0.32f)
                    lineTo(w * 0.72f, h * 0.52f)
                },
                color = stripeColor,
                style = stripeStroke
            )

            drawPath(
                path = createStarPath(
                    centerX = w * 0.5f,
                    centerY = h * 0.73f,
                    outerRadius = w * 0.15f,
                    innerRadius = w * 0.065f
                ),
                color = Color(0xFF47463D)
            )
        }
    }
}

@Composable
private fun RightSargentometroBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(72.dp)
                .height(72.dp)
        ) {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.83f, h * 0.25f)
                lineTo(w * 0.83f, h * 0.68f)
                quadraticTo(w * 0.83f, h * 0.90f, w * 0.5f, h * 0.98f)
                quadraticTo(w * 0.17f, h * 0.90f, w * 0.17f, h * 0.68f)
                lineTo(w * 0.17f, h * 0.25f)
                close()
            }

            drawPath(path = shield, color = Color(0xFFFCFCF8))
            drawPath(path = shield, color = Color(0xFF1F5A37), style = Stroke(width = w * 0.032f))

            drawPath(
                path = Path().apply {
                    moveTo(w * 0.18f, h * 0.63f)
                    lineTo(w * 0.82f, h * 0.63f)
                    quadraticTo(w * 0.74f, h * 0.86f, w * 0.5f, h * 0.97f)
                    quadraticTo(w * 0.26f, h * 0.86f, w * 0.18f, h * 0.63f)
                    close()
                },
                color = Color(0xFF0D4A2B)
            )

            val chevronStroke = Stroke(
                width = w * 0.065f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val chevronColor = Color(0xFF184F33)

            drawPath(
                path = Path().apply {
                    moveTo(w * 0.29f, h * 0.34f)
                    lineTo(w * 0.5f, h * 0.15f)
                    lineTo(w * 0.71f, h * 0.34f)
                },
                color = chevronColor,
                style = chevronStroke
            )
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.28f, h * 0.47f)
                    lineTo(w * 0.5f, h * 0.27f)
                    lineTo(w * 0.72f, h * 0.47f)
                },
                color = chevronColor,
                style = chevronStroke
            )
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.27f, h * 0.60f)
                    lineTo(w * 0.5f, h * 0.39f)
                    lineTo(w * 0.73f, h * 0.60f)
                },
                color = chevronColor,
                style = chevronStroke
            )
        }
    }
}

private fun createStarPath(
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadius: Float,
    points: Int = 5
): Path {
    return Path().apply {
        for (index in 0 until points * 2) {
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val angle = ((index * PI) / points) - (PI / 2)
            val x = centerX + (cos(angle) * radius).toFloat()
            val y = centerY + (sin(angle) * radius).toFloat()
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

@Composable
fun CentralTabIcon(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val iconBitmap = rememberCentralTabIconBitmap()

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.Default.RadioButtonUnchecked,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun rememberCentralTabIconBitmap(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val assetPath = resolveCentralTabAssetPath(context) ?: return@runCatching null
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

private fun resolveCentralTabAssetPath(context: Context): String? {
    val fileName = context.assets.list(CENTRAL_TAB_ASSET_DIR)
        ?.firstOrNull { assetFileName ->
            normalizeAssetName(assetFileName.substringBeforeLast(".")) == CENTRAL_TAB_ASSET_NAME
        }
        ?: return null

    return "$CENTRAL_TAB_ASSET_DIR/$fileName"
}

private fun normalizeAssetName(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
    return normalized
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
}

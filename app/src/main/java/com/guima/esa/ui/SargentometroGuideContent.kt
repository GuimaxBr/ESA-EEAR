package com.guima.esa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val GUIDE_CITY_GATE_ASSET_NAME = "Imagem final 2"
private const val GUIDE_CITY_MONUMENT_ASSET_NAME = "Imagem final 3"
private const val GUIDE_BASIC_PERIOD_ASSET_NAME =
    "comocar acima de  No Período Básico da ESA, o aluno aprende a se tornar militar. Resumidamente, ele aprende"
private const val GUIDE_QUALIFICATION_ASSET_NAME =
    "abaixo de  Após o período básico ano, o aluno segue para qualificação"
private const val GUIDE_ADAPTATION_ASSET_NAME = "Imagem final 4"
private const val GUIDE_AREAS_ASSET_NAME = "imagem final"

private data class GuidePlainSection(
    val title: String,
    val bodyLines: List<String>,
    val imageAssetName: String? = null,
    val imagePlacement: GuideSectionImagePlacement = GuideSectionImagePlacement.Top,
    val imageContentScale: ContentScale = ContentScale.Crop,
    val imageHeight: Dp = 210.dp
)

private enum class GuideSectionImagePlacement {
    Top,
    Bottom
}

private data class GuideAreaEntry(
    val title: String,
    val description: String,
    val accent: Color
)

@Composable
fun SargentometroGuideContent(
    bodyText: Color,
    mutedText: Color,
    cardSurface: Color,
    cardBorder: Color
) {
    val sections = remember {
        listOf(
            GuidePlainSection(
                title = "Me chamo Estevão",
                bodyLines = listOf(
                    "Me chamo Estevão, sou 3º Sgt do Exército Brasileiro, oriundo da Escola de Sargento de Armas. Cofundador do Aplicativo Questões ESA EEAR.",
                    "Conhecendo as dificuldades do concurseiro militar, eu e o Cb Guimarães e o Sd Sipriano tivemos a iniciativa de criar este aplicativo que fomentasse o interesse e servisse de instrumento para auxiliar no preparo intelectual do concurseiro e o levar a conquistar seu sonho de ser SARGENTO!"
                )
            ),
            GuidePlainSection(
                title = "A ESA em Três Corações",
                bodyLines = listOf(
                    "ESA",
                    "Três Corações - MG",
                    "Infantaria / Cavalaria / Artilharia / Engenharia / Comunicações"
                )
            ),
            GuidePlainSection(
                title = "RESUMO DO EDITAL Nº1/SCA, DE 26 DE MARÇO DE 2026",
                bodyLines = listOf(
                    "O concurso de admissão para a ESA oferece 1.100 vagas para os cursos de sargento. As oportunidades são para ambos os sexos, com ensino médio completo.",
                    "Área Combatente/Logística e Aviação:",
                    "Masculino: 910",
                    "Feminino: 105",
                    "As vagas femininas são somente Comunicações, Logística e Aviação.",
                    "Área Músico (ambos os sexos): 30",
                    "Área Saúde: 55",
                    "A prova da ESA é composta por 50 questões:",
                    "Matemática - 14 questões",
                    "Português - 14",
                    "História e Geografia - 12",
                    "Inglês - 10"
                )
            ),
            GuidePlainSection(
                title = "Orientação ao candidato para REALIZAÇÃO DA PROVA:",
                bodyLines = listOf(
                    "Você só entrará no local de prova com o CCI (Cartão de Confirmação de Inscrição) e com documento original de identificação.",
                    "Chegue no mínimo 3 horas antes da prova.",
                    "Leve canetas sobressalentes.",
                    "Saiba exatamente onde é seu local de prova, imprevistos acontecem, a preparação para a prova é importantíssima.",
                    "Durma bem um dia antes da prova."
                )
            ),
            GuidePlainSection(
                title = "TESTE DE APTIDÃO FÍSICO.",
                bodyLines = listOf(
                    "Área geral e saúde:",
                    "1º Dia",
                    "Corrida 12 min — Sexo masculino: 2450 metros | Sexo feminino: 2100 metros",
                    "Flexão de braços — Sexo masculino: 3 repetições | Sexo feminino: 1 repetição",
                    "2º Dia",
                    "Flexão de braço — Sexo masculino: 21 repetições | Sexo feminino: 12 repetições",
                    "Abdominal supra — Sexo masculino: 30 repetições | Sexo feminino: 27 repetições",
                    "Área músico:",
                    "1º Dia",
                    "Corrida 12 min — Sexo masculino: 2250 metros | Sexo feminino: 1900 metros",
                    "2º Dia",
                    "Flexão de braços — Sexo masculino: 12 repetições | Sexo feminino: 6 repetições",
                    "Adominal supra — Sexo masculino: 30 repetições | Sexo feminino: 27 repetições",
                    "Passar na prova é o primeiro passo, porém o teste físico é subsequente. E infelizmente, muitos não passam pelo teste de aptidão físico. Dito isso: vai treinar, candidato!"
                )
            ),
            GuidePlainSection(
                title = "No Período Básico da ESA",
                bodyLines = listOf(
                    "Após o candidato passar no Exame Intelectual (EI) ele será designado para uma das 13 UETE (Unidades Educacionais Tecnológicas do Exército) onde iniciará o período básico.",
                    "No Período Básico da ESA, o aluno aprende a se tornar militar. Resumidamente, ele aprende:",
                    "disciplina e hierarquia;",
                    "ordem unida;",
                    "treinamento físico militar;",
                    "armamento e tiro;",
                    "técnicas de combate e sobrevivência;",
                    "instruções de campo;",
                    "liderança e trabalho em grupo;",
                    "rotina e valores do Exército."
                ),
                imageAssetName = GUIDE_BASIC_PERIOD_ASSET_NAME,
                imagePlacement = GuideSectionImagePlacement.Top,
                imageContentScale = ContentScale.FillWidth,
                imageHeight = 300.dp
            ),
            GuidePlainSection(
                title = "Após o período básico ano, o aluno segue para qualificação:",
                bodyLines = listOf(
                    "ESA — Três Corações - MG — Infantaria / Cavalaria / Artilharia / Engenharia / Comunicações",
                    "Escola de Sargentos de Logística (EsSLog) — Rio de Janeiro - RJ — Intendência / Material Bélico - Manutenção de Armamento / Material Bélico - Mecânico Operador / Material Bélico - Manutenção de Viatura Automóvel / Material Bélico - Manutenção de Viatura Blindada / Manutenção de Comunicações / Topografia / Música / Saúde",
                    "Centro de Instrução de Aviação do Exército (CIAvEx) — Taubaté - SP — Aviação-Manutenção"
                ),
                imageAssetName = GUIDE_QUALIFICATION_ASSET_NAME,
                imagePlacement = GuideSectionImagePlacement.Bottom,
                imageContentScale = ContentScale.FillWidth,
                imageHeight = 170.dp
            ),
            GuidePlainSection(
                title = "Adaptação à rotina militar",
                bodyLines = listOf(
                    "O que costuma diferenciar quem consegue se adaptar",
                    "Disciplina",
                    "Bom condicionamento físico",
                    "Organização",
                    "Saber trabalhar em grupo",
                    "Cumprir bem feito as missões",
                    "Não ser ponderador!",
                    "O que mais pesa para muitos alunos",
                    "Pressão psicológica",
                    "Privação de conforto",
                    "Disciplina rígida",
                    "Condicionamento físico",
                    "Adaptação à rotina militar",
                    "Distância da família"
                ),
                imageAssetName = GUIDE_ADAPTATION_ASSET_NAME,
                imagePlacement = GuideSectionImagePlacement.Top,
                imageContentScale = ContentScale.Crop,
                imageHeight = 220.dp
            ),
            GuidePlainSection(
                title = "Vantagens de fazer a ESA:",
                bodyLines = listOf(
                    "1. Formação de nível superior (tecnólogo)",
                    "2. Estabilidade profissional",
                    "3. Independência financeira aos 20 e poucos anos",
                    "4. Cursos e estágios",
                    "5. Possibilidade de missões no exterior",
                    "6. Desenvolvimento pessoal"
                )
            )
        )
    }

    val areas = remember {
        listOf(
            GuideAreaEntry(
                title = "INFANTARIA",
                description = "A Infantaria é uma das armas mais antigas da guerra e atua diretamente nas linhas de frente do combate. Sua importância histórica foi consolidada por gregos e romanos, que elevaram o prestígio desses guerreiros ao longo dos séculos.",
                accent = Color(0xFF2E7D32)
            ),
            GuideAreaEntry(
                title = "CAVALARIA",
                description = "A Cavalaria é a arma responsável pelo reconhecimento, segurança e ações rápidas no combate. Conhecida pela mobilidade e poder de choque, teve origem nas tropas montadas a cavalo e evoluiu para o uso de blindados e veículos militares, mantendo sua tradição de velocidade e ousadia nas operações.",
                accent = Color(0xFFC62828)
            ),
            GuideAreaEntry(
                title = "ARTILHARIA",
                description = "A Artilharia é a arma responsável pelo apoio de fogo no combate, utilizando canhões, obuseiros e foguetes para atingir alvos a longa distância. Tem a missão de apoiar as tropas no terreno, garantindo maior poder ofensivo e proteção durante as operações militares.",
                accent = Color(0xFF4A148C)
            ),
            GuideAreaEntry(
                title = "COMUNICAÇÕES",
                description = "As Comunicações são responsáveis por garantir o comando e o controle das tropas durante as operações militares. Utilizando rádios, redes e sistemas tecnológicos, sua MISSÃO é instalar, explorar, manter e proteger o sistema de comunicações, mantendo a coordenação, a segurança e a eficiência das comunicações no campo de batalha.",
                accent = Color(0xFF1565C0)
            ),
            GuideAreaEntry(
                title = "ENGENHARIA",
                description = "A Engenharia é a arma responsável por garantir a mobilidade, a proteção e o apoio às tropas durante as operações militares. Atuando na construção de pontes, estradas, fortificações e na remoção de obstáculos, sua missão é facilitar o avanço das forças amigas e dificultar as ações do inimigo.",
                accent = Color(0xFF4FC3F7)
            ),
            GuideAreaEntry(
                title = "SAÚDE",
                description = "A Saúde é responsável pela proteção, prevenção e recuperação da saúde dos militares. Composta por profissionais como médicos, dentistas, farmacêuticos, enfermeiros, sua missão é garantir o apoio médico e hospitalar às tropas em treinamentos, operações e missões.",
                accent = Color(0xFF7C4D67)
            ),
            GuideAreaEntry(
                title = "MATERIAL BÉLICO",
                description = "O Material Bélico é o quadro do Exército Brasileiro responsável pela manutenção, armazenamento e gestão de armamentos, viaturas, munições e equipamentos militares. Sua missão é garantir que os meios de combate estejam em perfeitas condições de uso, assegurando o apoio logístico e operacional das tropas.",
                accent = Color(0xFF6A3F3F)
            ),
            GuideAreaEntry(
                title = "INTENDÊNCIA",
                description = "A Intendência é o quadro do Exército Brasileiro responsável pelo apoio logístico das tropas, atuando no fornecimento de alimentação, fardamento, transporte e suprimentos militares. Sua missão é suprir e garantir o suporte necessário para que as operações ocorram com organização, eficiência e continuidade.",
                accent = Color(0xFF556B2F)
            ),
            GuideAreaEntry(
                title = "MÚSICA",
                description = "A área de Música do Exército Brasileiro é responsável pela execução de atividades musicais em cerimônias, formaturas, desfiles e eventos militares. Composta por músicos militares, sua missão é preservar as tradições da Força, fortalecer o espírito militar e representar o Exército por meio das bandas e apresentações oficiais.",
                accent = Color(0xFF6750A4)
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IntroImageCard(
            title = sections[0].title,
            bodyLines = sections[0].bodyLines,
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[1],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[2],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[3],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[4],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[5],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[6],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[7],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        GuideSimpleCard(
            section = sections[8],
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )

        AreasCard(
            areas = areas,
            bodyText = bodyText,
            mutedText = mutedText,
            cardSurface = cardSurface,
            cardBorder = cardBorder
        )
    }
}

@Composable
private fun IntroImageCard(
    title: String,
    bodyLines: List<String>,
    bodyText: Color,
    mutedText: Color,
    cardSurface: Color,
    cardBorder: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = bodyText
            )
            bodyLines.forEach { line ->
                Text(
                    text = line,
                    color = mutedText,
                    lineHeight = 21.sp
                )
            }
            GuideCardImage(
                assetName = GUIDE_CITY_GATE_ASSET_NAME,
                contentDescription = "Entrada da ESA em Três Corações",
                contentScale = ContentScale.Crop,
                imageHeight = 220.dp
            )
            GuideCardImage(
                assetName = GUIDE_CITY_MONUMENT_ASSET_NAME,
                contentDescription = "Monumento da ESA",
                contentScale = ContentScale.Crop,
                imageHeight = 220.dp
            )
        }
    }
}

@Composable
private fun GuideSimpleCard(
    section: GuidePlainSection,
    bodyText: Color,
    mutedText: Color,
    cardSurface: Color,
    cardBorder: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (section.imageAssetName != null && section.imagePlacement == GuideSectionImagePlacement.Top) {
                GuideCardImage(
                    assetName = section.imageAssetName,
                    contentDescription = section.title,
                    contentScale = section.imageContentScale,
                    imageHeight = section.imageHeight
                )
            }

            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = bodyText
            )

            section.bodyLines.forEach { line ->
                Text(
                    text = line,
                    color = mutedText,
                    lineHeight = 21.sp
                )
            }

            if (section.imageAssetName != null && section.imagePlacement == GuideSectionImagePlacement.Bottom) {
                GuideCardImage(
                    assetName = section.imageAssetName,
                    contentDescription = section.title,
                    contentScale = section.imageContentScale,
                    imageHeight = section.imageHeight
                )
            }
        }
    }
}

@Composable
private fun AreasCard(
    areas: List<GuideAreaEntry>,
    bodyText: Color,
    mutedText: Color,
    cardSurface: Color,
    cardBorder: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Áreas e perfis de atuação",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = bodyText
            )

            GuideCardImage(
                assetName = GUIDE_AREAS_ASSET_NAME,
                contentDescription = "Mapa das áreas da ESA",
                contentScale = ContentScale.Crop,
                imageHeight = 240.dp
            )

            areas.forEach { area ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = area.accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.25.dp, area.accent.copy(alpha = 0.42f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = area.title,
                            color = area.accent,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = area.description,
                            color = mutedText,
                            lineHeight = 20.sp,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideCardImage(
    assetName: String,
    contentDescription: String,
    contentScale: ContentScale,
    imageHeight: Dp
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF3EEE4),
        border = BorderStroke(1.dp, Color(0xFFE0D5C1))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            AvatarAssetImage(
                assetBaseName = assetName,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}

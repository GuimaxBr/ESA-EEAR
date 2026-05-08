package com.guima.esa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val dailyMotivationalPhrases = listOf(
    "Dia que se planta não é dia que se colhe.",
    "Nada te mata mais rápido do que sua própria mente. Não se estresse com coisas que estão fora do seu controle.",
    "Um grande prazer na vida é fazer o que as pessoas disseram que você não conseguiria.",
    "O Sol vai nascer e nós vamos tentar de novo.",
    "Se vencer é importante para você, não se acomode nunca.",
    "Se você estiver mentalmente exausto: mude seu ambiente. Organize sua mesa. Tome um banho. Faça uma caminhada. Revigore seu estado mental.",
    "A disciplina irá levar você a lugares onde a motivação não leva.",
    "Nós nunca seremos jovens de novo.",
    "Seus sonhos não precisam fazer sentido para ninguém. Eles só devem fazer sentido para você.",
    "Não existe um meio termo. Ou você faz algo bem feito, ou não faz.",
    "Prepara-se o cavalo para o dia da batalha. Mas a vitória vem do Senhor. Pv 21:31",
    "Eu caí e achei que era meu fim, mas Deus me levantou e disse: ainda não acabou. Miqueias 7:8",
    "Só faz, não pensa muito.",
    "A respeito de cada ação, examina o que a antecede e o que a sucede e então compreende.",
    "Cada um tem um motivo pra fazer alguma coisa. Eu faço para dizerem que não sou capaz de fazer.",
    "Não sou talentoso. Mas eu trabalho duro.",
    "Observem as aves do céu: não semeiam nem colhem. Contudo, o Pai celestial as alimenta. Mateus 6:26-30",
    "Estou ocupado vivendo minha vida seguindo minhas ideias malucas para que o eu de 80 anos não se pergunte: e se?"
)

@Composable
fun DailyMotivationCard(
    phrase: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(
                text = "Frase do dia",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = phrase,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

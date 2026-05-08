package com.guima.esa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyConsentScreen(
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Política de privacidade",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Última atualização: 4 de maio de 2026.\n\nEste app pode salvar localmente apelido, avatar, progresso, histórico, metas, tema e lembretes para manter sua experiência personalizada."
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Se você entrar com Google, o app também pode usar e-mail, nome e foto para sincronizar ranking, progresso, conta e status premium entre dispositivos."
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "O app pode usar serviços como Google Sign-In, Firebase, Google Play Billing e AdMob. Seu aceite fica salvo para liberar os recursos do app e pode ser revisado nas atualizações desta política.",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aceitar privacidade")
                }
            }
        }
    }
}

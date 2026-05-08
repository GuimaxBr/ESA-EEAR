package com.guima.esa.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.guima.esa.R
import com.guima.esa.data.GoogleLoginResult
import kotlinx.coroutines.launch

enum class LegalDocumentType {
    TERMS,
    PRIVACY
}

private data class LegalSection(
    val title: String,
    val body: String
)

@Composable
fun FirstAccessLoginScreen(
    privacyAccepted: Boolean,
    onPrivacyAccepted: () -> Unit,
    onLoggedIn: () -> Unit,
    noticeMessage: String?,
    onNoticeDismissed: () -> Unit,
    performLogin: suspend (GoogleSignInAccount, Boolean) -> GoogleLoginResult
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var openDocument by remember { mutableStateOf<LegalDocumentType?>(null) }
    var pendingTakeoverAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                isLoading = true
                errorMessage = ""
                coroutineScope.launch {
                    when (val loginResult = performLogin(account, false)) {
                        GoogleLoginResult.Success -> {
                            isLoading = false
                            if (!privacyAccepted) {
                                onPrivacyAccepted()
                            }
                            onLoggedIn()
                        }
                        GoogleLoginResult.RequiresTakeover -> {
                            isLoading = false
                            pendingTakeoverAccount = account
                        }
                        is GoogleLoginResult.Error -> {
                            isLoading = false
                            errorMessage = loginResult.message
                        }
                    }
                }
            }
        } catch (error: ApiException) {
            isLoading = false
            errorMessage = "Não foi possível entrar com Google (código ${error.statusCode}). Tente novamente."
        }
    }

    if (noticeMessage != null) {
        AlertDialog(
            onDismissRequest = onNoticeDismissed,
            title = { Text("Sessão encerrada") },
            text = { Text(noticeMessage) },
            confirmButton = {
                TextButton(onClick = onNoticeDismissed) {
                    Text("Entendi")
                }
            }
        )
    }

    if (pendingTakeoverAccount != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    pendingTakeoverAccount = null
                }
            },
            title = { Text("Assumir sessão?") },
            text = {
                Text(
                    "Somente um dispositivo pode ficar conectado por Gmail. Se você assumir a sessão aqui, o outro dispositivo será desconectado automaticamente."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isLoading,
                    onClick = {
                        val account = pendingTakeoverAccount ?: return@TextButton
                        isLoading = true
                        errorMessage = ""
                        coroutineScope.launch {
                            when (val loginResult = performLogin(account, true)) {
                                GoogleLoginResult.Success -> {
                                    isLoading = false
                                    pendingTakeoverAccount = null
                                    if (!privacyAccepted) {
                                        onPrivacyAccepted()
                                    }
                                    onLoggedIn()
                                }
                                GoogleLoginResult.RequiresTakeover -> {
                                    isLoading = false
                                }
                                is GoogleLoginResult.Error -> {
                                    isLoading = false
                                    pendingTakeoverAccount = null
                                    errorMessage = loginResult.message
                                }
                            }
                        }
                    }
                ) {
                    Text("Assumir sessão")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isLoading,
                    onClick = { pendingTakeoverAccount = null }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (openDocument != null) {
        LoginLegalDocumentScreen(
            documentType = openDocument!!,
            onBack = { openDocument = null }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = "file:///android_asset/login/login_background.jpg",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE608162D),
                            Color(0xB80B3269),
                            Color(0xF104101F)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "ESA | EEAR",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            AsyncImage(
                model = "file:///android_asset/login/esa_logo.png",
                contentDescription = "Escudo ESA EEAR",
                modifier = Modifier
                    .size(176.dp)
                    .clip(RoundedCornerShape(28.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Seja bem-vindo! 🚀",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Sua preparação para ESA e EEAR começa agora.\n\nEstude com foco, acompanhe seu desempenho e evolua todos os dias. Entre com sua conta Google para salvar seu progresso, ranking e acesso ao Premium em qualquer dispositivo.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Entrar com Google",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Use sua conta para sincronizar seu progresso, ranking e conquistas com segurança.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Entrando...")
                        } else {
                            Text("Continuar com Google", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Ao continuar, você concorda com nossos Termos de Uso e Política de Privacidade.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { openDocument = LegalDocumentType.TERMS }) {
                            Text("Termos de Uso", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "|",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp
                        )
                        TextButton(onClick = { openDocument = LegalDocumentType.PRIVACY }) {
                            Text("Política de Privacidade", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginLegalDocumentScreen(
    documentType: LegalDocumentType,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val documentTitle = when (documentType) {
        LegalDocumentType.TERMS -> "Termos de Uso"
        LegalDocumentType.PRIVACY -> "Política de Privacidade"
    }
    val sections = when (documentType) {
        LegalDocumentType.TERMS -> loginTermsSections()
        LegalDocumentType.PRIVACY -> loginPrivacySections()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar"
                    )
                }
                Text(
                    text = documentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                sections.forEach { section ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = section.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun legacyLoginPrivacySections(): List<LegalSection> = listOf(
    LegalSection(
        title = "1. Dados usados no app",
        body = "O app usa informações como apelido, avatar, progresso, metas diárias, lembretes, ranking, status premium e preferência de tema para manter sua experiência funcionando de forma personalizada."
    ),
    LegalSection(
        title = "2. Login com Google",
        body = "Quando você entra com Google, podemos usar nome, email, foto e o identificador autenticado no Firebase para conectar sua conta, sincronizar ranking, restaurar perfil em outro aparelho e vincular compras premium."
    ),
    LegalSection(
        title = "3. Armazenamento e sincronização",
        body = "Parte dos dados fica salva no próprio aparelho e parte pode ser sincronizada pela conta autenticada para restauração de perfil, ranking e compras. O objetivo é permitir continuidade de uso entre sessões e dispositivos."
    ),
    LegalSection(
        title = "4. Anúncios e compras",
        body = "A versão gratuita pode exibir anúncios do Google AdMob. Compras premium são tratadas pelo Google Play Billing. Esses serviços podem processar identificadores e eventos técnicos necessários para anúncio, cobrança, restauração e segurança."
    ),
    LegalSection(
        title = "5. Controle do usuário",
        body = "Você pode sair da conta Google a qualquer momento. Ao sair, a sessão conectada é encerrada para evitar mistura entre contas. Algumas informações locais de estudo podem continuar no aparelho até que você as redefina manualmente."
    )
)

private fun loginPrivacySections(): List<LegalSection> = listOf(
    LegalSection(
        title = "1. Visão geral",
        body = "Última atualização: 4 de maio de 2026.\n\nO Questões ESA respeita a privacidade dos usuários. Esta política explica quais dados podem ser coletados, como são usados, com quem podem ser compartilhados e quais são os direitos do usuário ao utilizar o aplicativo."
    ),
    LegalSection(
        title = "2. Dados coletados",
        body = "O app pode coletar e processar apelido, avatar, identificador único do usuário, e-mail, nome e foto do perfil quando houver login com Google, além de progresso de estudo, histórico de simulados, metas, ranking, presença online, pontuação, preferências do app, status premium e dados técnicos necessários para autenticação, sincronização e segurança."
    ),
    LegalSection(
        title = "3. Uso e sincronização",
        body = "Os dados são usados para permitir o funcionamento do aplicativo, salvar e restaurar o progresso de estudo, sincronizar informações entre dispositivos, exibir ranking, liberar recursos premium, personalizar a experiência e melhorar estabilidade, desempenho e segurança."
    ),
    LegalSection(
        title = "4. Armazenamento local e nuvem",
        body = "Parte dos dados pode ficar armazenada localmente no aparelho, incluindo progresso, histórico, metas, tema, lembretes, apelido, avatar e preferências da conta. Quando o usuário entra com Google, parte dessas informações pode ser sincronizada em nuvem para recuperação de perfil, ranking, progresso e status premium."
    ),
    LegalSection(
        title = "5. Serviços de terceiros",
        body = "O app pode usar Google Sign-In, Firebase Authentication, Firebase Firestore, Google Play Billing, Google AdMob e serviços próprios de API e infraestrutura. Esses serviços podem processar dados necessários para autenticação, sincronização, cobrança, anúncios, ranking, funcionamento técnico e segurança."
    ),
    LegalSection(
        title = "6. Compartilhamento e segurança",
        body = "O Questões ESA não vende dados pessoais dos usuários. O compartilhamento ocorre apenas quando necessário para autenticação da conta, sincronização de progresso e perfil, exibição de ranking, validação de compra premium, anúncios e operação técnica do app. São adotadas medidas razoáveis de segurança, embora nenhum sistema seja totalmente imune a falhas."
    ),
    LegalSection(
        title = "7. Direitos, retenção e contato",
        body = "O usuário pode alterar apelido, avatar e preferências, desativar lembretes, redefinir dados de progresso, sair da conta Google vinculada e deixar de usar o app. Os dados podem ser mantidos enquanto forem necessários para funcionamento, sincronização, segurança e obrigações técnicas ou legais. Contato: guimaxguima@gmail.com e sktevao@gmail.com. Publicador no Google Play: Rotina Papiro."
    )
)

private fun loginTermsSections(): List<LegalSection> = listOf(
    LegalSection(
        title = "1. Finalidade do app",
        body = "O Questões ESA foi criado para apoio aos estudos de candidatos das provas ESA e EEAR. O app organiza questões, desempenho, ranking, lembretes e recursos premium para auxiliar a rotina de preparação."
    ),
    LegalSection(
        title = "2. Uso adequado",
        body = "Você concorda em usar o app de forma legítima, sem tentar manipular ranking, compras, autenticação ou funcionamento interno. Também concorda em não usar o serviço para fraudes, abuso técnico ou tentativa de acesso indevido."
    ),
    LegalSection(
        title = "3. Conta, ranking e progresso",
        body = "Ao entrar com Google, você assume responsabilidade pelas informações usadas em sua conta. O ranking depende dos dados associados a essa conta e do uso correto do app. Alterações de conta podem mudar os dados exibidos conforme a conta conectada."
    ),
    LegalSection(
        title = "4. Premium e conectividade",
        body = "Alguns recursos dependem de internet, autenticação e serviços externos como Firebase, Google Sign-In, Play Billing e AdMob. Recursos premium seguem as regras de cobrança e restauração da loja vinculada ao dispositivo."
    ),
    LegalSection(
        title = "5. Limites e atualizações",
        body = "O app é uma ferramenta de estudo e não garante aprovação em concurso. Recursos, visual, textos legais e integrações podem ser atualizados ao longo do tempo para melhorar segurança, desempenho e experiência."
    )
)

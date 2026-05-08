# ESA-EEAR

Aplicativo Android para estudo de provas ESA e EEAR, com foco em simulados, progresso por materia, ranking online, login com Google, historico de resultados, lembretes diarios, anuncios e liberacao de recursos premium.

## Visao geral

O projeto principal esta no modulo `app` e foi construido com:

- Kotlin
- Jetpack Compose
- Firebase Auth
- Firebase Firestore
- Google Sign-In
- Google AdMob
- Google Play Billing
- WorkManager

Principais recursos ja presentes no app:

- simulados por prova, ano e materia
- tela de progresso por materia
- ranking online
- login com Google com controle de sessao unica
- historico de simulados
- treino de dificuldades
- flash cards e anotacoes
- lembrete diario de estudo
- modo premium

## Estrutura do repositorio

- `app/`: aplicativo Android principal
- `cluster-bridge/`: painel e worker web para testes de processamento distribuido
- `remote-deploy/`: arquivos auxiliares de deploy e integracao remota
- `dist/`: utilitarios compilados usados no fluxo de geracao de questoes
- `img/`: imagens auxiliares do projeto

## Requisitos

- Android Studio recente
- JDK 17 para Gradle e Android Gradle Plugin
- SDK Android instalado
- conta Firebase
- conta AdMob
- conta Google Play Console para Billing

Observacao: o app esta configurado para compilar com `compileSdk 36`, `targetSdk 36` e `minSdk 24`.

## Como abrir o projeto

1. Abra a pasta raiz `ESA-EEAR` no Android Studio.
2. Aguarde o sync do Gradle.
3. Confirme que o arquivo `local.properties` aponta para o seu Android SDK.
4. Rode a configuracao `app` em um dispositivo fisico ou emulador.

## Configuracao do Firebase

O login Google e a sincronizacao dependem de Firebase Auth e Firestore.

### 1. Adicione o `google-services.json`

Baixe o arquivo do projeto Firebase e coloque em:

```text
app/google-services.json
```

### 2. Ative os servicos necessarios

No console do Firebase, habilite:

- Authentication > Google
- Firestore Database

### 3. Cadastre as chaves do app

Para o login Google funcionar corretamente:

- cadastre SHA-1 e SHA-256 do app no Firebase
- confirme que o cliente Web OAuth foi criado
- baixe novamente o `google-services.json` depois das alteracoes

Sem isso, o app pode falhar no fluxo de login e exibir erro de token do Firebase.

## Configuracao do AdMob

Os IDs atuais ficam em:

```text
app/src/main/res/values/strings.xml
```

Strings usadas hoje:

- `admob_app_id`
- `admob_banner_top_unit_id`

Em build debug, o banner usa automaticamente o ID de teste definido em `TopBannerAd.kt`.

O arquivo `app-ads.txt` foi preparado em:

```text
app-ads.txt
cluster-bridge/public/app-ads.txt
docs/app-ads.txt
```

Para anuncios reais serem validados pelo AdMob, publique esse arquivo na raiz do dominio/site associado ao app na Google Play Console.

Se quiser usar GitHub Pages, publique a pasta `docs/`, pois ela ja contem um `index.html` e o `app-ads.txt` na raiz.

## Configuracao do Billing

O produto premium atual esperado pelo app e:

```text
esa_premium
```

Ele esta referenciado em:

```text
app/src/main/res/values/strings.xml
```

Antes de publicar:

- crie o produto in-app no Google Play Console
- use o mesmo `productId`
- teste compras com contas de licenca

## Backend customizado

Parte do app usa um backend proprio para criacao de usuario e envio de estatisticas. A URL base atual esta hardcoded em:

```text
app/src/main/java/com/guima/esa/data/ApiService.kt
```

Hoje o codigo aponta para uma URL `trycloudflare.com`, que e temporaria. Antes de usar o projeto em producao, troque essa URL por um dominio estavel ou mova essa configuracao para `BuildConfig`, variavel de ambiente ou arquivo de configuracao por ambiente.

## Comandos uteis

Na raiz do projeto:

```powershell
.\gradlew.bat :app:assembleDebug
```

```powershell
.\gradlew.bat :app:installDebug
```

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Cluster Bridge

O modulo `cluster-bridge` e opcional e nao e necessario para executar o app Android.

Para rodar localmente:

```powershell
cd cluster-bridge
npm install
$env:CLUSTER_ADMIN_PASSWORD="sua-senha-forte"
npm start
```

Rotas principais:

- `http://localhost:8787/`
- `http://localhost:8787/worker.html`

## Estado atual e pendencias conhecidas

- O repositorio ja possui assets de `ESA` e `EEAR` em `app/src/main/assets/simulados`, mas a liberacao final da EEAR na UI precisa ser revisada.
- O backend proprio ainda precisa de URL estavel.
- A cobertura de testes automatizados ainda esta basica e precisa evoluir.
- O projeto possui varios artefatos auxiliares fora do modulo Android; revise o `.gitignore` antes de preparar commits de release.

## Arquivos importantes

- `app/build.gradle.kts`: configuracao do app Android
- `app/src/main/AndroidManifest.xml`: permissoes e componentes
- `app/src/main/java/com/guima/esa/MainActivity.kt`: entrada principal do app
- `app/src/main/java/com/guima/esa/data/`: repositorios e integracoes
- `app/src/main/java/com/guima/esa/ui/`: telas Compose
- `app/src/main/assets/simulados/`: banco de questoes

## Sugestao de proximo passo

Se voce vai continuar a evolucao do projeto, a ordem mais segura e:

1. estabilizar a URL do backend
2. revisar a liberacao da EEAR na interface
3. criar validacao automatica dos JSONs de questoes
4. ampliar os testes de login, ranking, sincronizacao e simulados

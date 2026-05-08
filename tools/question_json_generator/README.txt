Gerador de Questoes JSON

Como usar:
1. Copie o arquivo GeradorQuestoesJson.exe para a pasta da materia.
2. Abra o exe.
3. Preencha ou ajuste Materia e Ano.
4. Cole o texto da questao. O editor agora limpa melhor caracteres vindos de PDF e preserva mais simbolos matematicos.
5. Cole as alternativas, uma por linha ou em caixas separadas com formatacao.
6. Escolha qual alternativa esta correta.
7. Se quiser, marque a opcao de explicacao e cole o texto.
8. Clique em Gerar JSON para criar uma nova questao.

Recursos novos:
- Negrito e sublinhado no enunciado.
- Negrito e sublinhado nas alternativas A, B, C, D e E.
- Melhor tratamento de caracteres especiais copiados de PDF, incluindo espacos invisiveis, ligaturas e simbolos que costumam vir quebrados.
- Continua salvando no mesmo formato JSON compativel com o app.

Para editar:
1. Escolha um arquivo questao_N.json na lista.
2. Clique em Abrir questao existente.
3. Ajuste os campos. Alternativas antigas continuam abrindo normalmente, inclusive quando o arquivo ja tiver HTML.
4. Clique em Salvar edicao para sobrescrever o mesmo arquivo.

Regras:
- O arquivo sera salvo na mesma pasta do exe.
- O nome novo sera automatico: questao_1.json, questao_2.json, questao_3.json...
- Materia e Ano sao preenchidos automaticamente pela pasta, mas podem ser alterados manualmente.
- Gerar JSON sempre cria um novo arquivo.
- Salvar edicao atualiza o arquivo aberto no modo de edicao.

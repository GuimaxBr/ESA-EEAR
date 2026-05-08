# Cluster Bridge

Base leve para teste de processamento distribuido com:

- painel web com senha de admin
- lista de dispositivos conectados
- fila de tarefas estilo impressora
- reatribuicao automatica quando um worker some
- worker web para testes em navegadores autorizados

## Como rodar

```bash
cd cluster-bridge
set CLUSTER_ADMIN_PASSWORD=sua-senha-forte
npm start
```

Abra:

- `http://localhost:8787/` para o painel admin
- `http://localhost:8787/worker.html` para conectar um worker

## O que esta pronto

- upload por arrastar e soltar
- divisao do arquivo em chunks
- distribuicao de chunks para workers
- timeout de lease e devolucao do chunk para a fila
- download final em `.clusterzip.zip`

## Formato do resultado

Cada worker gera uma parte `.gz`. O download final e um `.zip` com:

- `manifest.json`
- `arquivo.part-0000.gz`
- `arquivo.part-0001.gz`
- ...

Para remontar o arquivo original, basta descompactar cada parte `.gz` e concatenar na ordem do `manifest.json`.

## Observacao

Esta e a base de teste. Ela foi pensada para usar pouco CPU do servidor e empurrar o trabalho de compactacao para os workers conectados.

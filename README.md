# marca-ai-auth

Serviço de autenticação e autorização do MarcaAi. Emite tokens JWT, guarda credenciais,
controla os vínculos entre usuários e empresas e protege o login contra força bruta,
enumeração de contas e abuso por volume.

> **Estado atual:** cadastro de usuário e login estão implementados e funcionando, com as
> defesas descritas na §7. Refresh token, verificação de e-mail, recuperação de senha e
> convite de funcionário ainda são **desenho pretendido** — as tabelas existem, o código
> não. As seções marcadas com ✅ descrevem o que está de pé.

---

## 1. Responsabilidade do serviço

**Faz:**

- Cadastro e autenticação de usuários (identidade única por CPF)
- Emissão de tokens JWT assinados com chave rotacionada semanalmente
- Vínculo de usuários a empresas com papel (`DONO` / `FUNCIONARIO`)
- Proteção do login: bloqueio progressivo por conta, rate limit por IP, detecção de
  varredura de e-mails
- Provisionamento de segredo TOTP no cadastro

**Não faz:**

- Não é dono da entidade *empresa* — ela vive no serviço `marca-ai`. Aqui só existe
  `empresa_id` como referência solta (§8.2)
- Não decide regra de negócio de marcas, faturas ou qualquer domínio da aplicação
- Não guarda dados de perfil além do mínimo de identificação

---

## 2. Stack ✅

| Peça | Escolha |
|---|---|
| Runtime | Quarkus 3.39.5, Java 25 |
| API | `quarkus-rest` + `quarkus-rest-jackson` |
| Banco | PostgreSQL 17 via `quarkus-reactive-pg-client` (Vert.x, sem Hibernate/JPA) |
| Cache | Redis 8 — contadores de bloqueio e rate limit (§7.2) |
| JWT | `quarkus-smallrye-jwt-build` (emissão) + `quarkus-smallrye-jwt` (verificação) |
| Senha | `quarkus-elytron-security-common` (BCrypt) |
| TOTP | `java-otp` + `commons-codec` (Base32) |
| Agendamento | `quarkus-scheduler` — rotação de chaves |

O acesso a dados é **reativo e por SQL explícito** (`io.vertx.mutiny.sqlclient`), sem ORM.
Toda query é escrita à mão e retorna `Uni`.

> `quarkus-smallrye-jwt` e `quarkus-security` estão declarados mas ainda sem uso em
> código — entram quando os endpoints protegidos existirem. A emissão
> (`smallrye-jwt-build`) essa sim já é usada pelo `TokenService`.

---

## 3. Subindo o ambiente ✅

A infraestrutura é compartilhada com o módulo `marca-ai` e vive em `docker/`:

```bash
cd docker
docker compose up -d
```

| Serviço | Porta | Volume |
|---|---|---|
| `postgres` | 5432 | `marcaai_pgdata` |
| `redis` | 6379 | `marcaai_redis_data` (AOF `everysec` + RDB) |
| `kafka` | 9092 | `marcaai_kafka_data` (KRaft, sem Zookeeper) |

O compose fixa `name: marcaai`. **Não remova essa linha** — sem ela o Compose deriva o
nome do projeto do diretório, e mover o arquivo de pasta desconecta todos os volumes.

```
Host: localhost   Port: 5432   Database: db_marca_ai   User: postgres   Password: admin
Schema: marca_ai_auth
```

### Schema e a pegadinha do initdb ✅

```
docker/initdb/01-schema.sql   schema marca_ai_auth, tabelas, índices, constraints
docker/initdb/02-seed.sql     usuário de teste com papel CLIENTE
```

Esses scripts rodam **uma única vez**, quando o volume está vazio. Editar o SQL depois não
faz efeito em um banco já inicializado — o log mostra `Database directory appears to
contain a database; Skipping initialization`.

Para reaplicar:

```bash
docker compose down -v && docker compose up -d   # -v apaga TODOS os volumes
```

Isso derruba também Redis e Kafka. **Principal dívida técnica do projeto** (§10.1).

> ⚠️ Se um script do initdb falhar, o entrypoint aborta e o container não sobe. Um erro de
> SQL aparece como falha de boot do Postgres, não como erro de query.

---

## 4. Configuração ✅

O serviço sobe na **porta 8081** (`quarkus.http.port`) — a 8080 fica para o `marca-ai`.

### Banco

```properties
quarkus.datasource.reactive.url=postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:db_marca_ai}
quarkus.datasource.reactive.additional-properties."search_path"=${DB_SCHEMA:marca_ai_auth}
```

As tabelas vivem em `marca_ai_auth`, não em `public`. O `search_path` na conexão permite
que o SQL dos repositories não precise qualificar cada tabela.

### Redis

```properties
quarkus.redis.hosts=redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}/1
```

O `/1` no fim da URI seleciona o **database 1**. Sem essa propriedade o Quarkus sobe um
Redis próprio via Dev Services (Testcontainers) e ignora o do compose.

### IP do cliente atrás de proxy

```properties
quarkus.http.proxy.proxy-address-forwarding=true
quarkus.http.proxy.allow-x-forwarded=true
quarkus.http.proxy.trusted-proxies=172.16.0.0/12
```

O `X-Forwarded-For` é um header que o cliente controla — aceitar sem restrição permitiria
forjar o IP e contornar todo o rate limit da §7. O `trusted-proxies` limita de quais
origens o header é considerado; vindo de qualquer outro lugar, o Quarkus usa o endereço
real da conexão. **Ajuste a faixa quando houver um proxy real na frente.**

### Token e chaves

```properties
auth.master-key=...              # AES-256 base64 — cifra a chave privada dos JWT
auth.token.duration=180          # ver §9.1 — a unidade é ambígua hoje
mp.jwt.verify.issuer=https://marca-ai.com.br/auth
smallrye.jwt.new-token.issuer=https://marca-ai.com.br/auth
```

> A `auth.master-key` está versionada em texto claro no `application.properties`. Para
> produção ela precisa vir de variável de ambiente ou cofre de segredos.

---

## 5. Modelo de dados ✅

Doze tabelas no schema `marca_ai_auth` do database `db_marca_ai`.

| Tabela | Papel |
|---|---|
| `usuario` | identidade (CPF único), sem default no `id` — gerado pela aplicação |
| `credencial` | e-mail, hash BCrypt, verificação e bloqueio — 1:1 com `usuario` |
| `papel_usuario` | papéis globais: `CLIENTE`, `ADMIN` |
| `membro_empresa` | papéis por empresa: `DONO`, `FUNCIONARIO` |
| `fator_autenticacao` | segredo TOTP cifrado, `verificado_em`, antirreplay |
| `desafio_mfa` | desafio em curso de segundo fator |
| `codigo_recuperacao` | códigos de recuperação de MFA (hash) |
| `refresh_token` | sessões com rotação e detecção de reuso |
| `token_uso_unico` | links de verificação de e-mail e recuperação de senha |
| `aceite_termo` | LGPD — histórico por `(usuario_id, tipo, versao)` |
| `evento_seguranca` | auditoria, `ON DELETE SET NULL` |
| `chave_assinatura` | chaves RSA dos JWT, índice único parcial em `status='ATIVA'` |

### `credencial` — o que sobrou

```
usuario_id           uuid PK/FK
email                varchar(254)  NOT NULL   UNIQUE btree(email) — case-sensitive (§9.5)
senha_hash           varchar(255)  NOT NULL   BCrypt
email_verificado_em  timestamptz              nulo = não verificado
bloqueado_ate        timestamptz              fim do bloqueio; 9999-12-31 = permanente
senha_alterada_em    timestamptz  NOT NULL
criado_em            timestamptz  NOT NULL
```

A coluna `tentativas_falhas` **foi removida**. O contador de falhas vive no Redis (§7.2) —
é dado volátil, de escrita frequente, com janela de expiração: cada tentativa gerava um
`UPDATE` e uma versão nova da tupla, no caminho mais quente da aplicação. O que fica no
Postgres é a **decisão** de bloqueio (`bloqueado_ate`), que precisa ser durável e auditável.

### `chave_assinatura`

```sql
CREATE UNIQUE INDEX uq_chave_ativa ON chave_assinatura (status) WHERE status = 'ATIVA';
```

Índice único **parcial**: o banco garante no máximo uma chave `ATIVA`, enquanto várias
`ANTERIOR` convivem. É dessa garantia que a rotação concorrente depende (§6.3).

---

## 6. O que está implementado ✅

### 6.1 `POST /user` — cadastro

Tudo numa transação (`TransactionPropagation.CONTEXT`, conexão única amarrada ao contexto
duplicado do Vert.x):

1. Valida formato e conteúdo (CPF com dígito verificador, força da senha, telefone, e-mail)
2. BCrypt da senha em worker thread (`runSubscriptionOn`) — operação bloqueante não pode
   rodar no event loop
3. `usuario` → `credencial` → `papel_usuario = CLIENTE` → `aceite_termo` (termos e
   privacidade, com IP)
4. Gera segredo TOTP de 160 bits, cifra com AES-GCM usando o `usuario_id` como AAD e grava
   em `fator_autenticacao`
5. Devolve o `usuario_id` e a `otpauth://` URI para o app autenticador

Violações de constraint do Postgres são traduzidas por `ExceptionMapper::fromPgException`.

### 6.2 `POST /login` — autenticação

```
1. valida formato do e-mail e senha não vazia
2. IP está em blocks:ip:?                      → 401 genérico, sem tocar no banco
3. SELECT credencial + papéis + empresas       (uma query, ARRAY/EXISTS)
4. usuário não existe                          → conta e-mail falso para o IP, 401 genérico
5. bloqueado_ate no futuro                     → 401 (permanente tem mensagem própria)
6. e-mail não verificado                       → 401 específico
7. BCrypt em worker thread
   ├─ falhou   → incrementa falhas da conta, escala bloqueio se for marco (§7.3)
   └─ acertou  → zera contador no Redis
8. carrega chave ATIVA, decifra a privada, assina o JWT
```

O token leva `sub`, `upn`, `groups` (papéis globais) e as claims `owner` / `employee` com
os UUIDs das empresas — sem consulta ao banco por requisição depois.

A ordem dos passos é deliberada: o passo 2 custa um `GET` no Redis e corta o caminho caro
(`SELECT` com quatro subqueries + BCrypt, que é lento de propósito) para quem já se
identificou como abusivo.

### 6.3 Rotação das chaves de assinatura

`AuthKeyScheduler`, `@Scheduled(cron = "0 0 4 ? * MON")` — toda segunda às 4h, mais uma
verificação no `StartupEvent`:

- Gera par RSA 2048 com `SecureRandom.getInstanceStrong()`
- `kid` no formato `2026-w40-6515` — ano e semana ISO dizem de quando a chave é, e dois
  bytes aleatórios evitam colisão de PK em restart ou rotação manual
- A `ATIVA` atual vira `ANTERIOR` e a nova entra como `ATIVA`, na mesma transação
- A privada é cifrada com AES-GCM usando o `kid` como AAD

Em múltiplas instâncias, a corrida é resolvida pelo banco: o índice único parcial rejeita a
segunda `ATIVA`, e o código reconhece o `SQLSTATE` de violação única como caso normal
("outra instância já criou a chave ativa"), não como erro.

**A aplicação não sobe sem chave ativa** — o `StartupEvent` falha explicitamente.

---

## 7. Defesas do login ✅

Três camadas independentes, cada uma ancorada numa identidade diferente.

### 7.1 As camadas

| Camada | Ancorada em | Limite | Onde |
|---|---|---|---|
| Rate limit geral | IP | 120 req/min | `IpRateLimitFilter` |
| Varredura de e-mails | IP | 120 inexistentes / 6h | `LoginService` |
| Força bruta de senha | conta (e-mail) | escalada progressiva | `LoginService` |

Elas cobrem ataques opostos e **não se substituem**:

- muitas contas a partir de um IP → só a camada de IP enxerga
- uma conta a partir de muitos IPs → só a camada de conta enxerga

As duas primeiras são contornáveis por rotação de IP, que é identidade fraca e barata de
trocar. O contador por conta não é: ele soma as falhas daquele e-mail venha de onde vier.
É nele que mora a proteção real contra adivinhação de senha; as camadas de IP valem pelo
que custam — pouco esforço e muito lixo filtrado.

### 7.2 Chaves no Redis (database 1)

| Chave | Conteúdo | TTL | Quem escreve |
|---|---|---|---|
| `login:failures:<email>` | falhas de senha da conta | 7 dias | `LoginService` |
| `login:failures:ip:<ip>` | requisições do IP (todos os endpoints) | 1 min | `IpRateLimitFilter` |
| `login:failures:false:email:ip:<ip>` | tentativas com e-mail inexistente | 6 h | `LoginService` |
| `blocks:ip:<ip>` | flag de IP bloqueado | 24 h | `LoginService` |

Todas usam `INCR` com `EXPIRE` aplicado **só quando o retorno é 1**, ou seja, na criação da
chave. Isso é o que impede o TTL de deslizar: a janela conta a partir da primeira
requisição e não se renova, por mais tráfego que venha depois. Aplicar `EXPIRE`
incondicionalmente transformaria qualquer bloqueio em permanente enquanto houvesse tráfego.

Nenhuma chave precisa ser limpa — todas expiram sozinhas.

### 7.3 Escalada de bloqueio por conta

`CredentialUtils.calculateBlockedUntil(attempts)`:

| Falhas | Bloqueio |
|---|---|
| 5 | 1 minuto |
| 8 | 5 minutos |
| 11 | 15 minutos |
| 15 | 1 hora |
| 17 | 24 horas |
| 20 | permanente (`9999-12-31T23:59:59Z`) |
| outros | `null` — não é marco, só "e-mail ou senha incorretos" |

São **marcos exatos**, não faixas, e isso é intencional: entre um marco e o outro a pessoa
erra e recebe apenas a mensagem genérica. Faixas (`>= 5`) rebloqueariam a cada erro e
destruiriam a progressão.

O `null` do `default` significa "não é marco de bloqueio" e **precisa** ser tratado pelo
chamador — é o `if (blockedUntil != null)` no `LoginService`. Sem esse guard, o `null`
chega ao `String.format` da mensagem e vira `"bloqueado até null/null/null"`, e pior,
grava `NULL` em `bloqueado_ate`, deixando a conta sem bloqueio nenhum.

O contador do Redis é apagado (`clearFaults`) no login bem-sucedido. É isso que torna a
janela de 7 dias segura: ela só acumula para quem **nunca** acerta. Uma janela curta seria
pior — bastaria espaçar as tentativas para o contador zerar sozinho e nunca escalar.

### 7.4 Bloqueio de IP por varredura

Tentativa de login com e-mail que não existe é sinal quase puro: uso legítimo é
praticamente zero. Passando de 120 em 6 horas, o IP vai para `blocks:ip:` por 24 horas.

O log do bloqueio sai **uma única vez**, e isso depende da atomicidade do `INCR`:

```java
.invoke(blocks -> { if (blocks == 1) Log.warnf(...); })
```

Só uma requisição no mundo recebe `1` como retorno, então não há corrida nem necessidade
de ler antes de escrever. Logar a cada requisição de IP já bloqueado seria um vetor de
abuso — a frequência estaria nas mãos do atacante.

Um IP bloqueado recebe a mesma mensagem genérica de credencial inválida. Não há como
descobrir que foi detectado.

### 7.5 Rate limit geral — `IpRateLimitFilter`

`@ServerRequestFilter` global, descoberto por CDI, sem registro em nenhum controller.
Isento: `/q/health`, `/q/metrics`, `/q/openapi` — se as probes entrassem na contagem, o
orquestrador receberia 429 e reiniciaria o pod.

Um único `INCR` por requisição decide tudo (o retorno já é o total, dispensando um `GET`
antes), e a recuperação fica **colada** na chamada ao Redis:

```java
return failedAttemptsRepository.insertFailedAttemptsIP(ip)
        .onFailure().recoverWithItem(this::rateLimitUnavailable)   // Redis fora → 0L → passa
        .chain(attempts -> attempts > MAX_REQUESTS_PER_MINUTE ? ... : ...);
```

Nesse ponto da cadeia a única falha possível é técnica — a exceção de negócio só nasce no
`chain` seguinte. Por isso não é preciso filtrar por tipo. O comportamento é **fail open**:
Redis indisponível libera a requisição, porque derrubar a API inteira é pior do que ficar
um minuto sem rate limit.

> Rate limit na aplicação é uma primeira linha. Para esgotamento por volume, a defesa
> estruturalmente certa é a borda (nginx, gateway, CDN): quando a requisição chega aqui, o
> TCP já foi aceito e o TLS já foi negociado — e o handshake custa mais que verificar um
> JWT.

### 7.6 Enumeração de contas

O login responde `INCORRECT_EMAIL_OR_PASSWORD` tanto para e-mail inexistente quanto para
senha errada. Duas exceções conhecidas e aceitas:

- `EMAIL_HAS_NOT_BEEN_VERIFIED` só aparece para conta existente — **decisão de produto**:
  avisar quem esqueceu de verificar vale mais que o sigilo sobre o cadastro existir
- O BCrypt só roda no ramo em que o usuário existe, então a latência difere (§9.3)

---

## 8. Decisões de modelagem

### 8.1 Papel como `CHECK`, não como tabela

`papel_usuario.papel` é validado por `CHECK (papel IN ('CLIENTE','ADMIN'))`, sem tabela
`papel`. O `CHECK` dá a mesma proteção que uma FK com `ON DELETE RESTRICT` nos dois
sentidos: valor inválido é rejeitado, e remover da lista um papel em uso falha.

A desvantagem é a descoberta: listar os papéis válidos exigiria regex em
`pg_get_constraintdef()`. Na prática se escreve um `enum` em Java e a lista passa a existir
em dois lugares. Com dois papéis fixos, aceitamos o custo.

### 8.2 `empresa_id` sem FK

`membro_empresa` tem FK para `usuario`, mas nenhuma para `empresa_id` — a empresa vive no
serviço `marca-ai`. Qualquer UUID é aceito, e empresa excluída do outro lado deixa linhas
órfãs. **A consistência de `empresa_id` é responsabilidade da aplicação, não do banco.**

### 8.3 Segredos nunca em texto claro

| Dado | Armazenamento |
|---|---|
| Senha | BCrypt |
| Refresh token / token de uso único | SHA-256 hex |
| Chave privada JWT | AES-GCM, AAD = `kid` |
| Segredo TOTP | AES-GCM, AAD = `usuario_id` |

O AAD amarra cada texto cifrado ao seu dono: um segredo TOTP copiado para outra linha não
decifra, porque o `usuario_id` não bate.

---

## 9. Pendências conhecidas

### 9.1 A unidade de `auth.token.duration` é ambígua

```properties
auth.token.duration=180
```
```java
.expiresIn(Duration.ofMinutes(duration))      // 180 minutos = 3 horas
return new TokenResponse(token, duration);    // devolve 180 cru ao cliente
```

O JWT vale 3 horas, mas o cliente recebe `180` sem unidade e, pela convenção de
`expires_in`, vai ler como 180 **segundos**. Resolver nomeando a unidade
(`auth.token-duration-minutes`) ou guardando em segundos com `Duration.ofSeconds`.

TTL curto é a defesa mais barata contra token vazado — e a única que funciona antes de
existir revogação.

### 9.2 MFA provisionado mas não exigido

O cadastro gera e cifra o segredo TOTP, o login calcula `mfa_ativo` e carrega no
`LoginModel`... e **nada consulta esse campo**. Conta com segundo fator configurado e
verificado entra só com a senha. As tabelas `desafio_mfa` e `codigo_recuperacao` existem e
estão vazias de uso.

É a defesa que falta contra credential stuffing — senha vazada de outro serviço, testada
uma vez por conta, de muitos IPs. Nenhuma das três camadas da §7 acende nesse ataque, e
nenhum contador acenderia: cada tentativa isolada é indistinguível de um login legítimo.

### 9.3 Tempo de resposta denuncia conta existente

`BcryptUtil.matches` só é chamado quando o usuário existe. Mesmo com mensagens idênticas, a
diferença de latência separa os dois casos. A correção é comparar contra um hash dummy no
ramo `model == null`.

### 9.4 `clearBlocksUser` sem chamador

O SQL está correto, mas nada chama. O uso previsto é desbloqueio administrativo de quem
levou `PERMANENT_BLOCK` — e esse endpoint precisa limpar **os dois lados**:

```java
credentialRepository.clearBlocksUser(ID)
        .chain(() -> failedAttemptsRepository.clearFaults(email));
```

Sem o segundo passo, o contador continua em 20 com TTL de 7 dias. A próxima falha leva a
21, que cai no `default -> null` da escalada, e a conta fica **sem bloqueio nenhum** até o
contador expirar.

### 9.5 E-mail é case-sensitive

O índice é `UNIQUE btree(email)`, sem `lower()`, e o login busca com `WHERE c.email = $1`.
Consequência: `Joao@teste.com` e `joao@teste.com` são contas **diferentes** — ambas podem
ser cadastradas, e quem digitar o e-mail com a capitalização errada no login recebe
"e-mail ou senha incorretos" sem entender por quê. Verificado no banco: a busca pelo mesmo
endereço em maiúsculas devolve zero linhas.

A correção é normalizar na entrada (`email.toLowerCase()` no cadastro e no login) e trocar
o índice por `UNIQUE (lower(email))`, que impede o cadastro duplicado no banco.

### 9.6 Menores

- `Retry-After` ausente na resposta 429 do `MiddlewareExceptionHandler`
- A chave `login:failures:ip:` guarda a contagem de todas as requisições de todos os
  endpoints — não são falhas, nem são de login
- `CredentialUtils`: construtor `public` em classe utilitária; constante `fourteen` vale 15
- Imports não usados: `LocalDateTime` em `LoginService`, `org.jose4j.http.Response` em
  `IpRateLimitFilter`

---

## 10. Roadmap

### 10.1 Trocar `initdb` por Flyway — prioridade

Hoje qualquer alteração de schema exige destruir o banco. Como o projeto usa o cliente
reativo, o Flyway precisa de um datasource JDBC ao lado só para migrar no boot:

```xml
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-flyway</artifactId></dependency>
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jdbc-postgresql</artifactId></dependency>
```

O `01-schema.sql` vira `src/main/resources/db/migration/V1__init.sql`.

### 10.2 Fluxos que faltam

- Verificação de e-mail e recuperação de senha (`token_uso_unico` já existe)
- Refresh token com rotação e detecção de reuso (`refresh_token` já existe)
- Convite e ativação de funcionário
- `GET /.well-known/jwks.json` publicando as públicas `ATIVA` + `ANTERIOR`
- Endpoints protegidos com `@RolesAllowed` — aí `quarkus-smallrye-jwt` e
  `quarkus-security` saem do papel

### 10.3 Revogação de token

JWT é auto-contido e não dá para invalidar antes de expirar — trocar a senha ou sair de
todos os dispositivos não derruba o que já foi emitido. A saída é uma denylist de `jti` no
Redis com TTL igual ao tempo restante do token:

```
SET revoked:<jti> 1 EX <segundos_restantes>
```

A chave some sozinha quando o token expiraria de qualquer jeito. Custa uma consulta por
requisição — o preço da revogação, que o TTL curto mantém baixo.

### 10.4 Rate limit por usuário

Em endpoint protegido existe identidade forte: o `sub` do token. Rate limit ancorado nele é
imune a rotação de IP. O filtro precisa rodar **depois** da autenticação, enquanto o de IP
(§7.5) roda antes — a camada de IP cobre quem ainda não tem identidade, a de usuário cobre
quem já tem.

### 10.5 Outros

- Publicar `evento_seguranca` no Kafka
- Job de expurgo de `refresh_token` e `token_uso_unico` expirados
- Testes de integração com `@QuarkusTest` + Testcontainers
- Decidir de quem é a entidade *empresa* (§8.2) e unificar a nomenclatura — schema em
  português, controllers em inglês

---

## 11. Desenvolvimento

```bash
./mvnw quarkus:dev          # modo dev com live reload (porta 8081)
./mvnw test
./mvnw package
./mvnw package -Dnative
```

Dev UI em <http://localhost:8081/q/dev/> (só em modo dev).

### Rodando pelo terminal

O projeto compila com `maven.compiler.release=25`. Se o `java` padrão do shell for mais
antigo, o dev mode falha com `UnsupportedClassVersionError` (class file 69). Aponte o
`JAVA_HOME` antes:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25-open
```

### Inspecionando o estado das defesas

```bash
# contadores e bloqueios
docker exec redis redis-cli -n 1 --scan --pattern 'login:*'
docker exec redis redis-cli -n 1 --scan --pattern 'blocks:*'
docker exec redis redis-cli -n 1 ttl blocks:ip:<ip>

# destravar uma conta em teste
docker exec redis redis-cli -n 1 del login:failures:<email>
docker exec -e PGPASSWORD=admin postgresql psql -U postgres -d db_marca_ai \
  -c "UPDATE marca_ai_auth.credencial SET bloqueado_ate = NULL WHERE email='<email>';"

# verificar um e-mail sem o fluxo de verificação
docker exec -e PGPASSWORD=admin postgresql psql -U postgres -d db_marca_ai \
  -c "UPDATE marca_ai_auth.credencial SET email_verificado_em = now() WHERE email='<email>';"
```

> Quando uma query falhar, o Postgres registra a instrução inteira no log do container —
> é o caminho mais rápido para achar SQL malformado:
> `docker logs postgresql 2>&1 | grep -A20 "ERROR:"`

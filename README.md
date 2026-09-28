# marca-ai-auth

Serviço de autenticação e autorização do MarcaAi. Emite e renova tokens JWT, guarda
credenciais, controla os vínculos entre usuários e empresas e registra auditoria de
segurança.

> **Estado atual:** o schema do banco está pronto e aplicado. O código Java ainda é
> esqueleto (`GreetingResource`, `UserController` vazio, `EnterpriseController` com um
> `POST` que devolve `Uni.createFrom().voidItem()`). Tudo que este documento descreve
> como fluxo ou endpoint é **desenho pretendido**, não implementação existente. As
> seções marcadas com ✅ são o que já está de pé.

---

## 1. Responsabilidade do serviço

**Faz:**

- Cadastro e autenticação de usuários (identidade única por CPF)
- Emissão, renovação e revogação de tokens
- Vínculo de usuários a empresas com papel (`DONO` / `FUNCIONARIO`)
- Convite e ativação de funcionários
- Recuperação de senha e verificação de e-mail
- Rotação das chaves de assinatura dos JWT
- Auditoria de eventos de segurança

**Não faz:**

- Não é dono da entidade *empresa* — ela vive no serviço `marca-ai`. Aqui só existe
  `empresa_id` como referência solta (ver §7.2)
- Não decide regra de negócio de marcas, faturas ou qualquer domínio da aplicação
- Não guarda dados de perfil além do mínimo de identificação

---

## 2. Stack ✅

| Peça | Escolha |
|---|---|
| Runtime | Quarkus 3.39.5, Java 25 |
| API | `quarkus-rest` + `quarkus-rest-jackson` |
| Banco | PostgreSQL 17 via `quarkus-reactive-pg-client` (Vert.x, sem Hibernate/JPA) |
| Cache | Redis 8 (sessões e permissões — ver §6.3) |
| Mensageria | Kafka (KRaft) |

O acesso a dados é **reativo e por SQL explícito** (`io.vertx.mutiny.sqlclient`), não há
ORM. Toda query é escrita à mão e retorna `Uni`/`Multi`.

### Dependências ainda não adicionadas

O `pom.xml` **não tem** nenhuma extensão de segurança. Para implementar o que está
descrito aqui será preciso:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt</artifactId>       <!-- validar JWT -->
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt-build</artifactId> <!-- emitir JWT -->
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-elytron-security-common</artifactId> <!-- BCrypt -->
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-redis-client</artifactId>
</dependency>
```

---

## 3. Subindo o ambiente ✅

A infraestrutura é compartilhada com o módulo `marca-ai` e vive em `docker/`:

```bash
cd docker
docker compose up -d
```

Sobem três serviços, todos com healthcheck e volume persistente:

| Serviço | Porta | Volume |
|---|---|---|
| `postgres` | 5432 | `marcaai_pgdata` |
| `redis` | 6379 | `marcaai_redis_data` (AOF `everysec` + RDB) |
| `kafka` | 9092 | `marcaai_kafka_data` (KRaft, sem Zookeeper) |

O compose fixa `name: marcaai`. **Não remova essa linha** — sem ela o Compose deriva o
nome do projeto do diretório, e mover o arquivo de pasta desconecta todos os volumes.

Conexão para DBeaver e afins:

```
Host: localhost   Port: 5432   Database: db_marca_ai   User: postgres   Password: admin
```

O database é `db_marca_ai`. Conectar no database padrão `postgres` mostra zero tabelas —
ele existe e está legitimamente vazio.

### Schema e a pegadinha do initdb ✅

O schema mora em `docker/initdb/` e é aplicado pelo entrypoint do Postgres:

```
docker/initdb/01-schema.sql   tabelas, índices, constraints
docker/initdb/02-seed.sql     um usuário de teste com papel CLIENTE
```

Esses scripts rodam **uma única vez**, quando o volume está vazio. Editar o SQL depois não
faz efeito nenhum em um banco já inicializado — o log mostra
`Database directory appears to contain a database; Skipping initialization`.

Para reaplicar:

```bash
docker compose down -v && docker compose up -d   # -v apaga TODOS os volumes
```

Isso derruba também Redis e Kafka. **Esta é a principal dívida técnica do projeto**
(ver §9.1).

> ⚠️ Se um script do initdb falhar, o entrypoint aborta e o container não sobe. Um erro
> de SQL aparece como falha de boot do Postgres, não como erro de query.

---

## 4. Modelo de dados ✅

Nove tabelas, todas no schema `public` do database `db_marca_ai`.

```
                       ┌───────────────────┐
                       │      usuario      │  identidade (CPF único)
                       └─────────┬─────────┘
          ┌──────────────┬───────┼────────┬──────────────┬──────────────┐
          │              │       │        │              │              │
    ┌─────┴─────┐  ┌─────┴────┐  │  ┌─────┴──────┐ ┌─────┴──────┐ ┌─────┴────────┐
    │ credencial│  │ aceite_  │  │  │ refresh_   │ │ token_uso_ │ │  evento_     │
    │   (1:1)   │  │  termo   │  │  │  token     │ │   unico    │ │  seguranca   │
    └───────────┘  └──────────┘  │  └────────────┘ └────────────┘ └──────────────┘
                    ┌────────────┴────────────┐
              ┌─────┴───────┐        ┌────────┴────────┐
              │papel_usuario│        │ membro_empresa  │
              │  (global)   │        │  (por empresa)  │
              └─────────────┘        └─────────────────┘

    ┌──────────────────┐
    │ chave_assinatura │  independente de usuário — chaves dos JWT
    └──────────────────┘
```

### 4.1 `usuario` — identidade

| Coluna | Tipo | Nota |
|---|---|---|
| `id` | `uuid` PK | **sem default** — gerado pela aplicação |
| `nome` | `varchar(150)` | |
| `cpf` | `char(11)` UNIQUE | só dígitos, validado na aplicação |
| `telefone` | `varchar(16)` | |
| `data_nascimento` | `date` | opcional |
| `criado_em` / `atualizado_em` | `timestamptz` | |

O CPF é a identidade natural: **um login por pessoa**, independente de quantas empresas
ela participe.

### 4.2 `credencial` — como entra (1:1 com `usuario`)

`usuario_id` é PK **e** FK, o que torna a relação 1:1 e a credencial **opcional**: um
usuário pode existir sem credencial. Esse é exatamente o estado "convidado, ainda não
ativou" (§6.4).

| Coluna | Nota |
|---|---|
| `email` | `NOT NULL`; índice único em `lower(email)` — case-insensitive |
| `senha_hash` | `NOT NULL`; BCrypt |
| `email_verificado_em` | nulo = não verificado |
| `tentativas_falhas`, `bloqueado_ate` | proteção contra força bruta |
| `senha_alterada_em` | usado para invalidar tokens emitidos antes da troca |

### 4.3 `papel_usuario` — papéis globais

```sql
papel varchar(30) CHECK (papel IN ('CLIENTE', 'ADMIN'))
PRIMARY KEY (usuario_id, papel)
```

PK composta ⇒ **é N:N**: um usuário tem vários papéis, um papel pertence a vários
usuários. Não existe tabela `papel` e isso é deliberado (§7.1).

### 4.4 `membro_empresa` — papéis por empresa

```sql
empresa_id uuid NOT NULL                                       -- sem FK (§7.2)
papel      varchar(30) CHECK (papel IN ('DONO', 'FUNCIONARIO'))
PRIMARY KEY (usuario_id, empresa_id)
```

Separado de `papel_usuario` porque `DONO` não significa nada sozinho — ninguém é dono
globalmente, é dono **de uma empresa**.

A PK `(usuario_id, empresa_id)` permite **um papel por usuário por empresa**. Se um dia
alguém puder acumular dois papéis na mesma empresa, a PK precisa virar
`(usuario_id, empresa_id, papel)`.

### 4.5 `refresh_token` — sessões com rotação

| Coluna | Nota |
|---|---|
| `token_hash` | `char(64)` UNIQUE — SHA-256 em hex. **O token puro nunca é gravado** |
| `familia_id` | agrupa a cadeia de rotações de uma mesma sessão |
| `usado_em` | marca o consumo; usar duas vezes = reuso detectado |
| `revogado_em` | revogação explícita |
| `ip_criacao`, `user_agent` | contexto para auditoria |

### 4.6 `token_uso_unico` — links de uso único

```sql
tipo varchar(30) CHECK (tipo IN ('VERIFICAR_EMAIL', 'RECUPERAR_SENHA'))
```

Mesma estratégia de hash do refresh: guarda-se `SHA-256(token)`, nunca o token.

### 4.7 `aceite_termo` — LGPD

PK `(usuario_id, tipo, versao)` ⇒ histórico preservado: publicar uma versão nova dos
termos gera uma linha nova, sem sobrescrever o aceite anterior. Guarda `ip` e `aceito_em`
como prova.

### 4.8 `evento_seguranca` — auditoria

`id bigint GENERATED ALWAYS AS IDENTITY`, `detalhes jsonb`, e FK para `usuario` com
`ON DELETE SET NULL` — o evento sobrevive à exclusão do usuário, que é o comportamento
correto para auditoria. Índices em `(usuario_id, criado_em DESC)` e `(ip, criado_em DESC)`.

### 4.9 `chave_assinatura` — chaves dos JWT

```sql
status varchar(20) CHECK (status IN ('ATIVA', 'ANTERIOR', 'REVOGADA'))
CREATE UNIQUE INDEX uq_chave_ativa ON chave_assinatura (status) WHERE status = 'ATIVA';
```

O índice único **parcial** garante no banco que existe no máximo uma chave `ATIVA`.
Verificado: a segunda tentativa de inserir uma `ATIVA` é rejeitada, enquanto várias
`ANTERIOR` convivem.

A chave privada é gravada cifrada (AES-GCM, Base64); a de cifragem vem de variável de
ambiente, **nunca do banco**.

---

## 5. Cascatas

Todas as tabelas de usuário usam `ON DELETE CASCADE`, menos a auditoria:

| Tabela | Ao apagar o usuário |
|---|---|
| `credencial`, `aceite_termo`, `papel_usuario`, `membro_empresa`, `refresh_token`, `token_uso_unico` | apagadas |
| `evento_seguranca` | mantida, `usuario_id` vira `NULL` |

---

## 6. Como deve funcionar

### 6.1 Cadastro de cliente

1. `POST /auth/registro` com nome, CPF, telefone, e-mail, senha e aceite dos termos
2. Valida CPF (dígito verificador) e força da senha
3. Numa transação: cria `usuario`, `credencial` (BCrypt), `papel_usuario = CLIENTE` e as
   linhas de `aceite_termo`
4. Gera `token_uso_unico` tipo `VERIFICAR_EMAIL` e dispara o e-mail
5. Registra `evento_seguranca`

Colisão de CPF ou e-mail deve responder a mesma mensagem genérica — não confirmar a
existência de cadastro para quem não está autenticado.

### 6.2 Login

1. `POST /auth/login` com e-mail e senha
2. Busca por `lower(email)`; verifica `bloqueado_ate`
3. Confere BCrypt. Falhou: incrementa `tentativas_falhas`, e ao passar do limite preenche
   `bloqueado_ate` (backoff exponencial). Acertou: zera o contador
4. Carrega os papéis dos dois escopos em **uma** query — `UNION ALL`, não join, porque as
   tabelas são conjuntos disjuntos:

```sql
SELECT 'GLOBAL' AS escopo, NULL::uuid AS empresa_id, papel
  FROM papel_usuario  WHERE usuario_id = $1
UNION ALL
SELECT 'EMPRESA',          empresa_id,               papel
  FROM membro_empresa WHERE usuario_id = $1;
```

O plano usa as PKs compostas (`usuario_id` é a coluna líder das duas), resultando em dois
index scans e um `Append` — sem índice adicional e sem sort.

5. Emite access token e refresh token, gravando `SHA-256(refresh)` com `familia_id` novo

### 6.3 Autorização por requisição

**Papéis globais** vão na claim `groups`, que o SmallRye JWT mapeia direto:

```java
@RolesAllowed("ADMIN")
```

**Papéis de empresa não cabem em `@RolesAllowed`** — e isso não é limitação da
ferramenta. `ADMIN` é estático; "é DONO desta empresa" depende de qual empresa a
requisição está tocando, coisa que só se sabe em runtime:

```java
@GET @Path("/empresas/{empresaId}/faturas")
public Uni<List<Fatura>> listar(@PathParam("empresaId") UUID empresaId) {
    if (!contexto.temPapel(empresaId, "DONO")) throw new ForbiddenException();
    ...
}
```

Vale encapsular num interceptor com anotação própria (`@PapelEmpresa("DONO")`) em vez de
repetir o `if`.

Formato do token:

```json
{
  "sub": "8b77990b-6857-4183-9df5-c244fb2afef0",
  "groups": ["CLIENTE"],
  "empresas": { "c8366e20-...": "DONO", "6c39c582-...": "FUNCIONARIO" },
  "exp": 1758800000,
  "kid": "2026-01-a"
}
```

**Nenhuma consulta ao banco por requisição.** Os papéis são resolvidos uma vez no login e
viajam assinados. O custo é a **defasagem**: remover alguém de uma empresa não invalida o
token dele até expirar. Por isso o access token é curto — 15 minutos — e a revogação real
acontece no refresh.

Quando as permissões granulares entrarem (§9.2), elas **não** vão no token: mudança de
permissão feita pelo DONO precisa valer na hora, então serão lidas do Redis com
invalidação na escrita, e o Postgres só como fallback.

### 6.4 Convite de funcionário

Só um usuário autenticado e `DONO` da empresa cria funcionário — não existe cadastro
público de funcionário, porque a operação acontece dentro do contexto de uma empresa, e
esse contexto vem do token.

```
DONO autenticado
  └─ POST /empresas/{id}/funcionarios  { nome, cpf, email }
       ├─ cria `usuario`  (SEM credencial — ainda não tem senha)
       ├─ cria `membro_empresa` com papel FUNCIONARIO
       ├─ gera `token_uso_unico` de convite
       └─ envia e-mail com o link

FUNCIONARIO abre o link
  └─ POST /auth/convite/aceitar  { token, senha }
       ├─ valida o token (hash, expiração, não usado)
       ├─ cria `credencial` com a senha escolhida
       ├─ marca `usado_em` e `email_verificado_em`
       └─ registra evento
```

Se o CPF já existir, **não** cria usuário novo: apenas adiciona `membro_empresa`. É o caso
de alguém que já é cliente e passa a ser funcionário de uma empresa, ou que trabalha em
duas empresas. Um login, vários vínculos.

> ⚠️ Este fluxo **não funciona com o schema atual**. Ver §8.1 e §8.2.

### 6.5 Renovação com detecção de reuso

```
POST /auth/refresh  { refresh_token }
  ├─ localiza por SHA-256(token)
  ├─ não existe            → 401
  ├─ revogado ou expirado  → 401
  ├─ JÁ TEM `usado_em`     → REUSO: revoga a família inteira, registra evento, 401
  └─ válido                → marca `usado_em`, emite par novo com o MESMO `familia_id`
```

A detecção de reuso é o que torna o refresh seguro: se um token vazou e o atacante o usa,
o usuário legítimo tentará usar o mesmo token depois e a família inteira cai — derrubando
os dois. Melhor uma sessão perdida do que um invasor persistente.

Logout revoga a família corrente. "Sair de todos os dispositivos" revoga todas as famílias
do usuário.

### 6.6 Recuperação de senha

1. `POST /auth/senha/recuperar` com o e-mail
2. **Sempre** responde `202 Accepted`, exista o e-mail ou não — caso contrário o endpoint
   vira um oráculo de cadastro
3. Se existir, gera `token_uso_unico` tipo `RECUPERAR_SENHA` com validade curta
4. `POST /auth/senha/redefinir` valida, troca o hash, atualiza `senha_alterada_em`,
   **revoga todos os refresh tokens** e marca o token como usado

### 6.7 Rotação das chaves

- Chave `ATIVA` assina os tokens novos
- Ao rotacionar: a `ATIVA` vira `ANTERIOR` e entra uma `ATIVA` nova
- `ANTERIOR` continua **validando** tokens em circulação até que todos expirem
- `GET /.well-known/jwks.json` publica as públicas `ATIVA` + `ANTERIOR`
- `REVOGADA` sai do JWKS imediatamente (usar só em incidente — derruba todos os tokens)

---

## 7. Decisões de modelagem e o porquê

### 7.1 Papel como `CHECK`, não como tabela

`papel_usuario.papel` é validado por `CHECK (papel IN ('CLIENTE','ADMIN'))`, sem tabela
`papel`. Isso foi questionado e testado no banco:

```
INSERT 'GERENTE' → ERROR: violates check constraint
INSERT 'admin'   → ERROR: violates check constraint   (case-sensitive)
INSERT 'ADMIN'   → INSERT 0 1
```

E ao tentar remover da lista um papel em uso:

```
ALTER ... CHECK (papel IN ('ADMIN'))   -- tirando CLIENTE
→ ERROR: check constraint is violated by some row
```

O `CHECK` dá a **mesma** proteção que uma FK com `ON DELETE RESTRICT`, nos dois sentidos.
O papel não fica "solto".

A desvantagem real é outra: para listar os papéis válidos, a aplicação teria que fazer
regex em `pg_get_constraintdef()`. Na prática se escreve um `enum` em Java, e a lista
passa a existir em dois lugares sem nada garantindo que concordem. Como são dois papéis
fixos verificados por anotação, aceitamos esse custo. Uma tabela `papel` sozinha
resolveria a descoberta, mas não a duplicação — o código continuaria com
`@RolesAllowed("ADMIN")` hardcoded.

### 7.2 `empresa_id` sem FK — e o que isso custa

`membro_empresa` tem FK para `usuario`, mas **nenhuma** para `empresa_id`:

```
p: PRIMARY KEY (usuario_id, empresa_id)
f: FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
c: CHECK (papel IN ('DONO','FUNCIONARIO'))
```

É consequência de a empresa viver no serviço `marca-ai`. Qualquer UUID é aceito ali, e se
a empresa for excluída do outro lado ficam linhas órfãs sem ninguém avisar. **A
consistência de `empresa_id` é responsabilidade da aplicação, não do banco.**

Duas saídas, ambas em aberto (§8.3):

1. **Banco compartilhado** — hoje os dois módulos apontam para `db_marca_ai`, então criar
   a FK de verdade está ao alcance
2. **Bancos separados** — mantém o UUID solto e exige reconciliação, via evento Kafka de
   empresa excluída ou job de limpeza

### 7.3 Segredos nunca em texto claro

| Dado | Armazenamento |
|---|---|
| Senha | BCrypt |
| Refresh token | SHA-256 hex (`char(64)`) |
| Token de uso único | SHA-256 hex |
| Chave privada JWT | AES-GCM, chave via ambiente |

Vazamento do dump do banco não entrega sessão nem senha de ninguém.

---

## 8. Pendências que bloqueiam o fluxo de convite

### 8.1 O e-mail está na tabela errada

```
credencial | email      | NOT NULL
credencial | senha_hash | NOT NULL
usuario    | (não tem coluna de email)
```

Para **enviar** o convite é preciso do e-mail, mas ele só existe em `credencial`, que
exige `senha_hash NOT NULL` — justamente o que o convidado ainda não definiu. Impasse.

**Correção:** mover `email` (e o índice único em `lower(email)`) para `usuario`.
Conceitualmente é onde ele deveria estar: e-mail é identidade, não credencial.
`credencial` fica com a senha e os campos de bloqueio.

### 8.2 Falta o tipo de token de convite

```sql
tipo varchar(30) CHECK (tipo IN ('VERIFICAR_EMAIL', 'RECUPERAR_SENHA'))
```

O convite é um terceiro caso. Dá para reaproveitar `RECUPERAR_SENHA`, mas a expiração é
bem diferente — convite dura dias, recuperação dura minutos — então vale um
`DEFINIR_SENHA` próprio.

### 8.3 De quem é a entidade *empresa*?

Existe um `EnterpriseController` nos **dois** módulos, e o do `marca-ai-auth` já tem um
`POST /enterprise`. Isso contradiz o comentário do schema ("a empresa mora na API") e
precisa ser decidido antes de avançar: ou a empresa é criada aqui e o `marca-ai` consome,
ou o contrário. Hoje está nos dois lugares pela metade.

Vale decidir junto a nomenclatura: o schema é em português (`empresa`, `usuario`,
`papel`) e os controllers em inglês (`EnterpriseController`, `UserController`).

### 8.4 Sem dependência de segurança no `pom.xml`

Nada de JWT, BCrypt ou Redis foi adicionado ainda (§2).

As pendências 8.1 e 8.2 alteram tabelas existentes — com o `initdb` isso exige
`down -v`, derrubando Redis e Kafka junto. As permissões de §9.2, por serem aditivas, não
têm esse problema.

---

## 9. Roadmap

### 9.1 Trocar `initdb` por Flyway — prioridade

Hoje qualquer alteração de schema exige destruir o banco. Para um serviço de autenticação
que vai evoluir, isso não se sustenta.

Como o projeto usa o cliente **reativo**, o Flyway precisa de um datasource JDBC ao lado
só para migrar no boot:

```xml
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-flyway</artifactId></dependency>
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jdbc-postgresql</artifactId></dependency>
```

```properties
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/db_marca_ai
quarkus.datasource.reactive.url=postgresql://localhost:5432/db_marca_ai
quarkus.flyway.migrate-at-start=true
```

O `01-schema.sql` vira `src/main/resources/db/migration/V1__init.sql`, e as pendências da
§8 viram `V2__`, `V3__`.

### 9.2 Permissões granulares — depois

O DONO tem acesso a tudo da empresa; o FUNCIONARIO tem acesso limitado, definido pelo
DONO. O modelo é **aditivo**, não exige `ALTER` em nada:

```sql
CREATE TABLE permissao (
    codigo    varchar(60) PRIMARY KEY,   -- 'MARCA_CRIAR', 'FATURA_LER'
    descricao varchar(160) NOT NULL
);

CREATE TABLE membro_permissao (
    usuario_id uuid        NOT NULL,
    empresa_id uuid        NOT NULL,
    permissao  varchar(60) NOT NULL REFERENCES permissao(codigo),
    PRIMARY KEY (usuario_id, empresa_id, permissao),
    FOREIGN KEY (usuario_id, empresa_id)
        REFERENCES membro_empresa(usuario_id, empresa_id) ON DELETE CASCADE
);
```

`permissao` é catálogo populado por migration — cada código corresponde a uma verificação
no código, então permissão nova significa deploy. O DONO só marca quais valem para cada
funcionário. Os defaults viram um `INSERT ... SELECT` na criação do funcionário.

> **Regra:** o DONO **não** recebe linhas em `membro_permissao`. Trate como implícito
> (`if (papel == DONO) permitir`). Materializar obrigaria a fazer backfill em todos os
> donos de todas as empresas a cada permissão nova.

### 9.3 Superfície de API pretendida

**Público**

| Método | Rota |
|---|---|
| `POST` | `/auth/registro` |
| `POST` | `/auth/login` |
| `POST` | `/auth/refresh` |
| `POST` | `/auth/senha/recuperar` |
| `POST` | `/auth/senha/redefinir` |
| `POST` | `/auth/convite/aceitar` |
| `GET` | `/auth/email/verificar` |
| `GET` | `/.well-known/jwks.json` |

**Autenticado**

| Método | Rota | Exige |
|---|---|---|
| `GET` | `/me` | qualquer |
| `POST` | `/auth/logout` | qualquer |
| `POST` | `/empresas` | qualquer (vira `DONO`) |
| `POST` | `/empresas/{id}/funcionarios` | `DONO` |
| `GET` | `/empresas/{id}/funcionarios` | `DONO` |
| `DELETE` | `/empresas/{id}/funcionarios/{usuarioId}` | `DONO` |

### 9.4 Outros

- Rate limit por IP e por e-mail no login e na recuperação
- Publicar eventos de segurança no Kafka para consumo externo
- Job de expurgo de `refresh_token` e `token_uso_unico` expirados
- Testes de integração com `@QuarkusTest` + Testcontainers

---

## 10. Desenvolvimento

```bash
./mvnw quarkus:dev          # modo dev com live reload
./mvnw test                 # testes
./mvnw package              # JAR
./mvnw package -Dnative     # binário nativo
```

Dev UI em <http://localhost:8080/q/dev/> (só em modo dev).

> O `application.properties` deste módulo está **vazio**. Sem `quarkus.datasource.*`
> explícito, o Dev Services sobe um Postgres descartável em porta aleatória e o serviço
> não enxerga o banco do compose. O módulo `marca-ai` já tem essa configuração; replique-a
> aqui ao começar a implementar.

---

## 11. Estado do código

| Arquivo | Situação |
|---|---|
| `GreetingResource.java` | scaffold do Quarkus, `GET /hello` |
| `UserController.java` | classe vazia |
| `EnterpriseController.java` | `POST /enterprise` devolvendo `Uni.createFrom().voidItem()` |
| `service/EnterpriseService.java` | classe vazia |

Nada de autenticação está implementado. O schema é a única parte pronta.

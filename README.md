# RER DSP — Job Data Migration

**Projeto:** Rural Environmental Registry — Data Sharing Platform  
**Componente:** Job de migração geoespacial (ETL Spring Batch)  
**Artefato Maven:** `dsp-batch`  
**Licença:** GPL-3.0

---

## O que este projeto faz

Este job sincroniza dados geoespaciais do **banco de origem** da sua organização para os bancos do DSP.

Ele possui dois tipos de migração:

| Tipo | O que migra | Onde grava |
|------|-------------|------------|
| **Jobs fixos** | Unidades administrativas (3 níveis) e áreas de interesse | DSP DB + geo-target |
| **Camadas genéricas** | Qualquer tabela PostGIS declarada no YAML | Somente geo-target |

A carga inclui detecção de mudanças, leitura particionada, UPSERT e (quando aplicável) remoção de registros órfãos no destino.

---

## Conceitos rápidos

- **Camada** = tabela geográfica (ex.: `conservation.rivers`)
- **Feição** = uma linha dentro dessa tabela (ex.: um trecho de rio)
- **Geo-target** = banco usado na exibição de mapas (WMS)

---

## Configuração mínima — camadas genéricas

```yaml
spring:
  datasource:
    source: { ... }       # banco de origem
    target: { ... }       # DSP DB (jobs fixos)
    geo-target: { ... }   # banco de exibição (camadas)
    batch: { ... }        # metadados Spring Batch

batch:
  layers:
    - source-table: schema.minha_tabela
      area-of-interest-id-column: id_da_aoi_na_origem
      srid: 4674

execution-jobs:
  area-of-interest-geoserver-job: true   # rodar antes das camadas
  layer-jobs: true
```

Destino automático: `dsp.minha_tabela` no geo-target.

---

## Como executar

```bash
# Metadados Spring Batch (primeira vez)
psql -d batch_metadata -f src/main/resources/db/batch_metadata/02_spring_batch_schema.sql

# Subir o job
./mvnw spring-boot:run
```

Ordem recomendada: **unidades administrativas → área de interesse → camadas**.

---

## Documentação completa

| Tópico | Onde ler |
|--------|----------|
| **Camadas genéricas (guia didático)** | [Migração de camadas](../rer-dsp-docs/docs/migration/layer-migration.md) |
| Job completo (YAML, datasources, jobs fixos) | [Job data-migration](../rer-dsp-docs/docs/migration/rer-dsp-job-data-migration.md) |
| Onboarding | [Começando](../rer-dsp-docs/docs/getting-started.md) |

---

## Geometria 3D (Z/M)

O geo-target usa colunas 2D para WMS. Geometrias com elevação ou medida (ex.: `POINT Z`) são achatadas com `ST_Force2D` — X/Y são mantidos, Z/M descartados. Um aviso é registrado nos logs na introspecção.

---

## Licença

[GNU General Public License v3.0](LICENSE)

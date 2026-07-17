# RER DSP — Job Data Migration

**Projeto**: Rural Environmental Registry — Data Sharing Platform  
**Componente**: Job de Migração de Dados (ETL)  
**Tipo**: Digital Public Good (DPG)  
**Licença**: GPL-3.0

---

## 📋 Visão Geral

Job de migração e transformação de dados da plataforma DSP do RER. Responsável pela extração, transformação e carga (ETL) de dados ambientais rurais entre sistemas legados e a nova plataforma.

## 🏗️ Arquitetura

Este componente faz parte do ecossistema RER DSP:

```
rer-dsp-frontend (UI)
    ↓
rer-dsp-backend (API REST)
    ↓
rer-dsp-core (lógica de domínio)
    ↓
rer-dsp-job-data-migration  ← ESTE REPO
rer-dsp-job-geo-file-generation (geoespacial)
```

## 🚀 Setup

```bash
# Clonar
git clone https://github.com/Rural-Environmental-Registry/rer-dsp-job-data-migration.git
cd rer-dsp-job-data-migration

# Instruções de build serão adicionadas conforme desenvolvimento
```

## 📖 Documentação

- [RER — Visão Geral](https://github.com/Rural-Environmental-Registry)
- [SDD (System Design Document)](https://github.com/Rural-Environmental-Registry/core)

## 📜 Licença

Este projeto é licenciado sob a [GNU General Public License v3.0](LICENSE).

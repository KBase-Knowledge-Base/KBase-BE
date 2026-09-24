# KBase AI Chatbot v1 – M9 KBase Guide

**Status:** READY – implementation has not started  
**Parent plan:** `../KBase_AI_Chatbot_v1_Implementation_Plan.md`  
**Depends on:** Completed AI M0–M8; Core v1 remains frozen

## Goal

Implement a stateless authenticated KBase Guide grounded only on the approved Core v1 and AI v1 product specifications. It must never access project documents, project vectors, private conversation history, or unapproved internal documents.

## Approved scope

- package the exact allowlisted Markdown source files in the backend artifact;
- deterministic Guide source hashing and durable Guide reindex lifecycle;
- Guide-only retrieval and strict grounded/no-evidence result;
- stateless REST query with bounded caller-provided USER/ASSISTANT context;
- real PostgreSQL/pgvector and deterministic fake-provider evidence.

## Explicit exclusions

No project-data retrieval, persistent Guide conversation, frontend, streaming, rate guard, OCR/multimodal/XLSX, broker, or real Gemini automated network test.

## Required preflight

Reload AGENTS routing docs, SD-14 through SD-19, M8 completed evidence and current code. Confirm M8's 352-test baseline before modifying code. Run incremental tests and record exact final `mvn -B -ntp clean verify`, Compose config, diff check, OpenAPI and privacy evidence.

## M9 gate

- only canonical allowlisted source files are indexed;
- Guide cannot query project corpus or reveal internal plans;
- documented questions have grounded sources and unsupported questions refuse safely;
- no persistent Guide conversation or provider/network dependency is introduced;
- generated API/DB docs change only if verified source-of-truth requires it.

# OpenCode Studio

A native-inspired OpenCode workspace UI prototype built with Next.js. It brings session chat, terminal, file explorer/ZIP export, browser controls, and five MCP server cards into a compact desktop-style layout.

## Run locally

Requires Node.js 20+.

```sh
npm ci
npm run dev
```

Then open http://localhost:3000.

## Important

This repository currently implements the interactive UI prototype. The terminal, browser, chat agent, and MCP status cards are interface demonstrations—not connections to a local OpenCode server or host shell. A browser-based Next.js app cannot safely provide native terminal/filesystem access on its own; connecting those capabilities needs a trusted local companion/runtime and explicit permissions.

## CI

GitHub Actions runs TypeScript checks and a production Next.js build for pushes and pull requests to `main`.

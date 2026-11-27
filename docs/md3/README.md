# MD3 设计令牌与实现对照（本项目自用）

本目录是「按 M3 规范改 UI」时的查表文档，**不是** M3 官方资料的镜像。

## 来源

| 来源 | 用途 | 核验日期 |
|---|---|---|
| https://m3.material.io/styles/shape/corner-radius-scale | 形状十档 | 2026-11-15（经 lwhhz/Android-MD3-Knowledge `05-Design-Tokens/shapes.json`，其对 material3 1.5.0 `ShapeTokens.kt` 逐值核对） |
| https://m3.material.io/styles/typography/type-scale-tokens | 字阶 15 档 | 同上（对 `TypeScaleTokens.kt` 核对） |
| https://developer.android.com/develop/ui/compose/designsystems/material3 | ColorScheme / dynamic color | 2026-11-14 |
| https://developer.android.com/develop/ui/compose/system/insets | edge-to-edge / insets | 2026-11-14 |

参考知识库：`lwhhz/Android-MD3-Knowledge`（**无 LICENSE**，因此本目录只记录数值与结论并标注来源，不复制其原文/JSON 文件）。

## 文件

- `tokens.md` —— 形状、字阶、组件默认值的规范数值表
- `audit-2026-11-27.md` —— 本项目 Material 风格对齐记录、Monet 取色审计、insets 审计
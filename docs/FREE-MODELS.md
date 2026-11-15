# 免费模型实测报告（2026-11-15）

> 结论摘要：免费池共 14 个 `:free` 模型。**3 个直接剔除**（每日额度耗尽 / 偶发 60 秒首字），**2 个强制思考**的模型改为「不带 reasoning 字段」调用后可用，**默认模型换成实测最快的 Nemotron 3.5 Lightning**，`kilo-auto/free` 因匿名额度退到链尾。

## 一、测试方法

对 `https://ai-gateway.kilo.ai/api/v1` 逐个模型发真实请求，请求体与 App 免费车道一致（`stream: true` + `stream_options.include_usage` + `max_completion_tokens 4096` + 归属头），每项 3 轮，另加翻译 / 指令遵循 / 摘要三类质量抽测。

## 二、逐个结论

| 模型 | 3 轮成功率 | 首字延迟 | 视觉 | 结论 |
|---|---|---|---|---|
| `nvidia/nemotron-3.5-lightning:free` | 3/3 | 599–740 ms | ✕ | ✅ **新默认模型** |
| `nvidia/nemotron-3.5-super-vl:free` | 3/3 | 709–737 ms | ✓ | ✅ 视觉首选（OCR 27 行全中） |
| `baidu/ernie-5.5-flash-preview:free` | 3/3（省略 reasoning） | 1137–2011 ms | ✕ | ✅ 中文最稳，属强制思考 |
| `particle-ai/particle-1:free` | 3/3 | 901–1504 ms | ✓ | ✅ 视觉备选 |
| `dots-studio/dots-3-note-preview:free` | 3/3 | 861–1316 ms | ✓ | ✅ 视觉备选，中文 OCR 最准 |
| `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` | 3/3 | 546–767 ms | ✓ | ✅ 唯一支持音频输入 |
| `reflection/beam-free` | 3/3 | 788–981 ms | ✕ | ✅ 文本兜底 |
| `liquid/lfm-3-1.7b:free` | 3/3（省略 reasoning） | 563–2160 ms | ✕ | ⚠️ 短问答可用，属强制思考 |
| `stealth/glyph-cluster` | 3/3 | 1261–3520 ms | ✕ | ⚠️ 可用但首字偏慢 |
| `cohere/north-mini-code:free` | 3/3 | 579–615 ms | ✕ | ⚠️ 快但答非所问，降到链尾 |
| `openrouter/free` | 3/3 | 646–848 ms | ⚠️ | ⚠️ 文本快，图片必 422 |
| `kilo-auto/free` | 3/3 或 0/10 | 1109–1545 ms | ✓ | ⚠️ 匿名额度常年耗尽，退到链尾 |
| `inclusionai/ling-3.2-flash:free` | **0/3** | — | — | ❌ 剔除：`limit_rpd` 每日额度 |
| `nvidia/nemotron-3-ultra-550b-a55b:free` | 2/3 | 826 ms / **59 200 ms** | ✕ | ❌ 剔除：偶发 60 秒首字 |

关于 `kilo-auto/free`：配额未耗尽时 3/3 正常；连续 10 轮探测时 **10/10 全部 429**，响应体为

```json
{"error":{"code":"PROMOTION_MODEL_LIMIT_REACHED","message":"Sign up for free to continue and explore 500 other models. Takes 2 minutes, no credit card required. Or come back later."}}
```

官方文档写明匿名访问按 **IP 限流 200 请求/小时**（[Authentication](https://kilo.ai/docs/gateway/authentication)）。保留在链尾，不再当默认。

## 三、关键发现：思考字段

把 `reasoning.enabled` 去掉后，多数模型会**自己开始长篇思考**——实测「用一句话解释什么是光」：

| 模型 | 省略字段 | 显式 `enabled:false` |
|---|---|---|
| Nemotron 3.5 Lightning | 正文 25 字 + **思考 2418 字** | 正文 32 字 + 思考 0 字 |
| Nemotron 3.5 Super VL | 正文 39 字 + 思考 822 字 | 正文 38 字 + 思考 0 字 |
| Dots3 Note | 正文 46 字 + 思考 290 字 | 正文 32 字 + 思考 0 字 |

所以**默认必须显式关闭**（否则首字慢、输出额度被思考吃掉）。只有 `freeForcedReasoning` 里的模型例外——它们收到 `enabled=false` 会 400「Reasoning is mandatory」，必须省略字段。

## 四、代码改动

1. 默认模型 `kilo-auto/free` → `nvidia/nemotron-3.5-lightning:free`。
2. 静态清单：剔除 2 个不可用模型；补入 `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`、`stealth/glyph-cluster`、`baidu/ernie-5.5-flash-preview:free`、`liquid/lfm-3-1.7b:free`。
3. `freeFallbackChain`：`lightning → super-vl → ernie → particle-1 → beam → kilo-auto`。
4. `freeTranslateChain`：换掉答非所问的 `north-mini-code`，改用 `lightning → dots-3 → particle-1`。
5. 新增 `freeVisionChain`（`super-vl → dots-3 → particle-1 → openrouter/free`），图片请求不再混入纯文本模型。
6. 新增 `freeModelBlocklist`（远端目录过滤）与 `freeForcedReasoning`（省略 reasoning 字段）。
7. `FreeProvider`：同一模型最多两次尝试；已知强制思考的直接省略字段，其余若是 400「Reasoning is mandatory」则自动去字段重试一次。

## 五、仍值得关注但这次没加的候选

| 模型 | 为什么值得 | 为什么暂缓 |
|---|---|---|
| `z-ai/glm-5:free`、`minimax/minimax-m2.1:free` | 官方文档举例的免费模型 | 当前目录里查不到，按账号/时段下发，写死会再次过期 |
| `humansand/ampersand-v0.1`、`google/lyria-3-*` | 定价 0 | 实测 401「need to sign in」，匿名不可用 |

## 六、已实施的改进（2026-11-15 当天完成）

1. **自检扩成逐模型探测**：`FreeLaneSelfTest` 在原来的「对话 / 翻译 / 朗读」三项之后，追加对降级链、视觉链与默认模型的逐个探测（单项超时 12 秒），结果直接列在设置页。
2. **失败冷却**：新增 `data/FreeModelHealth.kt`。任何模型失败一次就记录，之后 24 小时内优先走别人；成功一次立刻解除冷却。记录存在独立偏好文件 `free_model_health`，不进备份、不碰密钥。
   - 全部模型都在冷却时**退回原链**，不让本地记录把功能变成「静默失效」。
   - 设置页新增「模型健康」卡片：列出冷却中的模型与原因，并提供「清空失败记录」。
3. **额度说明**：免费链路设置页补充「免费池按 IP 限流（约 200 次/小时），高峰时可能提示额度已用完」。

## 七、仍待观察

- 上游若把 `limit_rpd` 改成按账号下发，`inclusionai/ling-3.2` 可能重新可用；冷却机制会在 24 小时后自动重试它，无需改代码。
- 若 Kilo 修改匿名额度策略（现在的 200 次/小时），需要重新评估是否保留自动路由在链尾。

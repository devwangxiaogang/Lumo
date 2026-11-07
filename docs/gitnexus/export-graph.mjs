// Export the graph from the local GitNexus HTTP service; no source text is exported.
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const out = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(out, '../..');
const server = process.argv[2] || 'http://127.0.0.1:4747';
const response = await fetch(`${server}/api/graph?repo=${encodeURIComponent(repo)}`);
if (!response.ok) throw new Error(`GitNexus graph request failed: ${response.status} ${await response.text()}`);
const graph = await response.json();
if (!graph.nodes?.length || !graph.relationships?.length) throw new Error('GitNexus returned an empty graph');
const nodes = new Map(graph.nodes.map(n => [n.id, n]));
const dangling = graph.relationships.filter(r => !nodes.has(r.sourceId) || !nodes.has(r.targetId));
if (dangling.length) throw new Error(`Graph has ${dangling.length} dangling relationships`);
await fs.writeFile(path.join(out, 'code-graph.json'), JSON.stringify(graph, null, 2));
const esc = s => String(s ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const keys = [['label','node','string'],['name','node','string'],['filePath','node','string'],['startLine','node','int'],['type','edge','string'],['confidence','edge','double'],['reason','edge','string'],['step','edge','int']];
const xml = ['<?xml version="1.0" encoding="UTF-8"?>', '<graphml xmlns="http://graphml.graphdrawing.org/xmlns">', ...keys.map(([id,scope,type]) => `<key id="${id}" for="${scope}" attr.name="${id}" attr.type="${type}"/>`), '<graph id="Lumo" edgedefault="directed">'];
for (const n of graph.nodes) {
  const p = n.properties;
  xml.push(`<node id="${esc(n.id)}"><data key="label">${esc(n.label)}</data><data key="name">${esc(p.name)}</data><data key="filePath">${esc(p.filePath)}</data>${p.startLine != null ? `<data key="startLine">${p.startLine}</data>` : ''}</node>`);
}
graph.relationships.forEach((r,i) => {
  xml.push(`<edge id="e${i}" source="${esc(r.sourceId)}" target="${esc(r.targetId)}">${['type','confidence','reason','step'].filter(k => r[k] != null).map(k => `<data key="${k}">${esc(r[k])}</data>`).join('')}</edge>`);
});
xml.push('</graph></graphml>');
await fs.writeFile(path.join(out, 'code-graph.graphml'), xml.join('\n'));
const count = (items, key) => Object.entries(items.reduce((a,x) => { const k=key(x); a[k]=(a[k]||0)+1; return a; }, {})).sort((a,b)=>b[1]-a[1]);
const moduleOf = n => {
  const f = n?.properties.filePath?.replaceAll('\\','/');
  if (!f || !/\.kt$/.test(f) || !f.includes('/src/main/')) return null;
  const marker = '/com/yuguang/lumo/';
  if (!f.includes(marker)) return null;
  const parts = f.split(marker)[1].split('/');
  return parts.length === 1 ? 'app-entry' : parts.slice(0,-1).join('/');
};
const modules = [...new Set(graph.nodes.map(moduleOf).filter(Boolean))].sort();
const modIds = new Map(modules.map((m,i)=>[m,`m${i}`]));
const dependencies = new Map();
const relationshipTypes = new Set(['CALLS','IMPORTS','EXTENDS','IMPLEMENTS','INJECTS']);
for (const r of graph.relationships) {
  if (!relationshipTypes.has(r.type)) continue;
  const a = moduleOf(nodes.get(r.sourceId)), b = moduleOf(nodes.get(r.targetId));
  if (!a || !b || a === b) continue;
  const key = `${a}|${b}`;
  dependencies.set(key,(dependencies.get(key)||0)+1);
}
const mermaid = ['flowchart LR', '  %% Aggregated actual GitNexus relations between Kotlin production packages.'];
for (const m of modules) mermaid.push(`  ${modIds.get(m)}["${m}"]`);
for (const [key,total] of [...dependencies].sort((a,b)=>b[1]-a[1])) {
  const [a,b] = key.split('|');
  mermaid.push(`  ${modIds.get(a)} -->|"${total}"| ${modIds.get(b)}`);
}
await fs.writeFile(path.join(out, 'modules.mmd'), mermaid.join('\n')+'\n');
const indegree = new Map();
for (const r of graph.relationships.filter(r=>r.type==='CALLS')) indegree.set(r.targetId,(indegree.get(r.targetId)||0)+1);
const hubs = [...indegree].sort((a,b)=>b[1]-a[1]).slice(0,15).map(([id,n])=>({node:nodes.get(id),n}));
const communities = graph.nodes.filter(n=>n.label==='Community').sort((a,b)=>(b.properties.symbolCount||0)-(a.properties.symbolCount||0));
const processes = graph.nodes.filter(n=>n.label==='Process').sort((a,b)=>(b.properties.stepCount||0)-(a.properties.stepCount||0));
const meta = JSON.parse(await fs.readFile(path.join(repo,'.gitnexus','meta.json'),'utf8'));
const link = n => n.properties.filePath ? `[${n.properties.name}](../../${n.properties.filePath.replaceAll('\\','/')}${n.properties.startLine ? '#L'+n.properties.startLine : ''})` : n.properties.name;
const lines = ['# 语光 Lumo · GitNexus 代码图谱', '', `生成时间：${new Date().toLocaleString('sv-SE',{timeZone:'Asia/Shanghai'})}（Asia/Shanghai）`, '', `索引提交：\`${meta.lastCommit || meta.commit || '见 .gitnexus/meta.json'}\`。数据来自 GitNexus 本地索引，未导出源码正文。`, '', `图谱共 **${graph.nodes.length} 个节点、${graph.relationships.length} 条关系、${communities.length} 个聚类、${processes.length} 条静态执行流**。文件、目录、文档节点也包含在全量计数中，不能将节点总数视作代码符号数。`, '', '## 打开图谱', '', `- [GitNexus 交互图谱](${server}/?project=Lumo&repo=${encodeURIComponent(repo)})：搜索符号、查看依赖和执行流。需要本机 GitNexus 服务运行。`, '- [全量 JSON](code-graph.json)：GitNexus 原始节点和关系，保留路径、行号、置信度与分析原因等字段。', '- [GraphML](code-graph.graphml)：可导入 Gephi、Cytoscape、yEd 等图工具。', '- [模块依赖 Mermaid 源文件](modules.mmd)：只汇总主代码 Kotlin 包之间的关系。', '', '## 主代码模块关系', '', '箭头由依赖方指向被依赖方；数字为跨包 CALLS / IMPORTS / EXTENDS / IMPLEMENTS / INJECTS 关系数，包含不同类型的关系，不能解读为运行时调用次数。同一文件中不重复绘制类和函数节点。', '', '```mermaid', ...mermaid, '```', '', '## 节点类型', '', '| 类型 | 数量 |','|---|---:|',...count(graph.nodes,n=>n.label).map(([k,v])=>`| ${k} | ${v} |`), '', '## 关系类型', '', '| 关系 | 数量 |','|---|---:|',...count(graph.relationships,r=>r.type).map(([k,v])=>`| ${k} | ${v} |`), '', '## 静态调用入度较高的符号', '', '入度仅代表索引中识别到的 CALLS 关系数量，不代表运行频次，也不保证覆盖所有调用者。', '', ...hubs.map(({node,n})=>`- ${link(node)}：${n} 条入向调用关系；\`${node.properties.filePath}:${node.properties.startLine ?? ''}\``), '', '## GitNexus 自动聚类', '', '聚类名称由算法启发式生成，可能混合多个业务职责。', '', ...communities.slice(0,20).map(n=>`- **${n.properties.heuristicLabel || n.properties.name}**：${n.properties.symbolCount ?? '?'} 个符号`), '', '## 较长的静态执行流', '', '这些是分析器沿静态关系识别出的候选链路，不是运行时采样记录。', '', ...processes.slice(0,15).map(n=>`- **${n.properties.heuristicLabel || n.properties.name}**：${n.properties.stepCount ?? '?'} 步`), '', '## 覆盖范围与限制', '', '- 全量导出覆盖 GitNexus 按忽略规则纳入的项目文件；构建产物、Gradle 缓存等不作为业务源码图谱。', '- 模块图只统计 `app/src/main` 下 `com.yuguang.lumo` 包内的 Kotlin 生产代码；测试、资源和文档仍可在全量图谱中检查。', '- 静态分析对 Hilt/Room 生成代码、接口动态分派、Compose 回调、协程和外部 Android SDK 的解析有限；缺少连线不能证明不存在依赖。', '- 本次未启用嵌入向量、CFG/PDG 或运行时追踪；聚类和执行链均为 GitNexus 静态分析结果。', '', '## 更新', '', '在项目根目录重新建立索引，启动本机服务，再运行此目录的导出脚本：', '', '```powershell', 'npx gitnexus analyze . --index-only --name Lumo', 'npx gitnexus serve --host 127.0.0.1 --port 4747', '# 在另一个终端运行', 'node docs/gitnexus/export-graph.mjs', '```', '', '若当前环境未将 Node/npm 加入 PATH，请使用本机已安装的 Node 与 GitNexus CLI 的完整路径。'];
await fs.writeFile(path.join(out,'README.md'),lines.join('\n')+'\n');
console.log(JSON.stringify({nodes:graph.nodes.length,relationships:graph.relationships.length,communities:communities.length,processes:processes.length,modules:modules.length,moduleRelations:dependencies.size,nodeTypes:count(graph.nodes,n=>n.label),relationshipTypes:count(graph.relationships,r=>r.type)}));

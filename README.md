# bank-risk-agent · 银行运营监督与风险预警 Agent

用真实的银行风控业务场景（制度问答 + 预警工单处置），把「本地大模型 + RAG + Tool Calling + MCP」这条链路完整跑通的个人预研项目。

**定位说明（先说清楚，避免误读）**：这是**个人 AI 预研项目**，语料是我自己按公开监管文件口径改写的示例语料，不涉及任何银行内部文件，也不是某家银行的生产系统。技术选型和场景设计来自我做过的银行风控项目积累的业务理解。

---

## 零、在 IDEA 里打开（最快路径）

1. IDEA 菜单 `File → Open`，选目录 `/Users/cheying/IdeaProjects/bank-risk-agent`（选到 `pom.xml` 那一层）
2. 弹「Trust Project」点 Trust，Maven 会自动 import（第一次要下依赖，约 1-2 分钟）
3. `Project Structure → SDK` 选 IDEA 自带的 JDK（我验证用的是 JBR 25），或本地 21+
4. 跑 `com.riskagent.RiskAgentApplication` 的 main 方法，浏览器开 `http://localhost:8080`

> 注意：从 IDEA 里直接 run 是 8080；用命令行 `./scripts/dev.sh` 也是 8080。

## 一、这个项目解决什么问题

银行运营监督岗每天面对两类重复劳动：

1. **查制度**：新人问"夜间异常时段交易几笔算预警、处置动作是什么"，要翻几十页制度文件。
2. **处置预警**：一条预警工单进来，要查规则条款 → 查同类历史案例 → 查账户画像 → 给结论 → 分派岗位，五步全靠人工。

本项目用 Agent 把这两件事自动化，并且死守两条金融场景的底线：**数据不出域**（本地模型，语料不上云）、**结论可追溯**（每条结论带条款引用，查不到就说查不到）。

## 二、架构

```
                      ┌──────────────────────────────────────────┐
  用户/Demo 页  ───►  │  AgentController                         │
                      │  /api/qa  /api/alert/dispose  /api/eval  │
                      └───────┬───────────────┬──────────────────┘
                              │               │
                    ┌─────────▼──────┐  ┌─────▼──────────────────┐
                    │ RagService     │  │ DispositionAgent       │
                    │ 制度问答        │  │ 工单处置（Agent）       │
                    │ 检索→拼上下文→生成│  │ 自主调用工具→结构化输出  │
                    └─────┬──────────┘  └─────┬──────────────────┘
                          │                   │ Tool Calling
              ┌───────────▼───────────┐  ┌────▼─────────────────────┐
              │ KnowledgeBaseService  │  │ RiskToolbox              │
              │ 混合检索（关键词+向量） │  │ ruleLookup / caseRecall  │
              └───────┬───────┬───────┘  │ accountProfile / dispatch│
                      │       │          └──────────────────────────┘
              ┌───────▼──┐ ┌──▼─────────┐
              │KeywordIndex│ │VectorStore │──► Ollama(本地)
              │ bigram+idf │ │(里程碑2换ES)│    qwen2.5:3b  数据不出域
              └────────────┘ └────────────┘    bge-m3 embedding
```

## 三、快速开始

### 0. 环境说明

本机没有独立安装 JDK17+ / Maven，项目脚本**直接复用 IntelliJ IDEA 自带的 JDK25（JBR）和 Maven 3.9**，省掉 200MB 下载。装了正式 JDK21 后改 `JAVA_HOME` 即可。

### 1. 装 Ollama 并拉模型（只有这一步需要联网）

```bash
brew install ollama        # 或 https://ollama.com/download
ollama serve &             # 常驻
./scripts/pull-model.sh    # 默认拉 qwen2.5:3b（≈1.9GB）+ bge-m3（≈1.2GB）
```

想效果好一档：`./scripts/pull-model.sh qwen2.5:7b bge-m3`（≈4.7GB，16G 内存推荐）。

### 2. 启动

```bash
./scripts/dev.sh
# 或已打包： java -jar target/bank-risk-agent-0.1.0.jar
```

浏览器打开 `http://localhost:8080`，就是演示页（制度问答 / 工单处置 / 评测三个面板）。

> 注意：如果在 WorkBuddy 这类容器里启动，环境变量 `SERVER__PORT` 会覆盖端口，实际端口看启动日志里的 `Tomcat started on port xxxx`。自己终端里跑则是 8080。

### 3. 只看检索、不装模型也能验

模型没起来时系统**自动降级**，问答返回命中的制度原文并标注 `RETRIEVAL_ONLY`，工单处置返回"待人工判定"——不会假装给出结论。这意味着你可以先验检索链路，再决定要不要花流量拉模型。

## 四、当前已验证的状态（可复现）

| 项目 | 结果 | 怎么验 |
|---|---|---|
| 编译 | BUILD SUCCESS（JDK25 + Spring Boot 3.5.10 + Spring AI 1.1.2） | `mvn -DskipTests package` |
| 启动 | 1.5 秒内启动，装载 5 篇语料 / 30 个切片 | 看启动日志 |
| 关键词检索 | 10 条自建用例检索命中 10/10 | `curl "localhost:8080/api/eval/run?withLlm=false"` |
| 模型未装时降级 | 问答/处置均秒级返回降级结果，不卡死 | 上面同一条接口 |
| 大模型生成 | **未验证**（本机未装 Ollama，未拉模型） | 装完模型后 `?withLlm=true` |

⚠️ 诚实提示：10/10 是在**自建语料 + 题库高度对应**的情况下用关键词检索拿到的，只能证明链路的召回通，**不能当成效果指标**。真实可信的数字要等接入 ES 向量检索 + 跨表述改写测试后再补。

## 五、五个关键技术决策（面试重点）

1. **标题感知切分，不用固定长度硬切**。制度文档的语义边界在条款标题处，硬切会把"第八条"的适用条件和结论切到两个片段，检索出来是半句话。`ChunkingService` 先按 `#` 标题分段，再按段落累积到 420 字，并在切口保留 80 字重叠。
2. **混合检索（关键词 + 向量），不是纯向量**。制度问答里"第八条""4.5 万""十个工作日"这类精确词，向量容易被语义相近的表述稀释；关键词路（bigram + idf）补上这一块，还能在 embedding 服务挂掉时兜底。
3. **引用由代码回填，不让模型自己编**。模型只负责在正文写 `[n]`，来源文件名、章节、得分由系统填。金融场景里"引用"必须可信，否则整个答案不可用。
4. **模型探针 + 优雅降级**。Spring AI 默认重试 10 次、间隔递增，Ollama 没启动时一次问答要卡两分钟。加了 700ms 探针（`ModelHealth`）并把重试关到 1 次，不可用就秒级降级。
5. **强制结构化输出**。"给一句建议"在银行落不了地，必须能落表、能分派、能审计，所以 Agent 最后一步固定输出 `风险等级 / 结论 / 依据 / 处置动作 / 分派岗位 / 引用`，解析失败有两层兜底。

## 六、MCP：同一批工具，跨客户端复用

`RiskToolbox` 的 4 个 `@Tool` 方法通过 `ToolCallbackProvider` 同时暴露成标准 MCP 工具（`McpServerConfig`）。
启动后本项目的 Agent、Claude Desktop、Cursor 调的是**同一份工具定义**，接真实风控接口时只改方法体，所有调用方同时生效。

Claude Desktop 接入配置（`claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "bank-risk": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

> 注意：Spring AI 1.1 新增了 `@McpTool` 注解扫描，本项目把它关掉（`spring.ai.mcp.server.annotation-scanner.enabled=false`），
> 统一走 `ToolCallbackProvider`，避免同一套工具要写两遍定义。

## 七、切换到 Elasticsearch（里程碑 2 的开关）

```bash
docker compose up -d elasticsearch
java -jar target/bank-risk-agent-0.1.0.jar --spring.profiles.active=es
```

上层检索代码零改动。**坑已经踩过了**：ES 向量库自动配置的默认条件是 `spring.ai.vectorstore.type=elasticsearch`（`matchIfMissing=true`），
只要 classpath 上有 ES 依赖就会尝试连库、连不上直接启动失败；所以默认配置里显式写了 `spring.ai.vectorstore.type: none`，
es profile 里再改回 `elasticsearch`。

## 八、目录结构

```
src/main/java/com/riskagent/
  config/     AgentProperties（可调参数） AiConfig（ChatClient/提示词） ModelHealth（模型探针）
  knowledge/  ChunkingService 切分  KeywordIndex 关键词检索  KnowledgeBaseService 混合检索  VectorStoreConfig
  rag/        RagService（检索→上下文→生成→引用回填）
  agent/      RiskToolbox（4 个 @Tool） DispositionAgent（工具调用 + 结构化输出）
  eval/       EvalService（10 条用例，检索/答案命中率）
  api/        AgentController
  domain/     AlertTicket  Disposition  Citation
src/main/resources/
  kb/         5 篇示例语料（制度/细则/监测规则/分派流程/案例库）
  prompts/    qa-system.md  dispose-system.md
  eval/       qa-gold.json
  static/     index.html（演示页）
docker-compose.yml   里程碑 2 用：Elasticsearch 8.15 + Kibana
scripts/             dev.sh  pull-model.sh  eval.sh
docs/                面试讲述.md  路线图.md
```

## 九、里程碑

- **M1 跑通链路**（已完成）：切分 → 检索 → 生成 → 引用，降级可用，10 条用例评测。
- **M2 换 Elasticsearch**：`VectorStore` 换成 `spring-ai-starter-vector-store-elasticsearch`，上层代码零改动；补 ES mapping、kNN 检索、Rerank。
- **M3 MCP + 多 Agent 编排**：把这 4 个工具封装成 MCP Server（`spring-ai-starter-mcp-server`），让 Claude/Cursor 也能调用；用 Spring AI Alibaba Graph 做「意图识别 → 条款检索 → 案例类比 → 处置生成」的 DAG 编排，加入人工确认节点。
- **M4 评测与调优**：扩到 40 条用例，加入"换个说法问同一件事"的改写集，对比纯向量 / 纯关键词 / 混合三组数字，把参数调到有据可依。
- **M5 工程化与开源**：Docker 镜像、K8s（MiniKube）部署、GitHub 开源、在线 Demo。

## 十、已知限制

- 只有 5 篇示例语料、30 个切片，规模不足以暴露长文档检索的真实问题（多跳、跨文档冲突）。
- 工具返回的是代码里的示例数据，未接真实风控系统接口（真实接入在 M3 做 MCP）。
- 3B 模型在结构化输出上偶尔会漏字段，解析兜底能兜住但信息会缺失；这是换 7B/14B 最直接的理由。
- 评测集自建、样本小，指标只能内部对比，不能对外宣称准确率。
- MCP 端点在本机沙箱环境里被代理拦截（`/mcp`、`/sse` 返回 401/502），暂用「装配测试断言 4 个工具已注册」代偿；
  自己终端里跑应该正常，M3 验收时用 Claude Desktop 实连一次截图留证。
- `mvn package` 在部分沙箱环境会报 `Unable to rename ... .jar.original`，删掉 `target/*.original` 重跑即可（构建机权限问题，不是代码问题）。
- MCP 端点在本机沙箱环境里被代理拦截（`/mcp`、`/sse` 返回 401/502），暂用「装配测试断言 4 个工具已注册」代偿；
  自己终端里跑应该正常，M3 验收时用 Claude Desktop 实连一次截图留证。
- `mvn package` 在部分沙箱环境会报 `Unable to rename ... .jar.original`，删掉 `target/*.original` 重跑即可（构建机权限问题，不是代码问题）。

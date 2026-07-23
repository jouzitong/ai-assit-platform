# RAGFlow 部署方案与接口操作文档

> 适用场景：你要创建多个“大分类/知识库”，例如：
>
> - 常见问题文档集合
> - 数据源文档集合
> - 组件集合
> - 指标口径集合
> - SQL 规则集合
>
> 每个大分类支持独立的切片方案、向量化模型、文档同步、检索、重排。

---

https://element-plus.org/en-US/component/tree

## 1. 结论

RAGFlow 中的 **Dataset** 就可以理解为你的“大分类/知识库”。

推荐映射：

| 你的业务概念 | RAGFlow 概念 |
|---|---|
| 大分类 / 知识库 | Dataset |
| 多文档集合 | Dataset 下的 Documents |
| 文档切片 | Chunks |
| 切片配置 | Dataset 的 `chunk_method` + `parser_config` |
| 向量化模型 | Dataset 的 `embedding_model` |
| 搜索 / 检索 | `/api/v1/retrieval` |
| 重排 | `rerank_id` 或 Java 服务层外部 reranker |

RAGFlow 比 Meilisearch 更适合你这个需求，因为它原生支持：

```text
创建 Dataset
配置切片方案
配置 embedding 模型
上传多个文档
解析文档生成 chunks
管理 chunks
按 dataset_ids 检索
设置向量相似度权重
设置 rerank_id
```

---

## 2. 官方资料

- GitHub：https://github.com/infiniflow/ragflow
- 官方文档：https://ragflow.io/docs/
- HTTP API：https://ragflow.io/docs/http_api_reference
- Memory 使用说明：https://ragflow.io/docs/dev/use_memory
- Dataset 配置说明：https://ragflow.io/docs/configure_knowledge_base

本文档基于 RAGFlow 官方文档 v0.26.4 口径整理。

---

## 3. 部署方案

### 3.1 服务器要求

官方建议：

```text
CPU: >= 4 cores, x86
RAM: >= 16 GB
Disk: >= 50 GB
Docker: >= 24.0.0
Docker Compose: >= v2.26.1
```

注意：

```text
RAGFlow 官方 Docker 镜像主要支持 x86 CPU 和 Nvidia GPU。
ARM64 平台可以运行，但官方不维护 ARM Docker 镜像，需要自行构建。
```

---

## 4. 一键部署脚本

保存为：

```bash
install-ragflow.sh
```

内容：

```bash
#!/usr/bin/env bash
set -euo pipefail

RAGFLOW_VERSION="${RAGFLOW_VERSION:-v0.26.4}"
INSTALL_DIR="${INSTALL_DIR:-/opt/ragflow}"

echo "==> Checking docker..."
docker --version
docker compose version

echo "==> Set vm.max_map_count..."
if [[ "$(uname -s)" == "Linux" ]]; then
  sudo sysctl -w vm.max_map_count=262144
  if ! grep -q "vm.max_map_count=262144" /etc/sysctl.conf 2>/dev/null; then
    echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
  fi
else
  echo "Non-Linux system detected. If using Docker Desktop, please manually ensure vm.max_map_count >= 262144."
fi

echo "==> Preparing install dir: ${INSTALL_DIR}"
sudo mkdir -p "${INSTALL_DIR}"
sudo chown -R "$USER":"$USER" "${INSTALL_DIR}"

if [[ ! -d "${INSTALL_DIR}/.git" ]]; then
  git clone https://github.com/infiniflow/ragflow.git "${INSTALL_DIR}"
fi

cd "${INSTALL_DIR}"

echo "==> Checkout RAGFlow version: ${RAGFLOW_VERSION}"
git fetch --tags
git checkout -f "${RAGFLOW_VERSION}"

cd docker

echo "==> Start RAGFlow..."
docker compose -f docker-compose.yml up -d

echo "==> RAGFlow started."
echo "Open: http://<YOUR_SERVER_IP>"
echo ""
echo "Check logs:"
echo "cd ${INSTALL_DIR}/docker && docker logs -f docker-ragflow-cpu-1"
```

执行：

```bash
chmod +x install-ragflow.sh
sudo ./install-ragflow.sh
```

默认访问：

```text
http://服务器IP
```

查看日志：

```bash
cd /opt/ragflow/docker
docker logs -f docker-ragflow-cpu-1
```

停止：

```bash
cd /opt/ragflow/docker
docker compose -f docker-compose.yml down
```

重启：

```bash
cd /opt/ragflow/docker
docker compose -f docker-compose.yml up -d
```

---

## 5. API 调用准备

### 5.1 获取 API Key

在 RAGFlow 页面中创建 API Key，然后 Java 服务端使用：

```http
Authorization: Bearer <YOUR_API_KEY>
```

### 5.2 统一环境变量

```bash
export RAGFLOW_BASE_URL="http://127.0.0.1"
export RAGFLOW_API_KEY="你的_API_KEY"
```

---

# 6. Dataset / 大分类接口

## 6.1 创建大分类 / 知识库

接口：

```http
POST /api/v1/datasets
```

说明：

```text
这个接口就是你要的“创建大分类”。
创建时可以配置：
- name
- description
- embedding_model
- permission
- chunk_method
- parser_config
- parse_type
- pipeline_id
```

注意：

```text
chunk_method + parser_config 用于内置切片方式。
parse_type + pipeline_id 用于自定义 ingestion pipeline。

两组不要混用。
如果使用 pipeline_id，不要传 chunk_method 和 parser_config。
```

---

### 6.1.1 创建 FAQ 知识库

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "name": "kb_faq",
    "description": "常见问题文档集合",
    "permission": "team",
    "embedding_model": "BAAI/bge-large-zh-v1.5@BAAI",
    "chunk_method": "naive",
    "parser_config": {
      "chunk_token_num": 512,
      "delimiter": "\n!?;。；！？",
      "layout_recognize": "DeepDOC",
      "auto_keywords": 3,
      "auto_questions": 3,
      "html4excel": false,
      "raptor": {
        "use_raptor": false
      },
      "graphrag": {
        "use_graphrag": false
      },
      "parent_child": {
        "use_parent_child": false,
        "children_delimiter": "\n"
      }
    }
  }'
```

---

### 6.1.2 创建数据源文档知识库

适合存：

```text
表结构说明
字段解释
指标口径
数据源说明
SQL 生成规则
```

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "name": "kb_datasource",
    "description": "数据源、表结构、字段解释、指标口径文档集合",
    "permission": "team",
    "embedding_model": "BAAI/bge-large-zh-v1.5@BAAI",
    "chunk_method": "naive",
    "parser_config": {
      "chunk_token_num": 768,
      "delimiter": "\n\n\n",
      "layout_recognize": "DeepDOC",
      "auto_keywords": 5,
      "auto_questions": 2,
      "html4excel": true,
      "parent_child": {
        "use_parent_child": true,
        "children_delimiter": "\n"
      },
      "raptor": {
        "use_raptor": false
      }
    }
  }'
```

---

### 6.1.3 创建组件知识库

适合存：

```text
组件 schema
render JSON
ECharts 配置
画布组件说明
组件属性解释
```

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "name": "kb_components",
    "description": "画布组件、render JSON、ECharts 配置知识库",
    "permission": "team",
    "embedding_model": "BAAI/bge-large-zh-v1.5@BAAI",
    "chunk_method": "naive",
    "parser_config": {
      "chunk_token_num": 1024,
      "delimiter": "\n\n",
      "layout_recognize": "DeepDOC",
      "auto_keywords": 5,
      "auto_questions": 5,
      "html4excel": false,
      "parent_child": {
        "use_parent_child": true,
        "children_delimiter": "\n"
      }
    }
  }'
```

---

## 6.2 切片方法说明

常见 `chunk_method`：

| chunk_method | 说明 | 适合场景 |
|---|---|---|
| `naive` | 通用切片 | Markdown、TXT、DOCX、普通 PDF |
| `qa` | Q&A 切片 | FAQ、问答表 |
| `table` | 表格切片 | Excel、CSV、结构化表格 |
| `paper` | 论文切片 | 论文 PDF |
| `book` | 图书切片 | 长文档、章节结构 |
| `laws` | 法规切片 | 法律法规、制度文档 |
| `manual` | 手册切片 | PDF 手册 |
| `presentation` | 演示文稿切片 | PPT、PDF 演示文稿 |
| `picture` | 图片解析 | 图片文档 |
| `one` | 整文档作为一个 chunk | 极短文档 |
| `tag` | 标签集 | 给其他 Dataset 使用标签集 |

推荐：

```text
FAQ：qa 或 naive
数据源文档：naive
组件文档：naive
Excel/CSV：table
长 PDF 手册：manual 或 book
PPT：presentation
图片：picture
```

---

## 6.3 更新大分类配置

接口：

```http
PUT /api/v1/datasets/{dataset_id}
```

示例：

```bash
curl --request PUT \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "name": "kb_faq_updated",
    "description": "更新后的 FAQ 知识库",
    "pagerank": 10,
    "parser_config": {
      "chunk_token_num": 768,
      "delimiter": "\n\n",
      "layout_recognize": "DeepDOC"
    }
  }'
```

注意：

```text
embedding_model 在已有 chunks 后通常不建议修改。
如果要更换 embedding_model，建议：
1. 新建 Dataset
2. 重新上传/解析文档
3. 切换业务侧 datasetId
```

---

## 6.4 查询大分类列表

接口：

```http
GET /api/v1/datasets
```

示例：

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets?page=1&page_size=30&include_parsing_status=true" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

按名称查询：

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets?name=kb_faq" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

---

## 6.5 删除大分类

接口：

```http
DELETE /api/v1/datasets
```

删除指定 Dataset：

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "ids": ["DATASET_ID_1", "DATASET_ID_2"]
  }'
```

删除当前用户所有 Dataset：

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "delete_all": true
  }'
```

---

# 7. 文档同步接口

## 7.1 上传多个本地文档

接口：

```http
POST /api/v1/datasets/{dataset_id}/documents
```

上传多个文件：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --form "file=@./FAQ.md" \
  --form "file=@./使用手册.pdf" \
  --form "file=@./指标口径.xlsx"
```

说明：

```text
上传后通常还没有完成解析。
需要再调用 Parse documents 接口。
```

---

## 7.2 从网页同步文档

接口：

```http
POST /api/v1/datasets/{dataset_id}/documents?type=web
```

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents?type=web" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --form "name=产品文档首页" \
  --form "url=https://example.com/docs"
```

---

## 7.3 创建空文档

接口：

```http
POST /api/v1/datasets/{dataset_id}/documents?type=empty
```

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents?type=empty" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "name": "manual_input.md"
  }'
```

---

## 7.4 解析文档生成 chunks

接口：

```http
POST /api/v1/datasets/{dataset_id}/chunks
```

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/chunks" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "document_ids": ["DOCUMENT_ID_1", "DOCUMENT_ID_2"]
  }'
```

说明：

```text
这个接口会使用 Dataset 的内置 chunking pipeline。
如果 Dataset 配置的是 ingestion pipeline，需要用 /api/v1/documents/ingest。
```

---

## 7.5 使用 ingestion pipeline 解析文档

接口：

```http
POST /api/v1/documents/ingest
```

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/documents/ingest" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "doc_ids": ["DOCUMENT_ID_1"],
    "run": "1",
    "delete": true
  }'
```

字段说明：

| 字段 | 说明 |
|---|---|
| `doc_ids` | 文档 ID 列表 |
| `run` | `"1"` 开始 ingestion，`"2"` 取消 ingestion |
| `delete` | 是否删除已有任务和 chunks 后重跑 |

---

## 7.6 更新文档配置

接口：

```http
PUT /api/v1/datasets/{dataset_id}/documents/{document_id}
```

示例：

```bash
curl --request PUT \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "name": "新的文档名称.md",
    "chunk_method": "naive",
    "parser_config": {
      "chunk_token_num": 512,
      "delimiter": "\n\n"
    }
  }'
```

推荐文档内容更新流程：

```text
1. 删除旧文档
2. 上传新文件
3. 重新解析
```

这样比直接改 chunk 更稳定。

---

## 7.7 删除文档

接口：

```http
DELETE /api/v1/datasets/{dataset_id}/documents
```

删除指定文档：

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "ids": ["DOCUMENT_ID_1", "DOCUMENT_ID_2"]
  }'
```

删除 Dataset 下全部文档：

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "delete_all": true
  }'
```

---

# 8. Chunk 操作接口

## 8.1 手动新增 chunk

接口：

```http
POST /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks
```

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}/chunks" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "content": "销售额 = 已支付订单金额 - 退款金额 - 优惠抵扣金额。",
    "important_keywords": ["销售额", "GMV", "退款"],
    "tag_kwd": ["指标", "交易"],
    "questions": [
      "销售额怎么算？",
      "GMV 和销售额有什么区别？"
    ]
  }'
```

---

## 8.2 查询 chunk 列表

接口：

```http
GET /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks
```

示例：

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}/chunks?page=1&page_size=30&keywords=销售额" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

---

## 8.3 获取单个 chunk

接口：

```http
GET /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks/{chunk_id}
```

示例：

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}/chunks/${CHUNK_ID}" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

---

## 8.4 更新 chunk

接口：

```http
PATCH /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks/{chunk_id}
```

示例：

```bash
curl --request PATCH \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}/chunks/${CHUNK_ID}" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "content": "销售额 = 已支付订单金额 - 退款金额。",
    "important_keywords": ["销售额", "退款"],
    "questions": ["销售额怎么算？"],
    "available": true
  }'
```

---

## 8.5 启用 / 禁用 chunk

接口：

```http
PATCH /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks
```

示例：

```bash
curl --request PATCH \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}/chunks" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "chunk_ids": ["CHUNK_ID_1", "CHUNK_ID_2"],
    "available": false
  }'
```

---

## 8.6 删除 chunks

接口：

```http
DELETE /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks
```

删除指定 chunks：

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}/chunks" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "chunk_ids": ["CHUNK_ID_1", "CHUNK_ID_2"]
  }'
```

删除该文档所有 chunks：

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/datasets/${DATASET_ID}/documents/${DOCUMENT_ID}/chunks" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "delete_all": true
  }'
```

---

# 9. 检索 / 搜索 / 向量化 / 重排

## 9.1 基于单个大分类搜索

接口：

```http
POST /api/v1/retrieval
```

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/retrieval" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "question": "销售额怎么算？",
    "dataset_ids": ["DATASET_ID_FAQ"],
    "page": 1,
    "page_size": 10,
    "similarity_threshold": 0.2,
    "vector_similarity_weight": 0.3,
    "top_k": 1024,
    "keyword": true,
    "highlight": true
  }'
```

---

## 9.2 基于多个大分类搜索

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/retrieval" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "question": "销售额字段应该用哪个表？",
    "dataset_ids": [
      "DATASET_ID_DATASOURCE",
      "DATASET_ID_METRIC",
      "DATASET_ID_SQL_RULE"
    ],
    "page": 1,
    "page_size": 10,
    "similarity_threshold": 0.2,
    "vector_similarity_weight": 0.4,
    "top_k": 1024,
    "keyword": true,
    "highlight": true
  }'
```

---

## 9.3 只在指定文档内搜索

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/retrieval" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "question": "订单表字段说明",
    "dataset_ids": ["DATASET_ID_DATASOURCE"],
    "document_ids": ["DOCUMENT_ID_1"],
    "page": 1,
    "page_size": 10
  }'
```

注意：

```text
如果使用 document_ids，确保这些文档使用相同 embedding_model。
```

---

## 9.4 向量权重控制

字段：

```json
{
  "vector_similarity_weight": 0.3
}
```

含义：

```text
vector_similarity_weight = x
向量相似度权重 = x
关键词/文本相似度权重 = 1 - x
```

推荐：

| 场景 | vector_similarity_weight |
|---|---:|
| 精准关键词、字段名、表名搜索 | 0.2 ~ 0.4 |
| 普通知识库问答 | 0.3 ~ 0.6 |
| 语义表达差异大 | 0.6 ~ 0.8 |
| 更偏向量召回 | 0.8+ |

---

## 9.5 重排 rerank

RAGFlow 检索接口支持：

```json
{
  "rerank_id": "RERANK_MODEL_ID"
}
```

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/retrieval" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "question": "销售额怎么算？",
    "dataset_ids": ["DATASET_ID_METRIC"],
    "page": 1,
    "page_size": 10,
    "similarity_threshold": 0.2,
    "vector_similarity_weight": 0.5,
    "top_k": 1024,
    "rerank_id": "RERANK_MODEL_ID",
    "keyword": true,
    "highlight": true
  }'
```

推荐流程：

```text
第一阶段：RAGFlow retrieval 召回 top_k
第二阶段：使用 rerank_id 重排
第三阶段：返回 page_size 条结果
```

如果你需要更强业务重排，可以在 Java 服务层做二次 rerank：

```text
RAGFlow retrieval top 50
        ↓
Java 调外部 reranker
        ↓
按 rerankScore 排序
        ↓
返回 top 10
```

---

## 9.6 元数据过滤

检索接口支持 `metadata_condition`。

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/retrieval" \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data '{
    "question": "销售额怎么算？",
    "dataset_ids": ["DATASET_ID_METRIC"],
    "metadata_condition": {
      "logic": "and",
      "conditions": [
        {
          "name": "business",
          "comparison_operator": "=",
          "value": "sales"
        },
        {
          "name": "env",
          "comparison_operator": "=",
          "value": "prod"
        }
      ]
    }
  }'
```

---

# 10. Java REST 调用示例

RAGFlow 没有明显官方 Java SDK，建议 Java 后端通过 REST API 封装。

## 10.1 Maven 依赖

```xml
<dependencies>
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>

    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.2</version>
    </dependency>
</dependencies>
```

---

## 10.2 Java 客户端封装

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.File;
import java.util.List;
import java.util.Map;

public class RagflowClient {

    private final String baseUrl;
    private final String apiKey;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    public RagflowClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.http = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    public String createDataset(Map<String, Object> body) throws Exception {
        return postJson("/api/v1/datasets", body);
    }

    public String updateDataset(String datasetId, Map<String, Object> body) throws Exception {
        return putJson("/api/v1/datasets/" + datasetId, body);
    }

    public String listDatasets() throws Exception {
        Request request = requestBuilder("/api/v1/datasets?include_parsing_status=true")
                .get()
                .build();
        return execute(request);
    }

    public String deleteDatasets(List<String> ids) throws Exception {
        return deleteJson("/api/v1/datasets", Map.of("ids", ids));
    }

    public String uploadDocuments(String datasetId, List<File> files) throws Exception {
        MultipartBody.Builder mb = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        for (File file : files) {
            mb.addFormDataPart(
                    "file",
                    file.getName(),
                    RequestBody.create(file, MediaType.parse("application/octet-stream"))
            );
        }

        Request request = requestBuilder("/api/v1/datasets/" + datasetId + "/documents")
                .post(mb.build())
                .build();

        return execute(request);
    }

    public String parseDocuments(String datasetId, List<String> documentIds) throws Exception {
        return postJson(
                "/api/v1/datasets/" + datasetId + "/chunks",
                Map.of("document_ids", documentIds)
        );
    }

    public String deleteDocuments(String datasetId, List<String> documentIds) throws Exception {
        return deleteJson(
                "/api/v1/datasets/" + datasetId + "/documents",
                Map.of("ids", documentIds)
        );
    }

    public String retrieve(
            String question,
            List<String> datasetIds,
            int pageSize,
            double similarityThreshold,
            double vectorSimilarityWeight,
            int topK,
            String rerankId
    ) throws Exception {
        Map<String, Object> body = Map.of(
                "question", question,
                "dataset_ids", datasetIds,
                "page", 1,
                "page_size", pageSize,
                "similarity_threshold", similarityThreshold,
                "vector_similarity_weight", vectorSimilarityWeight,
                "top_k", topK,
                "keyword", true,
                "highlight", true,
                "rerank_id", rerankId == null ? "" : rerankId
        );

        return postJson("/api/v1/retrieval", body);
    }

    public String addChunk(String datasetId, String documentId, Map<String, Object> body) throws Exception {
        return postJson(
                "/api/v1/datasets/" + datasetId + "/documents/" + documentId + "/chunks",
                body
        );
    }

    public String updateChunk(String datasetId, String documentId, String chunkId, Map<String, Object> body) throws Exception {
        return patchJson(
                "/api/v1/datasets/" + datasetId + "/documents/" + documentId + "/chunks/" + chunkId,
                body
        );
    }

    public String deleteChunks(String datasetId, String documentId, List<String> chunkIds) throws Exception {
        return deleteJson(
                "/api/v1/datasets/" + datasetId + "/documents/" + documentId + "/chunks",
                Map.of("chunk_ids", chunkIds)
        );
    }

    private String postJson(String path, Object body) throws Exception {
        RequestBody requestBody = RequestBody.create(
                mapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request request = requestBuilder(path)
                .post(requestBody)
                .build();

        return execute(request);
    }

    private String putJson(String path, Object body) throws Exception {
        RequestBody requestBody = RequestBody.create(
                mapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request request = requestBuilder(path)
                .put(requestBody)
                .build();

        return execute(request);
    }

    private String patchJson(String path, Object body) throws Exception {
        RequestBody requestBody = RequestBody.create(
                mapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request request = requestBuilder(path)
                .patch(requestBody)
                .build();

        return execute(request);
    }

    private String deleteJson(String path, Object body) throws Exception {
        RequestBody requestBody = RequestBody.create(
                mapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request request = requestBuilder(path)
                .delete(requestBody)
                .build();

        return execute(request);
    }

    private Request.Builder requestBuilder(String path) {
        return new Request.Builder()
                .url(baseUrl + path)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");
    }

    private String execute(Request request) throws Exception {
        try (Response response = http.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();

            if (!response.isSuccessful()) {
                throw new RuntimeException("RAGFlow API error. HTTP " + response.code() + ": " + body);
            }

            return body;
        }
    }
}
```

---

## 10.3 Java 创建 Dataset 示例

```java
RagflowClient client = new RagflowClient(
        System.getenv("RAGFLOW_BASE_URL"),
        System.getenv("RAGFLOW_API_KEY")
);

String response = client.createDataset(Map.of(
        "name", "kb_datasource",
        "description", "数据源、表结构、字段解释、指标口径知识库",
        "permission", "team",
        "embedding_model", "BAAI/bge-large-zh-v1.5@BAAI",
        "chunk_method", "naive",
        "parser_config", Map.of(
                "chunk_token_num", 768,
                "delimiter", "\n\n",
                "layout_recognize", "DeepDOC",
                "auto_keywords", 5,
                "auto_questions", 2,
                "html4excel", true,
                "parent_child", Map.of(
                        "use_parent_child", true,
                        "children_delimiter", "\n"
                ),
                "raptor", Map.of("use_raptor", false)
        )
));

System.out.println(response);
```

---

## 10.4 Java 上传并解析多文档

```java
String uploadResponse = client.uploadDocuments(
        "DATASET_ID",
        List.of(
                new File("./docs/表结构说明.md"),
                new File("./docs/指标口径.xlsx"),
                new File("./docs/SQL规则.pdf")
        )
);

System.out.println(uploadResponse);

// 从 uploadResponse 中解析出 document_id 列表，然后调用：
String parseResponse = client.parseDocuments(
        "DATASET_ID",
        List.of("DOCUMENT_ID_1", "DOCUMENT_ID_2", "DOCUMENT_ID_3")
);

System.out.println(parseResponse);
```

---

## 10.5 Java 检索示例

```java
String result = client.retrieve(
        "销售额字段应该用哪个表？",
        List.of("DATASET_ID_DATASOURCE", "DATASET_ID_METRIC"),
        10,
        0.2,
        0.4,
        1024,
        ""
);

System.out.println(result);
```

---

## 10.6 Java 带重排检索

```java
String result = client.retrieve(
        "销售额怎么算？",
        List.of("DATASET_ID_METRIC"),
        10,
        0.2,
        0.5,
        1024,
        "RERANK_MODEL_ID"
);

System.out.println(result);
```

---

# 11. 建议你封装的业务接口

不建议业务方直接调用 RAGFlow API。建议你的 Java 服务封装成统一接口。

## 11.1 创建大分类

```http
POST /kb-categories
```

请求：

```json
{
  "kbCode": "datasource",
  "name": "数据源文档集合",
  "description": "表结构、字段说明、指标口径",
  "ragflowDatasetName": "kb_datasource",
  "chunkConfig": {
    "chunkMethod": "naive",
    "chunkTokenNum": 768,
    "delimiter": "\n\n",
    "layoutRecognize": "DeepDOC",
    "parentChild": true
  },
  "embeddingConfig": {
    "model": "BAAI/bge-large-zh-v1.5@BAAI"
  },
  "retrievalConfig": {
    "similarityThreshold": 0.2,
    "vectorSimilarityWeight": 0.4,
    "topK": 1024,
    "rerankId": ""
  }
}
```

处理逻辑：

```text
1. 在业务库保存 kb category 配置
2. 调 RAGFlow POST /api/v1/datasets
3. 保存 RAGFlow dataset_id
```

---

## 11.2 同步多文档

```http
POST /kb-categories/{kbCode}/documents/sync
```

处理逻辑：

```text
1. 查 kbCode 对应的 dataset_id
2. 上传多个文件到 RAGFlow
3. 获取 document_ids
4. 调用 parse documents
5. 记录同步任务状态
```

---

## 11.3 搜索

```http
POST /kb-categories/{kbCode}/search
```

请求：

```json
{
  "query": "销售额怎么算？",
  "pageSize": 10,
  "rerank": true
}
```

处理逻辑：

```text
1. 查 kbCode 对应的 dataset_id
2. 读取 retrievalConfig
3. 调 RAGFlow /api/v1/retrieval
4. 如需要，Java 服务层再做二次 rerank
```

---

## 11.4 跨大分类搜索

```http
POST /kb-categories/search
```

请求：

```json
{
  "query": "销售额字段应该用哪个表？",
  "kbCodes": ["datasource", "metric", "sql_rule"],
  "pageSize": 10,
  "vectorSimilarityWeight": 0.4,
  "rerank": true
}
```

处理逻辑：

```text
1. kbCodes -> dataset_ids
2. 调 /api/v1/retrieval
3. 返回结果中保留 dataset/document/chunk 信息
```

---

# 12. RAGFlow 记忆 API

> 本节只介绍 RAGFlow 原生 Memory HTTP API，不包含项目自身的 Chat memory 设计或业务接口。
>
> 参考版本：RAGFlow 官方当前 HTTP API 参考。Memory API 在 RAGFlow v0.24.0 引入；不同部署版本可能存在字段或权限差异，使用前应以部署实例对应版本的 API 文档为准。

## 12.1 记忆能力和术语

RAGFlow Memory 用于保存 Agent 对话及从对话中提取的记忆。支持的 `memory_type`：

| 类型 | 说明 |
|---|---|
| `raw` | 用户与 Agent 的原始对话；官方文档说明默认/通常必选 |
| `semantic` | 用户和世界的通用知识、事实和偏好 |
| `episodic` | 带时间信息的事件和经历 |
| `procedural` | 技能、习惯和自动化流程 |

一次对话通常通过 `POST /api/v1/messages` 写入 Memory；RAGFlow 负责异步提取、向量化和保存对应的记忆条目。`GET /api/v1/messages/search` 用于按语义和关键词召回，`GET /api/v1/messages` 用于获取最近消息。

## 12.2 认证和环境变量

所有 Memory HTTP API 都使用 RAGFlow API Key：

```bash
export RAGFLOW_BASE_URL="http://127.0.0.1"
export RAGFLOW_API_KEY="你的_RAGFLOW_API_KEY"
export MEMORY_ID="MEMORY_ID"
export MESSAGE_ID="MESSAGE_ID"
export AGENT_ID="AGENT_ID"
export SESSION_ID="SESSION_ID"
export USER_ID="USER_ID"
```

请求头：

```http
Authorization: Bearer <YOUR_API_KEY>
Content-Type: application/json
```

## 12.3 API 总览

| 方法 | 路径 | 作用 |
|---|---|---|
| `POST` | `/api/v1/memories` | 创建 Memory |
| `PUT` | `/api/v1/memories/{memory_id}` | 更新 Memory 配置 |
| `GET` | `/api/v1/memories` | 分页查询 Memory |
| `GET` | `/api/v1/memories/{memory_id}/config` | 查询 Memory 配置 |
| `DELETE` | `/api/v1/memories/{memory_id}` | 删除 Memory |
| `GET` | `/api/v1/memories/{memory_id}` | 分页查询 Memory 中的消息 |
| `POST` | `/api/v1/messages` | 向一个或多个 Memory 写入对话 |
| `DELETE` | `/api/v1/messages/{memory_id}:{message_id}` | 忘记一条消息 |
| `PUT` | `/api/v1/messages/{memory_id}:{message_id}` | 启用或禁用一条消息 |
| `GET` | `/api/v1/messages/search` | 搜索 Memory 消息 |
| `GET` | `/api/v1/messages` | 查询最近消息 |
| `GET` | `/api/v1/messages/{memory_id}:{message_id}/content` | 查询消息完整内容和向量 |

## 12.4 创建 Memory

```http
POST /api/v1/memories
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `name` | `string` | 是 | Memory 名称；要求 BMP 字符，最多 128 个字符 |
| `memory_type` | `string[]` | 是 | 要提取的记忆类型：`raw`、`semantic`、`episodic`、`procedural` |
| `embd_id` | `string` | 是 | Embedding 模型，格式为 `model_name@model_factory`，最多 255 个字符 |
| `llm_id` | `string` | 是 | 用于提取记忆的聊天模型，格式为 `model_name@model_factory`，最多 255 个字符 |

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/memories" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "customer_memory",
    "memory_type": ["raw", "semantic", "episodic"],
    "embd_id": "BAAI/bge-large-zh-v1.5@BAAI",
    "llm_id": "glm-4-flash@ZHIPU-AI"
  }'
```

成功响应的 `data` 为新 Memory 对象，重点字段通常包括 `id`、`name`、`memory_type`、`embd_id`、`llm_id`、`memory_size`、`forgetting_policy` 和 `permissions`。

## 12.5 更新 Memory 配置

```http
PUT /api/v1/memories/{memory_id}
```

路径参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `memory_id` | `string` | 是 | Memory ID |

请求体参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `name` | `string` | 否 | 名称；BMP 字符，最多 128 个字符 |
| `avatar` | `string` | 否 | Base64 编码头像，最多 65535 个字符 |
| `permission` | `string` | 否 | `me` 仅自己管理；`team` 团队成员可管理 |
| `llm_id` | `string` | 否 | 记忆提取使用的聊天模型 |
| `description` | `string` | 否 | 描述 |
| `memory_size` | `int` | 否 | 容量上限，单位 Byte；最大 `10 * 1024 * 1024` |
| `forgetting_policy` | `string` | 否 | 当前官方文档列出 `FIFO`；容量达到上限时清理旧数据 |
| `temperature` | `float` | 否 | 提取模型随机性，范围 `[0, 1]` |
| `system_prompt` | `string` | 否 | 系统级提取指令；官方默认会根据 `memory_type` 组装基础 Prompt，保留其输出要求和格式部分 |
| `user_prompt` | `string` | 否 | 用户自定义的提取/响应要求 |

示例：

```bash
curl --request PUT \
  --url "${RAGFLOW_BASE_URL}/api/v1/memories/${MEMORY_ID}" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "customer_memory_v2",
    "permission": "team",
    "memory_size": 10485760,
    "forgetting_policy": "FIFO",
    "temperature": 0.2
  }'
```

## 12.6 查询 Memory 列表

```http
GET /api/v1/memories
```

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `tenant_id` | `string \| string[]` | - | 按所有者 ID 过滤，支持多个值 |
| `memory_type` | `string \| string[]` | - | 按类型过滤；Memory 包含所传类型之一即可匹配 |
| `storage_type` | `string` | `table` | 消息存储格式；当前文档列出 `table` |
| `keywords` | `string` | - | 按 Memory 名称模糊查询 |
| `page` | `int` | `1` | 页码 |
| `page_size` | `int` | `50` | 每页数量 |

示例：

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/memories?memory_type=semantic,episodic&keywords=customer&page=1&page_size=50" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

成功响应：

```json
{
  "code": 0,
  "data": {
    "memory_list": [
      {
        "id": "MEMORY_ID",
        "name": "customer_memory",
        "memory_type": ["raw", "semantic"],
        "permissions": "me",
        "storage_type": "table",
        "tenant_id": "TENANT_ID"
      }
    ],
    "total_count": 1
  },
  "message": true
}
```

## 12.7 查询和删除 Memory

查询配置：

```http
GET /api/v1/memories/{memory_id}/config
```

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/memories/${MEMORY_ID}/config" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

配置响应通常包含 `memory_type`、`embd_id`、`llm_id`、`memory_size`、`forgetting_policy`、`temperature`、`system_prompt` 和 `user_prompt`。

删除 Memory：

```http
DELETE /api/v1/memories/{memory_id}
```

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/memories/${MEMORY_ID}" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

删除 Memory 会一并影响其下的消息和提取结果。生产环境执行前应先通过消息查询接口确认范围。

## 12.8 向 Memory 添加对话消息

```http
POST /api/v1/messages
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `memory_id` | `string[]` | 是 | 要保存到的 Memory ID 列表，可同时写入多个 Memory |
| `agent_id` | `string` | 是 | 来源 Agent ID |
| `session_id` | `string` | 是 | 会话 ID |
| `user_id` | `string` | 否 | 参与对话的用户 ID；按当前版本权限规则使用 |
| `user_input` | `string` | 是 | 用户输入文本 |
| `agent_response` | `string` | 是 | Agent 回复文本 |

示例：

```bash
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --header "Content-Type: application/json" \
  --data '{
    "memory_id": ["MEMORY_ID"],
    "agent_id": "AGENT_ID",
    "session_id": "SESSION_ID",
    "user_id": "USER_ID",
    "user_input": "我更喜欢用表格展示结果。",
    "agent_response": "好的，后续我会优先使用表格展示。"
  }'
```

成功响应表示消息已提交处理任务，示例返回：

```json
{
  "code": 0,
  "data": null,
  "message": "All add to task."
}
```

写入后，RAGFlow 可能异步提取 `semantic`、`episodic` 或 `procedural` 条目；应通过消息列表和 `task` 字段检查处理状态。

## 12.9 查询 Memory 中的消息

```http
GET /api/v1/memories/{memory_id}
```

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `agent_id` | `string \| string[]` | - | 按来源 Agent ID 过滤 |
| `session_id` | `string` | - | 按会话 ID 模糊查询 |
| `page` | `int` | `1` | 页码 |
| `page_size` | `int` | `50` | 每页数量 |

示例：

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/memories/${MEMORY_ID}?session_id=SESSION_ID&page=1&page_size=50" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

响应中的 `message_list` 可能包含：

```text
message_id、memory_id、message_type、content、agent_id、session_id、user_id、status、valid_at、invalid_at、forget_at、source_id、extract、task
```

其中 `extract` 表示由原始 `raw` 对话提取出的记忆条目，`task.progress` 和 `task.progress_msg` 可用于查看异步提取进度。

## 12.10 忘记或启用/禁用消息

### 忘记消息

```http
DELETE /api/v1/messages/{memory_id}:{message_id}
```

忘记后，该消息不会再被 Agent 检索，并会被清理策略优先处理：

```bash
curl --request DELETE \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages/${MEMORY_ID}:${MESSAGE_ID}" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

### 更新消息状态

```http
PUT /api/v1/messages/{memory_id}:{message_id}
```

请求体：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `status` | `boolean` | 是 | `true` 启用；`false` 禁用。禁用后 Agent 不会检索该消息 |

```bash
curl --request PUT \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages/${MEMORY_ID}:${MESSAGE_ID}" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --header "Content-Type: application/json" \
  --data '{"status": false}'
```

## 12.11 搜索 Memory 消息

```http
GET /api/v1/messages/search
```

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `query` | `string` | - | 必填；自然语言问题或搜索词 |
| `memory_id` | `string \| string[]` | - | 必填；要搜索的 Memory ID，可多个 |
| `agent_id` | `string` | - | 按来源 Agent 过滤 |
| `session_id` | `string` | - | 按会话过滤 |
| `user_id` | `string` | - | 按用户过滤 |
| `similarity_threshold` | `float` | `0.2` | 余弦相似度下限，范围 `[0, 1]`；越高结果越精确但越少 |
| `keywords_similarity_weight` | `float` | `0.7` | 关键词匹配权重，范围 `[0, 1]`；越高越偏关键词匹配 |
| `top_n` | `int` | `10` | 返回最多多少条结果 |

示例：

```bash
curl --get \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages/search" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data-urlencode "query=用户喜欢什么样的结果展示方式？" \
  --data-urlencode "memory_id=${MEMORY_ID}" \
  --data-urlencode "similarity_threshold=0.2" \
  --data-urlencode "keywords_similarity_weight=0.7" \
  --data-urlencode "top_n=10"
```

返回结果为消息数组，通常包含 `content`、`message_id`、`message_type`、`similarity`、`status`、`session_id` 和时间字段。

## 12.12 查询最近消息

```http
GET /api/v1/messages
```

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `memory_id` | `string \| string[]` | - | 必填；要查询的 Memory ID，可多个 |
| `agent_id` | `string` | - | 按来源 Agent 过滤 |
| `session_id` | `string` | - | 按会话过滤 |
| `limit` | `int` | `10` | 返回最近消息数量 |

```bash
curl --get \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data-urlencode "memory_id=${MEMORY_ID}" \
  --data-urlencode "session_id=${SESSION_ID}" \
  --data-urlencode "limit=10"
```

该接口适合在 Agent 调用前获取最近上下文；需要按语义找相关历史时，应使用 `messages/search`。

## 12.13 获取消息完整内容

```http
GET /api/v1/messages/{memory_id}:{message_id}/content
```

```bash
curl --request GET \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages/${MEMORY_ID}:${MESSAGE_ID}/content" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}"
```

响应中的 `content_embed` 是完整 embedding 向量；通常只有排查、审计或调试时需要，不建议在普通业务链路中频繁调用或把向量返回前端。

## 12.14 参考调用流程

```text
创建 Memory
  -> 保存返回的 memory_id
  -> POST /api/v1/messages 写入 user_input + agent_response
  -> 等待/检查提取任务
  -> GET /api/v1/messages/{memory_id} 查看原始和提取消息
  -> GET /api/v1/messages/search 语义检索相关记忆
  -> GET /api/v1/messages 获取最近消息
  -> PUT/DELETE /api/v1/messages/{memory_id}:{message_id} 管理单条记忆
```

一个最小可用案例：

```bash
# 1. 创建 Memory，记下返回的 data.id
# 2. 写入一轮对话
curl --request POST \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --header "Content-Type: application/json" \
  --data '{
    "memory_id": ["MEMORY_ID"],
    "agent_id": "AGENT_ID",
    "session_id": "SESSION_ID",
    "user_id": "USER_ID",
    "user_input": "我喜欢简洁的表格结果。",
    "agent_response": "好的，我会优先使用表格。"
  }'

# 3. 查询最近消息
curl --get \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data-urlencode "memory_id=${MEMORY_ID}" \
  --data-urlencode "limit=10"

# 4. 检索相关记忆
curl --get \
  --url "${RAGFLOW_BASE_URL}/api/v1/messages/search" \
  --header "Authorization: Bearer ${RAGFLOW_API_KEY}" \
  --data-urlencode "query=用户偏好的结果格式" \
  --data-urlencode "memory_id=${MEMORY_ID}" \
  --data-urlencode "top_n=5"
```

## 12.15 使用建议

- `raw` 用于保留原始对话；需要长期偏好或事件召回时，再增加 `semantic`、`episodic` 或 `procedural`。
- 同一轮对话需要写入多个 Memory 时，使用 `memory_id` 数组；不同 Memory 应提前确认模型和记忆类型配置一致或符合业务预期。
- `POST /api/v1/messages` 是异步提取入口，不能把接口返回成功理解为所有语义记忆已经完成；读取 `task` 状态或稍后查询消息列表。
- `similarity_threshold` 先使用默认 `0.2` 验证召回，再根据误召回和漏召回调整；`keywords_similarity_weight` 越高越适合姓名、产品名、编号等精确词检索。
- `GET /api/v1/messages` 适合最近上下文，`GET /api/v1/messages/search` 适合相关记忆，两者不要混用。
- 需要让 Agent 不再使用某条记忆时，优先用状态接口禁用；确认永久遗忘后再调用 DELETE。
- `memory_size` 默认约 5 MB；容量达到上限后由 `forgetting_policy` 清理，生产环境应结合消息大小和向量维度评估容量。
- `user_id`、`agent_id`、`session_id` 应使用业务侧稳定 ID，并在权限边界内传递；不要把用户可修改的显示名称当作身份标识。
- API 文档当前仍存在历史字段命名差异：更新接口参数列表中有 `system_promot` 的旧拼写，但参数说明和返回对象使用 `system_prompt`；调用时使用 `system_prompt`，并以实际部署版本验证。

---

# 13. 推荐落地顺序

## 第一阶段：知识库基础能力

```text
1. 部署 RAGFlow
2. 创建 API Key
3. Java 封装 RagflowClient
4. 创建 Dataset
5. 上传多个文档
6. 解析文档
7. 调 retrieval 搜索
```

## 第二阶段：业务化大分类

```text
1. 自己建 kb_category 表
2. 保存 kbCode、datasetId、chunkConfig、retrievalConfig
3. 封装 /kb-categories 接口
4. 封装多文档同步任务
```

## 第三阶段：检索质量优化

```text
1. 调整 chunk_token_num
2. 调整 delimiter
3. 打开 auto_keywords / auto_questions
4. 针对 PDF 尝试 DeepDOC layout_recognize
5. 调整 vector_similarity_weight
6. 配置 rerank_id
7. Java 服务层增加外部 reranker
```

---

# 14. 数据库表设计建议

## 14.1 kb_category

```sql
CREATE TABLE kb_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    ragflow_dataset_id VARCHAR(64) NOT NULL,
    ragflow_dataset_name VARCHAR(128) NOT NULL,
    chunk_method VARCHAR(64),
    parser_config JSON,
    embedding_model VARCHAR(255),
    retrieval_config JSON,
    status VARCHAR(32) DEFAULT 'enabled',
    created_at DATETIME,
    updated_at DATETIME
);
```

## 14.2 kb_document

```sql
CREATE TABLE kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_code VARCHAR(64) NOT NULL,
    ragflow_dataset_id VARCHAR(64) NOT NULL,
    ragflow_document_id VARCHAR(64) NOT NULL,
    doc_name VARCHAR(255),
    source_type VARCHAR(32),
    source_url TEXT,
    sync_status VARCHAR(32),
    parse_status VARCHAR(32),
    created_at DATETIME,
    updated_at DATETIME
);
```

## 14.3 kb_sync_task

```sql
CREATE TABLE kb_sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_code VARCHAR(64) NOT NULL,
    task_type VARCHAR(32),
    status VARCHAR(32),
    message TEXT,
    started_at DATETIME,
    finished_at DATETIME
);
```

---

# 15. 生产注意事项

## 15.1 Dataset 粒度

推荐：

```text
一个业务大分类 = 一个 RAGFlow Dataset
```

例如：

```text
kb_faq
kb_datasource
kb_components
kb_metrics
kb_sql_rules
```

不要把所有文档都塞进一个 Dataset，除非它们的切片策略、embedding 模型、检索策略完全一致。

---

## 15.2 embedding_model 不要频繁改

RAGFlow 的 Dataset 一旦已经有 chunks，就不建议修改 embedding model。

推荐做法：

```text
要换 embedding model：
1. 新建 Dataset
2. 重新上传文档
3. 重新解析
4. Java 业务侧切换 dataset_id
5. 确认无问题后删除旧 Dataset
```

---

## 15.3 文档更新推荐重建

推荐：

```text
删除旧文档
重新上传新文档
重新解析
```

不推荐：

```text
直接逐个修改 chunk 来模拟文档更新
```

除非只是小范围修订。

---

## 15.4 检索参数建议

| 场景 | similarity_threshold | vector_similarity_weight | top_k | page_size |
|---|---:|---:|---:|---:|
| FAQ | 0.2 | 0.3 | 1024 | 5-10 |
| 指标口径 | 0.2 | 0.4 | 1024 | 10 |
| 表结构/字段 | 0.15 | 0.2-0.4 | 1024 | 10 |
| 组件 schema | 0.2 | 0.4-0.6 | 1024 | 10 |
| 语义问答 | 0.2 | 0.5-0.7 | 1024 | 10 |

---

## 15.5 权限控制

RAGFlow Dataset 权限不等于你业务系统权限。

建议：

```text
1. Java 服务维护用户可访问 kbCode
2. kbCode 映射 dataset_id
3. 搜索时只传用户有权限的 dataset_ids
4. 不要让前端直接传 dataset_ids 调 RAGFlow
```

---

# 16. 最终推荐方案

```text
RAGFlow:
- 负责 Dataset
- 负责文档解析
- 负责切片
- 负责向量化
- 负责搜索
- 负责 rerank

Java 服务:
- 负责业务大分类管理
- 负责权限
- 负责文档同步任务
- 负责封装标准 API
- 负责二次 rerank / 业务排序
- 负责业务元数据存储
```

推荐最终接口：

```text
POST   /kb-categories
PUT    /kb-categories/{kbCode}
DELETE /kb-categories/{kbCode}
GET    /kb-categories

POST   /kb-categories/{kbCode}/documents/sync
DELETE /kb-categories/{kbCode}/documents/{docId}
GET    /kb-categories/{kbCode}/documents

POST   /kb-categories/{kbCode}/search
POST   /kb-categories/search
POST   /kb-categories/{kbCode}/search/rerank
```

---

# 17. 一句话总结

如果你的核心需求是：

```text
创建大分类
每个大分类配置切片方案
基于大分类同步多文档
基于大分类搜索
支持向量化
支持重排
Java 后端调用
```

那么 RAGFlow 可以直接承担知识库核心能力。

最佳实践是：

```text
大分类 = RAGFlow Dataset
文档集合 = Dataset Documents
切片结果 = Chunks
搜索 = /api/v1/retrieval
业务封装 = Java 服务
```

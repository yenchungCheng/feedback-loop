# Feedback Loop - AI 驅動的 WhatsApp 情感分析系統

一個基於 Spring Boot 的智能反饋循環系統，使用本地 Ollama AI 模型進行 WhatsApp 訊息情感分析，並透過人工修正自動優化 AI 提示詞。

## 核心特色

- **本地 AI 處理**: 使用 Ollama 的 gemma2:2b 模型，完全在本機執行，無需外部 API
- **即時監聽機制**: MongoDB Change Stream 即時偵測人工修正
- **自動學習優化**: Critic Agent 分析 AI 與人工判斷的差異，自動改進提示詞
- **環境分離**: Spring Profile 機制支援開發與生產環境切換
- **完整 Docker 化**: 一鍵啟動 MongoDB Replica Set 和 Ollama 服務

## 系統架構

```
WhatsApp 訊息
    ↓
[AISentimentService] ← 使用優化後的提示詞
    ↓ AI 分析
[MongoDB 儲存] ← 儲存 AI 分析結果
    ↓
人工修正 (Override API)
    ↓
[MongoDB Change Stream] ← 即時監聽 humanSentiment 變更
    ↓
[CriticAgentService] ← 分析差異
    ↓
[更新 PromptContext] ← 儲存改進的提示詞
    ↓
形成反饋循環 ↑
```

## 技術棧

- **後端框架**: Spring Boot 3.2.5
- **語言版本**: Java 21
- **資料庫**: MongoDB 7.0 (Replica Set)
- **AI 框架**: LangChain4J 0.34.0
- **AI 模型**: Ollama gemma2:2b (本地執行)
- **容器化**: Docker Compose
- **建置工具**: Gradle

## 前置需求

- Docker & Docker Compose
- Java 21 (JDK)
- Gradle (或使用內建的 Gradle Wrapper)

## 快速開始

### 1. 啟動 Docker 服務

```bash
# 啟動 MongoDB Replica Set 和 Ollama
docker-compose up -d

# 等待服務初始化 (約 30-60 秒)
# MongoDB 需要初始化 Replica Set
# Ollama 需要下載 gemma2:2b 模型 (約 1.6GB)
```

### 2. 驗證服務狀態

```bash
# 檢查 MongoDB Replica Set 狀態
docker exec -it feedback-loop-mongo mongosh --eval "rs.status().ok"
# 應該返回: 1

# 檢查 Ollama 模型
docker exec -it feedback-loop-ollama ollama list
# 應該看到: gemma2:2b

# 測試 Ollama 服務
curl http://localhost:11434/
# 應該看到: Ollama is running
```

### 3. 啟動應用程式

```bash
# 使用開發環境設定
./gradlew bootRun
```

### 4. 驗證應用啟動成功

```bash
# 健康檢查
curl http://localhost:8080/actuator/health

# 預期回應
{
  "status": "UP",
  "components": {
    "mongo": {"status": "UP"}
  }
}
```

## API 端點

### 接收 WhatsApp 訊息並分析

```bash
POST /api/whatsapp/message
Content-Type: application/json

{
  "messageId": "msg-001",
  "fromNumber": "+886912345678",
  "toNumber": "+886987654321",
  "content": "I love this product! It's amazing!"
}
```

**回應範例:**
```json
{
  "id": "69a9373997a9b058ebe3b8de",
  "messageId": "msg-001",
  "fromNumber": "+886912345678",
  "toNumber": "+886987654321",
  "content": "I love this product! It's amazing!",
  "aiSentiment": {
    "sentiment": "POSITIVE",
    "productInterest": "HIGH",
    "confidence": 0.95,
    "reasoning": "Strong positive language with product enthusiasm",
    "analyzedBy": "AI"
  },
  "timestamp": "2026-03-05T16:10:35"
}
```

### 人工修正情感分析

```bash
POST /api/whatsapp/message/{id}/override-sentiment
Content-Type: application/json

{
  "sentiment": "NEUTRAL",
  "productInterest": "MEDIUM",
  "confidence": 1.0,
  "reasoning": "Customer is being sarcastic"
}
```

**說明**: 此 API 會觸發 MongoDB Change Stream，自動啟動 Critic Agent 分析差異並優化提示詞。

### 查詢單一訊息

```bash
GET /api/whatsapp/message/{id}
```

### 查詢所有訊息

```bash
GET /api/whatsapp/messages
```

## Docker 服務管理

### 常用指令

```bash
# 啟動所有服務
docker-compose up -d

# 查看服務狀態
docker-compose ps

# 查看日誌
docker-compose logs -f mongodb
docker-compose logs -f ollama

# 停止服務
docker-compose down

# 重置資料庫 (刪除所有資料)
docker-compose down -v

# 重新下載 Ollama 模型
docker exec -it feedback-loop-ollama ollama pull gemma2:2b
```

### 連接 MongoDB Shell

```bash
# 進入 MongoDB Shell
docker exec -it feedback-loop-mongo mongosh feedback_loop

# 查看所有訊息
db.whatsapp_messages.find().pretty()

# 查看優化後的提示詞
db.prompt_contexts.find().pretty()

# 手動測試 Change Stream
db.whatsapp_messages.watch([
  {
    $match: {
      "updateDescription.updatedFields.humanSentiment": { $exists: true }
    }
  }
])
```

## 系統工作流程

### 1. 訊息接收與 AI 分析

```java
// AISentimentService.java
public SentimentAnalysis analyzeSentiment(String messageContent) {
    // 1. 從 MongoDB 取得最新優化的提示詞
    String prompt = getOptimizedPrompt(messageContent);

    // 2. 呼叫 Ollama 進行分析
    String response = callAi(prompt);

    // 3. 解析回應並返回結果
    return parseSentimentResponse(response);
}
```

### 2. 人工修正觸發 Change Stream

```java
// MongoDBChangeStreamService.java
@PostConstruct
public void startChangeStreamListener() {
    // 監聽 humanSentiment 欄位的更新
    BsonDocument pipeline = BsonDocument.parse("""
        {
            $match: {
                $and: [
                    { operationType: 'update' },
                    { 'updateDescription.updatedFields.humanSentiment': { $exists: true } }
                ]
            }
        }
    """);

    // 偵測到變更時觸發 Critic Agent
    criticAgentService.analyzeAndImprove(updatedMessage);
}
```

### 3. Critic Agent 分析與優化

```java
// CriticAgentService.java
public void analyzeAndImprove(WhatsAppMessage message) {
    // 1. 比較 AI 與人工分析的差異
    if (isSignificantDifference(aiAnalysis, humanAnalysis)) {

        // 2. 請 Ollama 分析為何判斷錯誤
        String criticResponse = callCriticAgent(message, aiAnalysis, humanAnalysis);

        // 3. 更新提示詞到 MongoDB
        updatePromptContext("SENTIMENT_EXTRACTION", improvedPrompt, errorAnalysis);
    }
}
```

## 疑難排解

### MongoDB 無法連線

**症狀**: 應用啟動時顯示 "MongoDB 連線失敗"

**解決方法**:
```bash
# 1. 檢查容器狀態
docker-compose ps

# 2. 檢查 Replica Set 是否初始化
docker exec -it feedback-loop-mongo mongosh --eval "rs.status()"

# 3. 如果未初始化，重新啟動
docker-compose down
docker-compose up -d
```

### Change Stream 不觸發

**症狀**: 更新 humanSentiment 後沒有看到 Critic Agent 分析日誌

**可能原因**:
1. MongoDB 未使用 Replica Set 模式
2. 連線字串缺少 `?replicaSet=rs0` 參數

**解決方法**:
```bash
# 確認連線字串包含 replicaSet 參數
# application.properties 應該有:
# spring.data.mongodb.uri=mongodb://localhost:27017/feedback_loop?replicaSet=rs0

# 驗證 Replica Set 狀態
docker exec -it feedback-loop-mongo mongosh --eval "rs.status().ok"
```

### Ollama 模型回應緩慢

**症狀**: AI 分析需要 20-30 秒以上

**說明**: gemma2:2b 在 CPU 模式下執行速度較慢，這是正常現象。

**優化選項**:
1. 使用更小的模型: `gemma:2b` 或 `tinyllama`
2. 如有 GPU，可配置 Ollama 使用 GPU 加速
3. 調整 LangChain4jConfig 的 timeout 設定

### Ollama 服務無法連線

**症狀**: 應用報錯 "Failed to connect to Ollama"

**解決方法**:
```bash
# 檢查 Ollama 容器狀態
docker logs feedback-loop-ollama

# 驗證服務
curl http://localhost:11434/

# 重啟 Ollama
docker-compose restart ollama
```

### 模型下載失敗或超時

**症狀**: 首次啟動時 ollama-init 容器失敗

**解決方法**:
```bash
# 手動下載模型
docker exec -it feedback-loop-ollama ollama pull gemma2:2b

# 切換到其他模型（如果 gemma2:2b 太大）
# 修改 docker-compose.yml 的 ollama-init 命令
# 和 application.properties 的 model-name
```

## 開發指南

### 查看應用程式日誌

應用啟動後會顯示關鍵資訊：

```
✓ MongoDB 連線成功: feedback_loop
🔄 正在啟動 Change Stream 監聽器...
✓ Change Stream 連線成功，開始監聽...
```

當偵測到人工修正時：

```
🔔 偵測到訊息更新: 69a9373997a9b058ebe3b8de
✅ Human sentiment override detected for message: 69a9373997a9b058ebe3b8de
   AI: POSITIVE -> Human: NEUTRAL
```

### 測試完整反饋循環

```bash
# 1. 發送訊息進行 AI 分析
curl -X POST http://localhost:8080/api/whatsapp/message \
  -H "Content-Type: application/json" \
  -d '{
    "messageId": "test-001",
    "fromNumber": "+886912345678",
    "toNumber": "+886987654321",
    "content": "This product is just okay, nothing special."
  }'

# 記下返回的 id (例如: 69a9373997a9b058ebe3b8de)

# 2. 人工修正分析結果
curl -X POST http://localhost:8080/api/whatsapp/message/69a9373997a9b058ebe3b8de/override-sentiment \
  -H "Content-Type: application/json" \
  -d '{
    "sentiment": "NEGATIVE",
    "productInterest": "LOW",
    "confidence": 1.0,
    "reasoning": "Customer expresses disappointment with lack of features"
  }'

# 3. 查看應用程式日誌，應該看到 Critic Agent 分析過程

# 4. 驗證提示詞已更新
docker exec -it feedback-loop-mongo mongosh feedback_loop \
  --eval "db.prompt_contexts.find().pretty()"
```

### 修改 AI 模型

若要使用不同的 Ollama 模型：

1. 修改 `docker-compose.yml` 第 64 行的模型名稱
2. 修改 `application.properties` 的 `langchain4j.ollama.model-name`
3. 重新啟動服務

```yaml
# docker-compose.yml - ollama-init service
command:
  - |
    echo "Pulling tinyllama model..."
    curl -X POST http://ollama:11434/api/pull -d '{"name": "tinyllama"}'
```

```properties
# application.properties
langchain4j.ollama.model-name=tinyllama
```

## 專案結構

```
feedback-loop/
├── src/main/java/org/example/feedbackloop/
│   ├── config/
│   │   ├── MongoDBConfig.java          # MongoDB 連線設定
│   │   └── LangChain4jConfig.java      # Ollama AI 模型設定
│   ├── controllers/
│   │   └── WhatsAppController.java     # REST API 端點
│   ├── models/
│   │   ├── WhatsAppMessage.java        # 訊息實體
│   │   ├── SentimentAnalysis.java      # 情感分析結果
│   │   └── PromptContext.java          # 提示詞上下文
│   ├── repositories/
│   │   ├── WhatsAppMessageRepository.java
│   │   └── PromptContextRepository.java
│   └── services/
│       ├── AISentimentService.java          # AI 情感分析服務
│       ├── CriticAgentService.java          # Critic Agent 服務
│       ├── MongoDBChangeStreamService.java  # Change Stream 監聽
│       └── GeminiAISentimentService.java    # (備用) Gemini API 整合
├── src/main/resources/
│   ├── application.properties           # 基礎設定
├── docker-compose.yml                   # Docker 服務定義
├── build.gradle                         # Gradle 建置設定
└── README.md                           # 本文件
```
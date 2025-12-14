# OpenAI Responses API SSE Stream — Formatted Reference (Single File)

This document consolidates the full formatting of the provided Server-Sent Events (SSE) stream into a single, readable reference.

---

## 1. Response Lifecycle (High Level)

```text
response.created
→ response.in_progress
→ response.output_item.added
→ response.content_part.added
→ response.output_text.delta (multiple chunks)
→ response.output_text.done
→ response.output_item.done
→ response.completed
```

---

## 2. Events by Category

### 2.1 Response Creation

```json
{
  "event": "response.created",
  "sequence_number": 0,
  "response": {
    "id": "resp_0dc959507cc26e7400693f259e7a688194a1ce7846f52a20b2",
    "model": "gpt-4o-mini-2024-07-18",
    "status": "in_progress",
    "temperature": 1.0,
    "top_p": 1.0,
    "store": true,
    "parallel_tool_calls": true
  }
}
```

---

### 2.2 Output Message Initialization

```json
{
  "event": "response.output_item.added",
  "sequence_number": 2,
  "output_index": 0,
  "item": {
    "id": "msg_0dc959507cc26e7400693f259fac688194a8ea767ae318ebf6",
    "type": "message",
    "role": "assistant",
    "status": "in_progress",
    "content": []
  }
}
```

---

### 2.3 Content Part Initialization

```json
{
  "event": "response.content_part.added",
  "sequence_number": 3,
  "item_id": "msg_0dc959507cc26e7400693f259fac688194a8ea767ae318ebf6",
  "output_index": 0,
  "content_index": 0,
  "part": {
    "type": "output_text",
    "annotations": [],
    "logprobs": [],
    "text": ""
  }
}
```

---

## 3. Token Streaming (Deltas)

Each `response.output_text.delta` represents incremental token emission. Examples:

```json
{
  "event": "response.output_text.delta",
  "sequence_number": 4,
  "delta": "Sure"
}
```

```json
{
  "event": "response.output_text.delta",
  "sequence_number": 9,
  "delta": " three"
}
```

```json
{
  "event": "response.output_text.delta",
  "sequence_number": 19,
  "delta": " dreams"
}
```

Deltas continue until the full message is constructed.

---

## 4. Final Assembled Text

### 4.1 Output Text Completion

```json
{
  "event": "response.output_text.done",
  "sequence_number": 48,
  "text": "Sure! Here are some three-word phrases:\n\n1. **Chase your dreams.**\n2. **Live, laugh, love.**\n3. **Create your reality.**\n\nLet me know if you need more!"
}
```

### 4.2 Output Item Completion

```json
{
  "event": "response.output_item.done",
  "sequence_number": 50,
  "output_index": 0,
  "item": {
    "id": "msg_0dc959507cc26e7400693f259fac688194a8ea767ae318ebf6",
    "type": "message",
    "status": "completed",
    "role": "assistant",
    "content": [
      {
        "type": "output_text",
        "annotations": [],
        "logprobs": [],
        "text": "Sure! Here are some three-word phrases:\n\n1. **Chase your dreams.**\n2. **Live, laugh, love.**\n3. **Create your reality.**\n\nLet me know if you need more!"
      }
    ]
  }
}
```

---

## 5. Response Completion + Usage

```json
{
  "event": "response.completed",
  "sequence_number": 51,
  "response": {
    "id": "resp_0dc959507cc26e7400693f259e7a688194a1ce7846f52a20b2",
    "object": "response",
    "created_at": 1765746078,
    "status": "completed",
    "background": false,
    "error": null,
    "model": "gpt-4o-mini-2024-07-18",
    "parallel_tool_calls": true,
    "store": true,
    "temperature": 1.0,
    "top_p": 1.0,
    "truncation": "disabled",
    "usage": {
      "input_tokens": 11,
      "output_tokens": 45,
      "total_tokens": 56
    }
  }
}
```

---

## 6. Key Takeaways

- Text is streamed token-by-token via `response.output_text.delta`.
- Your UI should render incrementally by appending deltas in order.
- The final authoritative text arrives in `response.output_text.done`.
- Billing/usage is reliably available after `response.completed`.
- This structure maps cleanly to Android (OkHttp SSE/Flow), Web (EventSource/fetch streaming), and backend proxy patterns.


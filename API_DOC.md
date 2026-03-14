# AI 检测接口文档

## 1. 批量检测接口 (新)

该接口用于批量对已上传的图片进行 AI 异常检测。

**请求 URL:**
`POST /model/detectBatch`

**请求头:**
`Content-Type: application/json`

**请求参数 (Body):**

| 参数名 | 类型 | 必选 | 说明 |
| :--- | :--- | :--- | :--- |
| `imageIds` | `List<Long>` | 是 | 需要检测的图片 ID 列表，例如 `[1, 2, 3]` |

**请求示例:**

```json
{
  "imageIds": [101, 102]
}
```

**响应参数:**

返回一个 Map，键为图片 ID，值为该图片的检测结果列表。每个检测结果包含：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `label` | `String` | 异常类别 (P0, P1, P2, P3) |
| `score` | `Number` | 置信度分数 (0.0 - 1.0) |
| `x` | `Number` | 目标框左上角 X 坐标 (像素) |
| `y` | `Number` | 目标框左上角 Y 坐标 (像素) |
| `width` | `Number` | 目标框宽度 (像素) |
| `height` | `Number` | 目标框高度 (像素) |
| `classId` | `Integer` | 类别 ID 索引 |

**响应示例:**

```json
{
  "101": [
    {
      "label": "P0",
      "score": 0.92,
      "x": 120,
      "y": 450,
      "width": 50,
      "height": 80,
      "classId": 0
    }
  ],
  "102": [] // 空数组表示未检测到异常
}
```

## 2. 单张上传检测接口 (旧)

**请求 URL:**
`POST /model/detect`

**请求头:**
`Content-Type: multipart/form-data`

**请求参数:**

| 参数名 | 类型 | 必选 | 说明 |
| :--- | :--- | :--- | :--- |
| `file` | `File` | 是 | 上传的图片文件 |

**响应示例:**

```json
[
  {
    "label": "P1",
    "score": 0.88,
    "x": 50,
    "y": 100,
    "width": 200,
    "height": 150,
    "classId": 1
  }
]
```
